package com.style.seckill.service;

import com.style.seckill.common.ErrorCode;
import com.style.seckill.dto.AsyncPurchaseOrderMessage;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "seckill.rabbitmq", name = "enabled", havingValue = "true")
public class SeckillAsyncOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeckillAsyncOrderConsumer.class);

    private final SeckillAsyncOrderProcessingService seckillAsyncOrderProcessingService;

    public SeckillAsyncOrderConsumer(SeckillAsyncOrderProcessingService seckillAsyncOrderProcessingService) {
        this.seckillAsyncOrderProcessingService = seckillAsyncOrderProcessingService;
    }

    @RabbitListener(queues = "${seckill.rabbitmq.queue}")
    public void consume(AsyncPurchaseOrderMessage message,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws Exception {
        try {
            seckillAsyncOrderProcessingService.process(message.requestId());
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException exception) {
            log.error("Async order persistence failed for requestId={}", message.requestId(), exception);
            throw exception;
        }
    }
}
