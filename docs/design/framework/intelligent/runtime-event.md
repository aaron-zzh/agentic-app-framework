---
level: Practice
layer: Model
purpose: 定义执行事实、公共任务事件与 AG-UI 投影的三层事件契约，以及审计节点与重放恢复机制
status: draft
version: 1.1.1
date: 2026-08-25
author: Kiro
tags:
  - 执行事件
  - AG-UI
  - 审计
  - 重放恢复
dependencies:
  - ./architecture.md
  - ./runtime.md
scope:
  includes:
    - 内部执行事实、公共任务事件与 AG-UI 投影的三层模型
    - 事件类型清单与投影映射
    - 任务专属审计节点与决策记录
    - 订阅、cursor、重放与断线恢复
    - 公共视图的安全边界
  excludes:
    - 一次运行的执行契约（见 runtime.md）
    - 事件驱动的持久化与恢复实现（见 task-durability.md）
    - 前端渲染与组件设计
gains:
  - 能判断某个执行事实应产生哪些内部事件与公共事件
  - 能按 cursor 与 eventId 实现幂等的断线恢复
  - 能识别哪些内容禁止进入公共事件与 Snapshot
---

# 执行事件与投影

> 事件是执行事实的唯一时间线。内部 `ExecutionEvent` 是事实源，公共 `AafAiTaskEvent` 是无正文的状态与审计契约，AG-UI 是唯一正文投影。
> **三者不构成三条时间线**，公共事件与 AG-UI 都是同一事实的投影。

## 三层事件模型

```text
内部 ExecutionEvent（ai_task_event）     执行事实源，可含正文与内部 payload
  ├─ 统一 mapper 严格白名单脱敏 → 公共 AafAiTaskEvent（无正文状态、审计与 cursor）
  └─ AgUiProjector → AG-UI SSE（正文直投；其他事件先经公共 mapper 生成 aaf.* CUSTOM）
```

公共事件与 AG-UI 是同一内部事实的两种投影；公共无正文信封不是正文投影的上游。

三条硬约束：

- 领域层**不拼装协议事件**，任何层都不得建立平行事件语义
- 任务式 UI 可隐藏对话输入与消息气泡，但**不能绕过公共事件或直接消费模型私有事件**
- 应用 DEBUG 日志只用于运维诊断，**不能依赖日志重建任务状态**

## 事件类型与投影

| 内部事件族 | 公共事件 | AG-UI 正文投影 | 任务状态与 Snapshot |
|---|---|---|---|
| `EXECUTION_STARTED` | `aaf.execution.started` | `RUN_STARTED` | 完整安全信封 |
| `TASK_STATUS_CHANGED` | `aaf.task.status.changed` | `STATE_DELTA` / STEP | 策略与进度 |
| `MESSAGE_STARTED` / `_DELTA` / `_COMPLETED` | `aaf.output.started` / `.delta` / `.completed` | TEXT MESSAGE START / CONTENT / END | 正文不公开，只保留引用和长度 |
| `TOOL_CALL_*` | `aaf.tool.*` | TOOL CALL | 工具状态与安全摘要 |
| `AUTHORIZATION_*` · `APPROVAL_*` | `aaf.authorization.*` | `INTERRUPT` / `aaf.*` CUSTOM | 待用户处理 |
| `CLARIFICATION_*` | `aaf.clarification.*` | `aaf.*` CUSTOM | 待补输入 |
| `SUBTASK_*` | `aaf.subtask.*` | STEP / `aaf.*` CUSTOM | 子任务 DAG 状态 |
| `EXECUTOR_PLAN_*`（计划级 6 类 + 步骤级 3 类） | `aaf.executor_plan.*` | STEP / `aaf.*` CUSTOM | EXECUTOR 持久计划进度（AAF-107 #10705） |
| `VALIDATION_*` | `aaf.validation.*` | `STATE_DELTA` | 验证结果 |
| `EXECUTION_COMPLETED` / `_FAILED` / `_CANCELED` | `aaf.execution.completed` / `.failed` / `.canceled` | `RUN_FINISHED` / `RUN_ERROR` | 规范终态 |

内部事件枚举实现态：✅ 已实现 · `ExecutionEventType.java:4-69`。公共命名实现态：⚠️ 部分实现 · 当前仍为 `aaf.message.*` 等命名（`AafAiTaskEventRegistry.java:7-69`），尚未全部收敛为上表目标语义。

## 任务专属审计节点

以下控制节点必须形成持久事件，并由规范 mapper 产生可重放公共事实：

| 控制节点 | 记录内容 | 实现态 |
|---|---|---|
| 请求与身份解析 | 入口、主体、租户、TaskProfile、幂等键 | ⚠️ 部分实现 · 入口校验已实现（`AssistantAguiController.java:63-106`），内部信封持有身份与幂等键（`ExecutionEvent.java:21-64`）；尚无含 TaskProfile/解析摘要的专属持久审计事实 |
| 任务分析 | `taskType`、复杂度、执行策略、产物意图、置信度 | 🎯 目标态 · `TASK_ANALYZED` 未定义，当前不得声称已执行 |
| 路由与画像冻结 | Role、SkillVersion、模型、基础工具档案、最终工具摘要 | ⚠️ 部分实现 · 已有 `ROLE_RESOLVED`（`ExecutionEventType.java:4-69`）；`EXECUTION_PROFILE_FROZEN` 未定义 |
| 控制模式与授权 | 策略命中项、任务 grant、拒绝或降级原因 | ⚠️ 部分实现 · 授权事件已存在（`ExecutionEventType.java:4-69`），策略命中摘要未完整记录 |
| 规划与委派 | Agent Loop / Workflow、TaskBoard 版本、子任务与预算 | ⚠️ 部分实现 · 有 `SUBTASK_*`（`ExecutionEventType.java:4-69`）；`PLAN_CREATED/REVISED` 未定义 |
| 工具与产物 | 工具状态、receipt、artifact 状态与版本引用 | ⚠️ 部分实现 · 工具事件已覆盖（`ExecutionEventType.java:4-69`）；`ARTIFACT_*` 四类未定义 |
| 验证与终态 | 完成证据、验证结果、恢复点、最终责任主体 | ⚠️ 部分实现 · 通用 Agent 链会产生验证事件（`AssistantApplicationService.java:740-785`）；父委派任务可直接提交终态而无验证事实（`DelegatedTaskCoordinator.java:554-613`） |
| 决策记录 | 类型、选中项、备选项、理由摘要、置信度、策略版本 | 🎯 目标态 · `DECISION_RECORDED` 未定义，当前不得声称已执行 |

### 产物事件

产物工具的业务参数与时序见 [coordination.md](assistant/coordination.md#产物创建时序)；本节唯一负责事件名称、公共映射与安全字段。

| 工具成功事实 | 内部事件 | 公共事件 | 允许的安全数据 |
|---|---|---|---|
| `artifact.reserve` | `ARTIFACT_RESERVED` | `artifact.reserved` | id、version、kind、state、mediaType |
| `artifact.checkpoint` | `ARTIFACT_CHECKPOINTED` | `artifact.checkpointed` | id、version、checkpointNo、summaryHash |
| `content.draft.upsert` / `artifact.commit` | `ARTIFACT_COMMITTED` | `artifact.committed` | id、version、state、resourceRef、summaryHash |
| `artifact.fail` | `ARTIFACT_FAILED` | `artifact.failed` | id、version、failureCode、retryable |

实现态：🎯 目标态，`ExecutionEventType.java:4-69` 尚无四类 `ARTIFACT_*`，当前不得声称已执行。每次成功状态转换必须恰有一个持久事实；正文、`bufferRef`、原始参数和失败堆栈不得进入公共事件。

`DECISION_RECORDED` **只记录可审计摘要，不保存思维链**：

```json
{"decisionType":"EXECUTION_STRATEGY","selected":{"coordinationMode":"TASKBOARD"},"alternatives":["SINGLE_AGENT"],"reasonSummary":"任务开放且可并行拆分","confidence":0.84,"policyVersion":"task-analysis.v1"}
```

独立 decision audit 表保存结构化详情时，事件只记录“决策已发生”与引用，避免形成第二条执行时间线。

## 订阅、cursor 与重放

| 能力 | 契约 | 实现态 |
|---|---|---|
| 任务级重放 | 按 `eventOffset` 顺序读取 | ✅ 已实现 · `JpaExecutionEventStoreAdapter.java:58-96` |
| 执行级重放 | 按 `(tenantId, executionId, sequence)` 读取 | ✅ 已实现 · `JpaExecutionEventStoreAdapter.java:58-96` |
| 客户端幂等 | 以 `eventId` 去重；同 ID 不同事实拒绝 | ✅ 已实现 · `SynchronousExecutionEventWriter.java:28-78` |
| 断线续传 | `Last-Event-ID` + 不透明 cursor + frame 序号 | 🎯 目标态 · 主入口未读取 `Last-Event-ID`，当前不得声称已执行；SSE id 直接使用 execution sequence（`AssistantAguiController.java:73-78,171-183`） |
| 终态先于流结束 | terminal event 必须在关闭前从同一流送达 | ⚠️ 部分实现 · 错误路径只发无 id 的临时 `RUN_ERROR`（`AssistantAguiController.java:185-197`） |
| 心跳语义 | `[DONE]` 与心跳不承担业务终态语义 | ✅ 已实现 · `DelegatedTaskEventStreamService.java:52-143` |

### `AafAiTaskEvent` 安全信封

下表是公共信封的正式目标契约。字段必须由统一 mapper 生成，客户端不得根据内部 payload 自行补齐。

| 字段 | 类型与约束 |
|---|---|
| `specVersion` | 必填常量 `aaf.ai-task-event/1.0`；仅信封破坏性变更升级 major |
| `eventId` | 必填全局稳定 ID；重放与投影不得改写，同 ID 不同事实拒绝 |
| `eventType` | 必填稳定公共类型，如 `execution.started`；不得直接暴露内部枚举名 |
| `eventVersion` | 必填正整数；只约束该 `eventType` 的 payload 演进 |
| `category` | 必填：`EXECUTION/PROGRESS/OUTPUT/TOOL/ARTIFACT/AUTHORIZATION/DELEGATION/COLLABORATION/VALIDATION/OWNERSHIP` |
| `occurredAt` | 必填 UTC 时间，取内部事实发生时间，不取投影时间 |
| `audience` | 必填；当前外部视图仅 `END_USER`，由服务端生成，不由客户端过滤 |
| `delivery` | 必填：`CURSOR_AT_LEAST_ONCE`（持久可重放）、`LIVE_BEST_EFFORT`（瞬时投影）、`PROJECTION_ONLY`（投影器合成，不是事实） |
| `cursor` | `CURSOR_AT_LEAST_ONCE` 必填不透明游标；其余可空。不得暴露可由客户端拼装的数据库偏移 |
| `task` | 必填任务身份与顺序对象，字段见下表 |
| `state` | 必填事件发生后的安全状态，字段见下表 |
| `actor` | 必填安全主体摘要，字段见下表；不得含模型凭证或 Prompt |
| `trace` | 必填因果摘要，字段见下表 |
| `dataContentType` | 必填 `application/json` |
| `dataSchema` | 必填已注册 Schema URI：`urn:aaf:schema:{event-type}:{eventVersion}` |
| `data` | 必填事件专属白名单对象；只允许稳定引用、枚举码、计数、布尔值和安全摘要，不透传内部 payload |

嵌套对象契约：

| 对象 | 字段 |
|---|---|
| `task` | `taskId`、`executionId`、`runId`、`sessionId`、可选 `parentExecutionId`、可选 `predecessorExecutionId`、正整数 `executionSequence`、持久事实时正整数 `eventOffset` |
| `state` | `status`、可选 `controlMode`、`terminal`；状态驱动通用状态机，事件类型驱动专属 UI |
| `actor` | `type=SYSTEM/HUMAN/ASSISTANT/AGENT`，及按类型出现的 `userId/assistantId/agentId` 安全标识 |
| `trace` | `correlationId`、可选 `causationId`；只表达关联与因果，不携带调用正文 |

整体实现态：⚠️ 部分实现 · 当前 `AafAiTaskEvent` 已实现 `eventId`、扁平任务身份、`sequence/eventOffset`、状态、类型版本、受众、投递等级、安全 `data` 与时间（`AafAiTaskEvent.java:12-158`），并由白名单 mapper 生成（`ExecutionEventPublicMapper.java:20-163`）。`specVersion/category/cursor`、嵌套 `task/state/actor/trace`、`dataContentType/dataSchema` 尚未落地，均为 🎯 目标态，当前不得声称已执行。

`data` 的硬约束：正文、凭证、连接串、Cookie/API Key、系统提示、Prompt 片段、模型思维链、工具原始参数与外部不可信原文**一律禁止**。当前安全标量白名单和 256 字符引用限制已实现（`AafAiTaskEvent.java:73-157`）；内容级敏感信息检测仍是缺口。

### Snapshot 契约

Snapshot 是持久事件折叠后的安全读模型，不产生新事实、不分配新 `eventOffset`，也不返回正文。

| 字段 | 契约 |
|---|---|
| `requestedAfterEventOffset` | 当前请求的非负数值偏移 |
| `nextEventOffset` | Snapshot 已覆盖的最大偏移，≥请求偏移；客户端下一次从其后续读 |
| `deliveryGuarantee` | 固定 `AT_LEAST_ONCE` |
| `state.taskId/executionId/runId/sessionId` | 当前折叠身份 |
| `state.status/sequence/eventOffset` | 当前折叠状态与顺序 |
| `state.terminal/eventCount` | 是否终态及已折叠事件数 |
| `events` | `eventOffset > requestedAfterEventOffset` 的公共安全事件；允许重复，客户端按 `eventId` 幂等 |

实现态：✅ 已实现 · `AafAiTaskSnapshot.java:9-25`、`ExecutionEventReducer.java:71-104`、`DelegatedTaskEventService.java:47-69`。⚠️ 当前 Snapshot 仍暴露数值 offset；对外不透明 `cursor` 为目标态，当前不得声称已执行。

## 安全视图

公共事件与 Snapshot 使用**严格字段白名单**，不透传内部 payload。以下内容禁止进入任何对外视图：

- 正文以外通道的模型输出（正文只经 AG-UI）
- 凭证、密钥、连接串
- 系统提示与 Prompt 片段
- 模型思维链
- 工具原始参数（只公开安全摘要与引用）

实现态：⚠️ 部分实现 · 公共 mapper 已按字段白名单输出（`ExecutionEventPublicMapper.java:97-163`）；内部 payload 的防敏只检查字段名与安全标量格式，未做内容级检测。**模型思维链已在更早的拦截点封堵**：`AgentScopeEventMapper` 将 AgentScope core 的 `THINKING_BLOCK_START/DELTA/END` 三类源事件直接映射为空（不产生 `ExecutionEvent`），思考内容从源头就不进入内部事件流，不依赖公共 mapper 或字段白名单二次过滤（`AgentScopeEventMapper.java:188`，AAF-106 #10603 落地）。

## 实现态

| 契约 | 实现态 |
|---|---|
| 三层投影由统一 mapper 与单一 projector 完成 | ⚠️ 部分实现 · 主链已统一（`ExecutionEventPublicMapper.java:20-95`）；工作流仍自建投影（`WorkflowAgUiService.java:57-68`） |
| 内部事件持久化与双维度重放 | ✅ 已实现 · `JpaExecutionEventStoreAdapter.java:58-96` |
| 完整结构化安全信封 | 🎯 目标态 · 当前不得声称已执行；现有扁平信封见 `AafAiTaskEvent.java:12-158` |
| 九类审计节点齐备 | 🎯 目标态 · 当前不得声称已执行；分析、画像、计划、产物与决策事件未定义 |
| 公共事件命名收敛为 `output.*` / `artifact.*` / `plan.*` | 🎯 目标态 · 当前不得声称已执行 |
| `Last-Event-ID` 与不透明 cursor | 🎯 目标态 · 当前不得声称已执行 |
| 所有路径终态先于流结束 | ⚠️ 部分实现 · `AssistantAguiController.java:185-197` 错误路径未持久化终态 |
| `eventId` 跨重试稳定 | ⚠️ 部分实现 · 持久源事件稳定；投影合成事件使用随机值（`ExecutionEventPublicMapper.java:31-75`） |

## 验收基线

- 已持久化事件可按 `eventOffset` 做任务重放、按 `sequence` 做执行重放，客户端以 `eventId` 幂等
- AG-UI 只使用标准事件或 `aaf.*` CUSTOM，不出现内部枚举名作为 SSE event name
- terminal event 先于流结束送达，且该事件已持久化
- 任务事件、Snapshot 与查询接口不返回正文
- 审计方能还原画像、工具、授权、委派、产物与终态，且任何视图都不泄露凭证、系统提示或思维链
- 失败、重试、恢复与多副本切换不产生重复事件事实
