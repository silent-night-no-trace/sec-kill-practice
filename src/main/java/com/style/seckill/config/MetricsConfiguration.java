package com.style.seckill.config;

import com.style.seckill.domain.AsyncPurchaseStatus;
import com.style.seckill.domain.RedisCompensationTaskStatus;
import com.style.seckill.repository.RedisCompensationTaskRepository;
import com.style.seckill.repository.SeckillPurchaseRequestRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.config.NamingConvention;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class MetricsConfiguration {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTagsCustomizer(
            @Value("${spring.application.name:seckill}") String applicationName,
            Environment environment) {
        String env = resolveEnvironment(environment);
        return registry -> registry.config()
                .namingConvention(NamingConvention.dot)
                .commonTags("application", applicationName, "env", env);
    }

    @Bean
    public MeterBinder asyncRequestGaugeBinder(SeckillPurchaseRequestRepository seckillPurchaseRequestRepository) {
        return registry -> {
            io.micrometer.core.instrument.Gauge.builder("seckill.async.requests", seckillPurchaseRequestRepository,
                            repository -> repository.countByStatus(AsyncPurchaseStatus.PENDING))
                    .description("Current number of pending async purchase requests")
                    .tag("status", "pending")
                    .register(registry);
            io.micrometer.core.instrument.Gauge.builder("seckill.async.requests", seckillPurchaseRequestRepository,
                            repository -> repository.countByStatus(AsyncPurchaseStatus.FAILED))
                    .description("Current number of failed async purchase requests")
                    .tag("status", "failed")
                    .register(registry);
        };
    }

    @Bean
    public MeterBinder redisCompensationGaugeBinder(RedisCompensationTaskRepository redisCompensationTaskRepository) {
        return registry -> {
            io.micrometer.core.instrument.Gauge.builder("seckill.redis.compensation.tasks", redisCompensationTaskRepository,
                            repository -> repository.countByStatus(RedisCompensationTaskStatus.PENDING))
                    .description("Current number of pending Redis compensation tasks")
                    .tag("status", "pending")
                    .register(registry);
            io.micrometer.core.instrument.Gauge.builder("seckill.redis.compensation.tasks", redisCompensationTaskRepository,
                            repository -> repository.countByStatus(RedisCompensationTaskStatus.EXHAUSTED))
                    .description("Current number of exhausted Redis compensation tasks")
                    .tag("status", "exhausted")
                    .register(registry);
        };
    }

    private String resolveEnvironment(Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return "default";
        }
        String primaryProfile = activeProfiles[0];
        if (primaryProfile == null || primaryProfile.isBlank()) {
            return "default";
        }
        return primaryProfile;
    }
}
