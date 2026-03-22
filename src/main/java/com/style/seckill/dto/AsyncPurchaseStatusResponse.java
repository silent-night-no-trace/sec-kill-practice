package com.style.seckill.dto;

import java.time.LocalDateTime;

public record AsyncPurchaseStatusResponse(String requestId,
                                          Long eventId,
                                          String userId,
                                          String status,
                                          String failureCode,
                                          String failureMessage,
                                          Long orderId,
                                          String orderNo,
                                          LocalDateTime createdAt,
                                          LocalDateTime updatedAt) {
}
