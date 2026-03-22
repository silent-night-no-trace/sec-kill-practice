package com.style.seckill.stock;

import com.style.seckill.domain.SeckillEvent;
import com.style.seckill.config.SeckillRedisProperties;
import com.style.seckill.repository.PurchaseOrderEventUserView;
import com.style.seckill.repository.PurchaseOrderRepository;
import com.style.seckill.repository.SeckillEventRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisStockWarmupTest {

    @Mock
    private SeckillEventRepository seckillEventRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisStockReservationGateway redisStockReservationGateway;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    private RedisStockWarmup redisStockWarmup;
    private SeckillRedisProperties redisProperties;

    @BeforeEach
    void setUp() {
        redisProperties = new SeckillRedisProperties();
        redisStockWarmup = new RedisStockWarmup(
                seckillEventRepository,
                purchaseOrderRepository,
                stringRedisTemplate,
                redisStockReservationGateway,
                redisProperties);
    }

    @Test
    void shouldPreheatStockAndPurchasedUsers() {
        SeckillEvent event = new SeckillEvent();
        event.setName("Warmup event");
        event.setStartTime(LocalDateTime.now().minusMinutes(5));
        event.setEndTime(LocalDateTime.now().plusMinutes(30));
        event.setTotalStock(5);
        event.setAvailableStock(3);

        setEntityId(event, 7L);

        when(seckillEventRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(event)));
        when(purchaseOrderRepository.findPurchasedUsersByEventIds(List.of(7L))).thenReturn(List.of(
                projection(7L, "user-1"),
                projection(7L, "user-2")));
        when(redisStockReservationGateway.stockKey(7L)).thenReturn("seckill:stock:7");
        when(redisStockReservationGateway.userSetKey(7L)).thenReturn("seckill:users:7");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        redisStockWarmup.warmUpRedisStock();

        verify(valueOperations).set("seckill:stock:7", "3");
        verify(stringRedisTemplate).delete("seckill:users:7");
        verify(setOperations).add(eq("seckill:users:7"), any(String[].class));
    }

    private void setEntityId(SeckillEvent event, Long id) {
        try {
            var field = SeckillEvent.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(event, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private PurchaseOrderEventUserView projection(Long eventId, String userId) {
        return new PurchaseOrderEventUserView() {
            @Override
            public Long getEventId() {
                return eventId;
            }

            @Override
            public String getUserId() {
                return userId;
            }
        };
    }
}
