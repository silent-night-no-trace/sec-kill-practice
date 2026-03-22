package com.style.seckill.dto;

import java.util.List;

public record AsyncRequestReconcileResponse(long thresholdSeconds,
                                            int limit,
                                            int markedFailed,
                                            List<String> requestIds) {
}
