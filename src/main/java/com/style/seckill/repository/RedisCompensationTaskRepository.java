package com.style.seckill.repository;

import com.style.seckill.domain.RedisCompensationTask;
import com.style.seckill.domain.RedisCompensationTaskStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RedisCompensationTaskRepository extends JpaRepository<RedisCompensationTask, Long> {

    Optional<RedisCompensationTask> findByEventIdAndUserId(Long eventId, String userId);

    List<RedisCompensationTask> findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(RedisCompensationTaskStatus status,
                                                                                              LocalDateTime nextRetryAt,
                                                                                              Pageable pageable);

    long countByStatus(RedisCompensationTaskStatus status);
}
