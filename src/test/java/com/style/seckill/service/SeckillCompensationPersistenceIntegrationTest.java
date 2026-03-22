package com.style.seckill.service;

import com.style.seckill.domain.RedisCompensationTaskStatus;
import com.style.seckill.domain.SeckillEvent;
import com.style.seckill.exception.DuplicatePurchaseException;
import com.style.seckill.repository.RedisCompensationTaskRepository;
import com.style.seckill.repository.SeckillEventRepository;
import com.style.seckill.stock.StockReservationGateway;
import com.style.seckill.stock.StockReservationResult;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class SeckillCompensationPersistenceIntegrationTest {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private SeckillEventRepository seckillEventRepository;

    @Autowired
    private RedisCompensationTaskRepository redisCompensationTaskRepository;

    @MockBean
    private StockReservationGateway stockReservationGateway;

    @MockBean
    private SeckillOrderPersistenceService seckillOrderPersistenceService;

    private Long eventId;

    @BeforeEach
    void setUp() {
        redisCompensationTaskRepository.deleteAll();
        seckillEventRepository.deleteAll();

        SeckillEvent event = new SeckillEvent();
        event.setName("Compensation persistence event");
        event.setStartTime(LocalDateTime.now().minusMinutes(5));
        event.setEndTime(LocalDateTime.now().plusMinutes(30));
        event.setTotalStock(5);
        event.setAvailableStock(5);
        eventId = seckillEventRepository.save(event).getId();

        when(stockReservationGateway.isEnabled()).thenReturn(true);
        when(stockReservationGateway.reserve(eventId, "rollback-user")).thenReturn(StockReservationResult.RESERVED);
    }

    @Test
    void shouldPersistCompensationTaskEvenWhenOuterTransactionRollsBack() {
        when(seckillOrderPersistenceService.persistOrder(eventId, "rollback-user"))
                .thenThrow(new DataIntegrityViolationException("duplicate order"));
        doThrow(new IllegalStateException("redis release failed")).when(stockReservationGateway).release(eventId, "rollback-user");

        assertThrows(DuplicatePurchaseException.class, () -> seckillService.purchase(eventId, "rollback-user"));

        var task = redisCompensationTaskRepository.findByEventIdAndUserId(eventId, "rollback-user").orElseThrow();
        assertThat(task.getStatus()).isEqualTo(RedisCompensationTaskStatus.PENDING);
        assertThat(task.getAttemptCount()).isEqualTo(1);
    }
}
