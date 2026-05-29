---
level: Practice
layer: Model
purpose: Layer 2 智能体层 Agent——无状态任务执行的功能设计
status: draft
version: 1.0.0
date: 2026-05-28
author: AaronZZH
---

# 智能体层 Agent 功能设计

> 任务级·无状态，感知-规划-执行-评估的认知循环。

## 定位

Agent 是五层智能架构的执行层。接收 Assistant 派发的原子任务，通过认知循环完成后归还池中。不面向用户，只面向 Assistant。

## 核心能力

- **认知循环**：感知→规划→执行→评估→学习，每步可断点续跑
- **无状态执行**：执行前从 Cognition 拉取上下文，执行后写回，自身不持久化
- **池化复用**：AgentPool 全局共享，借出重置/归还清空，支持多实例并发
- **工具调用**：通过 MCP 协议调用工具，受 Assistant 白名单控制
- **步骤规划**：PlanNotebook 管理子任务步骤，支持可验证性降维
- **断点续跑**：步骤级 Checkpoint，失败从最近检查点恢复

## 认知循环

```text
感知 → 规划 → 执行 → 评估 → 学习 ↔ 记忆
```

| 模块 | 职责 |
|------|------|
| 感知 | 输入解析、意图识别 |
| 规划 | 目标分解、任务排序、可验证性降维 |
| 执行 | 工具调用、LLM 推理 |
| 评估 | 结果验证（可验证→自动检查；不可验证→标记待审查）、置信度评估 |
| 学习 | 执行结果写回 Cognition（通过 Learning 通道） |

## 何时启用 Agent

- 任务复杂度高 + 价值高 + 错误可控 → Agent
- 任务简单/可预测 → Workflow
- 错误成本高 → 加人工审核节点

**最适合场景**：编码 Agent（从需求文档到完整 PR）。

## 核心心法

- Workflow 适合可预测任务，Agent 适合动态场景
- **站在 Agent 视角思考，它只能看到你给的上下文**
- Agents are models using tools in a loop——保持极致简单

## 相关文档

- [技术方案 — Agent](agent-tech.md)
- [五层智能架构总览](../architecture.md)
- [执行轨迹](execution-trace.md)
- [工具权限](../../engine/execution/tool-permission.md)
