package com.style.seckill.mq;

import com.style.seckill.dto.AsyncPurchaseOrderMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "seckill.rabbitmq", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpOrderMessagePublisher implements OrderMessagePublisher {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void publish(AsyncPurchaseOrderMessage message) {
        throw new IllegalStateException("RabbitMQ publisher is disabled");
    }
}
