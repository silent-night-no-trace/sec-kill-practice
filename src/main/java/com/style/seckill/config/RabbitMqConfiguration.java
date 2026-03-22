package com.style.seckill.config;

import com.style.seckill.mq.FastJson2AmqpMessageConverter;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "seckill.rabbitmq", name = "enabled", havingValue = "true")
public class RabbitMqConfiguration {

    @Bean
    public DirectExchange seckillOrderExchange(SeckillRabbitMqProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public Queue seckillOrderQueue(SeckillRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getQueue())
                .withArgument("x-dead-letter-exchange", properties.getDeadLetterExchange())
                .withArgument("x-dead-letter-routing-key", properties.getDeadLetterRoutingKey())
                .build();
    }

    @Bean
    public Binding seckillOrderBinding(Queue seckillOrderQueue,
                                       DirectExchange seckillOrderExchange,
                                       SeckillRabbitMqProperties properties) {
        return BindingBuilder.bind(seckillOrderQueue)
                .to(seckillOrderExchange)
                .with(properties.getRoutingKey());
    }

    @Bean
    public DirectExchange seckillDeadLetterExchange(SeckillRabbitMqProperties properties) {
        return new DirectExchange(properties.getDeadLetterExchange(), true, false);
    }

    @Bean
    public Queue seckillDeadLetterQueue(SeckillRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.getDeadLetterQueue()).build();
    }

    @Bean
    public Binding seckillDeadLetterBinding(Queue seckillDeadLetterQueue,
                                            DirectExchange seckillDeadLetterExchange,
                                            SeckillRabbitMqProperties properties) {
        return BindingBuilder.bind(seckillDeadLetterQueue)
                .to(seckillDeadLetterExchange)
                .with(properties.getDeadLetterRoutingKey());
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new FastJson2AmqpMessageConverter();
    }
}
