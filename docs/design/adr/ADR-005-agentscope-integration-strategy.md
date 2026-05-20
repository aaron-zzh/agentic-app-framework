---
level: Practice
layer: Principle
purpose: ADR-005 AgentScope 整合策略——薄门面 + 委托
status: accepted
version: 1.0.0
date: 2026-05-20
author: AaronZZH
---

---
status: accepted
date: 2026-05-20
deciders: [AaronZZH]
consulted: []
informed: []
related-tasks: []
---

# ADR-005: AgentScope 整合策略——薄门面 + 委托

## Context and Problem Statement

AAF 智能层（Layer 3）已自研了 `CognitiveCycleExecutor`、`TeamOrchestrator`、`TaskDistributor`、`SessionManager`、`WorkingMemoryImpl` 等组件。同时 AgentScope Java SDK 提供了成熟的 `ReActAgent`、`Pipeline`、`Session`、`Memory`、`Hook` 等运行时能力，两者存在大量功能重叠。

需要决策：如何整合 AgentScope，避免重复造轮子，同时保留 AAF 五层智能架构的设计语言和代码层次。

## Decision Drivers

- AgentScope 的运行时（Session 持久化、优雅关闭、Tracing、autocontext-memory Token 截断）比 AAF 自研更成熟
- AAF 五层智能架构（Core→Cognition→Agent→Assistant→Team）是团队协作、文档、测试的共同语言，不能丢失
- AgentScope 是全响应式（Mono/Flux），AAF 现有接口是同步的，两者范式存在阻抗
- 禁兼容层原则：不允许双路径并存

## Considered Options

- **路线 A**：AAF 核心抽象不变，AgentScope 作为实现层（适配器模式）
- **路线 B**：AgentScope 为骨架，AAF 五层架构作为薄门面扩展层

## Decision Outcome

**Chosen option**: "路线 B——AgentScope 为骨架，AAF 五层架构作为薄门面"

理由：路线 A 的适配器会越来越厚，AgentScope 响应式范式与 AAF 同步接口的阻抗随功能增加而放大；路线 B 直接复用成熟运行时，AAF 只在 AgentScope 之上添加特有扩展。

### Positive Consequences

- 直接复用 AgentScope 的 Session/Memory/Hook/Pipeline/Tracing，减少自研维护成本
- 五层架构保留代码层次，新人可通过类名理解业务概念
- 每层只保留 AAF 特有逻辑，代码量小，易于理解

### Negative Consequences

- 现有厚实现（`CognitiveCycleExecutor`、`TeamOrchestrator` 等）需逐步替换为薄门面
- 需要引入 AgentScope 响应式编程（Mono/Flux），对团队有学习成本

### Reversal Triggers

仅当出现以下之一时考虑回切：

1. AgentScope Java SDK 停止维护或出现不可接受的 breaking change
2. AgentScope 响应式范式与 AAF 业务需求产生根本性冲突

## 薄门面设计原则

**判断标准**：一个类里超过 50% 的代码是在调用 AgentScope，说明这层太厚，应削减到只保留 AAF 特有逻辑。

每层的职责边界：

| AAF 层 | 代码量目标 | AgentScope 委托 | AAF 特有扩展 |
|--------|-----------|----------------|-------------|
| Team | ~50 行 | `Pipeline` / `MsgHub` | A2A 协议、冲突仲裁、进度同步 |
| Assistant | ~100 行 | 主 `ReActAgent` | 情感感知 Hook、用户画像注入 |
| Agent | ~30 行 | `ReActAgent`（执行循环） | `AgentExecutor` 接口适配 |
| Cognition | ~80 行 | `GenericRAGHook` + `LongTermMemory` | 记忆管道、用户私有隔离 |
| Core | ~30 行 | AgentScope `Model` | Token 计量、模型路由 |

## Pros and Cons of the Options

### 路线 A：AAF 核心抽象不变，AgentScope 作为实现层

- Good: 上层代码不变，渐进替换，风险低
- Bad: AgentScope 响应式（Mono/Flux）与 AAF 同步接口阻抗越来越大
- Bad: 适配器层会越来越厚，最终变成重复实现

### 路线 B：AgentScope 为骨架，AAF 五层架构作为薄门面

- Good: 直接复用成熟运行时，不重复造轮子
- Good: 五层架构保留代码层次，设计语言不丢失
- Bad: 现有厚实现需要重构替换
- Bad: 引入响应式编程有学习成本

## More Information

- 相关设计文档：[architecture-detail.md](../architecture-detail.md) — AgentScope 整合策略章节
- 后续动作：
  1. 引入 `agentscope-agui-spring-boot-starter`，删除自研 `AgUiStreamHandler`
  2. 引入 `agentscope-a2a-spring-boot-starter`，补全 `A2AProtocolService`
  3. 引入 `agentscope-extensions-session-redis`，替换自研 `SessionManager`
  4. 引入 `agentscope-extensions-autocontext-memory`，补全 Token 预算截断
  5. 将 `CognitiveCycleExecutor`、`TeamOrchestrator` 等厚实现重构为薄门面
