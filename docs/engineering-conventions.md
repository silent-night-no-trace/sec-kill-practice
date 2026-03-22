# 开发约定

本文档用于沉淀项目中已经明确做出的工程约束，避免后续迭代时出现风格漂移、基础设施回退或重复争论。

## 1. JSON 使用约定

### 1.1 当前原则

项目中**显式可控的 JSON 解析与序列化统一使用 `fastjson2`**。

这里的“显式可控”包括：

- 手写 JSON 解析逻辑
- 自定义消息转换器
- 测试中主动解析 JSON 字符串
- 后续新增的工具类/适配器中的 JSON 读写

### 1.2 当前仍保留 Jackson 的范围

当前仓库**不主动在业务代码里直接使用 Jackson API**，但 Spring Boot 默认基础设施仍会通过传递依赖引入 Jackson，例如：

- Spring MVC 默认 HTTP JSON 处理
- Actuator / Spring Boot 默认 Web 栈

这部分暂时保留，不做强行替换，原因是：

- 这套基础设施由 Spring Boot 默认托管，兼容性最好
- 当前项目显式 JSON 使用点很少，没有必要为了“统一名义”去大改 MVC 基础设施
- 已经明确可控的 JSON 处理位置都已切到 `fastjson2`

### 1.3 禁止事项

除非经过明确设计变更，否则不要在仓库源码中新增以下显式依赖：

- `com.fasterxml.jackson.*`
- `ObjectMapper`
- `JsonNode`
- `Jackson2JsonMessageConverter`

如果确实需要调整 Spring MVC 默认 JSON 方案，应当作为一次**单独的架构变更**处理，而不是在功能开发时顺手引入。

### 1.4 现有落地点

- RabbitMQ JSON 消息转换：`src/main/java/com/example/seckill/mq/FastJson2AmqpMessageConverter.java`
- 控制器测试中的 JSON 解析：`src/test/java/com/example/seckill/controller/SeckillControllerTest.java`

### 1.5 自动化守卫

仓库中已增加源码守卫测试，用于阻止显式 Jackson 回流：

- `src/test/java/com/example/seckill/JsonLibraryGuardTest.java`

这意味着后续如果有人重新在源码里直接引入 Jackson API，测试会直接失败。

## 2. 接口分页约定

- 新增列表接口优先提供分页能力
- 不允许新增无限制 `findAll()` 风格的公开列表接口
- 兼容旧接口时，应提供默认安全上限，而不是继续暴露无界查询

## 3. 测试约定

- 对真实基础设施语义敏感的逻辑，优先补真实集成测试，而不是只靠 mock
- 当前 Redis 保护链路已补真实 Redis 集成测试：
  - `src/test/java/com/example/seckill/service/SeckillProtectionRedisIntegrationTest.java`
- 高并发路径需要优先补“并发回归保护”测试，至少覆盖：
  - 单次令牌只能被一个并发请求成功消费
  - Redis 限流窗口在并发下保持原子性
  - 关键异常恢复路径能够自动调度或至少可验证执行
- 涉及“恢复闭环”的改动应验证：
  - 失败任务是否会被持久化
  - 外层事务回滚后失败任务是否仍然保留
  - 重试成功是否能关闭任务
  - 达到最大重试次数后是否进入 `EXHAUSTED`

## 4. 文档更新约定

涉及以下变更时，应同步更新文档：

- 新 profile
- 新公开接口
- 监控指标 / 健康检查策略变化
- 压测资产变化
- 工程约束变化

## 5. 数据库迁移约定

- 数据库 schema 由 Flyway migration 管理，不依赖 Hibernate 自动建表
- H2 与 MySQL 分别使用各自的 migration 目录：
  - `src/main/resources/db/migration/h2`
  - `src/main/resources/db/migration/mysql`
- 实体结构变化时，应同步补 migration，而不是只修改 JPA 注解
- 当前 Redis 补偿失败任务表也由 migration 管理：
  - `db/migration/h2/V2__redis_compensation_task.sql`
  - `db/migration/mysql/V2__redis_compensation_task.sql`
