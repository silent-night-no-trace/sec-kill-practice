package com.style.seckill.stock;

public interface StockReservationGateway {

    boolean isEnabled();

    StockReservationResult reserve(Long eventId, String userId);

    boolean release(Long eventId, String userId);
}
