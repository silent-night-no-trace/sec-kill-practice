package com.style.seckill.service;

import com.style.seckill.domain.SeckillEvent;
import com.style.seckill.dto.PurchaseOrderResponse;
import com.style.seckill.exception.DuplicatePurchaseException;
import com.style.seckill.exception.EventEndedException;
import com.style.seckill.exception.EventNotStartedException;
import com.style.seckill.exception.SoldOutException;
import com.style.seckill.repository.PurchaseOrderRepository;
import com.style.seckill.repository.SeckillEventRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class SeckillServiceTest {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private SeckillEventRepository seckillEventRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        purchaseOrderRepository.deleteAll();
        seckillEventRepository.deleteAll();
        executorService = Executors.newFixedThreadPool(20);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    void shouldPurchaseSuccessfully() {
        SeckillEvent event = saveEvent("Active event", LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusMinutes(30), 5);

        PurchaseOrderResponse response = seckillService.purchase(event.getId(), "buyer-1");

        assertThat(response.eventId()).isEqualTo(event.getId());
        assertThat(response.userId()).isEqualTo("buyer-1");
        assertThat(seckillEventRepository.findById(event.getId()).orElseThrow().getAvailableStock()).isEqualTo(4);
    }

    @Test
    void shouldRejectPurchaseBeforeStart() {
        SeckillEvent event = saveEvent("Future event", LocalDateTime.now().plusMinutes(5), LocalDateTime.now().plusMinutes(30), 5);

        assertThrows(EventNotStartedException.class, () -> seckillService.purchase(event.getId(), "future-user"));
    }

    @Test
    void shouldRejectPurchaseAfterEnd() {
        SeckillEvent event = saveEvent("Ended event", LocalDateTime.now().minusHours(1), LocalDateTime.now().minusMinutes(1), 5);

        assertThrows(EventEndedException.class, () -> seckillService.purchase(event.getId(), "late-user"));
    }

    @Test
    void shouldRejectDuplicatePurchaseSequentially() {
        SeckillEvent event = saveEvent("Duplicate event", LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusMinutes(30), 5);

        seckillService.purchase(event.getId(), "repeat-user");

        assertThrows(DuplicatePurchaseException.class, () -> seckillService.purchase(event.getId(), "repeat-user"));
    }

    @Test
    void shouldReturnDuplicatePurchaseEvenAfterStockBecomesEmpty() {
        SeckillEvent event = saveEvent("Duplicate sold-out event", LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusMinutes(30), 1);

        seckillService.purchase(event.getId(), "repeat-user");

        assertThrows(DuplicatePurchaseException.class, () -> seckillService.purchase(event.getId(), "repeat-user"));
    }

    @Test
    void shouldAllowOnlyFiveSuccessesForTwentyConcurrentUsers() throws Exception {
        SeckillEvent event = saveEvent("Concurrent event", LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusMinutes(30), 5);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger soldOutCount = new AtomicInteger();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(20);
        List<Throwable> unexpectedErrors = new ArrayList<>();

        for (int index = 0; index < 20; index++) {
            String userId = "user-" + index;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    seckillService.purchase(event.getId(), userId);
                    successCount.incrementAndGet();
                } catch (SoldOutException exception) {
                    soldOutCount.incrementAndGet();
                } catch (Throwable exception) {
                    synchronized (unexpectedErrors) {
                        unexpectedErrors.add(exception);
                    }
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(endLatch.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(unexpectedErrors).isEmpty();
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(soldOutCount.get()).isEqualTo(15);
        assertThat(purchaseOrderRepository.countByEvent_Id(event.getId())).isEqualTo(5);
        assertThat(seckillEventRepository.findById(event.getId()).orElseThrow().getAvailableStock()).isZero();
    }

    @Test
    void shouldRollbackStockWhenConcurrentDuplicatePurchaseHitsUniqueConstraint() throws Exception {
        SeckillEvent event = saveEvent("Rollback event", LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusMinutes(30), 5);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(10);
        List<Throwable> unexpectedErrors = new ArrayList<>();

        for (int index = 0; index < 10; index++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    seckillService.purchase(event.getId(), "same-user");
                    successCount.incrementAndGet();
                } catch (DuplicatePurchaseException exception) {
                    duplicateCount.incrementAndGet();
                } catch (Throwable exception) {
                    synchronized (unexpectedErrors) {
                        unexpectedErrors.add(exception);
                    }
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(endLatch.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(unexpectedErrors).isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(9);
        assertThat(purchaseOrderRepository.countByEvent_IdAndUserId(event.getId(), "same-user")).isEqualTo(1);
        assertThat(seckillEventRepository.findById(event.getId()).orElseThrow().getAvailableStock()).isEqualTo(4);
    }

    @Test
    void shouldRejectWhenSoldOut() {
        SeckillEvent event = saveEvent("Sold out event", LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusMinutes(30), 1);

        seckillService.purchase(event.getId(), "buyer-a");

        assertThrows(SoldOutException.class, () -> seckillService.purchase(event.getId(), "buyer-b"));
    }

    private SeckillEvent saveEvent(String name, LocalDateTime startTime, LocalDateTime endTime, int stock) {
        SeckillEvent event = new SeckillEvent();
        event.setName(name);
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        event.setTotalStock(stock);
        event.setAvailableStock(stock);
        return seckillEventRepository.save(event);
    }
}
