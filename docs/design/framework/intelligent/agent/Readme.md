---
level: Practice
layer: Model
purpose: 索引 L2 智能体层的受托执行职责与 Harness 运行边界设计
status: draft
version: 1.1.0
date: 2026-08-25
author: Kiro
tags:
  - L2 Agent
  - 目录索引
dependencies:
  - ../architecture.md
scope:
  includes:
    - L2 智能体的职责边界与执行端口契约索引
  excludes:
    - 跨节点任务编排与最终交付责任（属 L3）
    - 执行事件的对外投影（属顶层 runtime-event.md）
gains:
  - 能定位智能体执行、Harness 边界与执行工作态的设计文档
---

# L2 智能体层设计

> L2 接收助理指派的明确任务，将目标拆成可验证步骤，调用获准工具执行并反馈过程。
> **任务级，只保留执行期工作状态，无人格、无长期记忆**；不得扩大 Role、授权或工具边界。

## 文档列表

| 文档 | 内容 | 状态 |
|---|---|---|
| [agent.md](agent.md) | L2 职责、执行身份、`AgentExecutionPort` 契约、Harness ReAct 边界与 AAF 自持子智能体解析 | 草案 · 契约已细化，含目标态 |

## 与其他目录的关系

| 目录 | 关系 |
|---|---|
| [../](../Readme.md) | 双层循环（AAF 外层任务循环与 Agent 内层 ReAct）边界由 `architecture.md` 定义 |
| [../assistant/](../assistant/Readme.md) | L3 是唯一委派方，冻结本层的 Route、模型、工具与预算 |
| [../skill/](../skill/Readme.md) | 激活的 Skill 决定本层可见工具集 |
