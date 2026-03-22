# Changelog

All notable changes to this project are documented in this file.

## [Unreleased]

### Changed

- Default database stock deduction now uses a single atomic update path instead of an optimistic-lock retry loop.
- Shared event loading and purchase-window validation are centralized to reduce duplicated sync/async purchase logic.
- In-memory protection cleanup now runs on a periodic trigger instead of scanning on every request.
- Rate-limit buckets are reclaimed when expired to keep long-running memory usage more stable.
- Time-based rate-limit checks now use `Clock#millis()` to reduce unnecessary time object conversions.
- Event listing now has a paged endpoint and Redis warmup now processes events in batches instead of a single full scan.
- Async failed requests can now be replayed manually, and stale pending requests can be reconciled and marked failed.
- Added scheduled async recovery configuration and automatic stale-request reconciliation.
- Added high-concurrency Redis regression coverage for token single-consumption and rate-limit atomicity.
- Added persisted Redis compensation failure tasks with automatic retry and exhaustion handling.

### Added

- `SeckillEventAccessService` to centralize required-event lookup and purchasable validation.
- `IdGenerator` to unify compact ID generation across order numbers, async request IDs, captcha IDs, and access tokens.
- Micrometer business metrics for order persistence, async enqueue outcomes, protection rejects, and token issuance.
- Common Micrometer tags: `application` and `env`.
- Prometheus registry dependency and actuator `prometheus` endpoint exposure.
- Documentation portal: `docs/index.md`.
- Observability guide: `docs/observability.md`.
- Grafana / PromQL templates guide: `docs/grafana-promql-templates.md`.
- Grafana dashboard draft: `dashboards/grafana/seckill-overview-dashboard.json`.
- Grafana recovery / infra dashboard draft: `dashboards/grafana/seckill-recovery-infra-dashboard.json`.
- Grafana alert-rules draft: `dashboards/grafana/alerting/seckill-alert-rules.yaml`.
- Contributing guide: `CONTRIBUTING.md`.
- Operations guide: `docs/operations.md`.
- Architecture guide: `docs/architecture.md`.
- Load testing guide: `docs/load-test.md`.
- Engineering conventions guide: `docs/engineering-conventions.md`.
- Generic JMeter plan: `scripts/jmeter/seckill-generic-plan.jmx`.
- JMeter usage notes: `scripts/jmeter/README.md`.
- Phase optimization record: `docs/phase-optimization.md`.
- MySQL runtime profile: `src/main/resources/application-mysql.yml`.
- Flyway-based schema migrations for H2 and MySQL:
  - `src/main/resources/db/migration/h2/V1__init_schema.sql`
  - `src/main/resources/db/migration/mysql/V1__init_schema.sql`

### Changed

- Replaced explicit Jackson-based JSON handling with `fastjson2` where this repo directly controlled JSON serialization/deserialization:
  - RabbitMQ JSON message conversion now uses `FastJson2AmqpMessageConverter`
  - Controller JSON parsing in tests now uses `fastjson2`
- Excluded transitive `org.json:json` from the embedded Redis test dependency to remove duplicate `org.json.JSONObject` classpath conflicts.
- H2 local/test and MySQL profile now use Flyway-managed schema instead of Hibernate create-drop for runtime schema creation.

### Observability

- Added latency metrics for sync persistence, async enqueue, and async processing.
- Added gauges for pending and failed async request counts.
- Enabled Redis and RabbitMQ health indicators in their respective profiles.
- Added async reconcile metrics and Redis compensation outcome metrics.
- Added Redis compensation retry metrics and pending/exhausted task gauges.
- Enabled HTTP request histograms/percentiles and added RabbitMQ queue-depth plus Redis fallback degradation metrics.

### Docs

- README now reflects the actual database stock strategy, observability capabilities, and load-testing assets.
- Added a documentation index in `README.md` for optimization notes, load testing, and JMeter assets.

### Validation

- Verified repeatedly with Maven test and package flows during optimization iterations.
