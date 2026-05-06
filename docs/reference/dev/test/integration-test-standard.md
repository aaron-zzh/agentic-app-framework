---
level: Practice
layer: Model
purpose: 定义集成测试的编写规范、环境配置和执行要求
status: published
version: 1.0.0
date: 2026-05-06
author: AaronZZH
changelog:
  - 2026-05-06 | 补充 Front Matter
---

# 集成测试规范

> 定义模块间集成测试和回归测试的标准。集成测试是 **tester 的产出**，归入 `acceptance` target（与验收测试共用 Failsafe 执行通道），不属于 developer 的 `check`。
>
> **相关决策**：
> - 测试环境走本地真实 DB + CI service container（不用 Testcontainers / H2）— 起因：[ADR-002](../../../design/adr/ADR-002-local-env-vs-testcontainers.md)

## 适用场景

- 多任务/多模块集成后的整体验证
- 模块间接口对接验证
- 回归测试（已有功能未被破坏）
- 数据流转完整性（Controller → Service → Repository → DB）

## 测试环境策略

**使用本地真实数据库 + CI service container**，不使用 H2 内存库，也不使用 Testcontainers。

**理由**：
- AAF 重度依赖 PostgreSQL 特有能力（JSONB、全文检索 `tsvector`、Flyway PostgreSQL 方言）和 Neo4j（Cypher / 图关系），H2 无法替代
- 一人公司 + AI 协作场景下，Docker Desktop 的常驻内存（2-4 GB）和容器启动延迟超过 Testcontainers 带来的可移植性收益
- 决策记录见 [AAF-023 #3 dev-log](../../../task/v0.1.0/AAF-023/dev-log.md#3-测试环境方案本地-vs-testcontainers-决策记录)

**本地环境**：
- PostgreSQL 17（本地安装，独立 `aaf_test` 数据库）
- Neo4j 5 Community（本地安装，独立 `aaf_test` 数据库）
- 详见 [开发环境](../dev-environment.md#本地数据库)

**CI 环境**：
- GitHub Actions service container（PostgreSQL + Neo4j）
- 详见 [ci.yml](../../../../.github/workflows/ci.yml)

## 测试编写

- 技术栈：JUnit 5 + Spring Boot Test + Spring Data（JPA / Neo4j）
- 使用 `@SpringBootTest` 全栈启动，或 `@DataJpaTest` / `@DataNeo4jTest` 切片测试
- 数据清理：默认用 `@Transactional`（Spring Test 自动回滚）；涉及 Neo4j 用 `@BeforeEach` 显式 `MATCH (n) DETACH DELETE n`
- Mock LLM：`@MockBean ChatClient`，CI 不消耗真实 API

## 命名约定

| 类型 | 命名 | 执行器 |
|-----|------|--------|
| 集成测试 | `XxxIT.java` | Maven Failsafe |

详见 [验收测试规范 #命名约定](acceptance-test-standard.md#命名约定硬约束)。

## 覆盖要求

- 模块间接口调用路径 100% 覆盖
- 数据流转完整性验证（写入 → 读取 → 更新 → 删除）
- 已有功能的回归测试不可删除

## 产出

- 测试代码：`src/test/java` 下对应包，命名 `*IT.java`
- 集成验证结果记录到 [test-report.md](../../../task/_template/test-report.md)
