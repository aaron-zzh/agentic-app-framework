---
level: Practice
layer: Model
purpose: 定义当前最小 L4 Team 的 Leader 协调、静态成员、冻结边界与运行限制
status: draft
version: 1.2.0
date: 2026-08-20
author: AaronZZH
---

# 协作层 Team 功能设计

> 当前 Team 是受版本治理的最小 L4 协作能力：一个 Leader 协调 1..8 个静态 Worker；它不提供 Pipeline、平等讨论、动态成员或跨系统 A2A 协作。

## 定位

Team 用于多个独立 Assistant 必须保持各自角色、技能、工具上限和责任边界的复杂目标。它不同于单 Assistant 内部对 L2 Agent 的委派：Team 的 Leader 与每个 Worker 都是已发布 Assistant 的冻结版本，Leader 只能给已冻结 Worker 分配工作，不能临时加入成员、替换目标或扩大权限。

简单对话、单一任务和同一 Assistant 内部的任务拆分不创建 Team；它们使用普通 `TaskBoard`。Team 是 L4 的受控升级路径，不是另一套运行时或独立聊天入口。

## 当前协作模型

```text
已发布 TeamDefinition
  → Leader Assistant 生成严格 CoordinationPlan
  → TaskBoard 校验计划必须覆盖全部静态 Worker
  → Worker 按各自冻结的 Role / Skill / 工具 allowlist 执行
  → TaskBoard 以确定性聚合契约合并 Worker 结果
  → AG-UI 返回唯一正文流
```

当前只支持 `LEADER_COORDINATED`：

- 一个 `LEADER`，作为协调者并生成计划；
- 一至八个 `WORKER`，以唯一 `memberKey` 标识；
- 每名成员固定 `assistantId`、`assistantRevision`、`roleKey`、`skillKey` 和 `allowedToolKeys`；
- Leader 计划必须逐项覆盖全部 Worker，且每项的 `subTaskId`、Role 和 Skill 必须等于冻结目标；
- 聚合使用 `TaskBoard` 的确定性合约，当前默认按 Worker 固定顺序拼接结果。

计划非法、成员版本失效、成员 Assistant 未发布、Role/Skill 不匹配或工具白名单越界时，运行失败关闭，不降级到其他 Assistant 或动态路由。

## 定义与治理

Team 不是 `ai_team*` 独立表模型。它作为 `DefinitionKind.TEAM` 保存在统一 `ai_definition_lifecycle` 中，生命周期为 `DRAFT`、审核、`PUBLISHED`、停用、回滚。管理入口复用：

```text
POST /api/ai/definitions/TEAM/{teamId}/versions/{version}/draft
POST /api/ai/definitions/TEAM/{teamId}/versions/{version}/review
POST /api/ai/definitions/TEAM/{teamId}/versions/{version}/publish
POST /api/ai/definitions/TEAM/{teamId}/versions/{version}/deprecate
POST /api/ai/definitions/TEAM/{teamId}/versions/{version}/disable
POST /api/ai/definitions/TEAM/{teamId}/versions/{version}/rollback/{targetVersion}
```

上述入口要求 `ai:definition:manage`。草稿、发布和回滚都会校验成员 Assistant 已发布、revision 精确匹配、Role 包含指定 Skill，且成员工具 allowlist 只是 Assistant 与 Role 白名单的收窄。

## 运行入口与可见性

Assistant 只有一个正文启动入口：

```text
POST /api/agui/run
state.mode = TEAM
state.teamId + state.teamVersion
```

请求必须携带当前用户拥有的既有 AI `Conversation.threadId`；`threadId` 同时是 `ConversationId` 与 `SessionId`。每一轮使用独立 `runId`，且 `TaskId = ExecutionId = RunId = runId`。`TEAM` 请求不得自行覆盖 Leader 或 Worker 的 Assistant、Role、Skill、工具、交互模式、Route 或产物策略。

`MESSAGE_DELTA` 只经 `AgUiProjector` 投影为 AG-UI 正文。`AafAiTaskEvent`、任务查询与 Snapshot 只提供状态、审计摘要和 cursor，不提供正文；没有第二个 Headless 正文接口。

## 与单 Assistant 的边界

| 场景 | 使用方式 |
|---|---|
| 普通问答或单一执行任务 | `CHAT` 或 `EXECUTION` 模式的单 Assistant |
| 一个 Assistant 的受限 Agent 委派 | 普通 `TaskBoard` |
| 固定多个 Assistant 的分工、聚合与责任隔离 | `TEAM` 模式 |
| 流水线、平等讨论、动态成员、跨系统协作 | 当前不支持，须另行设计 |

## 当前范围

当前 MVP 不提供专用 Team 管理 UI、预置 Team seed、Pipeline/MsgHub、动态成员、A2A 远程 Worker、独立 Team 事件表或冲突仲裁协议。这些能力不得以兼容路径、运行时 fallback 或新增 `ai_team*` 表补入当前合同。

## 相关文档

- [技术方案 — Team](team-tech.md)
- [任务式 Assistant 统一执行路径设计](../assistant/task-oriented-assistant-execution-design.md)
- [五层智能架构总览](../../architecture.md)
