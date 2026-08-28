---
level: Overview
layer: Model
purpose: 定义 Assistant 专题设计的边界，并索引 Assistant、Skill 与执行编排设计
status: draft
version: 1.3.0
date: 2026-08-20
author: Kiro
scope:
  includes:
    - Assistant 的会话级职责与运行时设计
    - Assistant 路由、Skill 装配与上下文边界设计
    - 对话式与任务式 Assistant 的统一执行路径
gains:
  - 能找到 Assistant 领域的专项设计
  - 能区分 Assistant 路由、Skill 执行与任务交互策略的职责
---

# Assistant 专题设计

> 本目录描述五层智能架构中 Assistant 的会话级职责、路由与执行编排边界；目标领域模型以 [五层智能架构](../../architecture.md) 为准。

## 文档列表

### 当前合同

1. [通用智能助理一体化交互流程](unified-assistant-interaction-flow.md) — 以整体流程图呈现通用对话如何完成各种任务，并标记任务式交互的关键差异。
2. [任务式 Assistant 统一执行路径设计](task-oriented-assistant-execution-design.md) — Assistant 唯一启动入口、`CHAT`/`EXECUTION`/`TEAM` 运行模式、任务身份、AG-UI 唯一正文和无正文任务事件边界。
3. [Skill 渐进加载与在线知识边界设计](skill-progressive-loading-design.md) — Skill 正文、references、知识库、授权与版本模型设计。
4. [Team 技术方案](../team/team-tech.md) — 最小 L4 Team 的版本冻结、静态 Worker 和 TaskBoard 调度。

### 历史资料

- [Assistant 功能设计](assistant-design.md) — 已废弃的旧双入口设计。
- [Assistant 技术方案](assistant-tech.md) — 已废弃的旧实例/A2A 技术方案。
- [统一 AI 任务事件消息系统设计](ai-task-event-system-design.md) — 已废弃的多协议事件草案。
- [Assistant 运行要点历史记录](flow.md) — 原始十项运行要点，仅用于追溯架构演进，不构成现行合同。
