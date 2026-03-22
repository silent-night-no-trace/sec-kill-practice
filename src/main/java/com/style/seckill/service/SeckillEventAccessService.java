package com.style.seckill.service;

import com.style.seckill.domain.SeckillEvent;
import com.style.seckill.exception.EventEndedException;
import com.style.seckill.exception.EventNotFoundException;
import com.style.seckill.exception.EventNotStartedException;
import com.style.seckill.exception.SoldOutException;
import com.style.seckill.repository.SeckillEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class SeckillEventAccessService {

    private final SeckillEventRepository seckillEventRepository;
    private final Clock clock;

    public SeckillEventAccessService(SeckillEventRepository seckillEventRepository, Clock clock) {
        this.seckillEventRepository = seckillEventRepository;
        this.clock = clock;
    }

    public SeckillEvent getRequiredEvent(Long eventId) {
        return seckillEventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
    }

    public void validatePurchasable(SeckillEvent event) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!event.hasStarted(now)) {
            throw new EventNotStartedException();
        }
        if (event.hasEnded(now)) {
            throw new EventEndedException();
        }
        if (event.getAvailableStock() <= 0) {
            throw new SoldOutException();
        }
    }
}
