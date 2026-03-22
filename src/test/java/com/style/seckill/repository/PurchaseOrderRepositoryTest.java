package com.style.seckill.repository;

import com.style.seckill.domain.PurchaseOrder;
import com.style.seckill.domain.SeckillEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
class PurchaseOrderRepositoryTest {

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private SeckillEventRepository seckillEventRepository;

    @Test
    void shouldEnforceUniqueEventAndUserConstraint() {
        SeckillEvent event = seckillEventRepository.save(createEvent());

        PurchaseOrder firstOrder = createOrder(event, "same-user", "order-1");
        purchaseOrderRepository.saveAndFlush(firstOrder);

        PurchaseOrder duplicateOrder = createOrder(event, "same-user", "order-2");

        assertThrows(DataIntegrityViolationException.class,
                () -> purchaseOrderRepository.saveAndFlush(duplicateOrder));
    }

    @Test
    void shouldPersistEventAndQueryBack() {
        SeckillEvent savedEvent = seckillEventRepository.save(createEvent());

        SeckillEvent loadedEvent = seckillEventRepository.findById(savedEvent.getId()).orElseThrow();

        assertThat(loadedEvent.getName()).isEqualTo("Repository event");
        assertThat(loadedEvent.getAvailableStock()).isEqualTo(5);
    }

    private SeckillEvent createEvent() {
        SeckillEvent event = new SeckillEvent();
        event.setName("Repository event");
        event.setStartTime(LocalDateTime.now().minusMinutes(10));
        event.setEndTime(LocalDateTime.now().plusMinutes(10));
        event.setTotalStock(5);
        event.setAvailableStock(5);
        return event;
    }

    private PurchaseOrder createOrder(SeckillEvent event, String userId, String orderNo) {
        PurchaseOrder order = new PurchaseOrder();
        order.setEvent(event);
        order.setUserId(userId);
        order.setOrderNo(orderNo);
        order.setCreatedAt(LocalDateTime.now());
        return order;
    }
}
