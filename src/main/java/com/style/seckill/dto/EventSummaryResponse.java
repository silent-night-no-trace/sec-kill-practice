package com.style.seckill.dto;

import java.time.LocalDateTime;

public record EventSummaryResponse(Long id,
                                   String name,
                                   LocalDateTime startTime,
                                   LocalDateTime endTime,
                                   int availableStock,
                                   String saleStatus) {
}
