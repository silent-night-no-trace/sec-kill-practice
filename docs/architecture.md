# Architecture

本文档描述当前 `seckill` 项目的真实实现结构，重点说明系统分层、同步/异步交易链路、基础设施角色，以及当前版本的边界。

## 1. 目标与边界

当前项目的定位是：

- 先把秒杀系统的**业务正确性**做实
- 在单体单节点内验证热点保护、异步削峰、补偿恢复、可观测性和基础运维能力
- 在此基础上再逐步往更强的生产形态演进

这意味着当前版本重点不是“无限制横向扩展”，而是：

- 不超卖
- 不重复下单
- 同步 / 异步链路状态清晰
- Redis / MQ 异常时有明确退化和恢复策略
- 有指标、有文档、有压测资产

## 2. 总体分层

当前代码主要分为以下层次：

```text
controller -> service -> repository / stock / mq
                      -> protection
                      -> config / metrics / scheduler
```

更具体的目录职责如下：

```text
src/main/java/com/style/seckill
├── common       # 统一响应、错误码、工具类
├── config       # profile 配置、Flyway/Redis/RabbitMQ/metrics/scheduling 属性
├── controller   # REST API 入口
├── domain       # JPA 实体、状态枚举
├── dto          # 接口返回 / 消息载体 DTO
├── exception    # 业务异常与全局异常处理
├── mq           # RabbitMQ 发布器与消息转换
├── protection   # 客户端指纹与入口防护辅助组件
├── repository   # JPA Repository
├── stock        # Redis / DB-only 库存预占抽象
└── service      # 核心业务逻辑、调度器、恢复闭环
```

## 3. 核心组件与职责

### 3.1 Controller 层

- `SeckillController`
  - 活动列表 / 详情
  - 验证码 / access token
  - 同步购买 / 异步购买入口
- `SeckillRequestController`
  - 异步请求状态查询
  - 失败请求重放
  - 手动 reconcile

这一层的目标是：

- 保持 HTTP API 清晰
- 把参数校验放在入口层
- 不承载复杂业务决策

### 3.2 Service 层

Service 层是整个系统的核心：

- `SeckillService`
  - 同步购买主流程
- `SeckillAsyncPurchaseService`
  - 异步请求入队、重放、手动 reconcile
- `SeckillAsyncOrderProcessingService`
  - 异步消费者的实际下单处理
- `SeckillOrderPersistenceService`
  - 数据库库存扣减与订单落库
- `SeckillProtectionService`
  - 验证码 / access token / 限流链路
- `StockReservationCoordinator`
  - 统一封装 Redis 预扣与补偿
- `RedisCompensationRecoveryService`
  - Redis 补偿失败持久化与自动恢复主入口

设计原则是：

- 同步 / 异步路径尽量复用规则
- 与“库存预占”“补偿恢复”“入口防护”相关的高风险逻辑都集中在专门服务中，避免散落在 controller 或 repository 里

### 3.3 Repository 层

Repository 主要负责：

- 活动查询与库存原子更新
- 成功订单查询 / 唯一约束兜底
- 异步请求状态持久化
- Redis 补偿失败任务持久化

关键点：

- `SeckillEventRepository` 使用数据库单条原子扣减保护库存边界
- `PurchaseOrderRepository` 通过 `(event_id, user_id)` 唯一约束兜底防重复下单
- `SeckillPurchaseRequestRepository` 支撑异步请求状态流转
- `RedisCompensationTaskRepository` 支撑补偿失败任务的自动重试闭环

### 3.4 Stock 层

`stock` 包把“是否启用 Redis 预扣”封装成了可切换抽象：

- `StockReservationGateway`
- `RedisStockReservationGateway`
- `NoOpStockReservationGateway`

目标是：

- Redis 可用时走 Lua 原子预扣 + 用户防重
- Redis 不可用时明确退化回 DB-only 路径
- 上层服务不需要关心具体 Redis 脚本细节

### 3.5 MQ 层

RabbitMQ 相关职责包括：

- 发布异步下单消息
- 消费主队列
- 消费死信队列
- 统一 JSON 消息转换

关键类：

- `RabbitMqOrderMessagePublisher`
- `SeckillAsyncOrderConsumer`
- `SeckillAsyncDeadLetterConsumer`
- `FastJson2AmqpMessageConverter`

## 4. 关键链路

### 4.1 同步购买链路

同步链路核心步骤：

1. 客户端获取验证码
2. 客户端使用验证码换取短期 access token
3. 购买入口先做限流和 token 预检
4. 如果启用 Redis，先尝试 Redis 预扣库存
5. 进入数据库库存扣减与订单落库
6. 如果数据库失败，触发 Redis 补偿

对应设计目标：

- 热点尽量前移到 Redis / 入口保护层
- 数据库最终正确性兜底
- 失败时必须有补偿路径

### 4.2 异步购买链路

异步链路核心步骤：

1. 入口防护通过后，创建异步请求记录
2. 如果启用 Redis，先做 Redis 预扣库存
3. 发布 RabbitMQ 消息并返回 `requestId`
4. 消费者异步处理数据库库存扣减与订单落库
5. 客户端通过轮询接口查询状态

优势是：

- 入口请求可以快速返回
- 可将交易处理与前台请求线程解耦
- 更适合在高峰流量下削峰

### 4.3 异常闭环与恢复链路

这部分是当前实现的重要增强点。

#### 异步请求恢复

- 手动接口：
  - replay failed request
  - reconcile stale pending request
- 自动恢复：
  - 定时扫描 stale `PENDING`
  - 标记为 `ASYNC_REQUEST_STALE`
  - 需要时释放 Redis 预扣库存

#### Redis 补偿恢复

- Redis 补偿失败时，不再只打日志
- 会持久化到 `redis_compensation_task`
- 调度器会自动重试
- 达到最大重试次数后会进入 `EXHAUSTED`

这意味着系统在“Redis 补偿失败”这个过去最容易被日志吞掉的路径上，现在已经有：

- 可追踪任务
- 可观测指标
- 自动恢复尝试

## 5. 基础设施角色

### 5.1 H2 / MySQL

- `local` / `test` 主要使用 H2
- `mysql` profile 用于更真实的数据库验证
- schema 统一由 Flyway 管理

当前迁移目录：

- `db/migration/h2`
- `db/migration/mysql`

### 5.2 Redis

Redis 当前承担三类职责：

1. 秒杀库存预扣与用户防重
2. 保护层状态（验证码 / access token / 限流）
3. 退化检测（通过 fallback 指标感知 Redis 是否持续失效）

### 5.3 RabbitMQ

RabbitMQ 当前承担：

- 异步下单请求削峰
- 重试与 DLQ 收口
- 与恢复闭环配合定位 MQ 侧故障

### 5.4 Actuator / Micrometer / Prometheus

当前系统已经具备：

- Actuator 健康与指标端点
- 业务指标
- 延迟指标
- backlog / degraded / compensation / recovery 指标
- Grafana dashboard 草稿
- Grafana alert-rules 草稿

也就是说，现在已经不只是“有日志”，而是具备基础的值班与观测资产。

## 6. 当前架构优点

- 核心业务正确性优先，系统演进路径清晰
- 同步 / 异步购买都已有明确状态流转
- Redis / MQ 异常不是黑盒，有退化与恢复机制
- Flyway 已接管 schema，MySQL 落地不再依赖 Hibernate 自动建表
- 观测性、压测、文档资产都已具备基础盘

## 7. 当前架构边界

虽然当前版本已经不只是 demo，但仍有明确边界：

- 仍然是单体应用，不是完整分布式部署方案
- Redis / RabbitMQ / MySQL 的组合更多是“生产化骨架”，不是大规模生产拓扑的最终形态
- 还没有分库分表、复杂风控、正式发布手册和完整安全说明
- Grafana / alert rules 目前是 draft 资产，还没落成正式运维基线

## 8. 下一步自然演进方向

如果继续沿当前架构演进，最自然的下一步通常是：

1. 完善 `docs/release-playbook.md`
2. 补更强的风控与安全策略说明
3. 将 observability draft 资产接成正式 Grafana / alerting provisioning
4. 用 Oracle MySQL 官方发行版补一次 vendor-specific smoke test
5. 继续优化生产环境下的索引、归档与容量管理
