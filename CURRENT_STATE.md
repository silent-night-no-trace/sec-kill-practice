# Current State

## Overview

- Project: Spring Boot seckill system MVP with progressive production-oriented enhancements.
- Main stack: Java 21, Spring Boot 3.3, Maven, Spring Web, Spring Data JPA, H2, Actuator.
- Current working mode: local-first, zero-dependency by default; optional Redis and RabbitMQ profiles add enhanced behavior.

## What Has Been Implemented

### 1. Base seckill MVP

- Event list/detail query
- Sync purchase flow
- Time-window validation
- Duplicate purchase protection
- Anti-oversell using DB optimistic stock deduction + unique order constraint

### 2. Redis enhancement

- Optional Redis stock reservation gateway
- Lua-based atomic stock reservation and release
- Redis warmup for stock and purchased users
- Redis failure fallback to DB-only flow

## 3. RabbitMQ async ordering

- Async purchase request endpoint
- Async request status query endpoint
- Async request persistence model
- RabbitMQ producer / consumer
- Retry + dead-letter queue + manual ack
- Async failure compensation and final status tracking

### 4. Entry protection layer

- Arithmetic captcha issuance endpoint
- Short-lived seckill access token issuance endpoint
- App-layer rate limiting
- Client fingerprint binding via `X-Client-Id` or fallback remote address
- Protection applied to both sync and async purchase entrypoints

## Current API Flow

### Sync purchase

1. `POST /api/seckill/events/{eventId}/captcha?userId=...`
2. `POST /api/seckill/events/{eventId}/access-token?userId=...&challengeId=...&captchaAnswer=...`
3. `POST /api/seckill/events/{eventId}/purchase?userId=...&accessToken=...`

### Async purchase

1. `POST /api/seckill/events/{eventId}/captcha?userId=...`
2. `POST /api/seckill/events/{eventId}/access-token?userId=...&challengeId=...&captchaAnswer=...`
3. `POST /api/seckill/events/{eventId}/purchase-async?userId=...&accessToken=...`
4. `GET /api/seckill/requests/{requestId}`

## Important Behavior Notes

- Captcha and access token are bound to the same client fingerprint.
- Purchase rate limit is scoped primarily by `action + eventId + clientFingerprint`.
- Access token is not consumed during the early protection precheck.
- Access token is consumed only when the request passes protection and enters the actual purchase execution threshold.
- Missing event is returned as `EVENT_NOT_FOUND` before protection masking.
- Default local mode does not require Redis or RabbitMQ.
- When RabbitMQ is disabled, async purchase returns `ASYNC_QUEUE_DISABLED`.

## Key Files

- Planning docs:
  - `.sisyphus/plans/seckill-mvp.md`
  - `.sisyphus/plans/seckill-rabbitmq-async.md`
  - `.sisyphus/plans/seckill-request-protection.md`
- Core sync flow:
  - `src/main/java/com/example/seckill/service/SeckillService.java`
  - `src/main/java/com/example/seckill/service/SeckillOrderPersistenceService.java`
- Async flow:
  - `src/main/java/com/example/seckill/service/SeckillAsyncPurchaseService.java`
  - `src/main/java/com/example/seckill/service/SeckillAsyncOrderProcessingService.java`
  - `src/main/java/com/example/seckill/service/SeckillAsyncOrderConsumer.java`
  - `src/main/java/com/example/seckill/service/SeckillAsyncDeadLetterConsumer.java`
- Redis flow:
  - `src/main/java/com/example/seckill/stock/RedisStockReservationGateway.java`
  - `src/main/java/com/example/seckill/stock/RedisStockWarmup.java`
  - `src/main/resources/lua/reserve_stock.lua`
  - `src/main/resources/lua/release_stock.lua`
- Protection flow:
  - `src/main/java/com/example/seckill/service/SeckillProtectionService.java`
  - `src/main/java/com/example/seckill/protection/ClientFingerprintResolver.java`
  - `src/main/java/com/example/seckill/controller/SeckillController.java`
  - `src/main/java/com/example/seckill/config/SeckillProtectionProperties.java`
- Docs:
  - `README.md`

## Verification Evidence

- Latest full test run passed:
  - `mvnw.cmd test`
  - Result: `Tests run: 49, Failures: 0, Errors: 0, Skipped: 0`
- Latest package build passed:
  - `mvnw.cmd package`
- Latest local smoke test passed:
  - `/actuator/health` => `UP`
  - captcha issuance works
  - access token issuance works
  - sync purchase with valid token => `SUCCESS`
  - sync purchase without token => `ACCESS_TOKEN_REQUIRED`
  - token replay from different client fingerprint => `ACCESS_TOKEN_INVALID`

## Known Limitations

- H2 is only for local validation, not production parity.
- In-memory protection store and limiter are single-node only.
- Captcha is arithmetic text, not production-grade behavior/image captcha.
- Fingerprint is lightweight (`X-Client-Id` / remote address), not device fingerprinting.
- RabbitMQ mode assumes an external broker when enabled.
- Redis mode assumes an external Redis when enabled.

## Most Natural Next Steps

1. Move protection state and rate limiting from memory to Redis for multi-instance support.
2. Add stronger anti-abuse features: blacklist, device fingerprinting, behavior captcha.
3. Add reconciliation / alerting around Redis, DB, and MQ compensation paths.
4. Replace H2 with MySQL and harden toward a production deployment topology.

## Workspace Status

- Repository root: `C:\Users\leon\Desktop\seck`
- Git repository: not initialized as a git repo in this workspace
- Portable toolchain present for local execution via `mvnw.cmd`
