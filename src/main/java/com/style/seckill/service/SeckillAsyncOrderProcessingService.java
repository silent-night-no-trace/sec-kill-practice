package com.style.seckill.service;

import com.style.seckill.common.ErrorCode;
import com.style.seckill.domain.AsyncPurchaseStatus;
import com.style.seckill.domain.RedisCompensationSource;
import com.style.seckill.domain.SeckillPurchaseRequest;
import com.style.seckill.dto.PurchaseOrderResponse;
import com.style.seckill.exception.BusinessException;
import com.style.seckill.repository.SeckillPurchaseRequestRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SeckillAsyncOrderProcessingService {

    public enum AsyncProcessingOutcome {
        SUCCESS,
        HANDLED_FAILURE,
        IGNORED
    }

    private final SeckillPurchaseRequestRepository seckillPurchaseRequestRepository;
    private final SeckillOrderPersistenceService seckillOrderPersistenceService;
    private final StockReservationCoordinator stockReservationCoordinator;
    private final RedisCompensationRecoveryService redisCompensationRecoveryService;
    private final SeckillAsyncRequestStateService seckillAsyncRequestStateService;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public SeckillAsyncOrderProcessingService(SeckillPurchaseRequestRepository seckillPurchaseRequestRepository,
                                              SeckillOrderPersistenceService seckillOrderPersistenceService,
                                              StockReservationCoordinator stockReservationCoordinator,
                                              RedisCompensationRecoveryService redisCompensationRecoveryService,
                                              SeckillAsyncRequestStateService seckillAsyncRequestStateService,
                                              TransactionTemplate transactionTemplate,
                                              MeterRegistry meterRegistry,
                                              Clock clock) {
        this.seckillPurchaseRequestRepository = seckillPurchaseRequestRepository;
        this.seckillOrderPersistenceService = seckillOrderPersistenceService;
        this.stockReservationCoordinator = stockReservationCoordinator;
        this.redisCompensationRecoveryService = redisCompensationRecoveryService;
        this.seckillAsyncRequestStateService = seckillAsyncRequestStateService;
        this.transactionTemplate = transactionTemplate;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public AsyncProcessingOutcome process(String requestId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            AsyncProcessingOutcome outcome = transactionTemplate.execute(status -> processInTransaction(requestId));
            meterRegistry.counter("seckill.async.process", "outcome", outcome == null ? "ignored" : outcome.name().toLowerCase()).increment();
            return outcome;
        } catch (DataIntegrityViolationException exception) {
            SeckillPurchaseRequest request = seckillPurchaseRequestRepository.findByRequestId(requestId).orElse(null);
            if (request != null) {
                redisCompensationRecoveryService.releaseWithRecovery(
                        request.isRedisReserved(),
                        request.getEventId(),
                        request.getUserId(),
                        RedisCompensationSource.ASYNC_PROCESS);
            }
            seckillAsyncRequestStateService.markFailed(requestId, ErrorCode.DUPLICATE_PURCHASE, exception.getMessage());
            meterRegistry.counter("seckill.async.process", "outcome", "handled_failure").increment();
            return AsyncProcessingOutcome.HANDLED_FAILURE;
        } finally {
            sample.stop(meterRegistry.timer("seckill.async.process.latency"));
        }
    }

    private AsyncProcessingOutcome processInTransaction(String requestId) {
        SeckillPurchaseRequest request = seckillPurchaseRequestRepository.findByRequestId(requestId).orElse(null);
        if (request == null || request.getStatus() != AsyncPurchaseStatus.PENDING) {
            return AsyncProcessingOutcome.IGNORED;
        }
        try {
            PurchaseOrderResponse orderResponse = seckillOrderPersistenceService.persistOrder(request.getEventId(), request.getUserId());
            request.setStatus(AsyncPurchaseStatus.SUCCESS);
            request.setOrderId(orderResponse.orderId());
            request.setOrderNo(orderResponse.orderNo());
            request.setFailureCode(null);
            request.setFailureMessage(null);
            request.setUpdatedAt(LocalDateTime.now(clock));
            seckillPurchaseRequestRepository.saveAndFlush(request);
            return AsyncProcessingOutcome.SUCCESS;
        } catch (BusinessException exception) {
            failRequest(request, exception.getErrorCode(), exception.getMessage());
            return AsyncProcessingOutcome.HANDLED_FAILURE;
        }
    }

    private void failRequest(SeckillPurchaseRequest request, ErrorCode errorCode, String failureMessage) {
        redisCompensationRecoveryService.releaseWithRecovery(
                request.isRedisReserved(),
                request.getEventId(),
                request.getUserId(),
                RedisCompensationSource.ASYNC_PROCESS);
        request.setStatus(AsyncPurchaseStatus.FAILED);
        request.setFailureCode(errorCode.getCode());
        request.setFailureMessage(failureMessage == null ? errorCode.getMessage() : failureMessage);
        request.setUpdatedAt(LocalDateTime.now(clock));
        seckillPurchaseRequestRepository.saveAndFlush(request);
    }
}
