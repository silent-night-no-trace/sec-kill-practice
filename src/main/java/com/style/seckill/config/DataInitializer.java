package com.style.seckill.config;

import com.style.seckill.domain.SeckillEvent;
import com.style.seckill.repository.SeckillEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class DataInitializer {

    @Bean
    public ApplicationRunner seedSeckillEvents(SeckillEventRepository seckillEventRepository, Clock clock) {
        return args -> {
            if (seckillEventRepository.count() > 0) {
                return;
            }

            LocalDateTime now = LocalDateTime.now(clock);
            seckillEventRepository.save(createEvent(
                    "Flagship Phone Limited Drop",
                    now.minusMinutes(10),
                    now.plusHours(2),
                    5));
            seckillEventRepository.save(createEvent(
                    "Gaming Laptop Preview Sale",
                    now.plusHours(1),
                    now.plusHours(3),
                    8));
            seckillEventRepository.save(createEvent(
                    "Collector Keyboard Archive Sale",
                    now.minusHours(2),
                    now.minusMinutes(30),
                    0));
        };
    }

    private SeckillEvent createEvent(String name,
                                     LocalDateTime startTime,
                                     LocalDateTime endTime,
                                     int stock) {
        SeckillEvent event = new SeckillEvent();
        event.setName(name);
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        event.setTotalStock(stock);
        event.setAvailableStock(stock);
        return event;
    }
}
