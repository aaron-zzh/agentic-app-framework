---
level: Practice
layer: Model
purpose: 说明最小 L4 Team 的统一生命周期存储、冻结校验、TaskBoard 调度与 AG-UI 接口
status: draft
version: 1.2.0
date: 2026-08-20
author: AaronZZH
---

# Layer 4 协作层 Team 技术方案

> Team 复用 Assistant 运行时、`DelegatedTask`、`TaskBoard`、`TaskTransition` 和 outbox；没有 `TeamOrchestrator`、AgentScope Pipeline/MsgHub 或独立 `ai_team*` 持久化模型。

## 架构边界

```text
POST /api/agui/run (state.mode = TEAM)
  → AssistantAguiController：认证 AI thread 所有权
  → AssistantExecutionService.startTeam
  → DefinitionLifecycleService.requirePublishedTeam
  → TaskBoard.teamCoordinated
  → DelegatedTaskCoordinator.submitAndDispatch
  → Leader CoordinationPlan 校验与 Worker 执行
  → AgUiProjector：唯一正文 SSE
```

`AssistantAguiController` 是 Assistant 唯一启动入口。所有模式均先验证当前用户拥有 `ConversationTypeEnum.AI` 的既有 `threadId`；Team 不可借用其他用户的会话取得 lease 或创建任务。

## 持久化模型

Team 存入统一的 `ai_definition_lifecycle`：

```text
DefinitionKind.TEAM
lifecycle_payload.teamDefinition
  ├─ teamId = definitionId
  ├─ version = definitionVersion
  ├─ strategy = LEADER_COORDINATED
  ├─ leader = Member(LEADER, Assistant target)
  └─ workers = 1..8 × Member(WORKER, Assistant target)
```

`v16__intelligent_runtime_schema.sql` 在创建 `ai_definition_lifecycle` 时原生包含 `TEAM`，并通过闭世界、fail-closed 的 JSON 校验函数约束 Team 载荷，同时建立 Team 生命周期状态和 JSON 查询索引。缺失字段、未知字段、非静态 Worker、重复成员 key 或越界成员数量均不能入库。

Team 与其他定义共用管理 API 和 `ai:definition:manage` 权限。`v12__init_seed_data.sql` 直接初始化该权限、管理角色授权，以及文案 Assistant 的最终执行画像；基线不再先创建旧状态后用高版本 seed 修补。

## TeamDefinition 与发布校验

`TeamDefinition` 只接受：

- `LEADER_COORDINATED`；
- 一个 `LEADER`；
- 一至八个 `WORKER`；
- 不重复且不与 Leader 冲突的 `memberKey`；
- 非空 `assistantId`、`roleKey`、`skillKey` 与工具 allowlist。

`DefinitionLifecycleService` 在 draft、publish 和 rollback 时验证每名成员：

- Assistant 存在且已发布；
- revision 精确匹配；
- 固定 Role 包含固定 Skill；
- 成员工具 allowlist 不超出 Assistant 和 Role 白名单。

运行时再次解析与验证成员，并要求成员 Assistant 是系统管理 Assistant 或当前用户维护的 Assistant。发布后的 Team 版本不可覆盖；运行只接受 `PUBLISHED` Team。

## TaskBoard 冻结与调度

`AssistantExecutionService.startTeam` 拒绝调用方提供 Assistant、Role 或 Skill 覆盖，并只接受 `CONVERSATIONAL`、`AUTO` 与 `RETURN_ONLY`。它基于发布 Team 的 Leader 与 Worker target 生成 `TaskBoard.teamCoordinated`：

- Leader 是唯一初始 `COORDINATOR`；
- Worker 预先成为依赖 Leader 的 `EXECUTOR` 子任务；
- 每个子任务持有自己的冻结 `AssistantTarget`，不继承 Leader；
- 最大并行度不超过 Worker 数且不超过八；
- 默认聚合契约使用确定的 Worker 顺序。

Leader 返回严格 JSON `CoordinationPlan` 后，`DelegatedTaskCoordinator.decodeAndValidatePlan` 与 `TaskBoard.applyCoordinationPlan` 双重校验：计划必须且只能覆盖所有冻结 Worker，且 `subTaskId`、Role、Skill 和模型策略都不能越出冻结边界。随后普通 `DelegatedTask` 状态机、租约 fencing、TaskTransition/outbox、停止、接管、归还和恢复逻辑统一调度 TaskBoard。

## 身份、事件与恢复

```text
ConversationId = SessionId = threadId
TaskId = ExecutionId = RunId = runId
```

会话跨轮稳定；每轮 Team 请求创建独立 `runId` 对应独立任务和执行。正文仅由内部 `MESSAGE_DELTA` 经 `AgUiProjector` 映射为 AG-UI `TEXT_MESSAGE_CONTENT.delta`。其他事件经 `ExecutionEventPublicMapper` 输出无正文 `aaf.*` CUSTOM 状态、审计或 cursor 数据。任务查询、Snapshot 与任务 SSE 不复制模型正文。

## 非目标

当前未实现 Pipeline、Fanout、MsgHub、Peer collaboration、动态 Worker、Team 专属 UI、外部 A2A Worker、独立 `ai_team*` 表、Team 专属事件表、自动冲突仲裁或兼容旧 `/agui/runs/**` 路径。新能力必须扩展这一版本化模型和 TaskBoard 契约，不能另建旁路运行时。

## 相关文档

- [功能设计 — Team](team.md)
- [任务式 Assistant 统一执行路径设计](../assistant/task-oriented-assistant-execution-design.md)
- [五层智能架构总览](../../architecture.md)
