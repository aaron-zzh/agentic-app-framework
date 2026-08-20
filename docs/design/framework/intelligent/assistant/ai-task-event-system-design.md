---
level: Practice
layer: Model
purpose: 已被唯一 AG-UI 正文流边界替代的历史多协议事件设计草案
status: deprecated
version: 1.1.0
date: 2026-08-20
author: Kiro
tags:
  - AI 任务
  - 事件系统
  - AG-UI
  - SSE
  - 可观测性
dependencies:
  - ../architecture.md
  - ./task-oriented-assistant-execution-design.md
related:
  - ../assistant-agent-runtime-refactor.md
  - ./assistant-tech.md
scope:
  includes:
    - AAF AI 任务规范事件信封与事件分类
    - 对话、任务、工作流和多智能体事件语义
    - AG-UI、Headless SSE、同步结果与消息通道投影
    - 顺序、游标、重放、安全和 Schema 演进
  excludes:
    - 前端组件视觉设计
    - 具体消息中间件选型
    - 业务工具参数 Schema
    - 模型供应商原始事件协议
gains:
  - 能使用同一事件语义支持对话式和非对话式 AI 任务
  - 能把 AAF 事件无损投影到 AG-UI 与 Headless SSE
  - 能实现断线续读、幂等消费和多执行聚合
  - 能安全演进事件类型和 payload Schema
---

# 统一 AI 任务事件消息系统设计

> **已废弃的历史多协议草案。** 本文保留事件分类、游标和安全讨论，但不再定义可实现的对外协议；其中 Headless SSE、同步正文结果、`/assistant-runs` 与多正文投影均不可实现。当前合同以[任务式 Assistant 统一执行路径设计](task-oriented-assistant-execution-design.md)和[Team 技术方案](../team/team-tech.md)为准：`POST /api/agui/run` 是唯一 Assistant 启动入口，AG-UI 是唯一正文 SSE，公共任务事件只提供状态、审计与 cursor。

## 设计定位

### 为什么不能直接以 AG-UI 为领域协议

AG-UI 擅长描述人与 Agent 的实时交互，包括 run、文本消息、工具调用、状态同步和中断。AAF 还需要表达非对话任务、TaskBoard、多执行树、产物、授权、验证、预算、接管和后台恢复。若直接把 AG-UI 当领域模型，会出现两个问题：

- 非 UI 语义被迫塞进 `CUSTOM`，领域事实失去稳定类型；
- 领域层依赖某个前端协议，未来接入 CLI、Webhook、A2A 或批处理时再次转换。

因此采用三层结构：

```text
内部执行事实源      ExecutionEvent / ai_task_event
        ↓ 安全规范化
唯一公共事件契约    AafAiTaskEvent
        ↓ 协议投影
AG-UI | Headless SSE | Sync Result | Webhook/MQ | Operator Trace
```

`ExecutionEvent` 保留完整租户、责任主体、因果和幂等信息并形成内部事实时间线；`AafAiTaskEvent` 是从该时间线派生的可公开、可重放、协议无关安全信封，不单独持久化为第二事实源；各适配器只改变表示，不改变任务状态和事件含义。

### 当前实现缺口

现有 `ExecutionEvent` 已包含 `eventId/taskId/executionId/runId/parentExecutionId/sequence/status/controlMode/owner/correlation/causation/payload/time`，且 `ai_task_event` 支持 execution sequence 和 task eventOffset 续读，具备规范事件的基础。

当前出口尚未统一：

- `AssistantExecutionEventVO` 只保留 sequence、type、status、time 和安全 payload，丢失任务树标识、事件 ID、因果关系与重放游标；
- `AssistantAguiController` 直接使用内部 `ExecutionEventType` 作为 SSE event name，不是完整 AG-UI 标准投影；
- `WorkflowAgUiService` 通过另一套 `AgUiEvent` 工厂生成事件，形成平行映射；
- 对话文本使用 `MESSAGE_*`，而结构化结果、文档、媒体和指标缺少统一的 `OUTPUT_*` 语义。

目标实现应由一个 AAF mapper 先产生规范事件，再由多个 protocol projector 输出，禁止 Controller 和业务 Service 各自拼事件。

### 设计原则

- **轨迹唯一**：每个执行事实只追加一次 `ai_task_event`，协议事件不是第二事实源；TaskBoard、HITL、消息、产物和账本仍由各自业务表保存权威当前状态。
- **语义优先**：先定义 execution、output、artifact、tool 等领域语义，再映射协议名称。
- **内容与控制分离**：Markdown、JSON、媒体引用是输出内容；状态、授权、游标和 Schema 是控制面。
- **持久事实与流信号分离**：任务事实必须可重放；token delta、心跳等高频信号可以瞬时传输。
- **开放扩展、封闭解释**：允许新增可选事件和字段，不允许改变既有类型的含义。

### 五层与适配层职责

| 层级 | 事件职责 | 不承担的职责 |
|---|---|---|
| L4 Team | 产生多 Assistant 目标、分工、冲突和仲裁事实 | 不把 Team 协作降级为普通子 Agent |
| L3 Assistant | 产生任务分析、路由、控制模式、委派、聚合和终态事实 | 不直接实现协议序列化 |
| L2 Agent | 产生步骤、工具、输出、验证和恢复事实 | 不拥有长期记忆或最终用户责任 |
| L1 Cognition | 被动消费经治理的学习候选并保存长期认知 | 不主动推进任务事件循环 |
| L0 Core | 由调用适配器记录模型调用摘要和用量 | 不产生任务状态或承担业务责任 |
| 交互/API 适配层 | 投影 AG-UI、Headless SSE、同步响应和 Webhook/MQ | 不改变领域状态或创造执行事实 |

共享事件契约可位于 `intelligent.shared.event`，但其中不放 Assistant、Agent 或 Team 的业务决策；AG-UI 类型、SSE heartbeat、`Last-Event-ID` 和 Webhook 签名只存在于交互/API 或基础设施适配层。

### 适用场景

同一规范覆盖：

| 场景 | 主要输出 | 交互特征 |
|---|---|---|
| 对话式 Assistant | 消息、工具卡片、状态、HITL | 多轮、可中断 |
| 单次任务/CLI | 进度、日志摘要、结果、产物 | 少交互或无交互 |
| 开放复杂任务 | TaskBoard、子执行、聚合结果 | 并行、可恢复 |
| 预定义工作流 | 节点状态、等待输入、结果 | 确定性骨架 |
| 后台自动化 | 状态、预算、通知、产物 | 异步、长运行 |

## 规范事件模型

### 安全事件信封

对外规范事件采用 CloudEvents 风格但保持 AAF 领域字段清晰，不直接暴露内部对象：

```json
{
  "specVersion": "aaf.ai-task-event/1.0",
  "eventId": "evt-01J...",
  "eventType": "output.delta",
  "eventVersion": 1,
  "category": "OUTPUT",
  "occurredAt": "2026-08-18T09:00:00Z",
  "delivery": "REPLAYABLE_SIGNAL",
  "cursor": "opaque-task-cursor",
  "task": {
    "taskId": "task-001",
    "executionId": "exec-002",
    "runId": "run-002",
    "sessionId": "session-exec-002",
    "parentExecutionId": "exec-001",
    "predecessorExecutionId": null,
    "executionSequence": 18,
    "eventOffset": 41
  },
  "state": {
    "status": "RUNNING",
    "controlMode": "COLLABORATIVE"
  },
  "actor": {
    "type": "AGENT",
    "assistantId": "system.assistant.default-user",
    "agentId": "content-writer"
  },
  "trace": {
    "correlationId": "corr-001",
    "causationId": "evt-previous"
  },
  "dataContentType": "application/json",
  "dataSchema": "urn:aaf:schema:output-delta:1",
  "data": {
    "outputId": "out-primary",
    "kind": "TEXT",
    "mediaType": "text/markdown",
    "delta": "第一段内容"
  }
}
```

字段约束：

- `eventId` 全局唯一，用于幂等消费；
- durable fact 和 replayable signal 才同时分配两个持久顺序字段：`executionSequence` 在单 execution 内连续，`eventOffset` 在整个 task 执行树内单调递增；
- 公共信封中的 `cursor` 是由 eventOffset 生成的不透明事件游标；协议的一对多投影可以使用更细的 transport resume token 作为 SSE `id`，两者都不得由客户端解析或自行计算；snapshot cursor 表示“已覆盖到该位置”，不是新的 eventOffset；
- `eventType + eventVersion + dataSchema` 共同确定 data 契约；
- `actor` 只暴露安全主体标识，不暴露凭证和内部模型提示；
- `data` 必须通过事件专属安全投影，禁止直接复制内部 payload。

### 事件等级

`delivery` 区分事实与实时信号：

| 等级 | 语义 | 存储与重放 |
|---|---|---|
| `DURABLE_FACT` | 状态、工具结果、产物、授权、完成等事实 | 必须进入 `ai_task_event` 并可重放 |
| `REPLAYABLE_SIGNAL` | 合并后的文本 delta、可重放进度 | 必须批量写入 `ai_task_event` 后才进入公共事件流 |
| `SNAPSHOT` | 从持久事实或读模型生成的任务、输出和产物快照 | 不分配新 eventOffset；cursor 指向已覆盖的持久位置 |

原始 token、SSE heartbeat 和尚未批量持久化的临时进度是 transport signal，不属于 `AafAiTaskEvent`，不携带 SSE `id`，也不推进 durable cursor。不能只依赖 replayable signal 重建最终业务状态；`output.completed` 或 snapshot 必须提供权威结果引用或完整安全值。

### 输出模型

对话和非对话模式统一使用 `OUTPUT_*`，消息是输出的一种呈现方式：

```text
OutputDescriptor
├─ outputId
├─ kind                 TEXT | JSON | ARTIFACT_REF | MEDIA_REF | TABLE | METRICS
├─ role                 PRIMARY | SUPPLEMENTARY | DIAGNOSTIC
├─ presentation         MESSAGE | DOCUMENT | CARD | HIDDEN
├─ mediaType
├─ schema               可选业务输出 Schema
├─ value                小型安全值
└─ ref                  大型内容或持久产物引用
```

事件顺序通常为：

```text
output.started
→ output.delta × N
→ output.completed
```

AG-UI 对话文本把 `presentation=MESSAGE` 投影为 text message；CLI 可直接把 primary text 写到 stdout；JSON 任务按 schema 返回结构化值；文档和媒体通过 ref 返回，不复制大对象。

### 任务与执行树

一个 task 可以包含多个 execution：主协调者、子 Agent、恢复执行和补偿执行分别形成不可变执行树节点。对于 `TASK + FIXED + copywriting`，树至少包含一个 `COORDINATOR` execution 与一个 `EXECUTOR` execution；`plan.created` 记录 Assistant 已校验并冻结的 `CoordinationPlan` 摘要，随后才允许 `subtask.created`。每个委派事件的安全 data 必须包含 `agentKind`、稳定 `agentKey`、attempt 与 `modelSelectionMode`，但不得包含提示词、模型供应商凭证、用户正文或工具参数。身份与生命周期统一如下：

| 标识 | 稳定范围与语义 | 恢复规则 |
|---|---|---|
| `conversationId` | 用户交互连续性和 Assistant 会话状态 | 新 task 可复用，不用于执行幂等 |
| `taskId` | 一个稳定业务目标及其完整生命周期 | 恢复、委派和补偿均保持不变 |
| `executionId` | 主执行、子任务或恢复尝试的不可变节点 | 暂停后恢复、责任转移或失败恢复时创建新值 |
| `runId` | 某 execution 的一次具体运行尝试 | 同责任和同状态谱系内的瞬时重试可创建新值 |
| `sessionId` | AgentStateStore 槽位，不是业务身份；主 Assistant 使用 conversation 稳定槽位，委派 Agent 使用任务/子任务隔离槽位 | DIRECT 多轮与恢复复用 conversation session；委派执行恢复可在同谱系复用，需隔离或转移所有权时从 checkpoint fork 新槽位 |

`parentExecutionId` 只表达任务分解树；恢复关系使用 `predecessorExecutionId + checkpointRef`，不能滥用 parent。恢复总是创建新 execution：复用 sessionId 时由新 fencing token 接管原槽位，fork sessionId 时复制受控 checkpoint；两种方式都使旧 execution 失去写权，迟到执行者不得写事件、业务状态或 AgentState。规范顺序是：

- 单 execution：按 `executionSequence` 重放已持久化事件；
- 整个 task：按 `eventOffset` 重放已持久化事件；
- 不同 task：不承诺全局顺序；
- 同一 eventId：消费者必须幂等；
- 旧 execution 历史只读；被接管的 session 槽位只允许最新 fencing token 写入，恢复不能回写旧事件时间线。

### 执行轨迹与当前状态

`ai_task_event` 是 append-only 执行轨迹，而不是所有业务当前状态的唯一表：

| 数据 | 权威当前状态 | 事件职责 |
|---|---|---|
| TaskBoard、Goal、依赖 | PostgreSQL 编排表 | 记录创建、修订和状态变化 |
| HITL、授权与审批 | approval/grant 业务表 | 记录请求、批准、拒绝和撤销 |
| 用户可见消息 | `conversation_message` | 记录输出产生和完成，正文可使用引用 |
| artifact/document/media | 各自产物存储 | 记录 reserve、checkpoint、commit 和 fail |
| token、额度与结算 | 计量和账本表 | 记录安全摘要与关联标识 |
| Agent 恢复工作态 | AgentStateStore | 记录 checkpoint 或恢复事实，不复制快照正文 |

业务表与事件追加应通过同一应用事务、outbox 或可证明一致的机制完成，但禁止把同一当前状态同步双写到两个可独立修改的状态表。

### 安全视图

规范事件按 audience 生成视图，不让客户端自行过滤：

| 视图 | 使用方 | 内容范围 |
|---|---|---|
| `USER` | AG-UI、任务页、CLI | 安全状态、结果、工具摘要、授权、产物 |
| `OPERATOR` | 管理台、排障 | USER + 模型调用摘要、用量、策略版本 |
| `INTERNAL` | 应用服务、投影器 | 完整领域引用，仍不含凭证明文和思维链 |

任何视图都禁止系统提示词、凭证、Cookie/API Key、完整思维链、未脱敏工具参数和外部不可信原文进入事件。

## 事件分类与语义

### 核心事件族

| 类别 | 规范事件 | 说明 |
|---|---|---|
| EXECUTION | `execution.started/completed/failed/canceled/paused/resumed` | 顶层执行生命周期 |
| PROGRESS | `task.analyzed/status.changed/plan.created/plan.revised` | 任务分析、状态和计划 |
| OUTPUT | `output.started/delta/completed/failed` | 文本、JSON、表格和结果引用 |
| TOOL | `tool.started/arguments.delta/completed/failed` | 工具调用及安全结果 |
| ARTIFACT | `artifact.reserved/checkpointed/committed/failed` | 文档和 AIGC 素材生命周期 |
| AUTHORIZATION | `authorization.requested/granted/denied/revoked` | HITL 与任务 grant |
| DELEGATION | `subtask.created/started/completed/failed/canceled` | L3 Assistant 对 L2 Agent 的 TaskBoard 委派 |
| COLLABORATION | `team.goal.created/assignment.created/progress.updated/conflict.detected/arbitration.completed` | L4 Team 中多个 Assistant 的协作与仲裁 |
| VALIDATION | `validation.started/completed/failed` | 输出与完成门禁 |
| OWNERSHIP | `ownership.transferred/control-mode.changed` | 接管、交回和自主权 |

`DELEGATION` 只描述一个 Assistant 向任务级 Agent 的有界委派；具有独立 Persona、长期责任或冲突仲裁的多个 Assistant 必须使用 `COLLABORATION`，不能复用 `subtask.*` 混淆 L3 与 L4。模型 token、供应商请求 ID、重试细节和堆栈属于 operator trace，不应成为所有客户端必须处理的核心事件。

### 状态与事件的关系

事件描述发生了什么，`state.status` 描述事件发生后的任务状态。二者不能混用：

```text
authorization.requested + AWAITING_AUTHORIZATION
output.delta            + RUNNING
validation.started      + VERIFYING
execution.completed     + COMPLETED
```

客户端应以 status 驱动通用状态机，以 eventType 驱动专属 UI。未知可选事件可以忽略，但不能忽略未知终态或标记为 required 的控制事件。

### Progress 与日志

面向用户的进度不是服务器日志。`progress` data 只包含阶段、当前/总量、摘要和可选 ETA：

```json
{
  "stage": "research",
  "current": 2,
  "total": 4,
  "unit": "subtask",
  "summary": "正在分析竞品内容"
}
```

DEBUG/INFO 日志保留在日志系统；关键控制节点同时产生 durable fact。客户端不得通过解析日志文本判断任务状态。

### 产物与输出边界

`output` 表示本次执行产生的可消费结果，`artifact` 表示具有独立身份和生命周期的持久对象：

- 流式 Markdown 是 output；保存后的在线文档是 artifact；
- 图片生成进度是 progress；登记后的 mediaVersion 是 artifact；
- 结构化分析 JSON 可以只是 output，也可以按业务需要物化为 artifact；
- `execution.completed` 只列 primary outputId 和 artifactId，不重复正文或二进制。

### 错误契约

失败事件使用统一安全错误结构：

```json
{
  "code": "ASSISTANT_TOOL_AUTHORIZATION_REQUIRED",
  "category": "AUTHORIZATION",
  "message": "需要授权后继续执行",
  "retryable": true,
  "recoveryAction": "APPROVE_OR_CANCEL",
  "details": {}
}
```

错误码稳定，message 可本地化，details 只含安全结构化信息。HTTP 错误只表示请求未进入任务；任务开始后的失败必须通过事件和最终结果表达。

## 协议投影

### AG-UI 投影

AG-UI projector 属于交互/API 适配层，只订阅公共事件并序列化，不反向修改 Assistant、Agent 或 Team 状态。优先使用 AG-UI 标准事件，AAF 扩展通过 namespaced CUSTOM 表达：

| AAF 规范事件 | AG-UI |
|---|---|
| `execution.started` | `RUN_STARTED` |
| `execution.completed` | `RUN_FINISHED` |
| `execution.failed` | `RUN_ERROR` |
| message output start/delta/end | `TEXT_MESSAGE_START/CONTENT/END` |
| `tool.started/arguments.delta/completed` | `TOOL_CALL_START/ARGS/RESULT` |
| task/progress/artifact/subtask 快照 | `STATE_SNAPSHOT` / `STATE_DELTA` |
| `authorization.requested` | `INTERRUPT` + `CUSTOM(name=aaf.authorization.requested)` |
| 无标准等价的 durable fact | `CUSTOM(name=aaf.<eventType>)` |

AG-UI projector 负责 messageId/outputId、runId 和 toolCallId 的稳定映射。公共信封 cursor 只定位 AAF 事件；SSE `id` 使用协议专属的不透明 transport resume token，至少编码公共 cursor 与 `projectionIndex`。同一公共事件投影为多个 AG-UI frame 时，每帧获得不同 resume token，`Last-Event-ID` 从下一未发送 frame 继续，既不跳过后续 frame，也不要求客户端猜测包含式重放。transport signal 不设置 id；投影必须确定且 frame 级幂等。标准事件可以附加非冲突的 `aaf` metadata，但关键扩展必须发送 CUSTOM。

### Headless SSE 投影

Headless、CLI 和任务页直接消费完整 `AafAiTaskEvent` 安全信封，不再使用只含少数字段的专用 VO。Headless 是一公共事件对一 SSE frame，因此 transport resume token 可以等同公共 cursor；以下格式只用于 durable fact 或 replayable signal，snapshot 使用其 covered-through cursor，transport signal 不设置 `id`：

```text
id: opaque-task-cursor
event: output.delta
data: {完整 AafAiTaskEvent JSON}
```

通用消费者只需要处理 execution/status/output/error；高级消费者再处理 tool、artifact、subtask 和 authorization。接口可通过查询条件选择 audience、event category 和是否包含 stream signal，但不能改变事件语义。

### 同步结果投影

不需要流式的调用方仍执行同一任务并聚合规范事件，返回 `AiTaskResult`：

```json
{
  "taskId": "task-001",
  "executionId": "exec-001",
  "status": "COMPLETED",
  "primaryOutputs": [
    {
      "outputId": "out-primary",
      "kind": "TEXT",
      "mediaType": "text/markdown",
      "value": "最终内容"
    }
  ],
  "artifacts": [
    {
      "artifactId": "artifact-001",
      "kind": "DOCUMENT",
      "ref": "document:123:version:4"
    }
  ],
  "error": null,
  "lastCursor": "opaque-task-cursor"
}
```

同步接口是事件折叠结果，不建立第二套执行和错误模型。超时可返回 `202 + taskId + lastCursor`，调用方转为轮询或 SSE 续读。

### Webhook 与消息队列

Webhook/MQ 默认只发布 `DURABLE_FACT`，使用同一信封并保证至少一次投递。消费者按 eventId 幂等，按 eventOffset 处理单 task 顺序。订阅配置按 category、eventType、assistantId 或 taskType 过滤，不允许订阅者访问超出 audience 的 data。

### Operator Trace

模型调用、token、成本、缓存、重试和供应商 requestId 投影到 OPERATOR 视图。它们可以关联 eventId/executionId，但不阻塞用户事件流，也不要求普通客户端认识诊断事件。

## 投递、演进与治理

### 订阅与恢复

推荐统一资源：

```text
POST /assistant-runs                  创建任务，返回 taskId/executionId
GET  /ai-tasks/{taskId}/events       按 cursor 订阅/续读
GET  /ai-tasks/{taskId}/result       获取当前聚合结果
POST /ai-tasks/{taskId}/commands     取消、补充、授权、接管
```

AG-UI endpoint 仍可保留协议形状，但内部订阅相同 task event stream。首次连接可发送带 covered-through cursor 的 snapshot，再发送该 cursor 后的持久事件；恢复时优先使用协议专属 `Last-Event-ID` transport token。显式 `cursor` 查询参数定位 AAF 公共事件，只用于一对一 Headless 或重新开始整组协议投影，不能代替 AG-UI 的 projectionIndex。transport signal 不参与恢复定位。

### 背压与高频输出

- delta 按字符数或时间窗口合并并持久化后再成为 REPLAYABLE_SIGNAL，避免逐 token 入库；
- 心跳和原始 token 属 transport signal，使用 SSE comment 或无 id frame，不写业务事件；
- 慢消费者不得静默丢弃已编号公共事件；应断开并从 durable cursor 续读，或使用 snapshot 快速恢复；
- output completed、artifact committed 和 terminal event 必须在关闭流前送达；
- 大型内容写入对象/文档存储，事件只返回 ref 和 digest。

### Schema 演进

- `specVersion` 只在信封破坏性变化时升级 major；
- 每个 eventType 有独立 `eventVersion` 和 `dataSchema`；
- 同版本只允许增加可选字段，不删除、改名或改变语义；
- 新事件默认 optional，客户端必须忽略未知 optional 事件；
- required 控制事件必须经过能力协商，不能突然投放给旧客户端；
- 枚举解析保留 unknown 分支，终态判断以 status 契约为准。

AAF 未到 v1.0，落地时直接替换现有窄 Headless VO 和伪 AG-UI 输出，不保留双事件协议或兼容 shim。

### Schema 注册与生成

每种公开事件维护 JSON Schema，并据此生成 Java/TypeScript 类型和协议测试样例。Schema 注册表至少记录 eventType、eventVersion、audience、delivery、dataSchema、是否 required 和 AG-UI projector。禁止在 Controller 中以 Map 临时拼装未注册公开事件。

### 关键审计节点

请求、任务分析、画像冻结、工具集、授权、计划、委派、工具、产物、验证、接管和终态必须产生 DURABLE_FACT。决策审计只保存选择、候选、原因摘要、置信度和策略版本，不保存思维链。

### 落地路线与验收

| 阶段 | 改造 | 验收重点 |
|---|---|---|
| 规范事件 | 定义 AafAiTaskEvent、OUTPUT/ARTIFACT/PROGRESS 和 Schema 注册表 | 对话与任务产生同构事件 |
| 统一投影 | 建立 ExecutionEventPublicMapper、AgUiProjector、HeadlessProjector，删除 Controller 拼装 | Assistant、Workflow 与 Team 共用公共契约和 projector |
| 重放聚合 | 统一 cursor、snapshot、Last-Event-ID 和 AiTaskResult reducer | 断线、恢复、同步/异步结果一致 |
| 通道治理 | 接入 Webhook/MQ、audience、背压和 operator trace | 幂等、安全、未知事件兼容 |

验收基线：

- 同一 task 的 AG-UI 与 Headless 输出可还原相同状态、文本、工具和产物；
- 每个可重放 Headless 事件包含 eventId、task/execution/run/session、sequence、eventOffset、cursor 和 trace 标识；
- AG-UI 只发送标准事件或 `aaf.*` CUSTOM，不直接暴露内部枚举；一对多 frame 可从任意 projectionIndex 断点续传且不丢帧；
- Assistant、Workflow、子 Agent 和 Team 不再维护平行事件工厂；
- 断线续读不重复副作用，重复事件由 eventId 幂等消化；
- transport signal 丢失不影响权威状态；replayable signal 可按 cursor 续读，并可由 snapshot/完成事件快速恢复；
- 同步结果与流式终态由同一 reducer 得到；
- 事件不泄露凭证、系统提示、思维链或未脱敏工具参数。
