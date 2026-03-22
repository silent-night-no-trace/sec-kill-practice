package com.style.seckill.mq;

import com.style.seckill.config.SeckillRabbitMqProperties;
import com.style.seckill.dto.AsyncPurchaseOrderMessage;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "seckill.rabbitmq", name = "enabled", havingValue = "true")
public class RabbitMqOrderMessagePublisher implements OrderMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final SeckillRabbitMqProperties properties;

    public RabbitMqOrderMessagePublisher(RabbitTemplate rabbitTemplate,
                                         SeckillRabbitMqProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void publish(AsyncPurchaseOrderMessage message) {
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), message, rabbitMessage -> {
            rabbitMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            rabbitMessage.getMessageProperties().setMessageId(message.requestId());
            return rabbitMessage;
        });
    }
}
