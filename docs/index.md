# 项目文档导航

这个文件是 `seckill` 项目的统一文档入口，用于说明“遇到某类问题应该先看哪份文档”。

## 1. 建议阅读顺序

如果你第一次接手这个项目，建议按下面顺序阅读：

1. `README.md`：先看项目能力边界、启动方式、接口示例。
2. `docs/phase-optimization.md`：再看当前已经做过哪些优化、为什么这样做。
3. `CHANGELOG.md`：了解最近一轮迭代具体改了什么。
4. `docs/observability.md`：想看指标、端点、Prometheus 查询时查看。
5. `docs/load-test.md`：准备压测或容量评估时查看。
6. `scripts/jmeter/README.md`：需要直接跑 JMeter 压测时查看。
7. `docs/engineering-conventions.md`：查看开发约定与工程约束时查看。
8. `docs/grafana-promql-templates.md`：需要 Grafana 面板和 PromQL 告警模板时查看。
9. `dashboards/grafana/seckill-overview-dashboard.json`：业务总览 dashboard 草稿。
10. `dashboards/grafana/seckill-recovery-infra-dashboard.json`：恢复与基础设施 dashboard 草稿。
11. `dashboards/grafana/alerting/seckill-alert-rules.yaml`：Grafana alert-rules YAML 草稿。
12. `CONTRIBUTING.md`：贡献流程、开发约定入口与验证要求。
13. `docs/operations.md`：运行、排障、值班关注点。

## 2. 按目标找文档

### 2.1 想快速启动项目

- 看 `README.md`

### 2.2 想知道当前系统已经优化到了什么程度

- 看 `docs/phase-optimization.md`

### 2.3 想看最近一轮阶段性改动摘要

- 看 `CHANGELOG.md`

### 2.4 想做压测 / 容量评估

- 看 `docs/load-test.md`

### 2.5 想看监控指标、Prometheus 查询和看板建议

- 看 `docs/observability.md`

### 2.6 想直接用 JMeter 压同步/异步购买链路

- 看 `scripts/jmeter/README.md`
- 导入 `scripts/jmeter/seckill-generic-plan.jmx`

### 2.7 想知道仓库里的工程约束和开发边界

- 看 `docs/engineering-conventions.md`

### 2.8 想直接搭建 Grafana 看板和告警规则

- 看 `docs/grafana-promql-templates.md`

### 2.9 想直接导入现成的 Grafana dashboard 草稿

- 用 `dashboards/grafana/seckill-overview-dashboard.json`
- 用 `dashboards/grafana/seckill-recovery-infra-dashboard.json`

### 2.10 想直接起一份 Grafana 告警规则草稿

- 用 `dashboards/grafana/alerting/seckill-alert-rules.yaml`

### 2.11 想了解开发协作与提交流程

- 看 `CONTRIBUTING.md`

### 2.12 想看运行、排障和值班说明

- 看 `docs/operations.md`

## 3. 当前文档清单

- `README.md`：项目总览、接口示例、监控端点说明。
- `CHANGELOG.md`：阶段变更记录与迭代摘要。
- `docs/phase-optimization.md`：阶段性优化记录、优化收益与后续建议。
- `docs/observability.md`：监控端点、业务指标、Prometheus 查询和排查思路。
- `docs/load-test.md`：压测场景、验收指标、容量评估方法。
- `docs/engineering-conventions.md`：开发约定、JSON 策略、分页约束与测试原则。
- `docs/grafana-promql-templates.md`：Grafana 面板建议与 PromQL 告警模板。
- `dashboards/grafana/seckill-overview-dashboard.json`：Grafana 业务总览 dashboard 草稿。
- `dashboards/grafana/seckill-recovery-infra-dashboard.json`：Grafana 恢复与基础设施 dashboard 草稿。
- `dashboards/grafana/alerting/seckill-alert-rules.yaml`：Grafana alert-rules YAML 草稿。
- `CONTRIBUTING.md`：贡献流程与本地验证要求。
- `docs/operations.md`：运行、排障与值班手册。
- `scripts/jmeter/README.md`：JMeter 压测文件使用说明。
- `scripts/jmeter/seckill-generic-plan.jmx`：通用 JMeter 压测计划。

## 4. 后续建议补充的文档

后续如果继续往生产化演进，建议优先增加这几类文档：

1. `docs/architecture.md`：系统架构图、核心链路说明、依赖关系。
2. `docs/release-playbook.md`：发布检查项、回滚策略、压测前后核对项。
3. `docs/security.md`：入口防护策略、限流边界和安全注意事项。
