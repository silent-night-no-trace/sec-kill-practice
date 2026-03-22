package com.style.seckill.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.style.seckill.domain.SeckillEvent;
import com.style.seckill.domain.AsyncPurchaseStatus;
import com.style.seckill.domain.SeckillPurchaseRequest;
import com.style.seckill.repository.PurchaseOrderRepository;
import com.style.seckill.repository.SeckillEventRepository;
import com.style.seckill.repository.SeckillPurchaseRequestRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeckillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SeckillEventRepository seckillEventRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private SeckillPurchaseRequestRepository seckillPurchaseRequestRepository;

    private Long activeEventId;
    private Long futureEventId;
    private Long endedEventId;
    private Long singleStockEventId;

    @BeforeEach
    void setUp() {
        purchaseOrderRepository.deleteAll();
        seckillPurchaseRequestRepository.deleteAll();
        seckillEventRepository.deleteAll();

        activeEventId = seckillEventRepository.save(createEvent(
                "Active event",
                LocalDateTime.now().minusMinutes(30),
                LocalDateTime.now().plusMinutes(30),
                5)).getId();
        futureEventId = seckillEventRepository.save(createEvent(
                "Future event",
                LocalDateTime.now().plusMinutes(5),
                LocalDateTime.now().plusMinutes(30),
                3)).getId();
        endedEventId = seckillEventRepository.save(createEvent(
                "Ended event",
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusMinutes(10),
                0)).getId();
        singleStockEventId = seckillEventRepository.save(createEvent(
                "Single stock event",
                LocalDateTime.now().minusMinutes(30),
                LocalDateTime.now().plusMinutes(30),
                1)).getId();
    }

    @Test
    void shouldListEvents() throws Exception {
        mockMvc.perform(get("/api/seckill/events").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(4));
    }

    @Test
    void shouldCapLegacyEventListToDefaultPageSize() throws Exception {
        for (int index = 0; index < 30; index++) {
            seckillEventRepository.save(createEvent(
                    "Extra event " + index,
                    LocalDateTime.now().minusMinutes(10),
                    LocalDateTime.now().plusMinutes(10),
                    2));
        }

        mockMvc.perform(get("/api/seckill/events").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(20));
    }

    @Test
    void shouldListEventsPage() throws Exception {
        mockMvc.perform(get("/api/seckill/events/page")
                        .param("page", "0")
                        .param("size", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(4));
    }

    @Test
    void shouldRejectNegativePageForPagedEvents() throws Exception {
        mockMvc.perform(get("/api/seckill/events/page")
                        .param("page", "-1")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectInvalidSizeForPagedEvents() throws Exception {
        mockMvc.perform(get("/api/seckill/events/page")
                        .param("page", "0")
                        .param("size", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/seckill/events/page")
                        .param("page", "0")
                        .param("size", "101")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldGetEventDetail() throws Exception {
        mockMvc.perform(get("/api/seckill/events/{eventId}", activeEventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(activeEventId))
                .andExpect(jsonPath("$.data.saleStatus").value("ONGOING"));
    }

    @Test
    void shouldReturnNotFoundForMissingEvent() throws Exception {
        mockMvc.perform(get("/api/seckill/events/{eventId}", 9999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void shouldPurchaseSuccessfully() throws Exception {
        String accessToken = issueAccessToken(activeEventId, "controller-user", "client-success");

        mockMvc.perform(post("/api/seckill/events/{eventId}/purchase", activeEventId)
                        .param("userId", "controller-user")
                        .param("accessToken", accessToken)
                        .header("X-Client-Id", "client-success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value(activeEventId))
                .andExpect(jsonPath("$.data.userId").value("controller-user"));
    }

    @Test
    void shouldReturnDuplicatePurchaseError() throws Exception {
        String firstAccessToken = issueAccessToken(activeEventId, "duplicate-user", "client-duplicate");
        mockMvc.perform(post("/api/seckill/events/{eventId}/purchase", activeEventId)
                .param("userId", "duplicate-user")
                .param("accessToken", firstAccessToken)
                .header("X-Client-Id", "client-duplicate"));

        String secondAccessToken = issueAccessToken(activeEventId, "duplicate-user", "client-duplicate");

        mockMvc.perform(post("/api/seckill/events/{eventId}/purchase", activeEventId)
                        .param("userId", "duplicate-user")
                        .param("accessToken", secondAccessToken)
                        .header("X-Client-Id", "client-duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PURCHASE"));
    }

    @Test
    void shouldReturnDuplicatePurchaseEvenWhenEventIsNowSoldOut() throws Exception {
        String firstAccessToken = issueAccessToken(singleStockEventId, "duplicate-soldout-user", "client-soldout");
        mockMvc.perform(post("/api/seckill/events/{eventId}/purchase", singleStockEventId)
                .param("userId", "duplicate-soldout-user")
                .param("accessToken", firstAccessToken)
                .header("X-Client-Id", "client-soldout"));

        String secondAccessToken = issueAccessToken(singleStockEventId, "duplicate-soldout-user", "client-soldout");

        mockMvc.perform(post("/api/seckill/events/{eventId}/purchase", singleStockEventId)
                        .param("userId", "duplicate-soldout-user")
                        .param("accessToken", secondAccessToken)
                        .header("X-Client-Id", "client-soldout"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PURCHASE"));
    }

    @Test
    void shouldReturnNotStartedError() throws Exception {
        String accessToken = issueAccessToken(futureEventId, "future-user", "client-future");

        mockMvc.perform(post("/api/seckill/events/{eventId}/purchase", futureEventId)
                        .param("userId", "future-user")
                        .param("accessToken", accessToken)
                        .header("X-Client-Id", "client-future"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_STARTED"));
    }

    @Test
    void shouldReturnEndedError() throws Exception {
        String accessToken = issueAccessToken(endedEventId, "ended-user", "client-ended");

        mockMvc.perform(post("/api/seckill/events/{eventId}/purchase", endedEventId)
                        .param("userId", "ended-user")
                        .param("accessToken", accessToken)
                        .header("X-Client-Id", "client-ended"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_ENDED"));
    }

    @Test
    void shouldValidateUserId() throws Exception {
        mockMvc.perform(post("/api/seckill/events/{eventId}/purchase", activeEventId)
                        .param("userId", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldCreateCaptchaChallenge() throws Exception {
        mockMvc.perform(post("/api/seckill/events/{eventId}/captcha", activeEventId)
                        .param("userId", "captcha-user")
                        .header("X-Client-Id", "client-captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.data.question").isNotEmpty());
    }

    @Test
    void shouldIssueAccessTokenAfterCaptcha() throws Exception {
        MvcResult captchaResult = mockMvc.perform(post("/api/seckill/events/{eventId}/captcha", activeEventId)
                        .param("userId", "token-user")
                        .header("X-Client-Id", "client-token"))
                .andExpect(status().isOk())
                .andReturn();

        JSONObject captchaData = readData(captchaResult);
        mockMvc.perform(post("/api/seckill/events/{eventId}/access-token", activeEventId)
                        .param("userId", "token-user")
                        .param("challengeId", captchaData.getString("challengeId"))
                        .param("captchaAnswer", solve(captchaData.getString("question")))
                        .header("X-Client-Id", "client-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void shouldRejectInvalidCaptchaAnswer() throws Exception {
        MvcResult captchaResult = mockMvc.perform(post("/api/seckill/events/{eventId}/captcha", activeEventId)
                        .param("userId", "bad-captcha-user")
                        .header("X-Client-Id", "client-bad-captcha"))
                .andExpect(status().isOk())
                .andReturn();

        JSONObject captchaData = readData(captchaResult);
        mockMvc.perform(post("/api/seckill/events/{eventId}/access-token", activeEventId)
                        .param("userId", "bad-captcha-user")
                        .param("challengeId", captchaData.getString("challengeId"))
                        .param("captchaAnswer", "999")
                        .header("X-Client-Id", "client-bad-captcha"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPTCHA_INVALID"));
    }

    @Test
    void shouldRejectPurchaseWithoutAccessToken() throws Exception {
        mockMvc.perform(post("/api/seckill/events/{eventId}/purchase", activeEventId)
                        .param("userId", "no-token-user")
                        .header("X-Client-Id", "client-no-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCESS_TOKEN_REQUIRED"));
    }

    @Test
    void shouldRejectReusedAccessToken() throws Exception {
        String accessToken = issueAccessToken(activeEventId, "reuse-user", "client-reuse");

        mockMvc.perform(post("/api/seckill/events/{eventId}/purchase", activeEventId)
                        .param("userId", "reuse-user")
                        .param("accessToken", accessToken)
                        .header("X-Client-Id", "client-reuse"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/seckill/events/{eventId}/purchase", activeEventId)
                        .param("userId", "reuse-user")
                        .param("accessToken", accessToken)
                        .header("X-Client-Id", "client-reuse"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCESS_TOKEN_INVALID"));
    }

    @Test
    void shouldRateLimitRepeatedPurchaseAttempts() throws Exception {
        for (int index = 0; index < 3; index++) {
            mockMvc.perform(post("/api/seckill/events/{eventId}/purchase", activeEventId)
                            .param("userId", "rate-limit-user")
                            .param("accessToken", "invalid-token")
                            .header("X-Client-Id", "client-rate-limit"))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/seckill/events/{eventId}/purchase", activeEventId)
                        .param("userId", "rate-limit-user")
                        .param("accessToken", "invalid-token")
                        .header("X-Client-Id", "client-rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void shouldRejectAsyncPurchaseWhenQueueDisabled() throws Exception {
        String accessToken = issueAccessToken(activeEventId, "async-disabled-user", "client-async-disabled");

        mockMvc.perform(post("/api/seckill/events/{eventId}/purchase-async", activeEventId)
                        .param("userId", "async-disabled-user")
                        .param("accessToken", accessToken)
                        .header("X-Client-Id", "client-async-disabled"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ASYNC_QUEUE_DISABLED"));
    }

    @Test
    void shouldQueryAsyncRequestStatus() throws Exception {
        SeckillPurchaseRequest request = new SeckillPurchaseRequest();
        request.setRequestId("request-status-1");
        request.setEventId(activeEventId);
        request.setUserId("status-user");
        request.setStatus(AsyncPurchaseStatus.PENDING);
        request.setRedisReserved(false);
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        seckillPurchaseRequestRepository.saveAndFlush(request);

        mockMvc.perform(get("/api/seckill/requests/{requestId}", "request-status-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value("request-status-1"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void shouldReturnNotFoundForMissingAsyncRequestStatus() throws Exception {
        mockMvc.perform(get("/api/seckill/requests/{requestId}", "missing-request"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASYNC_REQUEST_NOT_FOUND"));
    }

    private SeckillEvent createEvent(String name,
                                     LocalDateTime startTime,
                                     LocalDateTime endTime,
                                     int stock) {
        SeckillEvent event = new SeckillEvent();
        event.setName(name);
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        event.setTotalStock(stock);
        event.setAvailableStock(stock);
        return event;
    }

    private String issueAccessToken(Long eventId, String userId, String clientId) throws Exception {
        MvcResult captchaResult = mockMvc.perform(post("/api/seckill/events/{eventId}/captcha", eventId)
                        .param("userId", userId)
                        .header("X-Client-Id", clientId))
                .andExpect(status().isOk())
                .andReturn();

        JSONObject captchaData = readData(captchaResult);
        MvcResult tokenResult = mockMvc.perform(post("/api/seckill/events/{eventId}/access-token", eventId)
                        .param("userId", userId)
                        .param("challengeId", captchaData.getString("challengeId"))
                        .param("captchaAnswer", solve(captchaData.getString("question")))
                        .header("X-Client-Id", clientId))
                .andExpect(status().isOk())
                .andReturn();

        return readData(tokenResult).getString("accessToken");
    }

    private JSONObject readData(MvcResult result) throws Exception {
        return JSON.parseObject(result.getResponse().getContentAsString()).getJSONObject("data");
    }

    private String solve(String question) {
        String expression = question.replace("= ?", "").trim();
        if (expression.contains("+")) {
            String[] parts = expression.split("\\+");
            return Integer.toString(Integer.parseInt(parts[0].trim()) + Integer.parseInt(parts[1].trim()));
        }
        String[] parts = expression.split("-");
        return Integer.toString(Integer.parseInt(parts[0].trim()) - Integer.parseInt(parts[1].trim()));
    }
}
