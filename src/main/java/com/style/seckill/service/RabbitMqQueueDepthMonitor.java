package com.style.seckill.service;

import com.style.seckill.config.RabbitMqObservabilityProperties;
import com.style.seckill.config.SeckillRabbitMqProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "seckill.rabbitmq", name = {"enabled", "observability.enabled"}, havingValue = "true")
public class RabbitMqQueueDepthMonitor {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqQueueDepthMonitor.class);

    private final AmqpAdmin amqpAdmin;
    private final SeckillRabbitMqProperties rabbitMqProperties;
    private final AtomicInteger mainQueueDepth = new AtomicInteger();
    private final AtomicInteger deadLetterQueueDepth = new AtomicInteger();
    private final AtomicInteger mainQueueConsumers = new AtomicInteger();
    private final MeterRegistry meterRegistry;

    public RabbitMqQueueDepthMonitor(AmqpAdmin amqpAdmin,
                                     SeckillRabbitMqProperties rabbitMqProperties,
                                     MeterRegistry meterRegistry) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitMqProperties = rabbitMqProperties;
        this.meterRegistry = meterRegistry;

        Gauge.builder("seckill.rabbitmq.queue.depth", mainQueueDepth, AtomicInteger::get)
                .description("Current depth of the main RabbitMQ queue")
                .tag("queue", "main")
                .register(meterRegistry);
        Gauge.builder("seckill.rabbitmq.queue.depth", deadLetterQueueDepth, AtomicInteger::get)
                .description("Current depth of the dead-letter RabbitMQ queue")
                .tag("queue", "dead_letter")
                .register(meterRegistry);
        Gauge.builder("seckill.rabbitmq.queue.consumers", mainQueueConsumers, AtomicInteger::get)
                .description("Current number of consumers on the main RabbitMQ queue")
                .tag("queue", "main")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${seckill.rabbitmq.observability.queue-depth-poll-interval-millis:5000}")
    public void collectQueueDepth() {
        try {
            updateMainQueue();
            updateDeadLetterQueue();
        } catch (RuntimeException exception) {
            meterRegistry.counter("seckill.rabbitmq.queue.inspect.errors").increment();
            log.warn("Failed to inspect RabbitMQ queue depth", exception);
        }
    }

    private void updateMainQueue() {
        Properties properties = amqpAdmin.getQueueProperties(rabbitMqProperties.getQueue());
        if (properties == null) {
            mainQueueDepth.set(0);
            mainQueueConsumers.set(0);
            return;
        }
        mainQueueDepth.set(intValue(properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)));
        mainQueueConsumers.set(intValue(properties.get(RabbitAdmin.QUEUE_CONSUMER_COUNT)));
    }

    private void updateDeadLetterQueue() {
        Properties properties = amqpAdmin.getQueueProperties(rabbitMqProperties.getDeadLetterQueue());
        if (properties == null) {
            deadLetterQueueDepth.set(0);
            return;
        }
        deadLetterQueueDepth.set(intValue(properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)));
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
