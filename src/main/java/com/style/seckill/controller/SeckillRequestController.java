package com.style.seckill.controller;

import com.style.seckill.common.ApiResponse;
import com.style.seckill.dto.AsyncPurchaseStatusResponse;
import com.style.seckill.dto.AsyncPurchaseAcceptedResponse;
import com.style.seckill.dto.AsyncRequestReconcileResponse;
import com.style.seckill.service.SeckillAsyncPurchaseService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/seckill/requests")
public class SeckillRequestController {

    private final SeckillAsyncPurchaseService seckillAsyncPurchaseService;

    public SeckillRequestController(SeckillAsyncPurchaseService seckillAsyncPurchaseService) {
        this.seckillAsyncPurchaseService = seckillAsyncPurchaseService;
    }

    @GetMapping("/{requestId}")
    public ApiResponse<AsyncPurchaseStatusResponse> getRequestStatus(@PathVariable String requestId) {
        return ApiResponse.success(seckillAsyncPurchaseService.getRequestStatus(requestId));
    }

    @PostMapping("/{requestId}/replay")
    public ApiResponse<AsyncPurchaseAcceptedResponse> replayRequest(@PathVariable String requestId) {
        return ApiResponse.success(seckillAsyncPurchaseService.replayRequest(requestId));
    }

    @PostMapping("/reconcile")
    public ApiResponse<AsyncRequestReconcileResponse> reconcileStaleRequests(@RequestParam(defaultValue = "300") long thresholdSeconds,
                                                                             @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(seckillAsyncPurchaseService.reconcileStaleRequests(thresholdSeconds, limit));
    }
}
