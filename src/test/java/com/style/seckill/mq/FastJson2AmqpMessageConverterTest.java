package com.style.seckill.mq;

import com.style.seckill.dto.AsyncPurchaseOrderMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FastJson2AmqpMessageConverterTest {

    private final FastJson2AmqpMessageConverter converter = new FastJson2AmqpMessageConverter();

    @Test
    void shouldRoundTripAsyncPurchaseOrderMessage() {
        AsyncPurchaseOrderMessage payload = new AsyncPurchaseOrderMessage("request-123");

        Message message = converter.toMessage(payload, new MessageProperties());
        Object converted = converter.fromMessage(message);

        assertThat(message.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(message.getMessageProperties().getHeaders()).containsEntry("__TypeId__", AsyncPurchaseOrderMessage.class.getName());
        assertThat(converted).isEqualTo(payload);
    }

    @Test
    void shouldRejectMessageWithoutTypeHeader() {
        MessageProperties properties = new MessageProperties();
        Message message = new Message("{}".getBytes(), properties);

        assertThrows(org.springframework.amqp.support.converter.MessageConversionException.class,
                () -> converter.fromMessage(message));
    }
}
