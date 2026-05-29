---
level: Practice
layer: Model
purpose: Layer 0 Core 核心接口——智能层接口契约定义与 LLM 基础能力
status: draft
version: 1.0.0
date: 2026-05-28
author: AaronZZH
---

# Core 核心接口功能设计

> 智能层接口定义层，零框架依赖，被 Agent/Assistant/Cognition 共同依赖。

## 定位

Core 是五层智能架构的最底层，承担两个职责：
1. **接口契约**：定义各层交互的稳定接口（AgentExecutor、AssistantExecutor、MemoryPipeline 等）
2. **LLM 基础能力**：模型管理、Token 计量、Prompt 模板等请求级无状态能力

## 核心接口

| 接口 | 职责 | 调用方 |
|------|------|--------|
| AgentExecutor | execute / interrupt / reset | Assistant 调度 Agent |
| AssistantExecutor | chat / forkParallel | 服务层调用 |
| MemoryPipeline | execute → MemoryContext | Agent 执行前拉取上下文 |
| MemoryStrategy | 枚举：决定检索哪些源 | Assistant 配置 |
| SkillProvider | match / getDefinitions | Assistant 技能匹配 |
| ToolProvider | getDefinitions / call | Agent 工具调用 |
| FunctionDefinition | name / description / parameters | 工具契约 |
| LlmClient | call / stream | Agent/Assistant 调用 LLM |

## 设计原则

- **完全无状态**：每次调用独立，不持有上下文
- **可水平扩展**：支持池化复用，多实例并发
- **框架可替换**：上层只依赖 Core 接口，实现层可切换（Spring AI / AgentScope）

## 相关文档

- [技术方案 — Core](core-tech.md)
- [模型管理与路由](model-router.md)
- [置信度门控器](confidence-gate.md)
- [五层智能架构总览](../architecture.md)
