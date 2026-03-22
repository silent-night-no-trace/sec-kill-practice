package com.style.seckill.dto;

import java.time.LocalDateTime;

public record PurchaseOrderResponse(Long orderId,
                                    String orderNo,
                                    Long eventId,
                                    String userId,
                                    LocalDateTime createdAt) {
}
