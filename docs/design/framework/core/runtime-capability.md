---
level: Theory
layer: Paradigm
purpose: 元引擎运行时能力——工作流执行、智能体编排、知识记忆集成、降级策略、沙箱
status: draft
version: 0.1.0
date: 2026-05-20
author: AaronZZH
---

# 运行时能力

> 运行时是元引擎的执行态，负责将开发时定义的 DSL 驱动为实际运行的业务系统。
> DSL 变更立即反映到运行时，代码变更热加载后立即生效，开发与运行的边界消失。
>
> 运行时能力是执行调度器路由后的执行结果——执行调度器负责"怎么调度"，运行时能力描述"能做什么"。

## 工作流执行

- Flowable 工作流由 DSL 定义，元引擎负责实例化和生命周期管理
- 工作流节点可嵌入 Agent 任务，实现确定性流程与不确定性任务的混合编排
- 工作流执行状态实时反映到对话区和工作区

详见 [工作流引擎](../engine/workflow.md)。

## 智能体编排

元引擎作为 Team 层的编排者：

```text
根据任务复杂度决定启用单 Agent 还是多 Agent
  ↓
管理 Agent 间的通信和任务分配
  ↓
汇总 Agent 执行结果，决定是否需要人工介入
```

遵循五层智能架构，详见 [智能体系统设计](../intelligent/agent.md)。

## 知识与记忆集成

元引擎在每次执行前后自动组装和归档上下文：

```text
执行前：
  从知识库检索相关规范和领域知识
  从记忆系统加载用户偏好和历史上下文
  组装高质量执行上下文

执行后：
  工具调用结果归档到知识库
  执行日志写入记忆系统
  效果评估触发知识更新
```

详见 [知识库引擎](../engine/nexus-knowledge.md) | [记忆引擎](../engine/atom-memory.md)。

## 智能降级策略

元引擎在各专项引擎不可用时自动降级，保证系统持续可用：

```text
AI 服务不可用    → 降级到规则引擎（预定义工作流）
知识检索失败     → 使用默认知识库（内置规范文档）
Agent 超时       → 切换简化流程（直接 LLM 调用）
沙箱执行失败     → 暂停并转人工处理
所有 AI 不可用   → 纯工作流模式，保留核心业务功能
```

降级不静默发生，对话区明确告知用户当前处于降级模式及原因。

## 运行时沙箱

所有不可信代码在隔离沙箱中执行，防止影响引擎核心。

隔离对象：
- 用户自定义工具 / MCP 插件
- 工作流自定义脚本节点
- Agent 工具调用结果处理逻辑

隔离机制（按环境选择）：

首选：GraalVM Polyglot Sandbox（GraalVM JDK 21）
- 统一支持 JS / Python / Groovy 等多语言脚本
- `allowAllAccess(false)` 禁止访问宿主 JVM
- `allowIO(IOAccess.NONE)` 禁止文件 IO
- `allowCreateThread(false)` 禁止创建线程
- `allowNet(false)` 禁止网络（白名单除外）
- `statementLimit(N)` 限制执行语句数，防死循环
- 每次执行用新 Context，执行完销毁，无状态残留

降级：子进程隔离 + 超时控制（无 GraalVM 时）
- 启动独立子进程执行脚本（Python 解释器）
- 通过 stdin/stdout 传递输入输出
- 超时强制 kill 子进程

执行结果：
- 成功 → 结果返回引擎，写入状态
- 失败 → 触发降级策略，记录日志，通知用户

代码结构见 [code-structure.md · core/sandbox](../code-structure.md#coresandbox--沙箱执行)。

## 相关文档

- [元引擎设计](../meta-engine.md)
- [执行调度器](execution-dispatcher.md)（调度运行时能力的入口）
- [工作流引擎](../engine/workflow.md)
- [智能体系统设计](../intelligent/agent.md)
