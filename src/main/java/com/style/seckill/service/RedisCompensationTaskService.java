package com.style.seckill.service;

import com.style.seckill.config.RedisCompensationRecoveryProperties;
import com.style.seckill.domain.RedisCompensationSource;
import com.style.seckill.domain.RedisCompensationTask;
import com.style.seckill.domain.RedisCompensationTaskStatus;
import com.style.seckill.repository.RedisCompensationTaskRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RedisCompensationTaskService {

    private final RedisCompensationTaskRepository redisCompensationTaskRepository;
    private final RedisCompensationRecoveryProperties redisCompensationRecoveryProperties;
    private final Clock clock;

    public RedisCompensationTaskService(RedisCompensationTaskRepository redisCompensationTaskRepository,
                                        RedisCompensationRecoveryProperties redisCompensationRecoveryProperties,
                                        Clock clock) {
        this.redisCompensationTaskRepository = redisCompensationTaskRepository;
        this.redisCompensationRecoveryProperties = redisCompensationRecoveryProperties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long eventId, String userId, RedisCompensationSource source, String errorMessage) {
        LocalDateTime now = LocalDateTime.now(clock);
        RedisCompensationTask task = redisCompensationTaskRepository.findByEventIdAndUserId(eventId, userId)
                .orElseGet(RedisCompensationTask::new);

        if (task.getId() == null) {
            task.setEventId(eventId);
            task.setUserId(userId);
            task.setCreatedAt(now);
            task.setAttemptCount(0);
        }

        task.setSource(source);
        task.setStatus(RedisCompensationTaskStatus.PENDING);
        task.setAttemptCount(task.getAttemptCount() + 1);
        task.setLastErrorMessage(truncate(errorMessage));
        task.setNextRetryAt(now.plusSeconds(redisCompensationRecoveryProperties.getRetryDelaySeconds()));
        task.setUpdatedAt(now);
        redisCompensationTaskRepository.save(task);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markResolved(Long eventId, String userId) {
        redisCompensationTaskRepository.findByEventIdAndUserId(eventId, userId).ifPresent(task -> {
            task.setStatus(RedisCompensationTaskStatus.RESOLVED);
            task.setUpdatedAt(LocalDateTime.now(clock));
            task.setLastErrorMessage(null);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetryFailure(Long taskId, String errorMessage) {
        RedisCompensationTask task = redisCompensationTaskRepository.findById(taskId).orElseThrow();
        LocalDateTime now = LocalDateTime.now(clock);
        task.setAttemptCount(task.getAttemptCount() + 1);
        task.setLastErrorMessage(truncate(errorMessage));
        task.setUpdatedAt(now);
        if (task.getAttemptCount() >= redisCompensationRecoveryProperties.getMaxAttempts()) {
            task.setStatus(RedisCompensationTaskStatus.EXHAUSTED);
            task.setNextRetryAt(now);
            return;
        }
        task.setStatus(RedisCompensationTaskStatus.PENDING);
        task.setNextRetryAt(now.plusSeconds(redisCompensationRecoveryProperties.getRetryDelaySeconds()));
    }

    @Transactional(readOnly = true)
    public List<RedisCompensationTask> findPendingTasks(int limit) {
        return redisCompensationTaskRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                RedisCompensationTaskStatus.PENDING,
                LocalDateTime.now(clock),
                PageRequest.of(0, limit));
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 255 ? message : message.substring(0, 255);
    }
}
