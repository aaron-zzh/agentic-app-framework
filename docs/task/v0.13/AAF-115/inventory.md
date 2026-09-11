---
level: Practice
layer: Model
purpose: 盘点 AAF-115 现有任务、执行、调度、事件、接口与前端链路并给出迁移结论
status: draft
version: 1.2.0
date: 2026-09-11
author: AaronZZH & Kiro
tags:
  - AAF-115
  - Task
  - Inventory
related:
  - requirement.md
  - design.md
  - tasks.md
---

# AAF-115 任务模型现状盘点

## 盘点结论

当前 Assistant 运行存在三处业务状态真理：`AssistantTask`、`DelegatedTask`、`TaskBoard`；`ExecutorPlan` 又保存单个执行节点内部的步骤计划。登录用户每次 AG-UI Run 都由 `AssistantExecutionService.execute(...)` 构造 `TaskBoard.coordinated(...)` 或 `TaskBoard.teamCoordinated(...)`，再调用 `DelegatedTaskCoordinator.submitAndDispatch(...)`；实际 Agent 执行进入 `AssistantApplicationService.prepareTask(...)` 后又创建 `AssistantTask.draft(...)`。因此普通 CHAT 也先持久化 `DelegatedTask + TaskBoard`，随后再持久化 `AssistantTask`，与需求描述的双真理源一致。

目标必须直接收敛为：复杂或持久目标只有一个 `Task` 根；可选 `TaskPlan` 内含 `TaskNode` 与 `TaskDependency`；每次真实运行由独立 `Execution` 表达；`TaskDispatch` 只保存调度事实。普通 CHAT 只产生无 Task 的 `Execution`。AAF 未发布 v1.0，本次不保留旧 DTO、旧 API、双写、fallback、legacy adapter 或长期兼容表。

## 当前运行时读写链

### AG-UI 新运行

```text
ChatterRuntime / AgUiChatProvider
  → POST /agui/run
  → AssistantAguiController.run
  → AssistantExecutionService.start
  → RunIdentity.create
     taskId = executionId = runId
     sessionId = conversation/threadId
  → AssistantExecutionService.execute
  → TaskBoard.coordinated | teamCoordinated
  → DelegatedTaskCoordinator.submitAndDispatch
  → DelegatedTaskPort + TaskBoardPort 持久化
  → DelegatedTaskDispatchPort.signal
  → DelegatedTaskScheduler / AgentTaskRuntime
  → AssistantCommandPort.execute
  → AssistantApplicationService.prepareTask
  → AssistantTask.draft + TaskControlPort.create
  → AgentExecutionPort.execute
  → ExecutionEventStorePort.append
  → AG-UI projector → SSE
```

`AssistantExecutionService.analyzedBoard(...)` 的注释明确说明“不再前置调用 TaskComplexityAnalyzer”，而是始终创建 coordinated board。这里没有“简单问题 direct Execution”分支。

### 子任务与重试

`TaskBoard.SubTask` 同时保存 node、attempt、executionId、sessionId、结果和失败：

- 首次 `claim()` 复用预分配 executionId/sessionId，`attempts + 1`。
- `RETRYABLE` 再次 `claim()` 随机生成新 executionId/sessionId，但没有独立 `predecessorExecutionId`。
- `AssistantCommand.forSubTask(...)` 将父 command 的 executionId 写入 child `parentExecutionId`，它表达分解关系。
- `AssistantCommand.newExecution(...)` 将旧 executionId 写入 `parentExecutionId`；当前 hand-back 因而混用了父子谱系和新执行谱系。
- `JpaDelegatedTaskAdapter.handBack(...)` 创建新 executionId/sessionId 并替换 DelegatedTask 当前执行。

目标中 retry/hand-back 的新 attempt 必须使用 `predecessorExecutionId`；`parentExecutionId` 只保留分解关系。

### approval、clarification 与恢复

结构化 HITL 当前经过以下路径：

```text
Agent 事件 AWAITING_AUTHORIZATION / AWAITING_CLARIFICATION
  → DelegatedTaskCoordinator.executeSubTask doOnNext 记录标志
  → takeUntil(...) 截断下游订阅
  → finalizeSubTaskExecution
  → JpaTaskTransitionAdapter.requestAuthorization | requestClarification
  → 同事务锁 task → board → HITL 事实 → event → outbox
```

授权等待由 `TaskBoard.awaitAuthorization(...)` 和 `DelegatedTask.awaitAuthorization(...)` 双写状态；澄清等待同样由 `TaskBoard.awaitClarification(...)` 和 `DelegatedTask.awaitClarification(...)` 双写。恢复时 `AssistantAguiController.resumeApproval(...)` 调用 recovery dispatch 并订阅事件流；`resumeClarification(...)` 提交输入后订阅指定 execution 的事件。

关键缺口：`takeUntil` 只是取消下游订阅，不会调用 `AgentExecutionPort.pause(...)`。`HarnessAgentExecutionAdapter.doFinally → release(...)` 在 `pauseWon=false` 时删除 AgentState。因此当前 structured approval/clarification 虽已提交等待事实，却不能原地续接旧 AgentState。

### AAF-110 PAUSE 与 AgentState

已核实：

- `AgentExecutionPort` 暴露 `execute`、`cancel`、`pause`。
- `AssistantApplicationService.controlTask(...)` 在 PAUSE 时调用 `agentExecution.pause(executionId)`；CANCEL/TAKE_OVER 调用 cancel。
- `HarnessAgentExecutionAdapter` 使用 `TerminalState.PAUSING` 和 `pauseWonRace(...)`；只有 `pauseWon=true` 的 release 路径保留 AgentState，其余完成、失败、取消、接管路径删除状态。
- `RedisAgentStateStore` 的 key 前缀为 `aaf:agentscope:state:`；`AgentScopeRuntimeContextMapper.stateUserKey(...)` 组合 tenant、user、task、agent、execution，DELEGATED 当前还包含 fencing token；session 使用 `InvocationContext.sessionId`。
- 同 attempt 恢复若生成新 fence，当前 DELEGATED state key 会改变，即使 executionId/sessionId 不变也读不到旧槽。
- `HarnessAgentExecutionAdapter.requireNoHiddenPersistentHistory(...)` 在模型调用前读取 `agent_state`；只要已有 state 的 context 或 summary 非空就抛 `ContextBudgetExceededException`。因此显式 PAUSE 虽保存状态，恢复加载端仍会拒绝合法的非空历史。

目标必须将 AgentState 槽代际与 dispatch fencing 解耦：同 attempt 的 state slot generation 稳定，dispatch generation 可递增；所有 load/save 仍校验当前 execution、owner、profile 与 fence。隐藏历史守卫必须区分 fresh/direct 与 validated same-attempt resume。

## 现有概念到目标概念

| 现有概念 | 当前职责 | 目标归属 | 结论 |
|---|---|---|---|
| `AssistantTask` | Agent 执行状态、controlMode、owner、recoveryPoint、转换历史 | `Task` 根状态；运行状态进 `Execution`；转换进 canonical event | 删除模型与端口 |
| `DelegatedTask` | 根任务、当前 execution、预算、lease/fence、调度、checkpoint、owner | `Task` + `Execution` + `TaskDispatch` | 删除模型，按职责拆迁 |
| `TaskBoard` | 目标、DAG、节点状态、执行 ID、重试、结果、澄清输入 | `TaskPlan` + `TaskNode` + `TaskDependency` + `Execution` | 删除旧持久模型；promotion/structured HITL 创建最小一节点 plan |
| 无 Board 的单 Agent 持久执行 | 现状未形成独立目标概念 | `TASK_ROOT Execution` | 仅限永不进入 structured HITL 的纯持久 Task；不能发 nodeId=null interrupt |
| `TaskBoard.SubTask` | 节点定义与 attempt 运行态混合 | 节点定义/状态进 `TaskNode`，每次运行进 `Execution` | 拆分，不保留 SubTask |
| `ExecutorPlan` | 单个 executor 内部步骤、审批与验证 | `NodeExecutionPlan` 或节点 execution plan payload | 不得成为根 `TaskPlan`；最终形态待人类选择 |
| `ExecutionEvent` | 实际执行事件及谱系 | canonical `TaskEventEnvelope`/Execution 事件 | 保留能力，扩展 nullable task/node/plan 与 attempt/generation |
| Conversation lease | conversation 级互斥 | dispatch/node execution fence；必要时保留入口会话互斥 | 不作为 Task 业务状态 |
| AgentState | AgentScope 可删除工作态 | 仍属于单个 Execution state slot | 保留但不可作为 Task 真理 |
| `ai_execution_run` / `aigc_execution_run` | 通用旧观测或 AIGC 项目执行 | 不属于 AAF-115 Assistant Execution | 排除，不合并 |
| `sys_async_task` / `sys_task_execution` | 系统异步/定时任务基础设施 | 不属于 AAF-115 聚合 | 排除，名称消歧 |
| `aigc_task` | 媒体生成供应商任务 | 作为外围业务 Tool/receipt 结果 | 排除，不改名合并 |

## 字段迁移矩阵

| 来源字段 | 目标字段/实体 | 规则 |
|---|---|---|
| `AssistantTask.taskId`、`DelegatedTask.taskId`、`TaskBoard.taskId` | `Task.taskId` | 唯一根标识，不再三处重复拥有状态 |
| `DelegatedTask.tenantId/userId/conversationId` | `Task.tenantId/owner*/conversationId`；Execution 保存调用快照 | tenant/owner 是安全边界；Execution 快照不可反向提权 |
| `DelegatedTask.sessionId/executionId` | `Execution.sessionId/executionId` | 从根 Task 移除“当前执行身份” |
| `DelegatedTask.parentExecutionId` | `Execution.parentExecutionId` | 仅表示分解父子关系 |
| 当前 retry/hand-back 的旧 execution 关系 | `Execution.predecessorExecutionId` | fresh attempt 专用，禁止写 parent 字段代替；TASK_NODE 约束同 node，TASK_ROOT 约束同 task/scope |
| direct Run 的 `conversationId/runId/correlationId` 与 prompt/input envelope | `Task.origin*` + `Execution.correlationId/inputRef/publicContextRef` | promotion 由服务端从锁定 origin DIRECT 复制不可变公开谱系；禁止客户端覆盖及复制 CoT/AgentState |
| 无 plan Task 的当前执行 | `Task.currentRootExecutionId/currentRootAttemptNo` | TASK_ROOT same-attempt/fresh-attempt 的唯一当前指针；以 Task version CAS 改写 |
| `AssistantTask.status`、`DelegatedTask.status` | `Task.status` | 唯一 command service 推进 |
| `TaskBoard.SubTask.status` | `TaskNode.status` 与 `Execution.status` | 节点可调度状态和一次运行状态分离 |
| `AssistantTask.controlMode`、`DelegatedTask.contract` | `Task.controlMode/executionContract` | 创建/promotion 时冻结，变更需显式命令与事件 |
| `AssistantTask.owner`、`DelegatedTask.owner` | `Task.ownerType/ownerId`、Execution owner snapshot | 统一 polymorphic Actor |
| `AssistantTask.recoveryPoint`、`DelegatedTask.checkpoint` | `Task.checkpoint`、`TaskNode.checkpoint`、Execution state slot metadata | 分层归属，不复制 AgentState |
| `DelegatedTask.priority/nextRunAt` | priority 在 `Task`；nextRunAt 在 `TaskDispatch` | 业务优先级与调度时间分离 |
| `leaseOwner/leaseUntil/fencingToken` | `TaskDispatch` | 不进入 Task 业务状态 |
| `attempts/consecutiveFailures` | `Execution.attemptNo` 与 Dispatch delivery attempts | 节点 attempt 和消息投递重试分开计数 |
| `BudgetUsage` | `Task.budgetLimit/budgetUsage` | 根预算唯一；按事件/receipt 原子累计 |
| `TaskBoard.goal` | `Task.goal/completionCriteria` + `TaskPlan.aggregationContract` | 目标属于 Task；plan 冻结唯一 terminal result producer/selector |
| `TaskBoard` 聚合结果/完成状态 | `Task.rootResultRef/completionEvidenceRef` + 来源 identity | Task CAS 只接受一次；旧 generation/未声明节点只审计拒绝 |
| `TaskBoard.maxParallelism` | `TaskPlan.maxParallelism` | 冻结时受系统硬上限约束 |
| `SubTask.subTaskId/kind/description` | `TaskNode.nodeKey/kind/instruction` | nodeKey 在 plan revision 内稳定 |
| `SubTask.dependsOn` | `TaskDependency` | 物理边表，不再 JSON Set |
| `SubTask.inputBindings` | `TaskNode.inputContract`/plan aggregation contract | 只能引用声明的前驱 |
| `roleKey/skillKey/assistantTarget/modelSelection` | `TaskNode` assignment + `Execution` frozen profile snapshot | 分析不能扩大权限 |
| `SubTask.executionId/sessionId/attempts` | `Execution` + `TaskNode.currentExecutionId/currentAttempt` | 每 attempt 一行 Execution |
| `SubTask.result/failure/clarifiedParameters` | node result/checkpoint + interrupt/input 事实 | 参数按 interruptId 精确绑定 |
| `ExecutorPlan.boardId` | `nodeId` | 删除 board 身份 |
| `ExecutorPlan.revision/status/steps` | 节点局部计划或 payload | 与根 DAG revision 分开命名 |
| `ExecutionEvent.nodeIdentity` | `planRevision/nodeId/nodeKey/attemptNo` | canonical envelope 显式化 |

## 状态迁移矩阵

### 根状态

| 当前状态 | 来源 | 目标 Task 状态 | 说明 |
|---|---|---|---|
| `DRAFT` | AssistantTask | `DRAFT` | 保留 |
| `PLANNING` | AssistantTask | `PLANNING` | 保留 |
| `PENDING` | DelegatedTask | `READY` | 已满足运行前条件 |
| `RUNNING` | 两根模型 | `RUNNING` | 仅 Task command service 写 |
| `AWAITING_AUTHORIZATION` | 两根+Board | 同名 | 根投影由等待节点决定 |
| `AWAITING_CLARIFICATION` | 两根+Board | 同名 | 同上 |
| `PAUSED` | 两根模型 | `PAUSED` | 稳定业务态 |
| `VERIFYING` | AssistantTask | `VERIFYING` | 保留 |
| `COMPLETED/FAILED/CANCELED` | 两根模型 | 同名 | 终态 |
| `RECOVERING` | AssistantTask | 不作为稳定 Task 状态 | 用 Execution/Dispatch 事件表达短暂恢复过程 |

### 节点与执行状态

| 当前 Board 状态 | 目标 TaskNode | 目标 Execution | 说明 |
|---|---|---|---|
| `PENDING` | `PENDING/READY` | 无或 `CREATED` | readiness 独立计算 |
| `RUNNING` | `CLAIMED/RUNNING` | `DISPATCHED/RUNNING` | claim 与真正启动分开 |
| `AWAITING_AUTHORIZATION` | 同名 | 同名 | 同 attempt 可恢复中断 |
| `AWAITING_CLARIFICATION` | 同名 | 同名 | 同 attempt 可恢复中断 |
| `RETRYABLE` | `READY` | 旧 attempt `FAILED` 或 `SUPERSEDED` | 新 attempt 明确建行 |
| `COMPLETED/FAILED/CANCELED` | 同名 | 同名 | 各层只写本层事实 |
| 无 | `PAUSED/VERIFYING/BLOCKED` | `PAUSED` | 补齐暂停、验证、依赖阻断语义 |
| 无 | — | `PROMOTED/SUPERSEDED` | direct promotion 与非终态被新 attempt 替代 |

## 事件迁移矩阵

| 当前事件/事实 | 当前写入 | 目标事件 | 处理 |
|---|---|---|---|
| `ExecutionEvent` execution 生命周期 | `ExecutionEventStorePort` | `execution.created/dispatched/started/completed/failed/canceled/paused/resumed/promoted/superseded` | 统一信封；DIRECT 的 task/plan/node 为空 |
| Task 状态通知 | Coordinator/notification outbox | `task.created/status-changed/owner-changed/result-committed` | Task 级事件不要求 Execution；按 Task aggregate sequence 排序 |
| Board plan/subtask 事件 | Coordinator 手工 emit | `plan.frozen/freeze-rejected/superseded`、`node.ready/claimed/status-changed/result-recorded/result-rejected` | Plan/Node 事件允许无 Execution；删除 board 事件名 |
| approval/clarification | `TaskTransitionPort` | `interrupt.requested/resolved` + typed payload | 精确绑定 task/node/execution/attempt |
| recovery | recovery command/job | `execution.resume-requested/resumed` 或 `retry-created` | 区分同 attempt 与 fresh attempt |
| Dispatch Spring event | `DelegatedTaskDispatchPort.signal` | outbox wake-up hint | 提示可丢，durable dispatch 表不可丢 |
| AG-UI `aaf.task.*` CUSTOM | projector | canonical event 安全投影 | 只触发 query invalidation/安全增量 |
| `ai_task_event.event_offset` | DB identity | 全局 cursor | 保留断线续读语义 |

目标事件信封不得包含 chain-of-thought、私有 scratchpad、凭证或未冻结上下文。

## 端口迁移矩阵

| 现有端口 | 当前职责 | 目标端口 | 结论 |
|---|---|---|---|
| `TaskControlPort` | 只写 AssistantTask | `TaskCommandPort` / `TaskAggregateStore` | 删除 |
| `DelegatedTaskPort` | 根状态、lease、budget、checkpoint、recovery、input 混合 | `TaskAggregateStore` + `ExecutionStore` + `TaskDispatchStore` | 删除并拆分 |
| `TaskBoardPort` | Board/子任务状态 | `TaskAggregateStore` | 删除 |
| `TaskTransitionPort` | task+board+event+outbox+HITL 跨表事务 | `TaskUnitOfWork`/command transaction facade | 删除旧接口，保留原子事务能力 |
| `TaskRecoveryPort` | approval 恢复作业 | `TaskCommandPort.resume` + durable dispatch | 删除专用根模型依赖 |
| `TaskRecoveryDispatchPort` | 发起恢复 | `TaskDispatchStore.enqueue` + signal | 收敛 |
| `TaskResumeSignalPort` | 进程内恢复信号 | `ExecutionInterruptPort` | 仅作提交后 runtime 中断/唤醒，不作事实源 |
| `DelegatedTaskDispatchPort` | Spring event signal | `TaskDispatchSignalPort` | 改名；信号不拥有状态 |
| `ExecutionEventStorePort` | append/read/sequence | `TaskEventStore` | 保留改造，支持 nullable taskId/nodeId |
| `ExecutorPlanPort` | 节点局部步骤 CAS | `NodeExecutionPlanPort` 或节点 payload | 待人类选型 |
| `AgentExecutionPort` | execute/cancel/pause | 保留 | 命令必须绑定 Execution identity 与 interruption mode |

所有 JPA/Redis 阻塞适配器必须由统一事务 facade 隔离到专用 scheduler，或只在 durable worker 的阻塞线程中运行；响应式调用链禁止嵌套 `block()`/`subscribe()`。

## 数据库盘点与结论

| 现有表 | 当前归属 | 目标结论 |
|---|---|---|
| `ai_assistant_task_control` | AssistantTask | 删除，由 `ai_task`/`ai_task_execution` 取代 |
| `ai_delegated_task` | DelegatedTask + command payload | 删除，由六张目标主表取代 |
| `ai_task_board` | TaskBoard JSONB | 删除，由 plan/node/dependency 表取代 |
| `ai_executor_plan`、`ai_executor_plan_step` | executor 局部计划 | 改名保留或并入节点 payload，不能冒充 TaskPlan |
| `ai_task_event` | append-only 执行事件；DDL 位于 `v2__ai_schema.sql` | 保留表名并重建；Task/Plan/Node 事件允许 executionId 为空，按 aggregate identity CHECK/FK；新增 aggregateSequence、plan/node/dispatch/attempt/generation |
| `ai_execution_sequence` | execution 内严格递增 sequence | 保留，以 executionId 分配序列 |
| `ai_task_transition_outbox` | 状态转换 outbox | 重建为 canonical event outbox，不双写 |
| `ai_task_notification_outbox` | 通知 | 保留，消费 canonical event |
| `ai_execution_profile_snapshot`、`ai_prompt_envelope` | 冻结画像与 prompt 证据 | 改以 executionId 为主，direct 时 taskId nullable |
| `ai_hitl_approval`、`ai_hitl_recovery`、`ai_clarification_request` | HITL | 保留，增加 node/execution/attempt/generation 精确身份 |
| `ai_task_recovery_command` | task 最新恢复命令 | 删除或并入 dispatch command payload；不能保留并行恢复真理 |
| `ai_task_input_buffer` | 输入缓冲 | 保留改外键与 interrupt identity |
| `ai_tool_invocation_receipt`、`ai_connector_action_execution` | 副作用幂等 receipt | 保留，绑定 execution/node/attempt |
| `ai_authorization_grant` | 授权 | 保留；Task 场景 taskId 非空 |
| `ai_automation_run` | 自动化运行引用 delegated_task_id | 改为 taskId/originExecutionId |
| `ai_learning_candidate` | 学习候选引用任务 | 改统一 identity |
| `aigc_task`、`aigc_execution_run` | AIGC 业务 | 排除 |
| `sys_async_task`、`sys_task_execution` 与 Entity Engine `async-task/task-execution` | 系统任务 | 排除，不得误删 |

现状没有独立的 Assistant `ai_task_execution` 主表；execution 仅散落于事件、sequence、profile、prompt、receipt、HITL 和 command payload。目标必须新增该主事实，而不是复用 AIGC 或系统任务表。

## REST、SSE、GraphQL 与 AG-UI 盘点

### REST/SSE

`DelegatedTaskController` 当前暴露：

- `GET /api/ai/tasks/delegated`
- `GET /api/ai/tasks/{taskId}/delegation`
- `GET /api/ai/tasks/{taskId}/events`
- `GET /api/ai/tasks/{taskId}/events/stream`
- `GET /api/ai/tasks/{taskId}/executor-plans`
- `POST /api/ai/tasks/{taskId}/stop`
- `POST /api/ai/tasks/{taskId}/take-over`
- `POST /api/ai/tasks/{taskId}/hand-back`
- `POST /api/ai/tasks/{taskId}/inputs`

`HumanApprovalController` 提供 pending/decision/recover 与 approval AG-UI 事件流。`AssistantAguiController` 的 `/agui/run` 是主入口，返回 `SseEmitter`。

approval/clarification 迁移矩阵：

| 当前 endpoint/符号 | 当前读写路径 | 目标资源 | 结论 |
|---|---|---|---|
| `GET /api/ai/approvals/pending` / `HumanApprovalController.pending` | `HumanApprovalPort.pending(tenant,user)` → `HumanApprovalVO` | `GET /api/ai/task-interrupts/pending?type=APPROVAL` → `TaskInterruptView` | 删除 approval 专属 DTO/查询；按 tenant + assignee 服务端过滤 |
| `POST /api/ai/approvals/{approvalId}/decision` / `HitlCoordinatorPort.decide` | 同步写 APPROVED/REJECTED | `POST /api/ai/tasks/{taskId}/interrupts/{interruptId}/resolutions`，typed `APPROVE/DENY` | 统一完整 task/plan/node/execution/attempt/state-slot/dispatch identity、expected versions、idempotencyKey；返回 202 |
| `POST /api/ai/approvals/{approvalId}/recover` / `TaskRecoveryDispatchPort.recover` | 用户手工重放批准恢复作业 | 无用户级替代 | 删除恢复旁路；resolution outbox + durable dispatch 自动恢复/重试，运维重放另走审计命令 |
| `GET /api/agui/approvals/{approvalId}/events` / `AssistantApprovalEventService.stream` | controller 内 `SseEmitter + subscribe` | task canonical event stream / 统一 AG-UI stream | 删除 approval 专属 SSE；按 eventOffset 重连 |
| `POST /api/ai/tasks/{taskId}/inputs` | `DelegatedTaskService.acceptInput`，缺少强制 interrupt identity | 同一 resolution endpoint，typed `INPUT` | 删除最近 waiting 推断；必须给 interruptId 与完整 identity |

目标 pending/detail DTO 不暴露 AgentState/私有 prompt；typed resolution 只提交事实，恢复由 durable worker 异步执行。approvalId 直接由统一 interruptId 取代，不保留 alias/adapter。

需移除的响应式反模式：

- `DelegatedTaskEventService.snapshot(...)` 使用 `collectList().blockOptional()`。
- `DelegatedTaskEventStreamService` 在虚拟线程轮询中多次 `blockOptional()` 并 `Thread.sleep(250ms)`。
- `AssistantAguiController.startRun/resume*` 和 `HumanApprovalController.events(...)` 在 controller 内手工 `subscribe()`。
- `DelegatedTaskCoordinator.emitPlanEvent(...)` 与 `cancelRunningChildren(...)` 内部手工 `subscribe()`。

目标 controller 直接返回 `Mono<ResponseEntity<...>>` 或 `Flux<ServerSentEvent<...>>`；长任务由 durable dispatch worker 运行，HTTP 命令提交后返回 `202 Accepted`，SSE 只异步读取已提交事件。

### GraphQL

GraphQL 资源仅发现 `apps/service/aaf-api/src/main/resources/graphql/movie.graphqls`，没有 Assistant Task、DelegatedTask、TaskBoard 或 ExecutorPlan 的查询/写入路径。AAF-115 不新增 GraphQL 并行 API；任务资源统一走 REST/SSE/AG-UI。

### AG-UI

`AssistantAguiController` 将 `ExecutionEvent` 投影为 AG-UI SSE。前端 `AgUiChatProvider` 订阅 CUSTOM 与 Activity：`aaf.task.*` 只触发 TanStack Query invalidation；SUBTASK activity 进入 `agent-run-store` 的瞬时活动视图。该 store 不是持久 Task 真理源，目标继续保持此边界并将 SUBTASK 命名迁为 node activity。

## 前端 TaskBoardPanel 盘点

```text
ChatterPanel
  → assistant-ui mainThreadId 作为 conversationId
  → useTaskBoard(conversationId)
  → delegatedTaskApi.list() 拉取当前用户全部 DelegatedTask
  → 前端 select 按 conversationId 过滤
  → TaskBoardPanel 使用 DelegatedTaskVO 渲染
  → 每个 TaskItem 内 ExecutorPlanSummary 独立 3 秒轮询
```

迁移结论：

| 当前前端 | 目标 | 结论 |
|---|---|---|
| `DelegatedTaskVO` 与旧 status/event 类型 | `TaskSummaryView`/`TaskDetailView` | 直接替换旧类型 |
| 全量 list 后前端 conversation filter | `GET /api/ai/tasks?conversationId=...` | 服务端按 tenant/owner/conversation 过滤 |
| `TaskBoardPanel` 展示每个 Run | 只展示真实 Task | direct Execution 不出现 |
| `ExecutorPlanSummary` 每 3 秒轮询 | detail 一次返回 plan/node/current execution | 删除独立轮询 |
| CUSTOM `aaf.task.*` | canonical task/node event projection | invalidation 或带 version 的安全增量 |
| `agent-run-store.subTaskActivities` | 瞬时 node activity | 可改名，仍不可承载 Task 真理 |
| TanStack Query | 服务端状态缓存 | 保留 |
| Zustand stores | 瞬时运行/UI | 保留边界，禁止复制 Task |

## 测试迁移矩阵

| 现有测试关注点 | 目标测试归属 | 迁移结论 |
|---|---|---|
| `AssistantTask` 状态转换测试 | `Task` 根状态机单元测试 | 删除旧模型断言，按唯一 command 规则重写 |
| `DelegatedTask` lease/budget/recovery 测试 | Task/Execution/Dispatch 分层测试 | 拆开业务状态、attempt 与 delivery retry |
| `TaskBoard` DAG/SubTask 测试 | `TaskDagService`、TaskNode readiness 单元测试 | 保留环检测、依赖、并行度语义，改用物理 node/edge |
| `ExecutorPlan` CAS/审批测试 | `NodeExecutionPlan` 测试或 node payload 合同测试 | 取决于人类选型，不与根 TaskPlan 混测 |
| approval/clarification recovery 测试 | interrupt identity + same-attempt/fresh-attempt 矩阵 | 必须覆盖状态保留与隐藏历史守卫闭环 |
| Delegated scheduler/lease 测试 | durable dispatch claim/renew/expiry/fencing 测试 | 增加旧 generation 迟到拒绝 |
| REST/SSE controller 测试 | 新 Task REST + Reactor SSE 测试 | 删除 delegated DTO/path 和手工 subscribe 假设 |
| 前端 `TaskBoardPanel` 测试 | Task summary/detail + conversation 服务端过滤 | direct Execution 不展示，删除 ExecutorPlan 轮询断言 |
| AG-UI CUSTOM/Activity 测试 | canonical event invalidation/gap refetch | Zustand 只验证瞬时 UI，不保存 Task 真理 |
| Gherkin acceptance | #11509 全量覆盖 | 新增 promotion、join、恢复单节点、兄弟不重放与 stale fence 故障注入 |

本设计阶段不修改或新增测试；具体测试文件在 #11503～#11509 实现时随代码一次替换，不建立新旧断言双轨。

## 删除与直接替换清单

直接删除：

- `AssistantTask`、`DelegatedTask`、旧持久 `TaskBoard`、`TaskBoard.SubTask`。
- `TaskControlPort`、`DelegatedTaskPort`、`TaskBoardPort`、旧 `TaskTransitionPort`、`DelegatedTaskDispatchPort`。
- `ai_assistant_task_control`、`ai_delegated_task`、`ai_task_board` 及其 Repository/Entity。
- delegated-task 旧 REST DTO、前端类型、endpoint 名和查询 key。
- controller/service/coordinator 内部嵌套 `block()`、`subscribe()` 和轮询 sleep。

保留改造：

- `AgentExecutionPort`、AgentScope state store、执行画像、prompt envelope、event sequence、receipt、HITL、authorization、通知 outbox。
- `ai_task_event` 的 append-only/cursor 能力，但重建字段与安全约束。
- AG-UI projector 与 TanStack Query 模式，但改读 canonical Task/Execution。

禁止误删：

- AIGC 的 `AigcTask`/`aigc_task`、`AigcExecutionRun`/`aigc_execution_run`。
- 系统异步/定时任务的 `AsyncTask`、`TaskExecution`、`sys_async_task`、`sys_task_execution`。
- Flowable Workflow 与 AI workflow run/node run。

## 未知项与人类决策

| 项目 | 当前结论 | 阻塞点 |
|---|---|---|
| 现网是否已有旧三表真实数据 | 文档称未发布，但未连接部署环境验证 | 实现前必须由人类/运维确认；有数据则先批准一次性离线转换或清空 |
| Flyway 版本号/模块号段 | 架构规范禁止 agent 自行分配 | 协调者登记号段后才能写迁移 |
| ExecutorPlan 最终形态 | 明确只属于单节点，不是根 DAG | 人类选择改名保留两表，或并入 TaskNode/Execution payload |
| AgentScope state slot API | 当前 key 混入 fence，守卫不支持 resume | #11506 实现前确认可扩展 metadata/CAS；否则 fresh attempt fail-closed |
| fresh attempt 的 session 策略 | 设计建议新 sessionId；same attempt 强制复用 | 需验证 AgentScope store/API 对 session 与 state slot 的约束 |
| Spring MVC 到 WebFlux controller 切换范围 | 目标必须直接返回 Reactor Publisher | 人类确认是否允许本批次迁 controller 栈；不得以手工 subscribe 作为折中 |
| 独立任务中心页面 | 当前仅 Chatter 内 TaskBoardPanel | 非核心模型阻塞项，产品决定是否另开页面 |
| structured HITL/promotion 的 plan 边界 | 设计推荐强制最小一节点 plan；TASK_ROOT 禁止结构化 interrupt | 人类批准；若拒绝需 product 先修改 AC4/AC12 |
| TASK_ROOT fresh attempt | 设计采用推荐方案：current-root CAS + predecessor + 新 execution/session/state slot | 人类批准；若拒绝须收紧需求为可恢复 Task 一律最小一节点 plan |
| 事件/状态保留周期与 payload 上限 | 当前未形成统一策略 | 上线前补容量与合规参数；同时决定 event 归档删除或 tombstone 保留 |
| hard purge 审计策略 | RESTRICT event 必须先归档并删除在线行，或保留 tombstone 聚合 | 人类/合规二选一，禁止保留在线 event 同时硬删实体 |
| 回滚数据策略 | 可整体恢复 DB 备份，无双写回滚 | 人类选择开发环境清空或保留公开摘要导出 |

上述未知不代表仍有未盘点的代码读写路径；它们是部署事实、第三方运行时能力或产品取舍，必须在开发前由人类确认。

## 证据索引

| 证据 | 说明 |
|---|---|
| `AssistantExecutionService.execute(...)`、`analyzedBoard(...)`、`teamBoard(...)`、`RunIdentity.create(...)` | 每次登录 Run 建 Board；当前 task/execution/run ID 重合 |
| `DelegatedTaskCoordinator.submitAndDispatch(...)`、`executeSubTask(...)`、`finalizeSubTaskExecution(...)` | 委托提交、takeUntil、HITL 收尾与子节点执行 |
| `AssistantApplicationService.prepareTask(...)`、`controlTask(...)` | 第二个 AssistantTask 真理与 pause/cancel 控制 |
| `AssistantCommand.asResume/newExecution/forSubTask` | 同 ID 续接、旧谱系混用与子执行 parent 关系 |
| `TaskBoard.SubTask.claim/authorizationGranted/clarificationResolved` | retry 新 ID；HITL 恢复复用 ID 但回退 attempt 计数 |
| `JpaTaskTransitionAdapter.requestAuthorization/requestClarification` | task+board+HITL+event+outbox 原子写 |
| `JpaDelegatedTaskAdapter.handBack(...)` | 人工交回创建新 execution/session |
| `HarnessAgentExecutionAdapter.pauseWonRace/release/requireNoHiddenPersistentHistory` | AgentState 保留、删除与合法恢复冲突 |
| `AgentScopeRuntimeContextMapper.stateUserKey(...)`、`RedisAgentStateStore` | state slot 当前包含 DELEGATED fence |
| `JpaExecutionEventStoreAdapter`、`SynchronousExecutionEventWriter` | JPA boundedElastic 隔离、sequence 与事件写入 |
| `v2__ai_schema.sql` | `ai_task_event` 精确 DDL、约束与索引 |
| `v16__intelligent_runtime_schema.sql` | 三根模型周边表、recovery、dispatch/outbox、ExecutorPlan |
| `DelegatedTaskController`、`HumanApprovalController`、`AssistantAguiController` | REST/SSE/AG-UI 入口及手工 subscribe |
| `DelegatedTaskEventService`、`DelegatedTaskEventStreamService` | blockOptional 与 sleep 轮询 |
| `ChatterPanel`、`use-task-board.ts`、`TaskBoardPanel.tsx`、`ExecutorPlanSummary.tsx` | 前端挂载、全量查询后过滤、局部轮询 |
| `ag-ui-runtime.tsx` | CUSTOM invalidation 与瞬时 SUBTASK activity |
| `movie.graphqls` | 当前无任务 GraphQL schema |
| `AigcTask`、`AigcExecutionRun`、`AsyncTask`、`TaskExecution` | 同名模型消歧与排除范围 |
