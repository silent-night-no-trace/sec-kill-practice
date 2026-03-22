package com.style.seckill.repository;

import com.style.seckill.domain.SeckillPurchaseRequest;
import com.style.seckill.domain.AsyncPurchaseStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeckillPurchaseRequestRepository extends JpaRepository<SeckillPurchaseRequest, Long> {

    Optional<SeckillPurchaseRequest> findByRequestId(String requestId);

    Optional<SeckillPurchaseRequest> findByEventIdAndUserId(Long eventId, String userId);

    List<SeckillPurchaseRequest> findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(AsyncPurchaseStatus status,
                                                                                    LocalDateTime updatedAt,
                                                                                    Pageable pageable);

    long countByStatus(AsyncPurchaseStatus status);
}
