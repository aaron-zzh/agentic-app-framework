---
level: Practice
layer: Principle
purpose: ADR-006 LLM 调用路径优先级——AgentScope 优先，Spring AI 轻量辅助
status: accepted
version: 1.0.0
date: 2026-05-23
author: AaronZZH
---

---
status: accepted
date: 2026-05-23
deciders: [AaronZZH]
consulted: []
informed: []
related-tasks: []
---

# ADR-006: LLM 调用路径优先级——AgentScope 优先，Spring AI 轻量辅助

## Context and Problem Statement

AAF 存在两条 LLM 调用路径：路径 A（Spring AI：ResilientChatService → ModelRouter → DynamicChatClientFactory）和路径 B（AgentScope：AgentFactory → ReActAgent）。ADR-005 确定了 AgentScope 为骨架的整合策略，但未明确**日常开发中应优先选择哪条路径**。开发者在实现新功能时缺乏明确指引，可能导致两条路径使用混乱、重复封装。

## Decision Drivers

- AgentScope 是原生 Reactor 架构（Mono/Flux 全链路非阻塞），与 AAF 的 WebFlux + SSE 流式输出天然匹配
- AgentScope 内置 ReAct 循环、工具调用自动重试/纠错、断点续跑、人工介入 Hook、多 Agent 协作（Pipeline/MsgHub/Supervisor）、A2A 协议——这些能力 Spring AI 均需自研
- Spring AI 的核心 API 是同步请求-响应模式，流式（Flux）是附加能力，上下游（工具调用、记忆读写）仍为同步
- ADR-005 已确定"薄门面 + 委托"策略，AgentScope 为骨架
- 禁兼容层原则：不允许两条路径做同一件事

## Considered Options

- **选项 A**：Spring AI 优先，AgentScope 仅用于复杂多 Agent 场景
- **选项 B**：AgentScope 优先，Spring AI 作为轻量辅助路径（单次 LLM 调用场景）
- **选项 C**：完全统一到 AgentScope，移除 Spring AI

## Decision Outcome

**Chosen option**: "选项 B——AgentScope 优先，Spring AI 作为轻量辅助路径"

理由：AgentScope 的运行时能力覆盖了 AAF 智能层 90% 的需求（Agent 执行、多 Agent 协作、工具调用、记忆、断点续跑），且原生响应式架构与 AAF 技术栈匹配；Spring AI 保留用于不需要 Agent 循环的轻量单次调用场景。

### Positive Consequences

- 开发者有明确的路径选择指引，减少决策成本
- 避免在 Spring AI 之上重复实现 AgentScope 已有的成熟能力
- 原生 Reactor 全链路非阻塞，性能和资源利用率更优
- 与 ADR-005 薄门面策略一致，形成完整的技术栈决策链

### Negative Consequences

- 团队需要熟悉 AgentScope Java SDK 的 API 和编程模型
- 部分已有的 Spring AI 直接调用代码需要评估是否迁移

### Reversal Triggers

仅当出现以下之一时考虑回切：

1. AgentScope Java SDK 停止维护或出现不可接受的性能/稳定性问题
2. Spring AI 演进出完整的 Agent 运行时能力（ReAct + 工具 + 记忆 + 多 Agent），且生态优势明显超过 AgentScope

## 路径选择指引

```
需要 Agent 推理循环？（ReAct / 工具调用 / 多步规划）
  → AgentScope 路径

需要多 Agent 协作？（Pipeline / MsgHub / Supervisor）
  → AgentScope 路径

单次 LLM 调用，无需推理循环？
  → Spring AI 路径
```

### AgentScope 路径适用场景（默认）

- Agent 执行（ReAct 循环、工具调用、断点续跑）
- 多 Agent 协作（Pipeline、MsgHub、Supervisor）
- 会话管理（Session 持久化、优雅关闭）
- 需要人工介入 Hook 的场景

### Spring AI 路径适用场景（轻量辅助）

- Embedding 生成（知识库入库、语义搜索）
- 简单分类/提取（意图识别、情感分析、实体提取）
- 摘要生成（对话摘要、文档摘要）
- 结构化数据提取（无需推理循环的单次调用）

### 共享基础设施

两条路径共享以下组件，不重复建设：

- `ai_model` 表：apiKey/baseUrl/capabilities 唯一来源
- `ModelPreference` 表：用户/系统级模型偏好
- `TokenUsageEvent`：统一 Token 计量
- `ModelRouter`（六层决策链）：两条路径均可使用

## Pros and Cons of the Options

### 选项 A：Spring AI 优先

- Good: Spring 生态原生，团队熟悉度高
- Bad: 需要自研 ReAct 循环、断点续跑、人工介入、多 Agent 协作等能力
- Bad: 同步 API 与 WebFlux/SSE 存在阻抗，需要额外异步适配

### 选项 B：AgentScope 优先（选定）

- Good: 直接复用成熟运行时，不重复造轮子
- Good: 原生 Reactor 架构，全链路非阻塞
- Good: 保留 Spring AI 处理轻量场景，各取所长
- Bad: 需要学习 AgentScope SDK
- Bad: 两条路径并存增加认知负担（但有明确边界）

### 选项 C：完全统一到 AgentScope

- Good: 单一路径，零认知负担
- Bad: Embedding、简单分类等场景用 AgentScope 过重
- Bad: 放弃 Spring AI 的 VectorStore 抽象等便利能力

## More Information

- 前置决策：[ADR-005](ADR-005-agentscope-integration-strategy.md) — AgentScope 整合策略（薄门面 + 委托）
- 相关设计：[execution-flow.md](../framework/execution-flow.md) — 两条 LLM 调用路径章节
- 相关设计：[execution-flow.md](../framework/execution-flow.md) — 路径可视化
