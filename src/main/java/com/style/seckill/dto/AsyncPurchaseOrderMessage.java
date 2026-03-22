package com.style.seckill.dto;

import java.io.Serial;
import java.io.Serializable;

public record AsyncPurchaseOrderMessage(String requestId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
