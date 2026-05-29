---
level: Practice
layer: Model
purpose: Layer 4 协作层 Team——多 Assistant 协作的功能设计
status: draft
version: 1.1.0
date: 2026-05-28
author: AaronZZH
---

# 协作层 Team 功能设计

> 项目级，由主助理协调多个 Assistant 协作完成复杂目标。

## 定位

Team 是五层智能架构的最高层。Team 本身无执行能力，由一个 **Leader Assistant（主助理）** 承担协调职责，将复杂目标拆解分发给多个 Worker Assistant，各 Worker 独立调度 Agent 执行。

大多数用户场景用单 Assistant 多实例即可。Team 用于需要**多视角协作**的场景（对抗性验证、跨领域协作、跨系统互联）。

## 核心机制

```text
用户 → Leader Assistant（主助理，协调者）
         ├── 目标拆解 → 分发给 Worker Assistant A
         ├── 目标拆解 → 分发给 Worker Assistant B
         ├── 进度同步（各 Worker 汇报进展）
         ├── 冲突仲裁（Worker 间意见分歧时裁决）
         └── 结果聚合 → 返回用户
```

Leader Assistant 本质上也是一个 Assistant，只是承担了协调角色。它可以：
- 拆解目标并分发子任务
- 监控各 Worker 进度
- 在 Worker 结果冲突时仲裁
- 聚合最终结果

## 认知循环

```text
目标对齐 → 任务分发 → 进度同步 → 结果聚合 → 冲突仲裁
```

## 协作模式

| 模式 | 说明 | 适用场景 | AgentScope 对应 |
|------|------|----------|----------------|
| Leader 协调 | 主助理统筹分发，Worker 各自执行 | 明确分工的项目 | Supervisor |
| 流水线 | Assistant 按顺序串行处理 | 有依赖关系的多步骤 | Pipeline |
| 平等协作 | 多个 Assistant 平等讨论决策 | 需要多视角的探索性任务 | MsgHub |

## 适用场景

| 场景 | 用 Team | 不用 Team |
|------|---------|-----------|
| 对抗性验证（写+审查） | ✅ | — |
| 跨领域协作（前端+后端+设计） | ✅ | — |
| 跨系统互联（外部 Agent） | ✅（A2A 协议） | — |
| 同一用户、同一目标的并行加速 | — | 用 Assistant 多实例 |
| 简单对话/单任务 | — | 用单 Assistant |

## 通信方式

- **内部协作**：Leader Assistant 直接调用 Worker Assistant 接口（同进程，无需协议）
- **跨系统协作**：通过 A2A 协议（Task/Artifact/Message）与外部 Agent 系统互联

A2A 不是 Team 内部的默认通信方式，只在跨系统边界时使用。

## 状态管理

- **GoalTracker**：目标级任务管理，持久化到 DB
- **轻量会话级状态**：任务分配表、进度、仲裁结果
- **不持有数据级状态**：数据统一由 Cognition 管理

## 与 Assistant 多实例的区别

| 维度 | Assistant 多实例 | Team |
|------|-----------------|------|
| 本质 | 同一 Assistant fork 多个 Role 并行 | 多个独立 Assistant 协作 |
| 协调者 | 主实例直接裁决 | Leader Assistant 仲裁 |
| 上下文 | 共享同一 Cognition 用户私有区 | 各 Assistant 上下文独立 |
| 用户感知 | 一个助理在高效工作 | 多个助理在协作 |
| 适用 | 同目标并行加速 | 多视角/跨边界协作 |

## 相关文档

- [技术方案 — Team](team-tech.md)
- [五层智能架构总览](../architecture.md)
- [A2A 协议](../../api/a2a.md)
