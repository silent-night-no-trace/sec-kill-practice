package com.style.seckill.service;

import com.style.seckill.common.IdGenerator;
import com.style.seckill.domain.PurchaseOrder;
import com.style.seckill.dto.PurchaseOrderResponse;
import com.style.seckill.exception.SoldOutException;
import com.style.seckill.repository.PurchaseOrderRepository;
import com.style.seckill.repository.SeckillEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class SeckillOrderPersistenceService {

    private final SeckillEventRepository seckillEventRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final IdGenerator idGenerator;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public SeckillOrderPersistenceService(SeckillEventRepository seckillEventRepository,
                                          PurchaseOrderRepository purchaseOrderRepository,
                                          IdGenerator idGenerator,
                                          MeterRegistry meterRegistry,
                                          Clock clock) {
        this.seckillEventRepository = seckillEventRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.idGenerator = idGenerator;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public PurchaseOrderResponse persistOrder(Long eventId, String userId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            decrementDatabaseStock(eventId);
            PurchaseOrderResponse response = createOrder(eventId, userId);
            meterRegistry.counter("seckill.order.persist", "result", "success").increment();
            return response;
        } catch (SoldOutException exception) {
            meterRegistry.counter("seckill.order.persist", "result", "sold_out").increment();
            throw exception;
        } catch (RuntimeException exception) {
            meterRegistry.counter("seckill.order.persist", "result", "error").increment();
            throw exception;
        } finally {
            sample.stop(meterRegistry.timer("seckill.order.persist.latency"));
        }
    }

    private void decrementDatabaseStock(Long eventId) {
        if (seckillEventRepository.decrementStockIfAvailable(eventId) != 1) {
            throw new SoldOutException();
        }
    }

    private PurchaseOrderResponse createOrder(Long eventId, String userId) {
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo(idGenerator.nextCompactId());
        order.setEvent(seckillEventRepository.getReferenceById(eventId));
        order.setUserId(userId);
        order.setCreatedAt(LocalDateTime.now(clock));

        PurchaseOrder savedOrder = purchaseOrderRepository.saveAndFlush(order);
        return new PurchaseOrderResponse(
                savedOrder.getId(),
                savedOrder.getOrderNo(),
                eventId,
                savedOrder.getUserId(),
                savedOrder.getCreatedAt());
    }
}
