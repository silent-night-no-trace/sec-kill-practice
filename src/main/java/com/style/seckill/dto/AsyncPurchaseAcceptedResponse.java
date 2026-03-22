package com.style.seckill.dto;

public record AsyncPurchaseAcceptedResponse(String requestId,
                                            Long eventId,
                                            String userId,
                                            String status) {
}
