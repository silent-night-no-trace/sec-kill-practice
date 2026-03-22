package com.style.seckill.service;

import com.style.seckill.common.ErrorCode;
import com.style.seckill.domain.AsyncPurchaseStatus;
import com.style.seckill.domain.RedisCompensationSource;
import com.style.seckill.domain.SeckillPurchaseRequest;
import com.style.seckill.dto.AsyncPurchaseOrderMessage;
import com.style.seckill.repository.SeckillPurchaseRequestRepository;
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
public class SeckillAsyncDeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeckillAsyncDeadLetterConsumer.class);

    private final SeckillPurchaseRequestRepository seckillPurchaseRequestRepository;
    private final RedisCompensationRecoveryService redisCompensationRecoveryService;
    private final SeckillAsyncRequestStateService seckillAsyncRequestStateService;

    public SeckillAsyncDeadLetterConsumer(SeckillPurchaseRequestRepository seckillPurchaseRequestRepository,
                                          RedisCompensationRecoveryService redisCompensationRecoveryService,
                                          SeckillAsyncRequestStateService seckillAsyncRequestStateService) {
        this.seckillPurchaseRequestRepository = seckillPurchaseRequestRepository;
        this.redisCompensationRecoveryService = redisCompensationRecoveryService;
        this.seckillAsyncRequestStateService = seckillAsyncRequestStateService;
    }

    @RabbitListener(queues = "${seckill.rabbitmq.dead-letter-queue}")
    public void consume(AsyncPurchaseOrderMessage message,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws Exception {
        try {
            SeckillPurchaseRequest request = seckillPurchaseRequestRepository.findByRequestId(message.requestId()).orElse(null);
            if (request != null && request.getStatus() == AsyncPurchaseStatus.PENDING) {
                redisCompensationRecoveryService.releaseWithRecovery(
                        request.isRedisReserved(),
                        request.getEventId(),
                        request.getUserId(),
                        RedisCompensationSource.ASYNC_DEAD_LETTER);
                seckillAsyncRequestStateService.markFailed(
                        request.getRequestId(),
                        ErrorCode.ASYNC_PROCESSING_EXHAUSTED,
                        "Message moved to dead-letter queue after retries were exhausted");
            }
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException exception) {
            log.error("Failed to process dead-letter message for requestId={}", message.requestId(), exception);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
