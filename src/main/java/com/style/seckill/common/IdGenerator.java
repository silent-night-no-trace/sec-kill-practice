package com.style.seckill.common;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdGenerator {

    public String nextCompactId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
