package com.style.seckill.service;

import com.style.seckill.domain.SeckillEvent;
import com.style.seckill.dto.EventDetailResponse;
import com.style.seckill.dto.EventSummaryResponse;
import com.style.seckill.dto.PagedResponse;
import com.style.seckill.dto.PurchaseOrderResponse;
import com.style.seckill.exception.DuplicatePurchaseException;
import com.style.seckill.exception.SoldOutException;
import com.style.seckill.repository.PurchaseOrderRepository;
import com.style.seckill.repository.SeckillEventRepository;
import com.style.seckill.stock.StockReservationResult;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeckillService {

    static final int DEFAULT_LIST_EVENTS_SIZE = 20;

    private final SeckillEventRepository seckillEventRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final StockReservationCoordinator stockReservationCoordinator;
    private final RedisCompensationRecoveryService redisCompensationRecoveryService;
    private final SeckillOrderPersistenceService seckillOrderPersistenceService;
    private final SeckillEventAccessService seckillEventAccessService;
    private final SeckillProtectionService seckillProtectionService;
    private final Clock clock;

    public SeckillService(SeckillEventRepository seckillEventRepository,
                          PurchaseOrderRepository purchaseOrderRepository,
                          StockReservationCoordinator stockReservationCoordinator,
                          RedisCompensationRecoveryService redisCompensationRecoveryService,
                          SeckillOrderPersistenceService seckillOrderPersistenceService,
                          SeckillEventAccessService seckillEventAccessService,
                          SeckillProtectionService seckillProtectionService,
                          Clock clock) {
        this.seckillEventRepository = seckillEventRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.stockReservationCoordinator = stockReservationCoordinator;
        this.redisCompensationRecoveryService = redisCompensationRecoveryService;
        this.seckillOrderPersistenceService = seckillOrderPersistenceService;
        this.seckillEventAccessService = seckillEventAccessService;
        this.seckillProtectionService = seckillProtectionService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EventSummaryResponse> listEvents() {
        return listEvents(0, DEFAULT_LIST_EVENTS_SIZE).items();
    }

    @Transactional(readOnly = true)
    public PagedResponse<EventSummaryResponse> listEvents(int page, int size) {
        LocalDateTime now = LocalDateTime.now(clock);
        Page<SeckillEvent> eventPage = seckillEventRepository.findAll(PageRequest.of(page, size));
        List<EventSummaryResponse> items = eventPage.getContent().stream()
                .map(event -> new EventSummaryResponse(
                        event.getId(),
                        event.getName(),
                        event.getStartTime(),
                        event.getEndTime(),
                        event.getAvailableStock(),
                        resolveStatus(event, now)))
                .toList();
        return new PagedResponse<>(
                items,
                eventPage.getNumber(),
                eventPage.getSize(),
                eventPage.getTotalElements(),
                eventPage.getTotalPages(),
                eventPage.hasNext());
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getEvent(Long eventId) {
        SeckillEvent event = seckillEventAccessService.getRequiredEvent(eventId);
        return toDetailResponse(event, LocalDateTime.now(clock));
    }

    @Transactional
    public PurchaseOrderResponse purchase(Long eventId, String userId) {
        return purchase(eventId, userId, null, null);
    }

    @Transactional
    public PurchaseOrderResponse purchase(Long eventId, String userId, String accessToken, String clientFingerprint) {
        SeckillEvent event = seckillEventAccessService.getRequiredEvent(eventId);

        if (purchaseOrderRepository.existsByEvent_IdAndUserId(eventId, userId)) {
            throw new DuplicatePurchaseException();
        }

        seckillEventAccessService.validatePurchasable(event);

        StockReservationCoordinator.ReservationAttempt reservationAttempt = stockReservationCoordinator.reserve(eventId, userId);
        if (reservationAttempt.result() == StockReservationResult.DUPLICATE) {
            throw new DuplicatePurchaseException();
        }
        if (reservationAttempt.result() == StockReservationResult.SOLD_OUT) {
            throw new SoldOutException();
        }

        seckillProtectionService.consumeAccessToken(eventId, userId, accessToken, clientFingerprint);

        try {
            return seckillOrderPersistenceService.persistOrder(eventId, userId);
        } catch (DataIntegrityViolationException exception) {
            redisCompensationRecoveryService.releaseWithRecovery(
                    reservationAttempt.redisReserved(),
                    eventId,
                    userId,
                    com.style.seckill.domain.RedisCompensationSource.SYNC_PURCHASE);
            throw new DuplicatePurchaseException();
        } catch (RuntimeException exception) {
            redisCompensationRecoveryService.releaseWithRecovery(
                    reservationAttempt.redisReserved(),
                    eventId,
                    userId,
                    com.style.seckill.domain.RedisCompensationSource.SYNC_PURCHASE);
            throw exception;
        }
    }

    private EventDetailResponse toDetailResponse(SeckillEvent event, LocalDateTime now) {
        return new EventDetailResponse(
                event.getId(),
                event.getName(),
                event.getStartTime(),
                event.getEndTime(),
                event.getTotalStock(),
                event.getAvailableStock(),
                resolveStatus(event, now));
    }

    private String resolveStatus(SeckillEvent event, LocalDateTime now) {
        if (!event.hasStarted(now)) {
            return "NOT_STARTED";
        }
        if (event.hasEnded(now)) {
            return "ENDED";
        }
        if (event.getAvailableStock() <= 0) {
            return "SOLD_OUT";
        }
        return "ONGOING";
    }
}
