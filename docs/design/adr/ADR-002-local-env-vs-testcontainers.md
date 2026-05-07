---
level: Practice
layer: Principle
purpose: 后端测试环境选型决策
status: accepted
version: 1.0.0
date: 2026-05-05
author: AaronZZH
deciders: [AaronZZH, 协调者]
---

# ADR-002: 后端测试环境 — 本地真实 DB vs Testcontainers

## Context and Problem Statement

AAF-023 #1 落地一键 check 时默认推荐 Testcontainers 作为后端测试环境方案，规范文档 6 处引用。随后收到质疑："Testcontainers 需要安装 Docker 吧，增加开发运行成本吧，感觉不如直接显式安装相关环境更好"。

AAF 的具体场景：

- 一人公司（AaronZZH）+ AI 协作为主
- Windows 开发主机
- AI 智能体需频繁执行测试做内循环验证
- 无团队扩张计划（v0.1-v0.3）
- 需要 PostgreSQL 17 + pgvector 扩展（为 v0.2 知识库预留）+ Neo4j 5

需要决策：后端集成与验收测试走"本地真实 DB"还是"Testcontainers"。

## Decision Drivers

- AI 协作内循环成本（每次运行测试的启动延迟累积）
- 开发机资源占用（内存、磁盘、CPU）
- 数据持久性与排错便利（DBeaver 直连查看能力）
- Docker Desktop 许可成本（商业限制）
- 团队规模与可移植性需求

## Considered Options

- 本地真实环境（显式安装 PostgreSQL 17 + Neo4j 5 Community）
- Testcontainers（pom 引入 testcontainers-postgresql + testcontainers-neo4j）

## Decision Outcome

**Chosen option**: "本地真实环境 + CI service container"，理由：AAF 是一人公司 + AI 协作为主的场景，"AI 内循环延迟"和"开发机常驻内存"是决定性因素；Testcontainers 的主要价值在团队协作与可移植性，在本场景下 ROI 为负。

### Positive Consequences

- AI 协作内循环 0 秒启动延迟，单次对话可完成"改代码 → 跑测试 → 看结果"多轮
- 开发机常驻内存约 600MB（PostgreSQL 100MB + Neo4j 500MB），远低于 Docker Desktop 2-4GB
- 测试数据可保留供 DBeaver 直连排错
- 无需 Docker Desktop 商业许可
- DB 客户端连接端口稳定（`localhost:5432` / `localhost:7687`），IDE 配置一次长期有效

### Negative Consequences

- 新开发机/新成员需手动安装 PostgreSQL + Neo4j + pgvector，有一次性学习成本
- 升级 PostgreSQL / Neo4j 版本时本地需单独操作（容器方案改 tag 即可）
- 无法天然并发隔离，需要靠 `@Transactional` + schema / 独立 db 名保证

### Reversal Triggers（反向选择触发条件）

仅当出现以下之一时考虑引入 Testcontainers：

1. 团队规模增长到 3+ 开发者，统一环境维护成本显著
2. 要做跨多 PostgreSQL 版本（17 / 16 / 15）的兼容性测试
3. 出现"本地环境漂移导致测试不一致"的具体事故并重现

以上均非 v0.1-v0.3 的紧迫需求。

## Pros and Cons of the Options

### 本地真实环境

| 维度 | 评估 |
|------|------|
| 开发机常驻内存 | PostgreSQL ~100MB + Neo4j ~500MB ≈ 600MB |
| 测试启动延迟 | 0 秒（进程常驻） |
| 数据持久性 | 保留，DBeaver 可直连排错 |
| DB 客户端连接 | `localhost:5432` / `localhost:7687` 稳定 |
| Docker Desktop 许可 | 无需 |
| AI 协作内循环成本 | 0 秒启动 × N 次 = 0 |
| 可移植性（新开发机） | 需装 PostgreSQL + Neo4j |
| 团队协作成本 | 新成员需学安装 |

### Testcontainers

| 维度 | 评估 |
|------|------|
| 开发机常驻内存 | Docker Desktop 2-4 GB + 容器 |
| 测试启动延迟 | 3-8 秒 / 容器（reuse 模式可规避但引入数据累积） |
| 数据持久性 | 默认清除，reuse 模式需额外清理策略 |
| DB 客户端连接 | 容器端口动态 |
| Docker Desktop 许可 | 商业用 > 250 人或 > $10M 收入需付费 |
| AI 协作内循环成本 | 3-8 秒 × N 次 = 累积分钟级 |
| 可移植性（新开发机） | Docker 搬一下 |
| 团队协作成本 | 约定 Docker 即可 |
| 并发隔离 | 每次新容器天然隔离 |

### 并列平手项

| 维度 | 两方案均可 |
|------|-----------|
| 版本锁定 | apt/压缩包 vs image tag |
| CI 复现 | GitHub Actions service container 原生支持 vs Docker-in-CI |

## More Information

### 行业趋势偏见自审

最初默认推荐 Testcontainers 源于"现代 Java 项目都在用"的行业偏见。Testcontainers 的价值命题是团队协作 + 可移植性，AAF 场景不在适配靶心。

### AAF 具体落地方案

**本地**：

- PostgreSQL 17（含 pgvector 扩展，v0.2 知识库用）+ Neo4j 5 Community
- 独立测试数据库 `aaf_test`（PostgreSQL）+ `aaf_test`（Neo4j 5 多库支持）
- 测试配置 `apps/service/src/test/resources/application-test.yaml` 指向 localhost
- 数据清理：`@Transactional`（Spring Test 自动回滚）+ Neo4j `@BeforeEach` 显式 `MATCH (n) DETACH DELETE n`
- Mock LLM：`@MockBean ChatClient`，CI 不消耗真实 API

**CI**：GitHub Actions service container（PostgreSQL + Neo4j），配置在 `.github/workflows/ci.yml` 的 acceptance 阶段。

### 历史讨论

- 原始决策记录：[AAF-023 dev-log #3](../../task/v0.1.0/AAF-023/dev-log.md#3-测试环境方案本地-vs-testcontainers-决策记录)
- 改进意见条目：`docs/prd/improvements.md`"后端测试环境走本地真实 DB + CI service container"（已采纳）

### 后续动作

- AAF-023 #8（后端测试环境基础设施）按本 ADR 落地
- `docs/reference/dev/dev-environment.md` 补"本地数据库"章节，加"起因：ADR-002"标注
- `docs/reference/dev/test/integration-test-standard.md` 和 `acceptance-test-standard.md` 顶部加"起因：ADR-002"
- `pom.xml` 不引入 testcontainers-* 依赖

### 已回收的反向痕迹

AAF-023 #1 曾在以下规范里引用 Testcontainers，本决策确立后已全部删除：

- `docs/reference/dev/test/acceptance-test-standard.md`
- `docs/reference/dev/test/unit-test-standard.md`
- `docs/reference/dev/test/integration-test-standard.md`（重写为"本地真实环境策略"）
- `docs/reference/team/roles/tester.md`
- `docs/reference/team/collaboration-standard.md`
- `docs/task/_template/test-report.md`
