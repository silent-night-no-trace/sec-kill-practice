package com.style.seckill.repository;

import com.style.seckill.domain.PurchaseOrder;
import java.util.Collection;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    boolean existsByEvent_IdAndUserId(Long eventId, String userId);

    long countByEvent_Id(Long eventId);

    long countByEvent_IdAndUserId(Long eventId, String userId);

    Optional<PurchaseOrder> findByOrderNo(String orderNo);

    @Query("select p.userId from PurchaseOrder p where p.event.id = :eventId")
    List<String> findUserIdsByEventId(@Param("eventId") Long eventId);

    @Query("select p.event.id as eventId, p.userId as userId from PurchaseOrder p where p.event.id in :eventIds")
    List<PurchaseOrderEventUserView> findPurchasedUsersByEventIds(@Param("eventIds") Collection<Long> eventIds);
}
