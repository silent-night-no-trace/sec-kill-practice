# 可观测性说明

本文档说明当前 `seckill` 项目已经具备的可观测性能力，包括 Actuator 端点、Micrometer 业务指标、Prometheus 抓取方式，以及建议优先关注的 Grafana 面板。

## 1. 当前能力边界

当前项目已经具备以下可观测性基础：

- Spring Boot Actuator 端点：`health`、`info`、`metrics`、`prometheus`
- Micrometer 业务指标：覆盖下单持久化、异步入队、保护层拒绝、令牌签发
- Prometheus 输出：支持直接抓取 `actuator/prometheus`
- 统一标签：所有指标统一带有 `application` 与 `env`
- HTTP 分位配置：`http.server.requests` 已开启 histogram 与 50/95/99 分位
- RabbitMQ 队列堆积采样：主队列 / DLQ 深度与主队列消费者数
- Redis fallback 退化追踪：最近窗口 fallback 次数与 degraded gauge

当前仍然偏向“业务结果可见”，尚未补充更细粒度的延迟分布、线程池利用率、消息堆积和数据库连接池看板。

## 2. 相关配置

### 2.1 Actuator 端点

当前在 `src/main/resources/application.yml` 中开放了：

- `health`
- `info`
- `metrics`
- `prometheus`

对应访问示例：

```powershell
curl.exe http://localhost:8080/actuator/health
curl.exe http://localhost:8080/actuator/metrics
curl.exe http://localhost:8080/actuator/prometheus
```

### 2.2 指标统一标签

`src/main/java/com/example/seckill/config/MetricsConfiguration.java` 中统一注入了：

- `application`：默认来自 `spring.application.name`，当前为 `seckill`
- `env`：取当前激活 profile 的第一个值；若未设置则为 `default`

这意味着同一个指标在查询时可以直接按环境筛选，例如：

```text
seckill_order_persist_total{application="seckill",env="local"}
```

### 2.3 健康检查的当前边界

当前 `rabbit`、`redis`、`redis-reactive` 的 health indicator 默认关闭，因此：

- `health` 更适合做应用本身存活探针
- 不适合直接判断外部 Redis / RabbitMQ 依赖是否健康

如果你后续要把它用于生产探活，需要再决定是否单独开启外部依赖健康检查。

补充：当启用 `redis` 或 `rabbitmq` profile 时，当前配置已经分别打开对应 health indicator，更适合做带依赖的联调验证。

## 3. 当前业务指标

### 3.1 下单持久化结果

指标名：`seckill.order.persist`

标签：

- `result=success`
- `result=sold_out`
- `result=error`

含义：

- `success`：数据库扣减和订单创建完成
- `sold_out`：数据库原子扣减失败，说明库存已耗尽
- `error`：除售罄以外的运行时异常

推荐查看：

```powershell
curl.exe "http://localhost:8080/actuator/metrics/seckill.order.persist"
```

### 3.2 异步入队结果

指标名：`seckill.async.enqueue`

标签：

- `result=accepted`
- `result=publish_failed`
- `result=queue_disabled`
- `result=duplicate`
- `result=already_pending`

含义：

- `accepted`：异步请求成功入队
- `publish_failed`：消息发布失败，通常要优先排查 MQ 可用性
- `queue_disabled`：队列功能被配置关闭
- `duplicate`：同用户同活动幂等约束命中
- `already_pending`：已有未完成请求，当前返回复用结果

推荐查看：

```powershell
curl.exe "http://localhost:8080/actuator/metrics/seckill.async.enqueue"
```

### 3.3 保护层拒绝原因

指标名：`seckill.protection.reject`

标签：

- `action`：例如 `token`、`purchase`、`consume`
- `reason`：例如 `rate_limit_exceeded`、`access_token_invalid`、`captcha_invalid`

这个指标最适合回答两个问题：

- 当前请求被挡住，主要是因为限流、验证码、还是 token 无效？
- 某次压测结果差，是业务核心链路慢，还是保护层提前拒绝过多？

推荐查看：

```powershell
curl.exe "http://localhost:8080/actuator/metrics/seckill.protection.reject"
```

### 3.4 访问令牌签发成功

指标名：`seckill.protection.token.issue`

标签：

- `result=success`

这个指标可以和保护层拒绝指标一起看，用来判断验证码通过后到底有多少请求真正进入购买前置阶段。

### 3.5 延迟与积压指标

当前还补充了以下指标：

- `seckill.order.persist.latency`
- `seckill.async.enqueue.latency`
- `seckill.async.process.latency`
- `seckill.async.requests{status="pending|failed"}`
- `seckill.async.reconcile.runs`
- `seckill.async.reconcile.marked_failed`
- `seckill.redis.compensation{outcome}`
- `seckill.redis.compensation.retry.runs`
- `seckill.redis.compensation.retry.processed`
- `seckill.redis.compensation.retry.resolved`
- `seckill.redis.compensation.retry.failed`
- `seckill.redis.compensation.retry.exhausted`
- `seckill.redis.compensation.tasks{status="pending|exhausted"}`

这些指标分别用于观察：

- 同步下单持久化耗时
- 异步请求从入队前置校验到发布的耗时
- 异步消费者处理耗时
- 当前积压的 `PENDING` / `FAILED` 请求数量
- 自动恢复任务执行次数与每轮标记失败的请求数量
- Redis 补偿结果分布（`released` / `marker_missing` / `failed` / `not_required`）
- Redis 补偿重试执行次数、处理数量、最终 resolved/failed/exhausted 分布
- 当前待处理 / 已耗尽的补偿任务数量

### 3.6 HTTP / MQ / Redis 退化指标

当前还增加了以下可观测性增强：

- `http.server.requests` histogram + 0.5 / 0.95 / 0.99 分位
- `seckill.rabbitmq.queue.depth{queue="main|dead_letter"}`
- `seckill.rabbitmq.queue.consumers{queue="main"}`
- `seckill.rabbitmq.queue.inspect.errors`
- `seckill.redis.reserve.fallback.recent`
- `seckill.redis.reserve.degraded`

这些指标分别用于回答：

- HTTP 请求慢，是偶发还是长尾分位整体变差？
- RabbitMQ 主队列或 DLQ 有没有积压？
- Redis 预扣是否进入了“持续 fallback 到 DB-only”的退化状态？

## 4. 常用 Prometheus 查询示例

### 4.1 看售罄比例

```text
seckill_order_persist_total{application="seckill",result="sold_out"}
```

### 4.2 看异步发布失败是否升高

```text
seckill_async_enqueue_total{application="seckill",result="publish_failed"}
```

### 4.3 看购买接口主要被什么保护规则挡住

```text
seckill_protection_reject_total{application="seckill",action="purchase"}
```

### 4.4 按原因拆保护层拒绝

```text
seckill_protection_reject_total{application="seckill",action="purchase",reason="rate_limit_exceeded"}
```

### 4.5 按环境筛选

```text
seckill_order_persist_total{application="seckill",env="local"}
```

### 4.6 看异步积压

```text
seckill_async_requests{application="seckill",status="pending"}
```

### 4.7 看 RabbitMQ 队列积压

```text
seckill_rabbitmq_queue_depth{application="seckill",queue="main"}
seckill_rabbitmq_queue_depth{application="seckill",queue="dead_letter"}
```

### 4.8 看 Redis fallback 是否进入退化状态

```text
seckill_redis_reserve_fallback_recent{application="seckill"}
seckill_redis_reserve_degraded{application="seckill"}
```

## 5. 建议优先做的 Grafana 面板

如果后续接入 Grafana，建议先做下面 11 张面板：

1. 下单持久化结果分布：`success / sold_out / error`
2. 异步入队结果分布：`accepted / publish_failed / queue_disabled`
3. 异步处理延迟：`seckill.async.process.latency`
4. 保护层拒绝原因 TopN：按 `action + reason` 分组
5. 异步请求积压：`seckill.async.requests{status}`
6. 令牌签发成功量：与购买成功量联动对比
7. 自动恢复标记失败数量：观察是否长期出现 stale `PENDING`
8. Redis 补偿结果分布：确认补偿是否频繁失败或丢 marker
9. HTTP 95/99 分位延迟：区分均值正常但长尾恶化的情况
10. RabbitMQ 主队列 / DLQ 深度：识别消费落后或死信堆积
11. Redis fallback 退化状态：识别 Redis 预扣是否正在被持续绕过
12. Redis 补偿任务积压：确认是否有长期未消化的 `pending` / `exhausted`

这样可以较快区分：

- 是库存确实卖完了
- 是请求还没进核心链路就被保护层挡住了
- 还是异步链路本身出了问题

## 6. 常见观察与排查思路

### 6.1 `sold_out` 很高，但 `error` 很低

- 这通常是正常现象，代表库存边界被正确保护住了。
- 重点看业务是否发生超卖，而不是单纯看失败数量高不高。

### 6.2 `publish_failed` 上升

- 优先检查 RabbitMQ 可用性和连接配置。
- 如果是压测中故意关 MQ，这个现象符合预期。

### 6.3 `rate_limit_exceeded` 激增

- 说明当前流量模式更多被保护层拦住，而不是打到核心交易逻辑。
- 在做纯吞吐压测时，建议在压测环境关闭 `seckill.protection.enabled`，否则结论容易被保护层噪声影响。

### 6.4 `access_token_invalid` 或 `captcha_invalid` 很高

- 优先检查是否复用了错误的 `X-Client-Id`
- 检查验证码与 token 请求是否仍处于同一客户端上下文
- 检查压测脚本是否正确串联了 `captcha -> access-token -> purchase`

## 7. 当前缺口与下一步建议

当前已经能回答“业务结果是什么”、初步定位“慢在哪一层”，但离完整生产级可观测性还差最后几类基础设施指标。后续建议继续补：

1. 数据库连接池与慢查询观察
2. JVM / GC 压力信号
3. Grafana dashboard 导出文件与正式告警阈值落地
