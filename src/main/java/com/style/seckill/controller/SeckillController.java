package com.style.seckill.controller;

import com.style.seckill.common.ApiResponse;
import com.style.seckill.dto.AsyncPurchaseAcceptedResponse;
import com.style.seckill.dto.CaptchaChallengeResponse;
import com.style.seckill.dto.EventDetailResponse;
import com.style.seckill.dto.EventSummaryResponse;
import com.style.seckill.dto.PagedResponse;
import com.style.seckill.dto.PurchaseOrderResponse;
import com.style.seckill.dto.SeckillAccessTokenResponse;
import com.style.seckill.protection.ClientFingerprintResolver;
import com.style.seckill.service.SeckillAsyncPurchaseService;
import com.style.seckill.service.SeckillProtectionService;
import com.style.seckill.service.SeckillService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/seckill/events")
public class SeckillController {

    private final SeckillService seckillService;
    private final SeckillAsyncPurchaseService seckillAsyncPurchaseService;
    private final SeckillProtectionService seckillProtectionService;
    private final ClientFingerprintResolver clientFingerprintResolver;

    public SeckillController(SeckillService seckillService,
                             SeckillAsyncPurchaseService seckillAsyncPurchaseService,
                             SeckillProtectionService seckillProtectionService,
                             ClientFingerprintResolver clientFingerprintResolver) {
        this.seckillService = seckillService;
        this.seckillAsyncPurchaseService = seckillAsyncPurchaseService;
        this.seckillProtectionService = seckillProtectionService;
        this.clientFingerprintResolver = clientFingerprintResolver;
    }

    @GetMapping
    public ApiResponse<List<EventSummaryResponse>> listEvents() {
        return ApiResponse.success(seckillService.listEvents());
    }

    @GetMapping("/page")
    public ApiResponse<PagedResponse<EventSummaryResponse>> listEventsPage(@RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                                           @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(seckillService.listEvents(page, size));
    }

    @GetMapping("/{eventId}")
    public ApiResponse<EventDetailResponse> getEvent(@PathVariable Long eventId) {
        return ApiResponse.success(seckillService.getEvent(eventId));
    }

    @PostMapping("/{eventId}/purchase")
    public ApiResponse<PurchaseOrderResponse> purchase(@PathVariable Long eventId,
                                                       @RequestParam @NotBlank String userId,
                                                       @RequestParam(required = false) String accessToken,
                                                       HttpServletRequest request) {
        String clientFingerprint = clientFingerprintResolver.resolve(request);
        seckillProtectionService.assertPurchaseAttemptAllowed(eventId, userId, accessToken, clientFingerprint);
        return ApiResponse.success(seckillService.purchase(eventId, userId, accessToken, clientFingerprint));
    }

    @PostMapping("/{eventId}/captcha")
    public ApiResponse<CaptchaChallengeResponse> createCaptcha(@PathVariable Long eventId,
                                                               @RequestParam @NotBlank String userId,
                                                               HttpServletRequest request) {
        return ApiResponse.success(seckillProtectionService.createCaptcha(eventId, userId, clientFingerprintResolver.resolve(request)));
    }

    @PostMapping("/{eventId}/access-token")
    public ApiResponse<SeckillAccessTokenResponse> issueAccessToken(@PathVariable Long eventId,
                                                                    @RequestParam @NotBlank String userId,
                                                                    @RequestParam @NotBlank String challengeId,
                                                                    @RequestParam @NotBlank String captchaAnswer,
                                                                    HttpServletRequest request) {
        return ApiResponse.success(seckillProtectionService.issueAccessToken(
                eventId,
                userId,
                challengeId,
                captchaAnswer,
                clientFingerprintResolver.resolve(request)));
    }

    @PostMapping("/{eventId}/purchase-async")
    public ResponseEntity<ApiResponse<AsyncPurchaseAcceptedResponse>> purchaseAsync(@PathVariable Long eventId,
                                                                                    @RequestParam @NotBlank String userId,
                                                                                    @RequestParam(required = false) String accessToken,
                                                                                    HttpServletRequest request) {
        String clientFingerprint = clientFingerprintResolver.resolve(request);
        seckillProtectionService.assertPurchaseAttemptAllowed(eventId, userId, accessToken, clientFingerprint);
        return ResponseEntity.accepted().body(ApiResponse.success(seckillAsyncPurchaseService.enqueuePurchase(eventId, userId, accessToken, clientFingerprint)));
    }
}
