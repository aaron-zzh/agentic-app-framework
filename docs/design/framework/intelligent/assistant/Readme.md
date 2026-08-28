---
level: Practice
layer: Model
purpose: 索引 L3 助理层的领域模型与多智能体协调编排设计
status: draft
version: 1.3.0
date: 2026-08-25
author: Kiro
tags:
  - L3 Assistant
  - 目录索引
dependencies:
  - ../architecture.md
  - ../runtime.md
scope:
  includes:
    - L3 助理的领域模型与会话级职责索引
    - 多智能体协调编排与聚合的索引
  excludes:
    - 唯一入口、任务身份、模式受理、复杂度判定与完成门禁（属顶层 runtime）
    - 事件投影与工具授权治理（属顶层）
    - Skill 版本与加载机制（属 skill/）
gains:
  - 能定位助理领域模型与协调编排的设计合同
  - 能判断某项设计属于 L3 助理职责还是跨层横向机制
---

# L3 助理层设计

> L3 是面向用户的认知主体：具有人格，可扮演角色并组织技能、工具、记忆与知识；理解意图、规划任务、调度执行、验证结果，并对最终交付负责。
> **会话级**，保存当前焦点、任务进展与交互上下文。

**本目录不按交互模式划分文档。** `CHAT`、`EXECUTION`、`TEAM` 共用同一运行时、执行画像、工具治理、完成验证与事件事实源；任务式只是受约束的执行策略，不是第二套运行时。模式差异统一在 [runtime.md](../runtime.md) 中表达。

## 文档列表

| 文档 | 内容 | 状态 |
|---|---|---|
| [assistant.md](assistant.md) | Persona、Role、MemoryStrategy、会话身份、路由、澄清、输入干预、系统模板与主体隔离 | 草案 · 契约已细化，含实现缺口 |
| [coordination.md](coordination.md) | TaskBoard、CoordinationPlan、聚合、产物时序与五类产物工具；事件仅链接顶层契约 | 草案 · 契约已细化，含目标态 |

协调编排独立成篇而非归入某个交互模式：`CHAT` 与 `EXECUTION` 可按 `TaskAnalysis` 判定使用 `single` 或 `coordinated`，`TEAM` 使用冻结 Team version 的 `teamCoordinated`；三者共用聚合契约。

## 与其他目录的关系

| 目录 | 关系 |
|---|---|
| [../](../Readme.md) | 入口、身份、执行意图、模式受理、复杂度判定、完成门禁、事件与工具治理由顶层文档定义 |
| [../cognition/](../cognition/Readme.md) | 本层通过 `ContextRequest` 获取受控上下文 |
| [../agent/](../agent/Readme.md) | 本层是唯一委派方，冻结 L2 的执行边界 |
| [../team/](../team/Readme.md) | 需要跨助理长期协作与仲裁时升级到 L4 |
| [../skill/](../skill/Readme.md) | 本层的 Role 组织 Skill，Skill 的版本与授权模型在该目录定义 |
