# Contributing

This repository is a Spring Boot seckill practice project. This guide explains how to make changes safely and consistently.

## 1. Development Flow

Recommended order when making changes:

1. Read `README.md` for startup and feature overview.
2. Read `docs/index.md` to find the right supporting documents.
3. If the change affects architecture or constraints, check `docs/engineering-conventions.md` first.
4. Implement the smallest change that solves the problem.
5. Run tests and package verification before sharing the result.

## 2. Local Setup

### 2.1 Default local run

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

### 2.2 Redis-enhanced run

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local,redis
```

### 2.3 MySQL-backed run

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local,mysql
```

## 3. Coding Rules

- Keep package prefix under `com.style`.
- Use `fastjson2` for explicit JSON parsing/serialization that the project controls.
- Do not reintroduce explicit Jackson API usage in application or test code.
- Prefer paged public list APIs over unbounded `findAll()`-style endpoints.
- Database schema changes must be reflected in Flyway migrations.

For detailed rules, see `docs/engineering-conventions.md`.

## 4. Testing Expectations

Run the full test suite before finalizing non-trivial changes:

```powershell
.\mvnw.cmd test
```

Run packaging verification after tests:

```powershell
.\mvnw.cmd -DskipTests package
```

When changing infrastructure-sensitive code, prefer targeted tests in addition to the full suite:

- Redis protection / Redis compensation changes -> Redis integration tests
- Async recovery changes -> recovery scheduler / service tests
- Controller and API contract changes -> controller tests

## 5. Git Expectations

- This repo uses `main` as the default branch.
- Remote origin is expected to point to:

```text
git@github.com:silent-night-no-trace/sec-kill-practice.git
```

- Do not commit generated output such as `target/`.
- Do not commit local tooling folders such as `.tools/` or local agent state.

## 6. Documentation Expectations

Update documentation when changes affect:

- startup profiles
- public endpoints
- metrics / health behavior
- dashboards / alert rules
- engineering constraints

At minimum, update the most relevant file among:

- `README.md`
- `docs/index.md`
- `docs/observability.md`
- `docs/engineering-conventions.md`
- `CHANGELOG.md`

## 7. Good Change Checklist

Before considering work done, verify all of the following:

- the code compiles
- tests pass
- packaging succeeds
- docs are updated if behavior changed
- no local helper files are left behind
