package com.style.seckill.repository;

import com.style.seckill.domain.SeckillEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeckillEventRepository extends JpaRepository<SeckillEvent, Long> {

    @Modifying
    @Query("""
            update SeckillEvent e
               set e.availableStock = e.availableStock - 1,
                   e.version = e.version + 1
             where e.id = :eventId
               and e.availableStock > 0
            """)
    int decrementStockIfAvailable(@Param("eventId") Long eventId);
}
