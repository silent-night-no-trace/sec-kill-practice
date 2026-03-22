package com.style.seckill.service;

import com.style.seckill.config.RabbitMqObservabilityProperties;
import com.style.seckill.config.SeckillRabbitMqProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RabbitMqQueueDepthMonitorTest {

    @Test
    void shouldCollectMainAndDeadLetterQueueDepth() {
        AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
        SeckillRabbitMqProperties rabbitProps = new SeckillRabbitMqProperties();
        rabbitProps.setQueue("main.queue");
        rabbitProps.setDeadLetterQueue("dead.queue");
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        Properties mainProps = new Properties();
        mainProps.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 7);
        mainProps.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, 2);
        Properties dlqProps = new Properties();
        dlqProps.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 3);

        when(amqpAdmin.getQueueProperties("main.queue")).thenReturn(mainProps);
        when(amqpAdmin.getQueueProperties("dead.queue")).thenReturn(dlqProps);

        RabbitMqQueueDepthMonitor monitor = new RabbitMqQueueDepthMonitor(amqpAdmin, rabbitProps, meterRegistry);
        monitor.collectQueueDepth();

        assertThat(meterRegistry.get("seckill.rabbitmq.queue.depth").tag("queue", "main").gauge().value()).isEqualTo(7.0);
        assertThat(meterRegistry.get("seckill.rabbitmq.queue.depth").tag("queue", "dead_letter").gauge().value()).isEqualTo(3.0);
        assertThat(meterRegistry.get("seckill.rabbitmq.queue.consumers").tag("queue", "main").gauge().value()).isEqualTo(2.0);
    }

    @Test
    void shouldRecordInspectionErrors() {
        AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
        SeckillRabbitMqProperties rabbitProps = new SeckillRabbitMqProperties();
        rabbitProps.setQueue("main.queue");
        rabbitProps.setDeadLetterQueue("dead.queue");
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        when(amqpAdmin.getQueueProperties("main.queue")).thenThrow(new IllegalStateException("broker down"));

        RabbitMqQueueDepthMonitor monitor = new RabbitMqQueueDepthMonitor(amqpAdmin, rabbitProps, meterRegistry);
        monitor.collectQueueDepth();

        assertThat(meterRegistry.get("seckill.rabbitmq.queue.inspect.errors").counter().count()).isEqualTo(1.0);
    }
}
