---
level: Practice
layer: Model
purpose: 索引 L4 群体层的多助理协作组织设计
status: draft
version: 1.2.0
date: 2026-08-25
author: Kiro
tags:
  - L4 Team
  - 目录索引
dependencies:
  - ../architecture.md
  - ../runtime.md
scope:
  includes:
    - L4 群体的协作组织、成员冻结与调度索引
  excludes:
    - L3 对 L2 的有界委派（属 assistant/ 与 agent/）
gains:
  - 能定位多助理协作的设计文档
  - 能判断某项协作应停留在 TaskBoard 委派还是升级为 Team
---

# L4 群体层设计

> L4 围绕共同目标组织多个助理，负责目标对齐、任务分工、进度协调、结果汇总与冲突仲裁。
> **项目级**，保存目标、分工、进度与仲裁结果。

升级判定：协作者需要独立 Persona、长期责任、跨助理目标对齐或冲突仲裁时才使用 L4；否则停留在 L3 对 L2 的有界委派，**不得把 Team 简化为 TaskBoard 中的普通子智能体**。

## 文档列表

| 文档 | 内容 | 状态 |
|---|---|---|
| [team.md](team.md) | TeamDefinition、发布与版本冻结、统一持久化、Worker 槽位、成员预算和失败隔离 | 草案 · 契约已细化，含实现偏离与目标态 |

## 与其他目录的关系

| 目录 | 关系 |
|---|---|
| [../runtime.md](../runtime.md) | Team 的运行模式、入口与完成门禁由统一运行时定义 |
| [../architecture.md](../architecture.md) | Team 的分解预算例外由顶层静态合同定义 |
| [../assistant/](../assistant/Readme.md) | Team 成员是独立 Assistant，各自遵循 L3 契约；调度复用 L3 TaskBoard |
