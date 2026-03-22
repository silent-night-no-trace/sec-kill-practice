package com.style.seckill.stock;

import com.style.seckill.config.SeckillRedisProperties;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "seckill.redis", name = "enabled", havingValue = "true")
public class RedisStockReservationGateway implements StockReservationGateway {

    private static final long RESERVED_CODE = 0L;
    private static final long SOLD_OUT_CODE = 1L;
    private static final long DUPLICATE_CODE = 2L;

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> reserveStockRedisScript;
    private final RedisScript<Long> releaseStockRedisScript;
    private final SeckillRedisProperties redisProperties;

    public RedisStockReservationGateway(StringRedisTemplate stringRedisTemplate,
                                        @Qualifier("reserveStockRedisScript") RedisScript<Long> reserveStockRedisScript,
                                        @Qualifier("releaseStockRedisScript") RedisScript<Long> releaseStockRedisScript,
                                        SeckillRedisProperties redisProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.reserveStockRedisScript = reserveStockRedisScript;
        this.releaseStockRedisScript = releaseStockRedisScript;
        this.redisProperties = redisProperties;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public StockReservationResult reserve(Long eventId, String userId) {
        Long result = executeReserveScript(eventId, userId);

        if (result == null) {
            throw new IllegalStateException("Redis stock reservation returned null");
        }
        if (result == RESERVED_CODE) {
            return StockReservationResult.RESERVED;
        }
        if (result == SOLD_OUT_CODE) {
            return StockReservationResult.SOLD_OUT;
        }
        if (result == DUPLICATE_CODE) {
            return StockReservationResult.DUPLICATE;
        }
        throw new IllegalStateException("Unexpected Redis reservation result: " + result);
    }

    @Override
    public boolean release(Long eventId, String userId) {
        Long result = stringRedisTemplate.execute(
                releaseStockRedisScript,
                List.of(stockKey(eventId), userSetKey(eventId)),
                userId);

        if (result == null) {
            throw new IllegalStateException("Redis stock release returned null");
        }
        return result == 1L;
    }

    private Long executeReserveScript(Long eventId, String userId) {
        return stringRedisTemplate.execute(
                reserveStockRedisScript,
                List.of(stockKey(eventId), userSetKey(eventId)),
                userId);
    }

    public String stockKey(Long eventId) {
        return redisProperties.getStockKeyPrefix() + eventId;
    }

    public String userSetKey(Long eventId) {
        return redisProperties.getUserSetKeyPrefix() + eventId;
    }
}
