package com.style.seckill.service;

import com.style.seckill.domain.AsyncPurchaseStatus;
import com.style.seckill.domain.SeckillEvent;
import com.style.seckill.domain.SeckillPurchaseRequest;
import com.style.seckill.dto.AsyncPurchaseOrderMessage;
import com.style.seckill.repository.PurchaseOrderRepository;
import com.style.seckill.repository.SeckillEventRepository;
import com.style.seckill.repository.SeckillPurchaseRequestRepository;
import com.rabbitmq.client.Channel;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@ActiveProfiles({"test", "rabbitmq"})
class SeckillAsyncOrderConsumerTest {

    @Autowired
    private SeckillAsyncOrderConsumer seckillAsyncOrderConsumer;

    @Autowired
    private SeckillEventRepository seckillEventRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private SeckillPurchaseRequestRepository seckillPurchaseRequestRepository;

    @Autowired
    private EntityManager entityManager;

    private Long activeEventId;
    private Long soldOutEventId;

    @BeforeEach
    void setUp() {
        seckillPurchaseRequestRepository.deleteAll();
        purchaseOrderRepository.deleteAll();
        seckillEventRepository.deleteAll();

        activeEventId = seckillEventRepository.save(createEvent("Async active", 5)).getId();
        soldOutEventId = seckillEventRepository.save(createEvent("Async sold out", 0)).getId();
    }

    @Test
    void shouldMarkRequestSuccessAfterConsumerCreatesOrder() {
        SeckillPurchaseRequest request = saveRequest("req-success", activeEventId, "consumer-user-1", false);
        Channel channel = mock(Channel.class);

        try {
            seckillAsyncOrderConsumer.consume(new AsyncPurchaseOrderMessage(request.getRequestId()), channel, 11L);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        entityManager.clear();

        SeckillPurchaseRequest updatedRequest = seckillPurchaseRequestRepository.findByRequestId("req-success").orElseThrow();
        assertThat(updatedRequest.getStatus()).isEqualTo(AsyncPurchaseStatus.SUCCESS);
        assertThat(updatedRequest.getOrderId()).isNotNull();
        assertThat(purchaseOrderRepository.countByEvent_Id(activeEventId)).isEqualTo(1);
        try {
            verify(channel).basicAck(11L, false);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void shouldMarkRequestFailedWhenConsumerCannotCreateOrder() {
        SeckillPurchaseRequest request = saveRequest("req-failed", soldOutEventId, "consumer-user-2", true);
        Channel channel = mock(Channel.class);

        try {
            seckillAsyncOrderConsumer.consume(new AsyncPurchaseOrderMessage(request.getRequestId()), channel, 12L);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        entityManager.clear();

        SeckillPurchaseRequest updatedRequest = seckillPurchaseRequestRepository.findByRequestId("req-failed").orElseThrow();
        assertThat(updatedRequest.getStatus()).isEqualTo(AsyncPurchaseStatus.FAILED);
        assertThat(updatedRequest.getFailureCode()).isEqualTo("SOLD_OUT");
        assertThat(purchaseOrderRepository.countByEvent_Id(soldOutEventId)).isZero();
        try {
            verify(channel).basicAck(12L, false);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private SeckillEvent createEvent(String name, int stock) {
        SeckillEvent event = new SeckillEvent();
        event.setName(name);
        event.setStartTime(LocalDateTime.now().minusMinutes(5));
        event.setEndTime(LocalDateTime.now().plusMinutes(30));
        event.setTotalStock(stock);
        event.setAvailableStock(stock);
        return event;
    }

    private SeckillPurchaseRequest saveRequest(String requestId, Long eventId, String userId, boolean redisReserved) {
        SeckillPurchaseRequest request = new SeckillPurchaseRequest();
        request.setRequestId(requestId);
        request.setEventId(eventId);
        request.setUserId(userId);
        request.setStatus(AsyncPurchaseStatus.PENDING);
        request.setRedisReserved(redisReserved);
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        return seckillPurchaseRequestRepository.saveAndFlush(request);
    }
}
