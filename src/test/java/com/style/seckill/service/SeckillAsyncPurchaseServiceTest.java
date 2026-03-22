package com.style.seckill.service;

import com.style.seckill.domain.AsyncPurchaseStatus;
import com.style.seckill.domain.SeckillEvent;
import com.style.seckill.domain.SeckillPurchaseRequest;
import com.style.seckill.dto.AsyncPurchaseAcceptedResponse;
import com.style.seckill.dto.AsyncRequestReconcileResponse;
import com.style.seckill.exception.AsyncQueueDisabledException;
import com.style.seckill.exception.AsyncQueueUnavailableException;
import com.style.seckill.mq.OrderMessagePublisher;
import com.style.seckill.repository.PurchaseOrderRepository;
import com.style.seckill.repository.SeckillEventRepository;
import com.style.seckill.repository.SeckillPurchaseRequestRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class SeckillAsyncPurchaseServiceTest {

    @Autowired
    private SeckillAsyncPurchaseService seckillAsyncPurchaseService;

    @Autowired
    private SeckillEventRepository seckillEventRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private SeckillPurchaseRequestRepository seckillPurchaseRequestRepository;

    @MockBean
    private OrderMessagePublisher orderMessagePublisher;

    private Long eventId;

    @BeforeEach
    void setUp() {
        seckillPurchaseRequestRepository.deleteAll();
        purchaseOrderRepository.deleteAll();
        seckillEventRepository.deleteAll();

        SeckillEvent event = new SeckillEvent();
        event.setName("Async event");
        event.setStartTime(LocalDateTime.now().minusMinutes(5));
        event.setEndTime(LocalDateTime.now().plusMinutes(30));
        event.setTotalStock(5);
        event.setAvailableStock(5);
        eventId = seckillEventRepository.save(event).getId();
    }

    @Test
    void shouldEnqueueAsyncPurchaseRequest() {
        when(orderMessagePublisher.isEnabled()).thenReturn(true);

        AsyncPurchaseAcceptedResponse response = seckillAsyncPurchaseService.enqueuePurchase(eventId, "async-user-1");

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.userId()).isEqualTo("async-user-1");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(seckillPurchaseRequestRepository.findByRequestId(response.requestId())).isPresent();
        verify(orderMessagePublisher).publish(any());
    }

    @Test
    void shouldRetryFailedAsyncRequestWithNewRequestId() {
        when(orderMessagePublisher.isEnabled()).thenReturn(true);
        SeckillPurchaseRequest request = new SeckillPurchaseRequest();
        request.setRequestId("existing-request");
        request.setEventId(eventId);
        request.setUserId("async-user-2");
        request.setStatus(AsyncPurchaseStatus.FAILED);
        request.setRedisReserved(false);
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        seckillPurchaseRequestRepository.saveAndFlush(request);

        AsyncPurchaseAcceptedResponse response = seckillAsyncPurchaseService.enqueuePurchase(eventId, "async-user-2");

        assertThat(response.requestId()).isNotEqualTo("existing-request");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(seckillPurchaseRequestRepository.findByEventIdAndUserId(eventId, "async-user-2").orElseThrow().getRequestId())
                .isEqualTo(response.requestId());
        verify(orderMessagePublisher).publish(any());
    }

    @Test
    void shouldRejectAsyncPurchaseWhenQueueDisabled() {
        when(orderMessagePublisher.isEnabled()).thenReturn(false);

        assertThrows(AsyncQueueDisabledException.class,
                () -> seckillAsyncPurchaseService.enqueuePurchase(eventId, "async-user-3"));
    }

    @Test
    void shouldMarkRequestFailedWhenPublisherThrows() {
        when(orderMessagePublisher.isEnabled()).thenReturn(true);
        doThrow(new IllegalStateException("broker down")).when(orderMessagePublisher).publish(any());

        assertThrows(AsyncQueueUnavailableException.class,
                () -> seckillAsyncPurchaseService.enqueuePurchase(eventId, "async-user-4"));

        SeckillPurchaseRequest failedRequest = seckillPurchaseRequestRepository
                .findByEventIdAndUserId(eventId, "async-user-4")
                .orElseThrow();
        assertThat(failedRequest.getStatus()).isEqualTo(AsyncPurchaseStatus.FAILED);
        assertThat(failedRequest.getFailureCode()).isEqualTo("ASYNC_QUEUE_UNAVAILABLE");
    }

    @Test
    void shouldReplayFailedRequest() {
        when(orderMessagePublisher.isEnabled()).thenReturn(true);
        SeckillPurchaseRequest request = new SeckillPurchaseRequest();
        request.setRequestId("failed-request");
        request.setEventId(eventId);
        request.setUserId("async-user-5");
        request.setStatus(AsyncPurchaseStatus.FAILED);
        request.setRedisReserved(false);
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        seckillPurchaseRequestRepository.saveAndFlush(request);

        AsyncPurchaseAcceptedResponse response = seckillAsyncPurchaseService.replayRequest("failed-request");

        assertThat(response.requestId()).isNotEqualTo("failed-request");
        assertThat(response.status()).isEqualTo("PENDING");
        verify(orderMessagePublisher).publish(any());
    }

    @Test
    void shouldMarkStalePendingRequestsDuringReconciliation() {
        SeckillPurchaseRequest request = new SeckillPurchaseRequest();
        request.setRequestId("stale-request");
        request.setEventId(eventId);
        request.setUserId("async-user-6");
        request.setStatus(AsyncPurchaseStatus.PENDING);
        request.setRedisReserved(false);
        request.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        request.setUpdatedAt(LocalDateTime.now().minusMinutes(10));
        seckillPurchaseRequestRepository.saveAndFlush(request);

        AsyncRequestReconcileResponse response = seckillAsyncPurchaseService.reconcileStaleRequests(60, 10);

        assertThat(response.markedFailed()).isEqualTo(1);
        assertThat(response.requestIds()).containsExactly("stale-request");
        assertThat(seckillPurchaseRequestRepository.findByRequestId("stale-request").orElseThrow().getFailureCode())
                .isEqualTo("ASYNC_REQUEST_STALE");
    }
}
