package com.style.seckill.mq;

import com.style.seckill.dto.AsyncPurchaseOrderMessage;

public interface OrderMessagePublisher {

    boolean isEnabled();

    void publish(AsyncPurchaseOrderMessage message);
}
