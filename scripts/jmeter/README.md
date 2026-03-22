# JMeter 使用说明

文件：`scripts/jmeter/seckill-generic-plan.jmx`

## 适用场景

- 同步购买：`purchase_mode=sync`
- 异步购买：`purchase_mode=async`
- 开启保护链路：`protection_enabled=true`
- 关闭保护链路：`protection_enabled=false`

## 打开后优先修改的变量

- `host` / `port`
- `event_id`
- `threads`
- `ramp_up_seconds`
- `loops`
- `purchase_mode`
- `protection_enabled`

## 说明

- 计划会为每次执行动态生成 `userId` 和 `X-Client-Id`，避免不同线程互相污染。
- 当 `protection_enabled=true` 时，会自动走 `captcha -> access-token -> purchase` 链路，并自动求解当前算术验证码。
- 当 `purchase_mode=async` 且 `poll_async_status=true` 时，会在提交异步请求后等待 `async_status_delay_ms` 再查询一次状态。
- 如果你的压测目标是纯购买吞吐，建议在压测环境关闭保护链路，否则验证码和 token 流程会放大非核心开销。
