# Grafana / PromQL 模板

本文档提供一组可直接复用的 Grafana 面板思路与 PromQL 告警模板，面向当前 `seckill` 项目已经暴露的业务指标、HTTP 指标、RabbitMQ backlog 指标以及 Redis 退化指标。

如果你想直接导入一个可用草稿，而不是手工建面板，可直接使用：

- `dashboards/grafana/seckill-overview-dashboard.json`
- `dashboards/grafana/seckill-recovery-infra-dashboard.json`

如果你想直接从 Grafana file provisioning 的角度起一份规则草稿，可参考：

- `dashboards/grafana/alerting/seckill-alert-rules.yaml`

注意：这份 YAML 当前是 **single-service / single-datasource draft**，默认按 `application="seckill"` 编写，且需要你在导入前确认 `datasourceUid`。如果一个 Prometheus 同时抓多个环境，建议按环境复制并加上明确的 `env` 选择器，而不是直接原样复用。

## 1. 使用前提

- 应用已开启 `actuator/prometheus`
- Prometheus 已成功抓取当前服务
- 所有指标都带有统一标签：
  - `application`
  - `env`

建议所有查询都至少带上：

```text
{application="seckill"}
```

## 2. Grafana 面板模板

### 2.1 秒杀结果分布

```text
sum by (result) (increase(seckill_order_persist_total{application="seckill"}[5m]))
```

用途：

- 看最近 5 分钟成功 / 售罄 / 异常分布
- 判断是正常售罄还是异常率升高

### 2.2 异步入队结果分布

```text
sum by (result) (increase(seckill_async_enqueue_total{application="seckill"}[5m]))
```

用途：

- 判断是入队成功、重复请求、还是 MQ 发布失败

### 2.3 HTTP 95 / 99 分位延迟

95 分位：

```text
histogram_quantile(0.95, sum by (le, uri, method) (rate(http_server_requests_seconds_bucket{application="seckill"}[5m])))
```

99 分位：

```text
histogram_quantile(0.99, sum by (le, uri, method) (rate(http_server_requests_seconds_bucket{application="seckill"}[5m])))
```

用途：

- 判断接口是否出现长尾恶化
- 区分“平均值正常”与“高分位很慢”

### 2.4 异步请求积压

```text
seckill_async_requests{application="seckill",status="pending"}
```

用途：

- 观察异步请求是否持续堆积

### 2.5 Redis 补偿任务积压

```text
seckill_redis_compensation_tasks{application="seckill",status="pending"}
```

以及：

```text
seckill_redis_compensation_tasks{application="seckill",status="exhausted"}
```

用途：

- 看补偿失败是否在持续积压
- 看是否出现已经耗尽的补偿任务

### 2.6 RabbitMQ 主队列 / DLQ 深度

主队列：

```text
seckill_rabbitmq_queue_depth{application="seckill",queue="main"}
```

死信队列：

```text
seckill_rabbitmq_queue_depth{application="seckill",queue="dead_letter"}
```

用途：

- 判断主消费是否跟不上
- 判断是否有异常消息持续打入 DLQ

### 2.7 Redis fallback 退化状态

最近窗口 fallback 次数：

```text
seckill_redis_reserve_fallback_recent{application="seckill"}
```

退化开关：

```text
seckill_redis_reserve_degraded{application="seckill"}
```

用途：

- 判断 Redis 预扣是否在持续退化为 DB-only 路径

## 3. PromQL 告警模板

下面的规则是模板，不是最终阈值。实际阈值应结合你的压测结论和 SLA 再做调整。

### 3.1 HTTP 95 分位延迟过高

```text
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{application="seckill",uri!~"/actuator.*"}[5m]))) > 0.5
```

建议告警语义：

- 持续 5 分钟大于 500ms 时告警

### 3.2 HTTP 99 分位长尾恶化

```text
histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{application="seckill",uri!~"/actuator.*"}[5m]))) > 1
```

建议告警语义：

- 持续 5 分钟大于 1 秒时告警

### 3.3 异步请求持续积压

```text
seckill_async_requests{application="seckill",status="pending"} > 20
```

建议告警语义：

- 持续 10 分钟高于阈值时告警

### 3.4 Redis 补偿任务出现耗尽

```text
seckill_redis_compensation_tasks{application="seckill",status="exhausted"} > 0
```

建议告警语义：

- 出现即告警，因为说明自动恢复已无法继续推进

### 3.5 Redis fallback 进入退化状态

```text
seckill_redis_reserve_degraded{application="seckill"} > 0
```

建议告警语义：

- 持续 3 分钟告警，避免瞬时抖动

### 3.6 RabbitMQ 主队列积压过高

```text
seckill_rabbitmq_queue_depth{application="seckill",queue="main"} > 100
```

建议告警语义：

- 持续 5 分钟告警

### 3.7 RabbitMQ DLQ 出现积压

```text
seckill_rabbitmq_queue_depth{application="seckill",queue="dead_letter"} > 0
```

建议告警语义：

- 持续 2 分钟告警，说明有消息在持续失败

## 4. 推荐告警优先级

### P1：立即处理

- Redis 补偿任务 `exhausted > 0`
- DLQ 积压持续存在
- Redis fallback degraded 持续打开

### P2：尽快处理

- 异步请求 `pending` 持续积压
- HTTP 99 分位显著恶化

### P3：持续观察

- 售罄比例升高
- protection reject 增长
- HTTP 95 分位略高但尚未越过 SLO

## 5. 推荐看板分组

建议 Grafana 按 4 组面板组织：

1. **入口层**：HTTP P50/P95/P99、保护层拒绝原因
2. **下单层**：同步下单结果、异步入队结果
3. **恢复层**：异步 reconcile、Redis compensation retry、补偿任务积压
4. **基础设施层**：RabbitMQ 主队列 / DLQ 深度、Redis fallback degraded

## 6. 当前局限

这些模板已经覆盖当前仓库已有指标，但还没有纳入：

- Hikari 连接池饱和度
- SQL 慢查询分布
- JVM / GC 重点告警

如果你后续继续做生产化增强，这三类通常是下一批应该纳入告警体系的指标。
