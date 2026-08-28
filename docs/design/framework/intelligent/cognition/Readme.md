---
level: Practice
layer: Model
purpose: 索引 L1 认知基础层的记忆、检索与学习沉淀设计
status: draft
version: 1.0.0
date: 2026-08-23
author: Kiro
tags:
  - L1 Cognition
  - 目录索引
dependencies:
  - ../architecture.md
scope:
  includes:
    - L1 认知基础的职责边界与受控上下文契约索引
  excludes:
    - 上下文如何装配进提示词（属 L0）
    - 任务编排与执行责任（属 L2/L3）
gains:
  - 能定位记忆、检索与学习相关的设计文档
  - 能判断某项上下文能力应由 L1 提供还是由调用方自备
---

# L1 认知基础层设计

> L1 管理记忆、知识、价值观与决策依据，为助理和智能体提供回忆、知识检索、上下文组织与学习沉淀。
> **持久级**，区分个人私有、组织共享、执行工作区与审计留存；**只按授权返回受控上下文，不作为开放业务工具暴露**。

## 文档列表

| 文档 | 内容 | 状态 |
|---|---|---|
| [cognition.md](cognition.md) | L1 总体职责、`ContextRequest`、四分区、用户理解与有效上下文透明度 | 草案 · 契约已完整，含目标态 |
| [memory.md](memory.md) | 记忆分型、唯一读管道、固定写管道、治理与 AgentScope 边界 | 草案 · 契约已完整，含目标态 |
| [retrieval.md](retrieval.md) | 统一检索门面、通道预算、并行、RRF、重排与引用 | 草案 · 契约已完整，含目标态 |
| [learning.md](learning.md) | 触发预算、类型化候选、产出分流与六道门禁 | 草案 · 契约已完整，含目标态 |

## 与其他目录的关系

| 目录 | 关系 |
|---|---|
| [../](../Readme.md) | 上下文请求在主流程中的位置由 `architecture.md` 定义 |
| [../assistant/](../assistant/Readme.md) | L3 通过 `ContextRequest` 请求受控上下文，不直接访问存储 |
| [../skill/](../skill/Readme.md) | Skill 的知识绑定限定可检索范围；程序化经验发布后以 SkillVersion 为能力真理源 |
