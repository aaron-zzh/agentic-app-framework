---
level: Practice
layer: Product
purpose: 说明 AAF 项目的参考来源和借鉴内容
status: published
version: 1.1.0
date: 2026-05-06
author: AaronZZH
---

# 参考来源与借鉴

AAF 项目在开发流程、规范体系、架构设计等方面借鉴了多个优秀项目和方法论。本文档统一说明借鉴来源，便于追溯和致谢。

## 开发流程及协作规范

- 规范驱动开发（SDD），先写规范再写代码，规范是人类和 AI 的共同真理来源
- 围栏工程，硬约束 + 自动验证构建 AI 行为围栏
- 价值驱动，以用户价值为导向，优先交付高价值功能
- 敏捷开发，迭代周期、用户故事、验收标准
- AI 结对编程，人类负责设计决策，AI 负责实现与验证
- 过程改进，过程域、度量分析、过程审计（CMMI5）
- 单元测试，TDD、测试金字塔、覆盖率门控

详见 [开发过程](reference/team/process-standard.md)

## AI 开发团队搭建

- [multica](https://github.com/multica-ai/multica)：多智能体协作开发框架，展示 CLAUDE.md 硬约束与验证循环
- [gstack](https://github.com/garrytan/gstack)：Garry Tan 的 Claude Code 工具集，提供 design-consultation、design-review 等技能模式

## 架构设计

- [Explicit Architecture](https://herbertograca.com/2017/11/16/explicit-architecture-01-ddd-hexagonal-onion-clean-cqrs-how-i-put-it-all-together/)：六边形 + 洋葱 + DDD 统一模型
- 领域模型 DDD（Eric Evans）：限界上下文、聚合、领域事件
- C4 Model：架构视图分层（Context/Container/Component/Code）
- Clean Architecture：依赖反转、层间隔离

### 架构核心原则

- **上层可调用任意下层，禁止下层调用上层**
- **五层架构**：基础设施层 → 引擎层 → 智能层 → 服务层 → 交互层
- **五层智能**：Core → Cognition → Agent → Assistant → Team

## 文档体系

- [Diátaxis](https://diataxis.fr/)：文档四象限分类（Tutorial/Guide/Reference/Explanation）
- ADR（Architecture Decision Records）：架构决策记录格式
- [Deepractice 知识框架](https://docs.deepractice.ai/zh/)：认知维度（level）× 抽象层级（layer）定位

### 内容体系设计

AAF 文档体系融合多种方法论：

- **知识框架理论**：level（Reality/Thought/Theory/Practice/Meaning）× layer（Principle/Paradigm/Pattern/Model/Product）
- **五度空间模型**：任何目录 = 1 个 README + ≤ 5 个内容项
- **类型驱动**：Tutorial / Guide / Reference / Explanation / Map

详见 [内容体系规范](reference/content-system/Readme.md)

## 工程化实践

### Monorepo 管理

- [Nx](https://nx.dev/)：Monorepo 编排、affected 命令、task pipeline、缓存
- [pnpm](https://pnpm.io/)：包管理、workspace 协议
- [Maven](https://maven.apache.org/)：Java 项目构建、依赖管理

### 质量保障

- CMMI5：过程域、度量分析、过程审计
- 敏捷开发：迭代周期、用户故事、验收标准
- Google Java Style：代码格式化规范（Spotless + AOSP 风格）
- ArchUnit：架构约束自动验证

## 技术栈

> 详见 [apps/service/pom.xml](../apps/service/pom.xml)（后端依赖）、[package.json](../package.json)（前端与工具链）

### 后端框架

- Java 25
- Spring Boot 4.0.6
- Spring AI 2.0.0
- Spring WebFlux（响应式 Web）
- Spring Data JPA（ORM）
- Spring Security（安全框架）
- Spring GraphQL（GraphQL API）

### 数据存储

- PostgreSQL（主数据库）
- PgVector（向量存储）
- Neo4j（图数据库/知识图谱）
- Redis（缓存/会话）
- Milvus（向量数据库，可选）
- H2（开发/测试数据库）

### AI 与大模型

- OpenAI API（GPT 系列模型接入）
- Ollama（本地模型部署）
- MCP 协议（工具调用与上下文传递）
- A2A 协议（多智能体协作）

### 前端技术

- Next.js 16.1.6（React 全栈框架）
- React 19.0.0
- TypeScript 5.9.2
- Vitest 3.1.0（单元测试）

### 工作流与文档

- Flowable（工作流引擎）
- Flyway（数据库迁移）
- Tika（文档解析）
- Jsoup（HTML 解析）

### 开发工具

- Lombok（代码简化）
- Spotless（代码格式化）
- SpringDoc OpenAPI（API 文档）
- Sentry（错误监控）
- Micrometer + Prometheus（指标监控）
- GraalVM Native（原生镜像编译）

### AI 开发助手

- Kiro CLI（AI 协作开发）
- Claude（代码生成与审查）

## 延伸阅读

- [规范驱动开发](explanation/spec-driven-development.md) — SDD 方法论详解
- [架构思想](explanation/architecture-thought.md) — 架构决策背后的 Why
- [设计原则](explanation/design-principles.md) — 化繁为简、DRY、AI 友好等
- [内容体系规范](reference/content-system/Readme.md) — 文档管理系统设计
