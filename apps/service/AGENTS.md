# AAF — Agentic App Framework

基于 Spring Boot 4 + Spring AI 2.0 的生产级多智能体应用开发框架。

## 框架概述

AAF 为 Java/Spring 生态开发者提供构建多智能体应用的完整工具链：从单个 Agent 的定义、工具调用、记忆管理，到多 Agent 的编排协作、RAG 检索增强，再到 REST/GraphQL/WebSocket/MCP 多协议暴露。开发者只需关注业务逻辑，框架处理 LLM 交互、上下文管理、可观测性等基础设施。

## 目标

- 降低多智能体应用的开发门槛，让 Spring 开发者用熟悉的方式构建 AI 应用
- 提供生产级的可靠性：可观测、可追溯、可扩展、安全
- 框架本身也用 AI 多智能体协作开发，持续验证和改进人机协作模式

## 基本原则

- 除了完成具体功能目标，持续关注如何提升 AI 与人的协作效率
- 不断优化协作规范、流程和工具配置，定期反思如何提升效率
- 每个智能体和人类用户都应积极提出改进意见
- 协调者定期审阅改进意见，评估后纳入规范或排入待办
- 所有重要事项都要落实到文档，并保持文档间的一致性
- 每项内容应有唯一来源，尽量不重复，其他地方通过链接引用

详细文档见 [docs/README.md](docs/README.md)。

## 技术栈

Java 25, Spring Boot 4.0.6, Spring AI 2.0-M4, PostgreSQL/PgVector, Redis, Neo4j, WebFlux, GraphQL, MCP, Flyway

## 架构

4 层分层，依赖单向上→下，禁止反向。详见 [docs/architecture.md](docs/architecture.md)。

```
L4 API (REST/GraphQL/WebSocket/MCP) → L3 编排 (Workflow/DAG) → L2 Agent核心 (Agent/Tool/Memory/RAG) → L1 基础设施 (LLM/DB/Cache)
```

- Web: WebFlux 主，WebMVC 兼容
- 数据: R2DBC 主，JPA 管理接口
- LLM: Spring AI ChatModel 统一抽象，能用 Spring AI 的不造轮子
- 向量: PgVector 默认

## 包结构

根包 `com.xuejiai.aaf`，层间依赖单向：`api → orchestration → agent/tool/memory → infra`。`common` 不依赖业务包。配置类集中在 `infra/config`。

| 包 | 职责 |
|---|------|
| `agent/` | Agent 接口、注册、上下文、生命周期 |
| `tool/` | Tool 注册、MCP 集成、内置工具 |
| `memory/` | 对话记忆、向量记忆、工作记忆 |
| `rag/` | RAG Pipeline |
| `orchestration/` | 编排引擎、DAG、编排模式 |
| `api/` | REST、GraphQL、WebSocket、MCP Server |
| `infra/` | 配置、安全、可观测性、持久化 |
| `common/` | 异常体系、事件、工具类 |

## 编码规范

Java 25，积极使用 record、sealed interface、pattern matching、virtual threads。

**原则**: 接口优先 · 不可变优先(DTO/值对象用 record) · 响应式核心链路(Mono/Flux) · Builder 构建复杂对象

**命名**: 接口=名词(`Agent`) · 实现=描述+接口(`ChatAgent`) · 配置=`XxxConfig` · 异常=`XxxException` · 事件=`XxxEvent`

**异常**: sealed 体系，基类 `AafException`，按模块分子类。不吞异常，消息含上下文。

**日志**: `@Slf4j`。Agent 执行 INFO，Tool 调用 DEBUG，异常 ERROR。

**测试**: JUnit 5 + Mockito 单元测试，Testcontainers 集成测试。Mock LLM 调用。命名 `shouldXxxWhenYyy()`。

## 组织架构

### 协调者（Tech Lead）

kiro_default（默认 agent）— 用户故事拆分、技术任务拆分、派发、集成验证、过程改进、配置管理（提交/CI）

### 职能团队

| 成员 | 配置文件 | 职责 |
|------|---------|------|
| product（产品经理） | `.kiro/agents/product.json` | 需求分析、用户故事、验收标准 |
| architect（架构师） | `.kiro/agents/architect.json` | 技术设计、接口定义、架构决策 |
| tester（测试工程师） | `.kiro/agents/tester.json` | 编写测试、质量验证 |
| reviewer（质量工程师） | `.kiro/agents/reviewer.json` | 代码 Review、规范检查 |

### 开发团队（可并行）

| 成员 | 配置文件 | 负责模块 |
|------|---------|---------|
| developer-agent | `.kiro/agents/developer-agent.json` | `agent/` |
| developer-tool | `.kiro/agents/developer-tool.json` | `tool/` |
| developer-memory | `.kiro/agents/developer-memory.json` | `memory/`, `rag/` |
| developer-orch | `.kiro/agents/developer-orch.json` | `orchestration/` |
| developer-api | `.kiro/agents/developer-api.json` | `api/` |
| developer-infra | `.kiro/agents/developer-infra.json` | `infra/`, `common/` |

## 路线图

- v0.1: Agent 核心抽象 + Tool 系统 + Chat Agent 示例
- v0.2: 编排引擎 + RAG Pipeline + MCP 集成
- v0.3: 多 Agent 协作 + 事件总线
- v0.4: 可观测性 + 安全 + 多租户

## AI 协作宣言

- 规范即共识 高于 口头约定
- 人机协作 高于 单方决策
- 响应变化 高于 遵循计划
- 持续改进 高于 固守流程
- 精益求精 高于 简单应付
- 知其然并知其所以然
