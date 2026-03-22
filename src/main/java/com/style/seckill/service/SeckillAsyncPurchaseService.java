package com.style.seckill.service;

import com.style.seckill.common.IdGenerator;
import com.style.seckill.common.ErrorCode;
import com.style.seckill.domain.AsyncPurchaseStatus;
import com.style.seckill.domain.RedisCompensationSource;
import com.style.seckill.domain.SeckillEvent;
import com.style.seckill.domain.SeckillPurchaseRequest;
import com.style.seckill.dto.AsyncPurchaseAcceptedResponse;
import com.style.seckill.dto.AsyncPurchaseOrderMessage;
import com.style.seckill.dto.AsyncRequestReconcileResponse;
import com.style.seckill.dto.AsyncPurchaseStatusResponse;
import com.style.seckill.exception.AsyncQueueDisabledException;
import com.style.seckill.exception.AsyncQueueUnavailableException;
import com.style.seckill.exception.AsyncRequestNotFoundException;
import com.style.seckill.exception.DuplicatePurchaseException;
import com.style.seckill.exception.SoldOutException;
import com.style.seckill.mq.OrderMessagePublisher;
import com.style.seckill.repository.PurchaseOrderRepository;
import com.style.seckill.repository.SeckillPurchaseRequestRepository;
import com.style.seckill.stock.StockReservationResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeckillAsyncPurchaseService {

    private static final Logger log = LoggerFactory.getLogger(SeckillAsyncPurchaseService.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SeckillPurchaseRequestRepository seckillPurchaseRequestRepository;
    private final OrderMessagePublisher orderMessagePublisher;
    private final StockReservationCoordinator stockReservationCoordinator;
    private final RedisCompensationRecoveryService redisCompensationRecoveryService;
    private final SeckillAsyncRequestStateService seckillAsyncRequestStateService;
    private final SeckillEventAccessService seckillEventAccessService;
    private final SeckillProtectionService seckillProtectionService;
    private final IdGenerator idGenerator;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public SeckillAsyncPurchaseService(PurchaseOrderRepository purchaseOrderRepository,
                                       SeckillPurchaseRequestRepository seckillPurchaseRequestRepository,
                                       OrderMessagePublisher orderMessagePublisher,
                                       StockReservationCoordinator stockReservationCoordinator,
                                       RedisCompensationRecoveryService redisCompensationRecoveryService,
                                       SeckillAsyncRequestStateService seckillAsyncRequestStateService,
                                       SeckillEventAccessService seckillEventAccessService,
                                       SeckillProtectionService seckillProtectionService,
                                       IdGenerator idGenerator,
                                       MeterRegistry meterRegistry,
                                       Clock clock) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.seckillPurchaseRequestRepository = seckillPurchaseRequestRepository;
        this.orderMessagePublisher = orderMessagePublisher;
        this.stockReservationCoordinator = stockReservationCoordinator;
        this.redisCompensationRecoveryService = redisCompensationRecoveryService;
        this.seckillAsyncRequestStateService = seckillAsyncRequestStateService;
        this.seckillEventAccessService = seckillEventAccessService;
        this.seckillProtectionService = seckillProtectionService;
        this.idGenerator = idGenerator;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public AsyncPurchaseAcceptedResponse enqueuePurchase(Long eventId, String userId) {
        return enqueuePurchase(eventId, userId, null, null);
    }

    public AsyncPurchaseAcceptedResponse enqueuePurchase(Long eventId,
                                                         String userId,
                                                         String accessToken,
                                                         String clientFingerprint) {
        return enqueuePurchaseInternal(eventId, userId, accessToken, clientFingerprint, true);
    }

    public AsyncPurchaseAcceptedResponse replayRequest(String requestId) {
        SeckillPurchaseRequest request = seckillPurchaseRequestRepository.findByRequestId(requestId)
                .orElseThrow(AsyncRequestNotFoundException::new);
        if (request.getStatus() != AsyncPurchaseStatus.FAILED) {
            return toAcceptedResponse(request);
        }
        return enqueuePurchaseInternal(request.getEventId(), request.getUserId(), null, null, false);
    }

    @Transactional
    public AsyncRequestReconcileResponse reconcileStaleRequests(long thresholdSeconds, int limit) {
        LocalDateTime threshold = LocalDateTime.now(clock).minusSeconds(thresholdSeconds);
        List<SeckillPurchaseRequest> staleRequests = seckillPurchaseRequestRepository
                .findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(AsyncPurchaseStatus.PENDING, threshold, PageRequest.of(0, limit));
        for (SeckillPurchaseRequest request : staleRequests) {
            redisCompensationRecoveryService.releaseWithRecovery(
                    request.isRedisReserved(),
                    request.getEventId(),
                    request.getUserId(),
                    RedisCompensationSource.ASYNC_RECONCILE);
            seckillAsyncRequestStateService.markFailed(
                    request.getRequestId(),
                    ErrorCode.ASYNC_REQUEST_STALE,
                    "Marked failed by reconciliation after remaining pending beyond threshold");
        }
        return new AsyncRequestReconcileResponse(
                thresholdSeconds,
                limit,
                staleRequests.size(),
                staleRequests.stream().map(SeckillPurchaseRequest::getRequestId).toList());
    }

    private AsyncPurchaseAcceptedResponse enqueuePurchaseInternal(Long eventId,
                                                                 String userId,
                                                                 String accessToken,
                                                                 String clientFingerprint,
                                                                 boolean consumeProtectionToken) {
        Timer.Sample sample = Timer.start(meterRegistry);
        if (!orderMessagePublisher.isEnabled()) {
            meterRegistry.counter("seckill.async.enqueue", "result", "queue_disabled").increment();
            throw new AsyncQueueDisabledException();
        }

        if (purchaseOrderRepository.existsByEvent_IdAndUserId(eventId, userId)) {
            throw new DuplicatePurchaseException();
        }

        SeckillEvent event = seckillEventAccessService.getRequiredEvent(eventId);
        seckillEventAccessService.validatePurchasable(event);

        SeckillPurchaseRequest existingRequest = seckillPurchaseRequestRepository
                .findByEventIdAndUserId(eventId, userId)
                .orElse(null);
        if (existingRequest != null && existingRequest.getStatus() != AsyncPurchaseStatus.FAILED) {
            meterRegistry.counter("seckill.async.enqueue", "result", "already_pending").increment();
            return toAcceptedResponse(existingRequest);
        }

        StockReservationCoordinator.ReservationAttempt reservationAttempt = stockReservationCoordinator.reserve(eventId, userId);
        if (reservationAttempt.result() == StockReservationResult.DUPLICATE) {
            throw new DuplicatePurchaseException();
        }
        if (reservationAttempt.result() == StockReservationResult.SOLD_OUT) {
            throw new SoldOutException();
        }

        if (consumeProtectionToken) {
            seckillProtectionService.consumeAccessToken(eventId, userId, accessToken, clientFingerprint);
        }

        String requestId = idGenerator.nextCompactId();
        SeckillPurchaseRequest request = existingRequest == null
                ? createPendingRequest(eventId, userId, requestId, reservationAttempt.redisReserved())
                : resetFailedRequest(existingRequest, requestId, reservationAttempt.redisReserved());

        try {
            SeckillPurchaseRequest savedRequest = seckillAsyncRequestStateService.savePendingRequest(request);
            return publishAfterCommit(savedRequest, reservationAttempt.redisReserved());
        } catch (DataIntegrityViolationException exception) {
            redisCompensationRecoveryService.releaseWithRecovery(
                    reservationAttempt.redisReserved(),
                    eventId,
                    userId,
                    RedisCompensationSource.ASYNC_ENQUEUE);
            meterRegistry.counter("seckill.async.enqueue", "result", "duplicate").increment();
            throw new DuplicatePurchaseException();
        } finally {
            sample.stop(meterRegistry.timer("seckill.async.enqueue.latency"));
        }
    }

    private SeckillPurchaseRequest createPendingRequest(Long eventId,
                                                        String userId,
                                                        String requestId,
                                                        boolean redisReserved) {
        SeckillPurchaseRequest request = new SeckillPurchaseRequest();
        request.setEventId(eventId);
        request.setUserId(userId);
        request.setCreatedAt(LocalDateTime.now(clock));
        return applyPendingState(request, requestId, redisReserved);
    }

    private SeckillPurchaseRequest resetFailedRequest(SeckillPurchaseRequest request,
                                                      String requestId,
                                                      boolean redisReserved) {
        return applyPendingState(request, requestId, redisReserved);
    }

    private SeckillPurchaseRequest applyPendingState(SeckillPurchaseRequest request,
                                                     String requestId,
                                                     boolean redisReserved) {
        request.setRequestId(requestId);
        request.setStatus(AsyncPurchaseStatus.PENDING);
        request.setRedisReserved(redisReserved);
        request.setFailureCode(null);
        request.setFailureMessage(null);
        request.setOrderId(null);
        request.setOrderNo(null);
        request.setUpdatedAt(LocalDateTime.now(clock));
        return request;
    }

    private AsyncPurchaseAcceptedResponse publishAfterCommit(SeckillPurchaseRequest request, boolean redisReserved) {
        try {
            orderMessagePublisher.publish(new AsyncPurchaseOrderMessage(request.getRequestId()));
            meterRegistry.counter("seckill.async.enqueue", "result", "accepted").increment();
            return toAcceptedResponse(request);
        } catch (RuntimeException exception) {
            redisCompensationRecoveryService.releaseWithRecovery(
                    redisReserved,
                    request.getEventId(),
                    request.getUserId(),
                    RedisCompensationSource.ASYNC_ENQUEUE);
            seckillAsyncRequestStateService.markFailed(request.getRequestId(), ErrorCode.ASYNC_QUEUE_UNAVAILABLE, exception.getMessage());
            meterRegistry.counter("seckill.async.enqueue", "result", "publish_failed").increment();
            log.warn("Async purchase enqueue failed for eventId={} userId={}",
                    request.getEventId(),
                    request.getUserId(),
                    exception);
            throw new AsyncQueueUnavailableException();
        }
    }

    @Transactional(readOnly = true)
    public AsyncPurchaseStatusResponse getRequestStatus(String requestId) {
        SeckillPurchaseRequest request = seckillPurchaseRequestRepository.findByRequestId(requestId)
                .orElseThrow(AsyncRequestNotFoundException::new);
        return new AsyncPurchaseStatusResponse(
                request.getRequestId(),
                request.getEventId(),
                request.getUserId(),
                request.getStatus().name(),
                request.getFailureCode(),
                request.getFailureMessage(),
                request.getOrderId(),
                request.getOrderNo(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }

    private AsyncPurchaseAcceptedResponse toAcceptedResponse(SeckillPurchaseRequest request) {
        return new AsyncPurchaseAcceptedResponse(
                request.getRequestId(),
                request.getEventId(),
                request.getUserId(),
                request.getStatus().name());
    }

}
