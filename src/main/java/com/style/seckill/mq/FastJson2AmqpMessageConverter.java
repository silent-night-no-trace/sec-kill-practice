package com.style.seckill.mq;

import com.alibaba.fastjson2.JSON;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;

public class FastJson2AmqpMessageConverter implements MessageConverter {

    @Override
    public Message toMessage(Object object, MessageProperties messageProperties) throws MessageConversionException {
        byte[] body = JSON.toJSONBytes(object);
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
        messageProperties.setContentLength(body.length);
        messageProperties.setHeader("__TypeId__", object.getClass().getName());
        return new Message(body, messageProperties);
    }

    @Override
    public Object fromMessage(Message message) throws MessageConversionException {
        byte[] body = message.getBody();
        Object typeId = message.getMessageProperties().getHeaders().get("__TypeId__");
        if (!(typeId instanceof String className)) {
            throw new MessageConversionException("Missing __TypeId__ header for fastjson2 AMQP conversion");
        }

        try {
            Class<?> targetType = Class.forName(className);
            return JSON.parseObject(body, targetType);
        } catch (ClassNotFoundException exception) {
            throw new MessageConversionException("Failed to resolve AMQP payload target type: " + className, exception);
        }
    }
}
