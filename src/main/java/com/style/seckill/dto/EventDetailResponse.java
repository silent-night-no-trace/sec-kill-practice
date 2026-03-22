package com.style.seckill.dto;

import java.time.LocalDateTime;

public record EventDetailResponse(Long id,
                                  String name,
                                  LocalDateTime startTime,
                                  LocalDateTime endTime,
                                  int totalStock,
                                  int availableStock,
                                  String saleStatus) {
}
