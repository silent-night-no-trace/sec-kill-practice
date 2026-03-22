package com.style.seckill.service;

import com.style.seckill.domain.AsyncPurchaseStatus;
import com.style.seckill.domain.SeckillEvent;
import com.style.seckill.domain.SeckillPurchaseRequest;
import com.style.seckill.dto.AsyncPurchaseOrderMessage;
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
class SeckillAsyncDeadLetterConsumerTest {

    @Autowired
    private SeckillAsyncDeadLetterConsumer seckillAsyncDeadLetterConsumer;

    @Autowired
    private SeckillEventRepository seckillEventRepository;

    @Autowired
    private SeckillPurchaseRequestRepository seckillPurchaseRequestRepository;

    @Autowired
    private EntityManager entityManager;

    private Long eventId;

    @BeforeEach
    void setUp() {
        seckillPurchaseRequestRepository.deleteAll();
        seckillEventRepository.deleteAll();

        SeckillEvent event = new SeckillEvent();
        event.setName("DLQ event");
        event.setStartTime(LocalDateTime.now().minusMinutes(5));
        event.setEndTime(LocalDateTime.now().plusMinutes(30));
        event.setTotalStock(5);
        event.setAvailableStock(5);
        eventId = seckillEventRepository.save(event).getId();
    }

    @Test
    void shouldMarkPendingRequestFailedAfterDeadLetterProcessing() {
        SeckillPurchaseRequest request = new SeckillPurchaseRequest();
        request.setRequestId("dlq-request-1");
        request.setEventId(eventId);
        request.setUserId("dlq-user");
        request.setStatus(AsyncPurchaseStatus.PENDING);
        request.setRedisReserved(false);
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        seckillPurchaseRequestRepository.saveAndFlush(request);
        Channel channel = mock(Channel.class);

        try {
            seckillAsyncDeadLetterConsumer.consume(new AsyncPurchaseOrderMessage("dlq-request-1"), channel, 21L);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        entityManager.clear();

        SeckillPurchaseRequest updatedRequest = seckillPurchaseRequestRepository.findByRequestId("dlq-request-1").orElseThrow();
        assertThat(updatedRequest.getStatus()).isEqualTo(AsyncPurchaseStatus.FAILED);
        assertThat(updatedRequest.getFailureCode()).isEqualTo("ASYNC_PROCESSING_EXHAUSTED");
        try {
            verify(channel).basicAck(21L, false);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
