package com.style.seckill.stock;

import com.style.seckill.config.SeckillRedisProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisStockReservationGatewayTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisScript<Long> reserveStockRedisScript;

    @Mock
    private RedisScript<Long> releaseStockRedisScript;

    private RedisStockReservationGateway redisStockReservationGateway;

    @BeforeEach
    void setUp() {
        SeckillRedisProperties redisProperties = new SeckillRedisProperties();
        redisProperties.setEnabled(true);
        redisStockReservationGateway = new RedisStockReservationGateway(
                stringRedisTemplate,
                reserveStockRedisScript,
                releaseStockRedisScript,
                redisProperties);
    }

    @Test
    void shouldReturnReservedWhenLuaScriptSucceeds() {
        when(stringRedisTemplate.execute(eq(reserveStockRedisScript), anyList(), anyString())).thenReturn(0L);

        StockReservationResult result = redisStockReservationGateway.reserve(1L, "user-a");

        assertThat(result).isEqualTo(StockReservationResult.RESERVED);
    }

    @Test
    void shouldReturnSoldOutWhenLuaScriptReportsNoStock() {
        when(stringRedisTemplate.execute(eq(reserveStockRedisScript), anyList(), anyString())).thenReturn(1L);

        StockReservationResult result = redisStockReservationGateway.reserve(1L, "user-a");

        assertThat(result).isEqualTo(StockReservationResult.SOLD_OUT);
    }

    @Test
    void shouldReturnDuplicateWhenLuaScriptReportsRepeatUser() {
        when(stringRedisTemplate.execute(eq(reserveStockRedisScript), anyList(), anyString())).thenReturn(2L);

        StockReservationResult result = redisStockReservationGateway.reserve(1L, "user-a");

        assertThat(result).isEqualTo(StockReservationResult.DUPLICATE);
    }

    @Test
    void shouldRejectNullScriptReturn() {
        when(stringRedisTemplate.execute(eq(reserveStockRedisScript), anyList(), anyString())).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> redisStockReservationGateway.reserve(1L, "user-a"));
    }

    @Test
    void shouldUseExpectedRedisKeysWhenReleasingReservation() {
        when(stringRedisTemplate.execute(eq(releaseStockRedisScript), anyList(), anyString())).thenReturn(1L);

        boolean released = redisStockReservationGateway.release(9L, "user-z");

        assertThat(released).isTrue();
        verify(stringRedisTemplate).execute(
                eq(releaseStockRedisScript),
                eq(List.of("seckill:stock:9", "seckill:users:9")),
                eq("user-z"));
    }

    @Test
    void shouldReturnFalseWhenReleaseDidNotChangeRedisState() {
        when(stringRedisTemplate.execute(eq(releaseStockRedisScript), anyList(), anyString())).thenReturn(0L);

        boolean released = redisStockReservationGateway.release(9L, "user-z");

        assertThat(released).isFalse();
    }
}
