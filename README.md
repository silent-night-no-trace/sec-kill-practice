# Spring Boot 秒杀系统 MVP

这是一个从 0 到 1 设计并落地的秒杀系统后端样例，主路线采用 `Java + Spring Boot`。当前版本聚焦单体单节点正确性，优先验证核心业务是否可靠，而不是直接上分布式复杂架构。

## 1. 当前版本解决什么问题

- 活动查询：支持秒杀活动列表和详情查询。
- 秒杀下单：支持同步下单，也支持基于 RabbitMQ 的异步下单请求。
- 入口防护：支持算术验证码、一次性秒杀令牌、应用层限流和基础防刷。
- 防止超卖：默认使用数据库单条原子扣减；启用 Redis 模式后使用 Lua 原子预扣库存。
- 防重复下单：同一 `eventId + userId` 只能成功一次。
- 时间窗控制：活动未开始、已结束时拒绝下单。
- 本地可运行：默认使用 H2 内存数据库，无需额外安装 Redis / MySQL / MQ；若本机有 Redis，可切换到增强模式。
- 可观测性：支持业务指标、Actuator `metrics/prometheus` 端点和统一指标标签。
- 压测准备：提供通用 JMeter 压测计划和压测场景手册。

## 2. 技术路线

### Phase 1：本次实现的 MVP

- 单体应用：Spring Boot 3.3
- 语言版本：Java 21
- 构建工具：Maven
- 持久层：Spring Data JPA + H2
- 接口层：Spring Web + Validation
- 监控基线：Spring Boot Actuator
- 并发策略：数据库原子扣减 + 唯一约束兜底

### Phase 2：当前已接入的增强能力

- Redis 预热活动库存与已购用户集合
- Redis Lua 原子预扣库存 + 用户防重
- 订单落库失败时自动执行 Redis 库存补偿
- RabbitMQ 异步下单入口与请求状态查询
- RabbitMQ 死信队列、消费重试与手动 ack
- 算术验证码、短期一次性秒杀令牌、购买入口限流
- Micrometer 业务指标、统一指标标签、Prometheus 抓取端点
- 通用 JMeter 压测计划与压测手册
- JSON 策略：项目显式控制的 JSON 解析/消息转换统一使用 `fastjson2`；Spring MVC 仍保留 Spring Boot 默认 JSON 基础设施
- 可观测性增强：HTTP `http.server.requests` 分位、RabbitMQ 队列堆积指标、Redis fallback 退化指标

### Phase 2：下一步高并发增强方向

- 黑白名单、设备指纹和更强风控
- 死信队列、重试策略、消息确认增强

### Phase 3：生产化增强方向

- MySQL 持久化与读写分离
- Redis + MQ + 订单补偿机制
- Grafana 看板、指标聚合与告警阈值
- 风控、熔断、降级、分库分表

## 3. 核心设计

### 3.1 为什么先做单体 MVP

秒杀系统最关键的是业务正确性：库存不能扣成负数、同一用户不能重复下单、错误码要稳定。先把核心交易链路在单体内跑通，后续再把热点流量拆给 Redis、MQ、网关，是更稳的演进路径。

### 3.2 为什么选择数据库原子扣减 + 唯一约束 + Redis Lua + RabbitMQ + 防护入口

- 数据库单条 `update ... set available_stock = available_stock - 1 where available_stock > 0`：保证库存不会扣成负数，并缩短热点扣减路径。
- `purchase_order(event_id, user_id)` 唯一约束：保证同一用户只会有一笔成功订单。
- Redis 模式下，Lua 脚本会原子完成“库存预扣 + 用户防重”；若订单插入失败，服务会补偿 Redis 库存并删除购买标记。
- RabbitMQ 模式下，请求先进入异步请求表并返回 `requestId`，消费者异步完成数据库扣库存和订单落库，用轮询接口查询最终状态。
- 如果异步请求因队列不可用或消费失败进入 `FAILED`，同一用户可以重新发起异步请求，系统会复用原记录并生成新的 `requestId` 重试。
- RabbitMQ 主消费者对可恢复异常走重试，重试耗尽后消息进入 DLQ，由死信消费者统一把请求标为失败并补偿 Redis 预扣。
- 购买前必须先获取验证码并换取短期 `accessToken`；购买接口先做限流和 token 预检，基础业务前置校验通过后才真正消费 token，能更早挡住脚本刷请求。
- 验证码和 `accessToken` 都绑定同一个客户端指纹（优先取 `X-Client-Id`），降低跨客户端转发复用风险。
- 秒杀事务统一放在服务层处理，若订单插入失败，事务整体回滚，数据库库存不会被错误扣减。

## 4. 项目结构

```text
src/main/java/com/example/seckill
├── common       # 统一响应和错误码
├── config       # 时钟配置、示例数据初始化
├── controller   # REST API
├── domain       # 实体模型
├── dto          # 接口返回 DTO
├── exception    # 业务异常和全局异常处理
├── mq           # RabbitMQ 生产者抽象
├── protection   # 客户端指纹与入口防护辅助组件
├── repository   # JPA Repository
├── stock        # DB-only / Redis 库存预占抽象
└── service      # 秒杀核心业务逻辑
```

## 5. 快速启动

### 5.1 环境说明

当前目录已放入便携版 JDK 和 Maven，因此可以直接使用 `mvnw.cmd` 运行，不依赖系统全局安装的 `java` 或 `mvn`。

### 5.2 运行测试

```powershell
.\mvnw.cmd test
```

### 5.3 启动应用

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

### 5.4 启动 Redis 增强模式

如果本机已有 Redis 并监听 `localhost:6379`，可以启用 Redis + Lua 链路：

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local,redis
```

Redis 模式下会在应用启动后自动把数据库中的活动库存和已购用户信息预热到 Redis；如果 Redis 暂时不可用，服务会记录告警并退回数据库扣减链路。

### 5.4.1 启动 MySQL profile

如果本机已有 MySQL，可使用 `mysql` profile：

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=mysql
```

默认读取这些环境变量：`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DB`、`MYSQL_USER`、`MYSQL_PASSWORD`。

当前已经接入 Flyway，因此 `mysql` profile 不再依赖 Hibernate 自动建表，而是由迁移脚本管理 schema。

迁移位置：

- `src/main/resources/db/migration/mysql/V1__init_schema.sql`

推荐启动方式：

- 纯 MySQL 验证：`mysql`
- MySQL + 本地 demo 数据：`local,mysql`

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local,mysql
```

说明：`local` profile 会启用 `DataInitializer`，在表为空时自动写入 3 条演示活动数据。

### 5.5 打包

```powershell
.\mvnw.cmd package
```

### 5.6 启动 RabbitMQ 异步模式

如果本机已有 RabbitMQ 并监听默认地址，可启用异步削峰链路：

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local,rabbitmq
```

如果同时启用 Redis + RabbitMQ：

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local,redis,rabbitmq
```

RabbitMQ 模式默认包含：消息持久化、主队列重试、死信队列、消费者手动 ack。

### 5.7 防护配置

默认已经开启入口防护，核心配置在 `application.yml` 的 `seckill.protection.*`：

- `captcha-ttl-seconds`：验证码有效期，默认 120 秒
- `access-token-ttl-seconds`：秒杀令牌有效期，默认 60 秒
- `purchase-rate-limit-requests`：购买接口窗口内允许次数，默认 3 次
- `purchase-rate-limit-window-seconds`：购买接口限流窗口，默认 10 秒

### 5.8 异步恢复配置

`application.yml` 中新增了 `seckill.async.recovery.*`，用于自动对账卡住过久的异步请求：

- `enabled`：是否开启自动恢复，默认 `true`
- `stale-threshold-seconds`：把 `PENDING` 视为陈旧请求的阈值，默认 `300`
- `batch-limit`：每轮最多处理多少条，默认 `50`
- `fixed-delay-millis`：调度固定间隔，默认 `60000`

自动恢复会复用现有 reconcile 逻辑：标记陈旧请求为 `ASYNC_REQUEST_STALE`，并在需要时释放 Redis 预扣库存。

### 5.9 Redis 补偿恢复配置

`application.yml` 中新增了 `seckill.redis.compensation-recovery.*`，用于持久化 Redis 补偿失败并自动重试：

- `enabled`：是否开启补偿重试调度，默认 `true`
- `fixed-delay-millis`：调度固定间隔，默认 `60000`
- `batch-limit`：每轮最多处理多少个补偿任务，默认 `50`
- `retry-delay-seconds`：失败后下次重试延迟，默认 `60`
- `max-attempts`：最大重试次数，默认 `10`

补偿失败任务会落库到 `redis_compensation_task`，并由 Flyway migration 管理。
- 当前限流主键以 `eventId + clientFingerprint` 为主，避免仅通过切换 `userId` 绕过限流。

## 6. API 示例

### 6.1 健康检查

```powershell
curl.exe http://localhost:8080/actuator/health
```

### 6.2 查询活动列表

```powershell
curl.exe http://localhost:8080/api/seckill/events
```

### 6.3 查询活动详情

```powershell
curl.exe http://localhost:8080/api/seckill/events/1
```

### 6.4 发起秒杀

```powershell
curl.exe -X POST "http://localhost:8080/api/seckill/events/1/captcha?userId=test-user-1" -H "X-Client-Id: demo-client"
```

先从响应里拿到 `challengeId` 和算术题 `question`，算出答案后换取访问令牌：

```powershell
curl.exe -X POST "http://localhost:8080/api/seckill/events/1/access-token?userId=test-user-1&challengeId=<challengeId>&captchaAnswer=<answer>" -H "X-Client-Id: demo-client"
```

再携带 `accessToken` 发起秒杀：

```powershell
curl.exe -X POST "http://localhost:8080/api/seckill/events/1/purchase?userId=test-user-1&accessToken=<accessToken>" -H "X-Client-Id: demo-client"
```

注意：`captcha`、`accessToken` 和真正的购买请求必须使用同一个 `X-Client-Id`，否则会被判定为无效 token / 无效验证码链路。

`accessToken` 会在请求通过保护层并进入真正购买执行门槛时消费；如果请求在更早阶段因为活动不存在、时间窗不合法或令牌本身不匹配被拒绝，令牌不会被提前烧掉。

### 6.5 验证重复购买

```powershell
curl.exe -X POST "http://localhost:8080/api/seckill/events/1/captcha?userId=test-user-1" -H "X-Client-Id: demo-client"
```

重新拿新的验证码和新的 `accessToken` 后再次购买，预期返回 `DUPLICATE_PURCHASE`。

### 6.6 发起异步秒杀请求

```powershell
curl.exe -X POST "http://localhost:8080/api/seckill/events/1/purchase-async?userId=async-user-1&accessToken=<accessToken>" -H "X-Client-Id: demo-client"
```

预期返回 `202 Accepted`，响应体中包含 `requestId` 和 `PENDING`。

### 6.7 查询异步请求状态

```powershell
curl.exe http://localhost:8080/api/seckill/requests/<requestId>
```

异步状态会返回 `PENDING`、`SUCCESS` 或 `FAILED`。

如果消费者在多次重试后仍失败，最终会把请求标记为 `ASYNC_PROCESSING_EXHAUSTED`。

### 6.8 监控与指标查询

应用已开放 `health`、`info`、`metrics`、`prometheus` 端点，且业务指标统一带有 `application` 与 `env` 通用标签。

- 查看可用指标名：

```powershell
curl.exe http://localhost:8080/actuator/metrics
```

- 查看秒杀下单持久化结果分布（成功/售罄/异常）：

```powershell
curl.exe "http://localhost:8080/actuator/metrics/seckill.order.persist"
```

- 查看异步入队结果分布（accepted/publish_failed/queue_disabled 等）：

```powershell
curl.exe "http://localhost:8080/actuator/metrics/seckill.async.enqueue"
```

- 查看保护层拒绝原因分布（按 action + reason 标签）：

```powershell
curl.exe "http://localhost:8080/actuator/metrics/seckill.protection.reject"
```

- 查看 Prometheus 抓取格式：

```powershell
curl.exe http://localhost:8080/actuator/prometheus
```

常见查询示例：

- `seckill_order_persist_total{result="sold_out",application="seckill"}`
- `seckill_async_enqueue_total{result="publish_failed",application="seckill"}`
- `seckill_protection_reject_total{action="purchase",reason="rate_limit_exceeded",application="seckill"}`
- `seckill_async_process_latency_seconds_count{application="seckill"}`
- `seckill_async_requests{status="pending",application="seckill"}`
- `http_server_requests_seconds_bucket{application="seckill"}`
- `seckill_rabbitmq_queue_depth{queue="main",application="seckill"}`
- `seckill_redis_reserve_degraded{application="seckill"}`

### 6.9 重放失败异步请求

```powershell
curl.exe -X POST http://localhost:8080/api/seckill/requests/<requestId>/replay
```

这个接口用于把已经 `FAILED` 的异步请求重新置回 `PENDING` 并重新入队。

### 6.10 对账卡住过久的异步请求

```powershell
curl.exe -X POST "http://localhost:8080/api/seckill/requests/reconcile?thresholdSeconds=300&limit=50"
```

这个接口会找出超过阈值仍处于 `PENDING` 的异步请求，将其标记为 `ASYNC_REQUEST_STALE`，并在需要时释放 Redis 预扣库存。

## 7. 测试覆盖

- `SeckillApplicationTests`：应用上下文加载。
- `PurchaseOrderRepositoryTest`：仓储与唯一约束验证。
- `SeckillServiceTest`：时间窗、售罄、重复购买、并发扣减、事务回滚验证。
- `SeckillAsyncPurchaseServiceTest`：异步请求入队、幂等与队列开关验证。
- `SeckillAsyncOrderConsumerTest`：异步消费者成功/失败状态流转验证。
- `SeckillAsyncDeadLetterConsumerTest`：死信队列收口与失败状态更新验证。
- `SeckillProtectionServiceTest`：验证码、访问令牌和限流规则验证。
- `SeckillControllerTest`：HTTP 接口契约验证。
- `RedisStockReservationGatewayTest`：Lua 返回码映射与 Redis key 约定验证。
- `RedisStockWarmupTest`：Redis 预热库存与已购用户集合验证。

重点校验场景：

- 库存为 5 时，20 个并发不同用户请求只能成功 5 次。
- 同一用户并发请求只能成功 1 次，库存也只减少 1 次。
- 重复下单触发唯一约束时，库存会随着事务一起回滚。
- 异步请求在 MQ 关闭时返回稳定错误码 `ASYNC_QUEUE_DISABLED`。
- 异步消费者处理成功后，请求状态会从 `PENDING` 变为 `SUCCESS`。
- 异步消费者处理失败后，请求状态会变为 `FAILED`，并记录失败码。
- 异步消息在重试耗尽后进入 DLQ，并把请求状态更新为 `ASYNC_PROCESSING_EXHAUSTED`。
- 缺失 token、无效 token、错误验证码、重复 token 使用、超频请求都能返回稳定错误码。
- 同一客户端换不同 `userId` 继续刷购买接口时，仍会命中同一个事件级限流窗口。

## 8. Known limitations

- 当前为单体单节点实现，未覆盖生产级流量削峰。
- Redis 模式依赖外部 Redis 实例；如果 Redis 不可用，当前实现会降级回数据库扣减链路，但不会提供 Redis 侧能力。
- RabbitMQ 模式依赖外部 RabbitMQ broker；当前仓库未内置 broker 安装或容器编排。
- 当前异步链路已实现基础重试、DLQ 和手动 ack，但还未补充更高级的告警、监控和人工重放工具。
- 当前验证码是本地算术题实现，适合本地验证，不等价于生产风控级图形验证码/行为验证码。
- 当前限流为应用内内存实现，单机可用，多实例生产环境需要迁移到 Redis / 网关侧统一限流。
- 当前客户端指纹主要依赖 `X-Client-Id` 或 `remoteAddr`，属于轻量识别，不等价于生产级设备指纹系统。
- 未接入登录体系，使用 `userId` 参数模拟用户身份。
- H2 仅用于本地验证，不等价于生产 MySQL 行为。

## 9. 下一步演进建议

1. 把入口限流、验证码和秒杀令牌迁到 Redis/网关层，支持多实例部署。
2. 增加黑白名单、设备指纹、行为验证码等更强风控。
3. 增加 Redis/DB 定时对账，处理极端异常补偿场景。
4. 切换到 MySQL + Redis + MQ 的完整生产部署拓扑。

## 10. 文档索引

- `docs/index.md`：项目文档导航与推荐阅读顺序。
- `CONTRIBUTING.md`：贡献流程、开发与验证要求。
- `CHANGELOG.md`：阶段变更记录与迭代摘要。
- `docs/phase-optimization.md`：阶段性优化记录、当前收益和后续建议。
- `docs/observability.md`：监控端点、业务指标、Prometheus 查询与排查思路。
- `docs/grafana-promql-templates.md`：Grafana 面板建议与 PromQL 告警模板。
- `dashboards/grafana/seckill-overview-dashboard.json`：业务总览 Grafana dashboard 草稿。
- `dashboards/grafana/seckill-recovery-infra-dashboard.json`：恢复与基础设施 Grafana dashboard 草稿。
- `dashboards/grafana/alerting/seckill-alert-rules.yaml`：Grafana alert-rules YAML 草稿。
- `docs/operations.md`：运行、排障与值班关注点。
- `docs/load-test.md`：压测场景、验收指标与容量评估方法。
- `docs/engineering-conventions.md`：开发约定、JSON 策略与工程边界。
- `scripts/jmeter/README.md`：JMeter 通用压测计划使用说明。
- `scripts/jmeter/seckill-generic-plan.jmx`：可直接导入的通用 JMeter 压测文件。
