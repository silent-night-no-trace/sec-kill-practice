package com.style.seckill.stock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "seckill.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpStockReservationGateway implements StockReservationGateway {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public StockReservationResult reserve(Long eventId, String userId) {
        return StockReservationResult.RESERVED;
    }

    @Override
    public boolean release(Long eventId, String userId) {
        return true;
    }
}
