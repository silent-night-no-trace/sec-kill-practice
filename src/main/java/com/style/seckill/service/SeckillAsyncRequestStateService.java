package com.style.seckill.service;

import com.style.seckill.common.ErrorCode;
import com.style.seckill.domain.AsyncPurchaseStatus;
import com.style.seckill.domain.SeckillPurchaseRequest;
import com.style.seckill.repository.SeckillPurchaseRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeckillAsyncRequestStateService {

    private final SeckillPurchaseRequestRepository seckillPurchaseRequestRepository;
    private final Clock clock;

    public SeckillAsyncRequestStateService(SeckillPurchaseRequestRepository seckillPurchaseRequestRepository,
                                           Clock clock) {
        this.seckillPurchaseRequestRepository = seckillPurchaseRequestRepository;
        this.clock = clock;
    }

    @Transactional
    public SeckillPurchaseRequest savePendingRequest(SeckillPurchaseRequest request) {
        request.setUpdatedAt(LocalDateTime.now(clock));
        return seckillPurchaseRequestRepository.saveAndFlush(request);
    }

    @Transactional
    public void markFailed(String requestId, ErrorCode errorCode, String failureMessage) {
        SeckillPurchaseRequest request = seckillPurchaseRequestRepository.findByRequestId(requestId).orElseThrow();
        request.setStatus(AsyncPurchaseStatus.FAILED);
        request.setFailureCode(errorCode.getCode());
        request.setFailureMessage(failureMessage == null ? errorCode.getMessage() : failureMessage);
        request.setUpdatedAt(LocalDateTime.now(clock));
    }
}
