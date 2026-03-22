# Operations Guide

This document explains how to run, inspect, and troubleshoot the project in daily development or lightweight operations scenarios.

## 1. Runtime Profiles

### 1.1 `local`

- Uses H2 in-memory database
- Runs with local demo data initialization
- Best for quick development and endpoint testing

### 1.2 `redis`

- Enables Redis-backed protection state and Redis stock reservation path
- Useful for validating protection behavior, stock pre-deduction, and Redis degradation metrics

### 1.3 `rabbitmq`

- Enables async queue publishing / consumption
- Useful for validating async purchase flow, queue backlog, DLQ behavior, and recovery metrics

### 1.4 `mysql`

- Uses MySQL-compatible database settings
- Flyway manages schema creation and migration
- Best for realistic database verification beyond H2

## 2. Common Startup Commands

### 2.1 H2-only local run

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

### 2.2 Local + Redis

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local,redis
```

### 2.3 Local + RabbitMQ

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local,rabbitmq
```

### 2.4 Local + MySQL

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local,mysql
```

## 3. Health and Inspection Endpoints

Useful checks:

```powershell
curl.exe http://localhost:8080/actuator/health
curl.exe http://localhost:8080/actuator/metrics
curl.exe http://localhost:8080/actuator/prometheus
```

What to look for:

- `health=UP`
- Redis component status when `redis` profile is enabled
- queue-related metrics when `rabbitmq` profile is enabled
- compensation / reconcile metrics if async recovery is active

## 4. Key Operational Signals

### 4.1 Business health

- `seckill.order.persist{result}`
- `seckill.async.enqueue{result}`
- `seckill.protection.reject{action,reason}`

### 4.2 Recovery health

- `seckill.async.reconcile.runs`
- `seckill.async.reconcile.marked_failed`
- `seckill.redis.compensation{outcome}`
- `seckill.redis.compensation.retry.*`
- `seckill.redis.compensation.tasks{status}`

### 4.3 Infrastructure health

- `http.server.requests`
- `seckill.rabbitmq.queue.depth{queue}`
- `seckill.rabbitmq.queue.consumers{queue}`
- `seckill.redis.reserve.fallback.recent`
- `seckill.redis.reserve.degraded`

## 5. Common Failure Scenarios

### 5.1 Redis degraded / fallback to DB-only

Symptoms:

- `seckill.redis.reserve.degraded = 1`
- rising `seckill.redis.reserve.fallback.recent`

First checks:

1. verify Redis process is up
2. check `health` output for Redis component
3. inspect recent Redis connection or script errors in logs

### 5.2 Async requests stay pending too long

Symptoms:

- rising `seckill.async.requests{status="pending"}`
- reconcile task starts marking stale requests failed

First checks:

1. verify RabbitMQ availability
2. inspect main queue depth and consumer count
3. inspect DLQ depth
4. inspect async recovery metrics

### 5.3 Compensation tasks remain pending or exhausted

Symptoms:

- `seckill.redis.compensation.tasks{status="pending"}` remains high
- `seckill.redis.compensation.tasks{status="exhausted"}` becomes non-zero

First checks:

1. inspect Redis availability
2. inspect compensation retry counters
3. inspect logs for repeated release failures or missing reservation markers

### 5.4 High HTTP tail latency

Symptoms:

- rising `http.server.requests` p95 / p99

First checks:

1. compare with queue backlog and async pending counts
2. check whether Redis degraded gauge is active
3. inspect whether traffic is being pushed into DB-only fallback path

## 6. Portable Tooling Notes

This repository may keep local runtime helpers under `.tools/` for developer-side verification.

Important:

- `.tools/` is ignored by Git
- treat it as local-only runtime support, not as a tracked project artifact

## 7. Recommended Verification Before Sharing Results

```powershell
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package
```

If the change touched observability or recovery logic, also verify the relevant dashboard / alert / metric docs still match the implementation.
