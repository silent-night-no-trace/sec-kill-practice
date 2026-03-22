# 压测与容量评估手册

本文档用于在当前 `seckill` 后端上快速执行三类压测：

1. 基线吞吐与延迟
2. 售罄边界与重复购买保护
3. 异步链路异常（队列不可用）

目标是把“压测请求”与“业务指标变化”对应起来，便于判断系统是正常退化还是异常失效。

## 1. 前置准备

### 1.1 启动应用

- 基线/售罄压测（同步链路）：

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

- 异步链路压测（需要 RabbitMQ）：

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local,rabbitmq
```

### 1.2 确认监控端点

```powershell
curl.exe http://localhost:8080/actuator/health
curl.exe http://localhost:8080/actuator/metrics
curl.exe http://localhost:8080/actuator/prometheus
```

### 1.3 关注的核心指标

- `seckill.order.persist{result=success|sold_out|error}`
- `seckill.async.enqueue{result=accepted|publish_failed|queue_disabled|duplicate|already_pending}`
- `seckill.protection.reject{action,reason}`

建议同时加过滤标签：`application="seckill"`。

## 2. 场景 A：同步购买基线压测

### 2.1 目的

- 观察在正常库存下，系统吞吐与错误率基线。
- 确认 `success` 与 `sold_out` 比例合理，无异常 `error` 激增。

### 2.2 执行方法（PowerShell 并发示例）

以下示例用 20 个并发用户打同一个活动（`eventId=1`）：

```powershell
1..20 | ForEach-Object -Parallel {
  $u = "load-user-$($_)"
  $client = "client-$($_)"
  $captcha = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/seckill/events/1/captcha?userId=$u" -Headers @{"X-Client-Id"=$client}
  $answer = Read-Host "Input answer for $u ($($captcha.question))"
  $tokenResp = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/seckill/events/1/access-token?userId=$u&challengeId=$($captcha.challengeId)&captchaAnswer=$answer" -Headers @{"X-Client-Id"=$client}
  Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/seckill/events/1/purchase?userId=$u&accessToken=$($tokenResp.accessToken)" -Headers @{"X-Client-Id"=$client}
} -ThrottleLimit 20
```

说明：当前防护链路包含验证码，若要纯压购买路径，建议在压测环境临时关闭 `seckill.protection.enabled`。

### 2.3 验收要点

- `seckill.order.persist{result="success"}` 应稳定增长。
- 库存耗尽后 `result="sold_out"` 增长，`result="error"` 不应显著增长。
- `seckill.protection.reject` 若异常飙升，说明请求模式触发了限流或 token 校验失败。

## 3. 场景 B：售罄边界与重复购买

### 3.1 目的

- 验证并发售罄时不超卖。
- 验证同一用户重复请求只能成功一次。

### 3.2 执行方法

- 选一个低库存活动（例如库存 5）。
- 同一个 `userId` 发起多次购买（每次使用新验证码/新 token）。
- 再用多个不同 `userId` 并发购买。

### 3.3 验收要点

- 单用户只出现一次成功单。
- 多用户并发总成功数不超过库存。
- `seckill.order.persist{result="sold_out"}` 在边界时上升是正常现象。

## 4. 场景 C：异步链路异常注入

### 4.1 目的

- 验证 MQ 不可用时系统返回稳定错误码。
- 验证异步入队失败指标可反映故障。

### 4.2 执行方法

1. 启动 `local,rabbitmq` 配置。
2. 在压测过程中临时关闭 RabbitMQ 或断开连接。
3. 持续调用异步接口：

```powershell
curl.exe -X POST "http://localhost:8080/api/seckill/events/1/purchase-async?userId=async-load-1&accessToken=<accessToken>" -H "X-Client-Id: async-client-1"
```

### 4.3 验收要点

- `seckill.async.enqueue{result="publish_failed"}` 或 `result="queue_disabled"` 增长。
- 同时应看到业务返回 `ASYNC_QUEUE_UNAVAILABLE` 或 `ASYNC_QUEUE_DISABLED`，而不是 500 泛化错误。
- 恢复 MQ 后，`accepted` 恢复增长。

## 5. 快速查询模板

```text
seckill_order_persist_total{application="seckill"}
seckill_async_enqueue_total{application="seckill"}
seckill_protection_reject_total{application="seckill"}
```

如果你后续接入 Grafana，可先做 3 张面板：

1. 下单持久化结果分布（success/sold_out/error）
2. 异步入队结果分布（accepted/publish_failed/queue_disabled）
3. 保护层拒绝原因 TopN（action + reason）
