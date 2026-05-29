---
level: Practice
layer: Model
purpose: Layer 0 内核层 Core——无状态 LLM 推理最小执行单元 + 接口契约层
status: draft
version: 1.0.0
date: 2026-05-28
author: AaronZZH
---

# Layer 0 内核层 Core

> 无状态·请求级，LLM 推理的最小执行单元。同时承载智能层接口契约定义。

## 职责

- LLM 接入与调用
- 上下文窗口管理
- Token 预算控制
- 多模型路由（按任务类型选择模型）
- 接口契约定义（AgentExecutor / AssistantExecutor / MemoryPipeline 等）

## 认知循环

```text
接收上下文（由 Agent 组装）
  ↓
推理 / 生成
  ↓
返回结果 + Token 消耗统计
```

## 状态策略

- **完全无状态**：不持有任何上下文，每次调用独立
- **可水平扩展**：支持池化复用，多实例并发
- **不需要 Checkpoint**：原子操作，失败直接重试

## 设计要点

- 上下文由调用方（Agent）组装后传入，Core 不负责上下文管理
- 支持 function calling，工具调用决策在此层完成
- 模型选择策略：简单任务用轻量模型，复杂任务用强模型

## 两种 LLM 封装

```text
LlmClient（接口）
  ├── SpringAiLlmClient
  │     包装 Spring AI ChatClient
  │     用于：直接 LLM 调用（对话引擎、记忆提取、重排等）
  └── AgentScopeLlmClient
        包装 AgentScope OpenAIChatModel
        用于：AgentScope ReActAgent 内部的 LLM 调用
        统一接入 AiModel 表的模型配置和 Token 计量
```

## 接口契约（intelligent/core/）

零框架依赖，被 Agent/Assistant/Cognition 共同依赖：

```text
├── agent/AgentExecutor        接口：execute / interrupt / reset / getName
├── assistant/AssistantExecutor 接口：chat / forkParallel
├── skill/SkillProvider        接口：match / getDefinitions
├── skill/SkillDef             Record：纯数据契约
├── tool/ToolProvider          接口：getDefinitions / call
├── tool/FunctionDefinition    Record：name / description / parameters
├── memory/MemoryPipeline      接口：execute(PipelineInput) → MemoryContext
├── memory/MemoryStrategy      枚举：MEMORY_ONLY / KNOWLEDGE_ONLY / HYBRID / PROCEDURAL_FIRST / FULL
├── llm/LlmClient              接口：call / stream
├── model/                     AiModel @Entity + 模型管理
├── prompt/                    PromptTemplate @Entity + 模板引擎
└── token/                     Token 计量与配额
```

## 相关文档

- [五层智能架构总览](../architecture.md)
- [模型路由技术方案](model-router-tech.md)
- [置信度门控器](confidence-gate.md)
