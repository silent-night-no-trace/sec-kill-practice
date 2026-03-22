package com.style.seckill.stock;

import com.style.seckill.domain.SeckillEvent;
import com.style.seckill.repository.PurchaseOrderEventUserView;
import com.style.seckill.repository.PurchaseOrderRepository;
import com.style.seckill.repository.SeckillEventRepository;
import com.style.seckill.config.SeckillRedisProperties;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "seckill.redis", name = "enabled", havingValue = "true")
public class RedisStockWarmup {

    private static final Logger log = LoggerFactory.getLogger(RedisStockWarmup.class);

    private final SeckillEventRepository seckillEventRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisStockReservationGateway redisStockReservationGateway;
    private final SeckillRedisProperties redisProperties;

    public RedisStockWarmup(SeckillEventRepository seckillEventRepository,
                            PurchaseOrderRepository purchaseOrderRepository,
                            StringRedisTemplate stringRedisTemplate,
                            RedisStockReservationGateway redisStockReservationGateway,
                            SeckillRedisProperties redisProperties) {
        this.seckillEventRepository = seckillEventRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisStockReservationGateway = redisStockReservationGateway;
        this.redisProperties = redisProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpRedisStock() {
        try {
            int page = 0;
            Page<SeckillEvent> eventPage;
            do {
                eventPage = seckillEventRepository.findAll(PageRequest.of(page++, redisProperties.getWarmupBatchSize()));
                List<SeckillEvent> events = eventPage.getContent();
                List<Long> eventIds = events.stream().map(SeckillEvent::getId).toList();
                Map<Long, List<String>> purchasedUsersByEvent = purchaseOrderRepository.findPurchasedUsersByEventIds(eventIds).stream()
                        .collect(Collectors.groupingBy(PurchaseOrderEventUserView::getEventId,
                                Collectors.mapping(PurchaseOrderEventUserView::getUserId, Collectors.toList())));

                for (SeckillEvent event : events) {
                    stringRedisTemplate.opsForValue().set(
                            redisStockReservationGateway.stockKey(event.getId()),
                            Integer.toString(event.getAvailableStock()));

                    String userSetKey = redisStockReservationGateway.userSetKey(event.getId());
                    stringRedisTemplate.delete(userSetKey);

                    List<String> purchasedUsers = purchasedUsersByEvent.getOrDefault(event.getId(), List.of());
                    if (!purchasedUsers.isEmpty()) {
                        stringRedisTemplate.opsForSet().add(userSetKey, purchasedUsers.toArray(String[]::new));
                    }
                }
            } while (eventPage.hasNext());
        } catch (RuntimeException exception) {
            log.warn("Redis warmup skipped because Redis is unavailable; service will fall back to DB-only stock deduction", exception);
        }
    }
}
