---
level: Practice
layer: Model
purpose: 定义 AAF-115 统一 Task、TaskPlan、TaskNode、Execution 与 TaskDispatch 技术方案
status: draft
version: 1.3.0
date: 2026-09-11
author: AaronZZH & Kiro
tags:
  - AAF-115
  - Task
  - DAG
  - Execution
related:
  - requirement.md
  - inventory.md
  - tasks.md
  - ../../../design/adr/ADR-005-agentscope-boundary-and-orchestration.md
  - ../../v0.12/AAF-110/tasks.md
---

# 设计：统一任务模型与 DAG 编排（AAF-115）

## 设计状态

**人类已批准按保守决策开发，后续由代码审查验证**（2026-09-11）。独立 QA 尚未 CLEAR，历史结论如实保留，不据此伪造 QA 通过。

## 保守决策收口

| 项目 | 冻结决策 |
|---|---|
| DIRECT promotion / side-effect intent | 共用 origin DIRECT Execution 行锁与 `sideEffectEpoch`，互斥且失败关闭 |
| generation / fence | `TaskDispatch` 是唯一当前真理源，其余位置只存快照 |
| ready 物化 | `materializeReadyNode` 在单一短事务中原子创建 Node 当前 attempt、Execution、Dispatch、事件与 outbox |
| 补偿 | `CompensationContract` 随 Plan 冻结为可执行合同，仅按合同、receipt 与逆拓扑执行 |
| purge | Task purge 保留 origin DIRECT Execution，仅清理或匿名化跨保留域引用 |

## 目标与边界

建立一条任务真理链：

```text
Task（复杂/持久目标唯一聚合根）
├─ optional TaskPlan revision
│  ├─ TaskNode
│  └─ TaskDependency
├─ CompletionCriteria / ExecutionContract / Budget / Owner
└─ aggregate invariants

Execution（独立运行事实）
├─ DIRECT：taskId=null, planId=null, nodeId=null
├─ TASK_ROOT：taskId!=null, planId=null, nodeId=null；仅限无结构化 HITL 的纯持久单 Agent
└─ TASK_NODE：taskId!=null, planId!=null, nodeId!=null；promotion/structured HITL 强制采用

TaskDispatch（独立调度事实）
└─ executionId / nextRunAt / lease / fencing / delivery attempts
```

`TaskPlan` 是 Task 聚合内的版本化计划，不是第二聚合根；物理分表是为约束和查询服务。`Execution` 和 `TaskDispatch` 有独立生命周期，但所有影响 Task/Node 的命令必须由唯一 `TaskCommandService` 在短事务中协调。AgentState 是 Execution 的可删除工作态，不承载业务真理。

不合并 AIGC `aigc_task/aigc_execution_run`、系统 `sys_async_task/sys_task_execution`、Flowable workflow 或 Entity Engine 的 async-task/task-execution。AgentScope 原生 subagent 继续关闭。

## 核心设计决策

| 决策 | 选择 | 原因 |
|---|---|---|
| 根任务 | 只保留 `Task` | 消除 AssistantTask/DelegatedTask 双状态 |
| 简单 CHAT | 只创建 `Execution(DIRECT)` | 不制造任务噪声 |
| 纯单 Agent 持久目标 | 仅在确认不需要 structured HITL 或多节点 DAG 时，允许无 TaskPlan 的 `Execution(TASK_ROOT)`；仍支持 Task 级 PAUSE/优雅停机 same/fresh attempt | 保留 TaskPlan 可选性且不削弱持久恢复合同 |
| promotion / structured HITL | 必须建立最小一节点 `TaskPlan`，后续由 `Execution(TASK_NODE)` 承担 | 满足 AC4/AC12 的 nodeId 与新节点 Execution 合同 |
| 多节点目标 | 冻结 TaskPlan DAG | 支持串并行、join、fan-out/fan-in |
| 计划版本 | 冻结后不可改；新 revision supersede | 保证执行可追溯 |
| 根结果 | 冻结唯一 terminal result producer/selector，并由 Task CAS 接受一次 | 防止重复或迟到结果覆盖 AC8 根结果 |
| retry 谱系 | `predecessorExecutionId` | 不污染分解关系 `parentExecutionId` |
| 同 attempt 恢复 | 复用 executionId/sessionId/stateSlotGeneration | 保留 AAF-110 合同 |
| fresh attempt | 新 executionId、新 sessionId、attempt+1 | 隔离旧状态；不迁移旧 fence AgentState |
| 调度 | DB durable dispatch + outbox wake-up | 信号可丢，事实不可丢 |
| 事件 | canonical append-only event + outbox | API、AG-UI、通知统一投影 |
| 响应式 | controller 返回 Mono/Flux；JPA 统一隔离 | 禁止 event-loop 阻塞和嵌套 subscribe |
| 兼容 | 维护窗口直接切换 | AAF 未 v1.0，禁止双写/adapter/fallback |

## 领域模型

### Task 聚合

```java
public record Task(
        TaskId taskId,
        TenantId tenantId,
        ConversationId conversationId,
        RunId originRunId,
        CorrelationId originCorrelationId,
        ExecutionId originExecutionId,
        ImmutableInputReference originInputRef,
        PublicContextReference publicContextRef,
        Source source,
        String goal,
        TaskStatus status,
        ControlMode controlMode,
        TaskOwner owner,
        ExecutionContract executionContract,
        CompletionCriteria completionCriteria,
        BudgetLimit budgetLimit,
        BudgetUsage budgetUsage,
        int priority,
        Instant deadline,
        Integer currentPlanRevision,
        ExecutionId currentRootExecutionId,
        Integer currentRootAttemptNo,
        TaskCheckpoint checkpoint,
        ResultReference rootResultRef,
        CompletionEvidenceReference completionEvidenceRef,
        Integer rootResultPlanRevision,
        NodeId rootResultNodeId,
        ExecutionId rootResultExecutionId,
        Integer rootResultAttemptNo,
        Long rootResultGeneration,
        Long rootResultFencingToken,
        long version,
        Instant createdAt,
        Instant updatedAt) {}
```

不变量：

- `originExecutionId` 仅 promotion Task 非空，并在 tenant 内唯一，且必须引用同 tenant 的 `DIRECT` Execution；禁止创建占位 Execution。
- promotion Task 的 `originRunId/originCorrelationId/originInputRef/publicContextRef` 只能从已锁定的 origin DIRECT Execution 及其不可变 prompt/input envelope 复制或引用，客户端不得提交或覆盖；四者创建后不可变。`originInputRef` 指向原始用户输入事实，`publicContextRef` 只指向经白名单冻结的公开上下文，两者均排除 CoT、scratchpad、隐式 AgentState 和模型私有历史。
- owner 使用 `actor_type + actor_id`，不并列 userId/agentId。
- `executionContract`、完成合同、预算上限和安全边界创建后冻结；变更必须显式产生 revision/事件，不能由模型静默扩大。
- 根 status 持久化，由唯一 command service 推进；查询侧可按 node/execution 派生校验并告警，但不得另写根状态。
- 无 TaskPlan 的 Task 只能有一个当前 `TASK_ROOT` attempt。`currentRootExecutionId/currentRootAttemptNo` 是根执行权威指针；创建、same-attempt resume、fresh attempt 和终结均须以 Task version CAS 更新。该 Execution 不得请求 structured interrupt；若执行中首次发现该边界，须在首个外部副作用前，或对**已存在 Task 的 TASK_ROOT→TASK_NODE 内部形态转换**在 receipt 证明的安全点，创建最小一节点 plan，以新 `TASK_NODE` Execution 接管并将旧 root 标记 `SUPERSEDED`。此 receipt 安全点绝不适用于 DIRECT→Task promotion。
- promotion、approval、structured clarification 以及要求节点级恢复的 Task 必须有最小一节点 plan；不得以 `nodeId=null` 发 canonical interrupt。
- 有冻结 plan 的 Task 不允许再创建 `TASK_ROOT` Execution；工作由 node execution 承担，转换事务先清空 current root 指针再设置 current plan。
- `AggregationContract` 必须冻结唯一 `terminalResultNodeId` 和确定性 `resultSelector`；该节点必须是当前 revision 中唯一声明的根结果生产者。
- 根结果写入是 Task 聚合的权威完成事实：TASK_NODE 提交事务必须同时匹配 currentPlanRevision、terminalResultNodeId、executionId、attemptNo、generation、fencingToken、node/result contract 与 Task version；TASK_ROOT 提交事务必须匹配 `currentRootExecutionId/currentRootAttemptNo`、executionId、attemptNo、generation、fencingToken、CompletionCriteria 与 Task version。两类都以 `rootResultRef IS NULL` 的 CAS 只接受一次。重复提交、非当前 root、未声明节点、旧 revision/attempt/generation/fence 只追加 `node.result-rejected` 或 `execution.result-rejected` 审计事件，不覆盖根结果、不释放后继、不完成 Task。

### TaskPlan、TaskNode 与依赖

```java
public record TaskPlan(
        PlanId planId,
        TaskId taskId,
        int revision,
        TaskPlanStatus status,
        int maxParallelism,
        AggregationContract aggregationContract,
        FailurePolicy failurePolicy,
        CompensationContract compensationContract,
        String graphHash,
        ActorRef frozenBy,
        Instant frozenAt,
        int freezeAttemptNo,
        List<PlanValidationFailure> lastFreezeFailures,
        ActorRef lastFreezeRejectedBy,
        Instant lastFreezeRejectedAt,
        long version) {}

public record AggregationContract(
        NodeId terminalResultNodeId,
        String resultSelector,
        Set<NodeId> allowedInputNodeIds,
        String contractHash) {}

public record CompensationContract(
        Set<CompensationTrigger> triggers,
        Map<NodeId, CompensationStep> steps,
        int maxParallelism,
        CompensationFailureTerminal failureTerminal,
        String contractHash) {}

public record CompensationStep(
        NodeId nodeId,
        String handlerRef,
        String inputSelector,
        Set<String> requiredReceiptTypes,
        String providerIdempotencyTemplate,
        FrozenPolicyBoundary policyBoundary) {}

public enum CompensationTrigger { REQUIRED_NODE_FAILED, TASK_CANCELED_AFTER_SIDE_EFFECT }
public enum CompensationFailureTerminal { COMPENSATION_FAILED }

public record PlanValidationFailure(
        String reasonCode,
        NodeId nodeId,
        NodeId relatedNodeId,
        String safeDetail) {}

public record TaskNode(
        NodeId nodeId,
        TaskId taskId,
        PlanId planId,
        int planRevision,
        String nodeKey,
        TaskNodeKind kind,
        String instruction,
        TaskNodeStatus status,
        NodeAssignment assignment,
        InputContract inputContract,
        OutputContract outputContract,
        ResultReference result,
        NodeCheckpoint checkpoint,
        ExecutionId currentExecutionId,
        int currentAttempt,
        long version) {}

public record TaskDependency(
        TaskId taskId,
        PlanId planId,
        int planRevision,
        NodeId predecessorNodeId,
        NodeId successorNodeId,
        DependencyType type) {}
```

`TaskPlanStatus = DRAFT | FROZEN | SUPERSEDED`。冻结前可整体替换草稿；冻结后正文、节点、边、maxParallelism、aggregation/failure policy 均不可变。新计划直接创建新 revision，并将旧 revision 标记 SUPERSEDED；已开始 execution 的旧 revision 先暂停/终止并完成显式迁移决策，不原地改图。

### Execution

```java
public record Execution(
        ExecutionId executionId,
        ExecutionScope scope,
        TenantId tenantId,
        UserId userId,
        ConversationId conversationId,
        SessionId sessionId,
        TaskId taskId,
        PlanId planId,
        Integer planRevision,
        NodeId nodeId,
        RunId runId,
        CorrelationId correlationId,
        ImmutableInputReference inputRef,
        PublicContextReference publicContextRef,
        ExecutionId parentExecutionId,
        ExecutionId predecessorExecutionId,
        int attemptNo,
        ExecutionPurpose purpose,
        ExecutionId compensatesExecutionId,
        ExecutionStatus status,
        PromotionState promotionState,
        long sideEffectEpoch,
        ActorRef ownerSnapshot,
        ProfileReference profile,
        StateSlotIdentity stateSlot,
        String controlRequestId,
        long controlGeneration,
        String controlAction,
        String terminalCode,
        String terminalReason,
        long version,
        Instant createdAt,
        Instant startedAt,
        Instant pausedAt,
        Instant completedAt) {}
```

public enum ExecutionPurpose { NORMAL, COMPENSATION }
public enum PromotionState { NOT_APPLICABLE, ELIGIBLE, PROMOTING, PROMOTED, REJECTED }

Scope 与闸门约束：

- `DIRECT`：taskId/planId/planRevision/nodeId 均空，attemptNo=1、purpose=NORMAL；创建时 `promotionState=ELIGIBLE`、`sideEffectEpoch=0`，两者是该 DIRECT 唯一副作用授权闸门。`correlationId/inputRef/publicContextRef` 创建后不可变。
- `TASK_ROOT`：taskId 非空，其余 plan/node 字段为空；purpose 只能 NORMAL；仅用于无 plan、无 structured HITL 的纯单 Agent Task。允许 same-attempt resume，也允许状态无效、owner/画像变化或 worker 丢失后的 fresh attempt。
- `TASK_NODE`：taskId/planId/planRevision/nodeId 均非空；NORMAL 与 COMPENSATION 分别按 `(tenantId,taskId,planId,planRevision,nodeId,purpose,attemptNo)` 唯一。COMPENSATION 必须引用同 node 已成功并产生可补偿 receipt 的 `compensatesExecutionId`；NORMAL 的该字段必须为空。非 DIRECT 的 `promotionState=NOT_APPLICABLE` 且不得改变 `sideEffectEpoch`。
- `correlationId` 对所有 Execution 必填且不可变；`inputRef/publicContextRef` 指向不可变公开输入事实。promotion 来源 Task 与新 TASK_NODE 的谱系值必须由服务端从 origin DIRECT 复制或引用，客户端不得覆盖。
- `parentExecutionId` 表达 coordinator→child 等分解关系。
- `predecessorExecutionId` 只表达 fresh attempt 谱系：DIRECT 必须为空；TASK_NODE 必须引用同 tenant/task/plan/node 的前一 attempt；TASK_ROOT 必须引用同 tenant/task/scope 的前一 attempt。fresh attempt 均要求 predecessor.attemptNo+1=current.attemptNo。
- promotion 后原 DIRECT 进入 `PROMOTED`，并创建最小一节点 plan 与新的 TASK_NODE；不得创建 TASK_ROOT 作为 promotion 目标。
- TASK_ROOT 不得进入 approval/structured clarification。执行中意外遇到该边界时，仅可在首个外部副作用前，或对已存在 Task 的内部形态转换在 receipt 证明的安全点冻结最小计划，以新 TASK_NODE 接管；无法证明安全点则 fail-closed 暂停并转人工。receipt 安全点不得用于放宽 DIRECT promotion。
- TASK_ROOT fresh attempt：旧非终态 root 先以匹配 generation/fence 的 CAS 进入 `SUPERSEDED`（终态失败保持 FAILED），Task.currentRoot 指针再以同一事务改向 attempt+1 的新 Execution；新 executionId、新 sessionId、新 state slot，predecessor 指向旧 root，不迁移旧 AgentState。旧 dispatch 关闭并提升 fence，receipt/provider idempotency 防止副作用重放。

### TaskDispatch

```java
public record TaskDispatch(
        DispatchId dispatchId,
        TenantId tenantId,
        ExecutionId executionId,
        DispatchStatus status,
        Instant nextRunAt,
        String leaseOwner,
        Instant leaseUntil,
        long generation,
        long fencingToken,
        int deliveryAttempts,
        String lastError,
        long version,
        Instant createdAt,
        Instant updatedAt) {}
```

Dispatch 不保存 Task/Node/Execution 业务 status。`deliveryAttempts` 是领取/传输重试，不是 Execution attempt。一个 Execution 同时最多有一个 PENDING/CLAIMED dispatch。

**唯一真理约束**：lease、generation 与 fencingToken 只持久化在 `TaskDispatch`。Execution、TaskNode、AgentState metadata、interrupt、receipt 和 canonical event 只能保存观察时快照，不得作为当前值来源。首次 enqueue 原子分配 `generation=1,fencingToken=nextval(ai_task_fence_seq)`；resume、lease-expiry recovery、fresh dispatch replacement 均锁当前 active Dispatch，以 `WHERE dispatch_id=? AND version=? AND generation=? AND fencing_token=? AND status IN (...)` CAS 关闭旧行，再创建 generation=`old+1` 且 fence 取全局单调序列的新 active 行。lease renew 不换 generation/fence，只允许同 leaseOwner/current version 延长；claim 从 PENDING→CLAIMED 时保留 generation 并分配新的全局单调 fence。任何 CAS 零行都视为失去执行权并 fail-closed，不得从 Execution 或 event 回填。

所有 AgentState load/save、interrupt request/ACK/resolution、side-effect intent、receipt 和 result commit 事务都必须 join 并锁定该 Execution 的唯一 active Dispatch，复核 dispatchId/status/generation/fencingToken/leaseOwner/leaseUntil 与 Execution/Node current identity。没有 active Dispatch、身份不全、lease 过期或快照不匹配时拒绝操作；事件只记录被接受事务观察到的 dispatch 快照。

## 类结构与模块归属

| 类/接口 | 建议包 | 职责 |
|---|---|---|
| `Task`、`TaskPlan`、`TaskNode`、`TaskDependency` | `aaf-framework/.../intelligent/assistant/domain/task` | 纯领域模型与不变量，零 Spring/JPA |
| `Execution`、`TaskDispatch` | `.../domain/execution` | 运行与调度事实 |
| `TaskAnalysis`、`TaskAnalysisPolicy` | `.../domain/analysis` | 确定性分类规则与模型结果收敛 |
| `TaskCommandService` | `.../application/task` | 唯一命令入口和事务编排 |
| `TaskAnalysisService` | `.../application/task` | 入口分析、direct/task/promotion 决策 |
| `TaskDagService` | `.../domain/task` | freeze 校验、readiness、失败传播 |
| `TaskDispatchWorker` | `.../application/task` | 领取 dispatch、调用 Agent、提交结果 |
| `ExecutionResumePolicy` | `.../domain/execution` | same-attempt/fresh-attempt 矩阵 |
| `TaskAggregateStore`、`ExecutionStore`、`TaskDispatchStore`、`TaskEventStore` | `.../port/task` | 出站端口 |
| `TaskDispatchSignalPort`、`ExecutionInterruptPort` | `.../port/task` | 提交后提示与 runtime 中断 |
| `JpaTaskUnitOfWorkAdapter` | `.../infrastructure/assistant/persistence/task` | 单一短事务 facade |
| `JpaTaskQueryAdapter` | 同上 | 只读投影，统一 scheduler 隔离 |
| `RedisExecutionStateStoreAdapter` | `.../infrastructure/agentscope` | state slot metadata 与 CAS/fence 校验 |
| `TaskController`、`TaskEventController` | `aaf-api/module/ai/assistant/controller` | REST/SSE 边界 |
| `AssistantAguiController` | 现包改造 | 直接返回 Flux SSE，不手工 subscribe |
| `TaskViewMapper` | `aaf-api/module/ai/assistant/mapper` | DTO 映射，不返回 Entity |

## 状态机

### Task 状态

```text
DRAFT → PLANNING → READY → RUNNING → VERIFYING → COMPLETED
  │         │         │       │          │
  └─────────┴─────────┴───────┴──────────┼→ FAILED
                                        └→ CANCELING → CANCELED
RUNNING/PLANNING/READY
  → AWAITING_AUTHORIZATION | AWAITING_CLARIFICATION
  → RUNNING | PLANNING | PAUSING | CANCELING | FAILED
RUNNING/PLANNING/READY/AWAITING_*
  → PAUSING → PAUSED → RUNNING | PLANNING | CANCELING | FAILED
```

状态集合：`DRAFT, PLANNING, READY, RUNNING, PAUSING, AWAITING_AUTHORIZATION, AWAITING_CLARIFICATION, PAUSED, CANCELING, VERIFYING, COMPENSATING, COMPLETED, FAILED, COMPENSATION_FAILED, CANCELED, PURGING`。`COMPENSATING` 只执行冻结 CompensationContract 的逆拓扑；`COMPENSATION_FAILED` 是人工处置前不可恢复的终态。`PURGING` 只由 retention/hard-purge 运维命令从终态进入，禁止任何 claim、resume、result commit 或新 event append（仅允许 purge audit manifest）；归档失败时保持 PURGING 等待受审计重试或显式恢复原终态。

`RECOVERING` 不作为稳定根态。恢复请求、校验、dispatch claim 由事件和 Execution/Dispatch 短期状态表达。`PAUSING/CANCELING` 是持久控制握手态，不允许新 claim 或 readiness release。Task 状态计算规则：

- dispatch claim 必须在同一事务验证 Task status ∈ `{READY,RUNNING}`、无 control request、Execution/Dispatch 当前 generation/version 一致；TASK_NODE 还必须验证 current plan 为 FROZEN 且 Node=READY，TASK_ROOT 则必须验证 Task.currentPlanRevision 为空且不存在 structured interrupt。Task 为 `PAUSING/PAUSED/CANCELING/CANCELED` 或任一结构化等待且无其他可运行分支时禁止领取。
- readiness 可以计算候选，但 Task 非可运行状态时不得把候选推进 READY 或创建 Dispatch；恢复到 RUNNING 后统一重算。
- 任一活跃节点 RUNNING/CLAIMED → RUNNING。
- 无活跃运行且有结构化等待节点 → 对应 AWAITING 状态；两类并存时按 command 时间和安全优先级形成一个根 projection，detail 保留逐节点事实。
- 用户显式 pause 请求先进入 PAUSING，优先于节点可运行性；全部活跃执行完成保存/关闭握手后才进入 PAUSED。
- cancel 请求先进入 CANCELING，待 pending dispatch 取消且活跃执行关闭后进入 CANCELED；失败关闭按审计记录但不重新开放执行权。
- 全部 completion evidence 满足 → VERIFYING，再由完成合同进入 COMPLETED。
- 必需节点不可重试失败 → 按 frozen failure policy 进入 FAILED、PAUSED 或 compensation flow。

### TaskNode 状态

```text
PENDING → READY → CLAIMED → RUNNING → VERIFYING → COMPLETED
                          │    ├→ AWAITING_AUTHORIZATION → RUNNING
                          │    ├→ AWAITING_CLARIFICATION → RUNNING
                          │    ├→ PAUSED → RUNNING
                          │    ├→ FAILED → READY（fresh retry）
                          │    └→ CANCELED
PENDING/READY → BLOCKED（必需依赖不可成功）
```

状态集合：`PENDING, READY, CLAIMED, RUNNING, AWAITING_AUTHORIZATION, AWAITING_CLARIFICATION, PAUSED, VERIFYING, COMPLETED, FAILED, CANCELED, BLOCKED, COMPENSATING, COMPENSATED, COMPENSATION_FAILED`。

### Execution 状态

```text
CREATED → DISPATCHED → RUNNING
  → AWAITING_AUTHORIZATION | AWAITING_CLARIFICATION | PAUSED → DISPATCHED/RUNNING
  → COMPLETED | FAILED | CANCELED | PROMOTED | SUPERSEDED
```

same-attempt resume 不创建新 Execution；只增加 dispatch generation，并保留 stateSlotGeneration。fresh retry 创建新 Execution；旧 FAILED 保持 FAILED，旧非终态等待执行转 SUPERSEDED。

### Dispatch 状态

`PENDING → CLAIMED → DONE`，任意未终结 dispatch 可进入 `CANCELED`；lease 过期的 CLAIMED 通过 CAS 回到 PENDING 并递增 generation/fencing。它不复制 Task status。

## 应用接口

### 命令入口

```java
public interface TaskCommandPort {
    Mono<TaskView> create(CreateTaskCommand command);
    Mono<PromotionResult> promote(PromoteExecutionCommand command);
    Mono<CommandReceipt> pause(PauseTaskCommand command);
    Mono<CommandReceipt> resume(ResumeTaskCommand command);
    Mono<CommandReceipt> cancel(CancelTaskCommand command);
    Mono<CommandReceipt> takeOver(TakeOverTaskCommand command);
    Mono<CommandReceipt> handBack(HandBackTaskCommand command);
    Mono<RetryResult> retryNode(RetryTaskNodeCommand command);
    Mono<CommandReceipt> resolveInterrupt(ResolveTaskInterruptCommand command);
}

public record CreateTaskCommand(
        TenantId tenantId,
        UserId requesterId,
        ConversationId conversationId,
        RunId runId,
        Source source,
        String goal,
        ControlMode controlMode,
        ExecutionContract executionContract,
        CompletionCriteria completionCriteria,
        BudgetLimit budgetLimit,
        int priority,
        Instant deadline,
        IdempotencyKey idempotencyKey) {}

public record PromoteExecutionCommand(
        TenantId tenantId,
        UserId requesterId,
        ExecutionId originExecutionId,
        TaskAnalysis analysis,
        IdempotencyKey idempotencyKey,
        Instant requestedAt) {}

public record ResumeTaskCommand(
        TenantId tenantId,
        UserId requesterId,
        TaskId taskId,
        ExecutionScope scope,
        NodeId nodeId,
        ExecutionId executionId,
        String interruptId,
        ResumePayload payload,
        DispatchId expectedDispatchId,
        long expectedDispatchGeneration,
        long expectedFencingToken,
        long expectedTaskVersion,
        long expectedExecutionVersion,
        IdempotencyKey idempotencyKey,
        Instant requestedAt) {}

public record RetryTaskNodeCommand(
        TenantId tenantId,
        UserId requesterId,
        TaskId taskId,
        NodeId nodeId,
        ExecutionId predecessorExecutionId,
        String reason,
        long expectedNodeVersion,
        Instant requestedAt) {}

public record ResolveTaskInterruptCommand(
        TenantId tenantId,
        UserId requesterId,
        TaskId taskId,
        PlanId planId,
        int planRevision,
        NodeId nodeId,
        ExecutionId executionId,
        int attemptNo,
        String interruptId,
        InterruptResolutionType resolutionType,
        Map<String, Object> typedValue,
        String reason,
        String stateSlotKey,
        long stateSlotGeneration,
        DispatchId dispatchId,
        long dispatchGeneration,
        long fencingToken,
        long expectedTaskVersion,
        long expectedNodeVersion,
        long expectedExecutionVersion,
        IdempotencyKey idempotencyKey,
        Instant requestedAt) {}

public enum InterruptResolutionType { APPROVE, DENY, INPUT }
```

`ResumeTaskCommand` 的 `scope` 必须与 Execution 一致：TASK_NODE 时 nodeId/interruptId 必填；TASK_ROOT 时二者必须为空且 Task.currentRootExecutionId 必须等于 executionId。TASK_ROOT 只接受 PAUSE/优雅停机恢复，不接受 structured interrupt resolution。两种 scope 都必须校验 expected Task/Execution version、current generation/fence 与幂等键；校验失败不得静默转其他 Execution。

Pause/cancel/take-over/hand-back 命令都必须携带 tenant、requester、taskId、expectedVersion、reason、requestedAt；实现不得按“最近任务”猜测。`resolveInterrupt` 必须校验 interrupt 类型与 resolutionType：approval 仅接受 APPROVE/DENY，clarification 仅接受 INPUT；完整身份、state slot、dispatch generation/fence、三个 expectedVersion 和 idempotencyKey 任一不匹配均 fail-closed。命令只提交 resolution 事实并返回 202，恢复必须由 durable dispatch 异步执行。

### 查询与事件

```java
public interface TaskQueryPort {
    Mono<TaskDetailView> findTask(TaskQuery query);
    Flux<TaskSummaryView> listTasks(TaskListQuery query);
    Mono<ExecutionView> findExecution(ExecutionQuery query);
}

public interface TaskEventStore {
    Mono<TaskEventEnvelope> append(TaskEventEnvelope event);
    Flux<TaskEventEnvelope> read(TaskEventQuery query);
    Flux<TaskEventEnvelope> stream(TaskEventStreamQuery query);
}

public record TaskListQuery(
        TenantId tenantId,
        UserId ownerId,
        ConversationId conversationId,
        Set<TaskStatus> statuses,
        int limit,
        String cursor) {}

public record TaskEventQuery(
        TenantId tenantId,
        TaskId taskId,
        ExecutionId executionId,
        long afterEventOffset,
        int limit) {}
```

### 分析与调度

```java
public interface TaskAnalysisPort {
    Mono<TaskAnalysis> analyze(AnalyzeExecutionCommand command);
}

public interface TaskDispatchStore {
    Mono<ClaimedDispatch> claimDue(ClaimDueDispatch command);
    Mono<ClaimedDispatch> renew(RenewDispatchLease command);
    Mono<Void> complete(CompleteDispatch command);
    Mono<Void> release(ReleaseDispatch command);
    Mono<Void> enqueue(EnqueueDispatch command);
}

public interface TaskMaterializationPort {
    Mono<MaterializedNode> materializeReadyNode(MaterializeReadyNodeCommand command);
    Mono<MaterializedNode> materializeCompensation(MaterializeCompensationCommand command);
}

public interface SideEffectAuthorizationPort {
    Mono<SideEffectIntent> prepareIntent(PrepareSideEffectIntentCommand command);
    Mono<AuthorizedSideEffect> authorizeBeforeCall(AuthorizeSideEffectCallCommand command);
    Mono<SideEffectReceipt> recordReceipt(RecordSideEffectReceiptCommand command);
}

public interface ExecutionInterruptPort {
    Mono<Void> interruptPreservingState(ExecutionInterrupt command);
    Mono<Void> cancel(ExecutionCancel command);
}

public interface TaskDispatchSignalPort {
    Mono<Void> signal(TenantId tenantId, Instant earliestDueAt);
}
```

signal 只是低延迟提示；worker 必须定期扫描 durable dispatch，信号丢失不影响正确性。

## 响应式与线程模型

硬性规则：

- HTTP 命令只提交短事务，返回 `202 Accepted + commandId/taskId/executionId`，不等待模型、Tool 或 Task 完成。
- SSE controller 直接返回 `Flux<ServerSentEvent<TaskEventEnvelope>>`；断开只取消投影订阅，不改变业务状态。
- controller/application 链内禁止调用 `block()`、`blockOptional()` 或手工嵌套 `subscribe()`。
- JPA、JDBC 和阻塞 Redis 操作由 `JpaTaskUnitOfWorkAdapter` 统一使用专用 `taskPersistenceScheduler`：

```java
Mono.fromCallable(() -> transactionTemplate.execute(status -> commitFacts(command)))
        .subscribeOn(taskPersistenceScheduler);
```

- 不允许每个 Repository 随意切 scheduler；事务 facade 一次隔离完整短事务。
- durable worker 若运行在专用阻塞线程，可直接执行短同步事务，但不得占 Reactor event-loop。
- 事务内只锁行、校验、写业务事实、event 与 outbox；模型、Agent、Tool、网络、SSE 和通知都在提交后执行。
- 现有 `SseEmitter + subscribe`、虚拟线程轮询 `blockOptional + sleep` 全部删除，不作为过渡兼容路径保留。

## 数据模型

以下为目标逻辑 DDL 合同；实际 Flyway 版本号必须由协调者登记后分配。

### `ai_task`

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `task_id` | varchar(128) | PK |
| `tenant_id` | varchar(128) | not null |
| `owner_type/owner_id` | varchar(16/128) | not null，HUMAN/ASSISTANT/AGENT/SYSTEM |
| `conversation_id` | varchar(128) | not null |
| `origin_run_id` | varchar(128) | nullable；promotion 时服务端复制 |
| `origin_correlation_id` | varchar(128) | nullable；promotion Task 必填，服务端从 origin DIRECT 复制且不可变 |
| `origin_execution_id` | varchar(128) | nullable，promotion 幂等 |
| `origin_input_ref/public_context_ref` | jsonb | nullable；promotion Task 必填，分别引用不可变原始输入与白名单公开上下文，禁止 CoT/AgentState |
| `source` | varchar(24) | CONVERSATION/MANUAL/AUTOMATION |
| `goal` | text | not null |
| `status` | varchar(32) | check TaskStatus |
| `control_mode` | varchar(24) | READ_ONLY/COLLABORATIVE/DELEGATED |
| `execution_contract` | jsonb | not null |
| `completion_criteria` | jsonb | not null |
| `budget_limit/budget_usage` | jsonb | not null |
| `priority` | integer | not null default 0 |
| `deadline` | timestamptz | nullable |
| `current_plan_revision` | integer | nullable；复合 FK `(tenant_id,task_id,current_plan_revision)` → plan 同 Task revision，DEFERRABLE INITIALLY DEFERRED |
| `current_root_execution_id/current_root_attempt_no` | varchar(128)/integer | 无 plan Task 的当前 TASK_ROOT 权威指针；与 current_plan_revision 互斥，复合 DEFERRABLE FK 到同 tenant/task TASK_ROOT |
| `checkpoint` | jsonb | not null default `{}` |
| `root_result_ref/completion_evidence_ref` | jsonb | nullable；唯一根结果与完成证据权威引用 |
| `root_result_plan_revision/root_result_node_id/root_result_execution_id` | integer/varchar/varchar | nullable；TASK_NODE 根结果完整来源 |
| `root_result_attempt_no/root_result_generation/root_result_fencing_token` | integer/bigint/bigint | nullable；结果 CAS 身份 |
| `status_code/status_reason` | varchar(64)/text | nullable |
| `version` | bigint | not null default 0 |
| `created_at/updated_at` | timestamptz | not null |

约束与索引：

- `UNIQUE (tenant_id,task_id)` 供所有 tenant-scoped 复合 FK 引用；物理 `task_id` 可继续作全局 PK。
- partial unique `(tenant_id, origin_execution_id) WHERE origin_execution_id IS NOT NULL`，并以 `(tenant_id,origin_execution_id)` DEFERRABLE INITIALLY DEFERRED FK 引用同 tenant Execution；事务额外校验 scope=DIRECT、status=PROMOTED。
- promotion Task 要求 `origin_execution_id/origin_run_id/origin_correlation_id/origin_input_ref/public_context_ref` 成组非空；数据库权限/不可变触发器或只允许 insert 的写模型禁止后续更新。事务从已锁定 origin DIRECT Execution 与不可变 input/prompt envelope 读取，不接受 command body 覆盖；`origin_input_ref/public_context_ref` 引用对象必须 tenant-scoped 且不可变。
- current plan 使用 `(tenant_id,task_id,current_plan_revision)` DEFERRABLE FK 引用 `ai_task_plan(tenant_id,task_id,revision)`；删除/切 revision 前必须先显式清空或改写引用，`ON DELETE RESTRICT`。
- current root 使用 `(tenant_id,task_id,'TASK_ROOT',current_root_execution_id,current_root_attempt_no)` 的 DEFERRABLE 复合约束/约束触发器引用同 tenant/task/scope/attempt Execution；CHECK 要求 `current_plan_revision` 与 current-root 两字段组恰有一组非空于可运行持久 Task。创建 fresh attempt 时同事务 CAS 改写指针，`ON DELETE RESTRICT`。
- 根结果来源对 TASK_NODE 通过复合 FK/约束触发器校验同 tenant/task/current plan/node/execution；TASK_ROOT 要求 plan/node 为空且来源 execution/attempt 等于 current-root 指针。CAS 条件至少为 `root_result_ref IS NULL AND version=?`，并按 scope 额外匹配 current plan 或 current root。
- check status/control_mode/owner_type、priority 与预算非负。
- index `(tenant_id, owner_type, owner_id, updated_at DESC)`。
- index `(tenant_id, owner_id, conversation_id, updated_at DESC)`。
- partial index `(tenant_id, status, priority DESC, updated_at) WHERE status NOT IN terminal`。

### `ai_task_plan`

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `plan_id` | varchar(128) | PK |
| `tenant_id` | varchar(128) | not null；所有子表复合 FK 安全边界 |
| `task_id` | varchar(128) | 与 tenant_id 组成 FK → ai_task，ON DELETE RESTRICT |
| `revision` | integer | >=1 |
| `status` | varchar(24) | DRAFT/FROZEN/SUPERSEDED |
| `max_parallelism` | integer | 1..system hard limit |
| `aggregation_contract` | jsonb | not null |
| `failure_policy` | varchar(24) | FAIL_TASK/PAUSE_TASK/COMPENSATE |
| `compensation_contract` | jsonb | failure_policy=COMPENSATE 时 not null，随 FROZEN 不可变 |
| `graph_hash` | varchar(64) | frozen 时 not null |
| `frozen_by_type/id` | varchar | nullable until frozen |
| `frozen_at` | timestamptz | nullable until frozen |
| `freeze_attempt_no` | integer | not null default 0 |
| `last_freeze_failures` | jsonb | not null default `[]`，有序 reason 列表 |
| `last_freeze_rejected_by_type/id` | varchar | nullable |
| `last_freeze_rejected_at` | timestamptz | nullable |
| `version` | bigint | optimistic lock |
| timestamps | timestamptz | not null |

唯一 `(tenant_id,plan_id)`、`(tenant_id,task_id,revision)` 与 `(tenant_id,task_id,plan_id,revision)`；plan 以 `(tenant_id,task_id)` FK 引用 Task。partial unique `(tenant_id,task_id) WHERE status='FROZEN'` 仅允许当前 frozen revision。若需保留历史 frozen plan，则在 supersede 同事务先改旧状态再 freeze 新 revision。Task.currentPlanRevision 的反向 FK 为 DEFERRABLE INITIALLY DEFERRED，双方删除均 RESTRICT。索引 `(tenant_id,task_id,status)`。

### `ai_task_node`

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `node_id` | varchar(128) | PK |
| `tenant_id` | varchar(128) | not null；与 task/plan/node 组成复合身份 |
| `task_id/plan_id/plan_revision` | varchar/varchar/int | not null，FK |
| `node_key` | varchar(128) | 安全键 |
| `kind` | varchar(24) | COORDINATOR/EXECUTOR/EVALUATOR/AGGREGATOR |
| `instruction` | text | not null |
| `status` | varchar(32) | check TaskNodeStatus |
| assignment refs | varchar/jsonb | assistant/agent/role/skill/tool allowlist/model policy |
| `input_contract/output_contract` | jsonb | not null |
| `result_ref/checkpoint` | jsonb | nullable/not null |
| `current_execution_id` | varchar(128) | nullable |
| `current_attempt` | integer | >=0 |
| `version` | bigint | optimistic lock |
| timestamps | timestamptz | not null |

复合约束：

- `(tenant_id,task_id,plan_id,plan_revision)` FK → plan 同一 revision。
- unique `(tenant_id,plan_id,node_key)`、`(tenant_id,task_id,plan_revision,node_key)`。
- unique `(tenant_id,plan_id,node_id)`、`(tenant_id,task_id,node_id)`、`(tenant_id,task_id,plan_id,plan_revision,node_id)`，供边、Execution 与 currentExecution 复合 FK 引用。
- `current_execution_id` 与完整 node identity 组成 DEFERRABLE INITIALLY DEFERRED FK，引用 Execution 的 `(tenant_id,task_id,plan_id,plan_revision,node_id,execution_id)`；`ON DELETE RESTRICT`，purge/attempt 切换前先清空或 CAS 改写。事务同时校验其为最高 currentAttempt。
- 索引 `(tenant_id,task_id,plan_revision,status)`、`(tenant_id,plan_id,status)`。

### `ai_task_dependency`

字段：`tenant_id`、`task_id`、`plan_id`、`plan_revision`、`predecessor_node_id`、`successor_node_id`、`dependency_type`（REQUIRED/OPTIONAL）。主键 `(tenant_id,plan_id,predecessor_node_id,successor_node_id)`；check 前后节点不同；前驱和后继分别以 `(tenant_id,task_id,plan_id,plan_revision,node_id)` 复合 FK 引用 `ai_task_node`，从数据库层保证同 tenant、同 Task、同 plan revision。索引 `(tenant_id,plan_id,successor_node_id)` 与 `(tenant_id,plan_id,predecessor_node_id)`；删除 node/plan 时 `ON DELETE RESTRICT`，先显式删除边。

SQL 无法用简单 check 保证无环。freeze 事务在加锁后以确定性拓扑排序验证节点唯一、引用存在、无自边、无环、input/aggregation 引用合法，再持久化 `graph_hash`。worker 只执行 FROZEN 且 hash 匹配的图。

### `ai_task_execution`

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `execution_id` | varchar(128) | PK |
| `scope` | varchar(16) | DIRECT/TASK_ROOT/TASK_NODE |
| `tenant_id/user_id/conversation_id/session_id/run_id` | varchar(128) | not null |
| `correlation_id` | varchar(128) | not null，创建后不可变 |
| `input_ref/public_context_ref` | jsonb | not null，不可变公开输入谱系；不得含 CoT、scratchpad、AgentState |
| `task_id/plan_id/plan_revision/node_id` | varchar/varchar/int/varchar | 按 scope nullable；TASK_NODE 全部非空并复合 FK 到同一 node |
| `parent_execution_id` | varchar(128) | nullable；与 tenant 组成 self FK |
| `predecessor_execution_id` | varchar(128) | nullable self FK |
| `attempt_no` | integer | >=1 |
| `purpose` | varchar(16) | NORMAL/COMPENSATION；DIRECT/TASK_ROOT 仅 NORMAL |
| `compensates_execution_id` | varchar(128) | COMPENSATION 必填，引用同 tenant/task/plan/node 的已接受 NORMAL Execution |
| `status` | varchar(32) | check ExecutionStatus |
| `promotion_state` | varchar(16) | DIRECT: ELIGIBLE/PROMOTING/PROMOTED/REJECTED；其他 NOT_APPLICABLE |
| `side_effect_epoch` | bigint | not null default 0；DIRECT side-effect gate 单调 CAS |
| `owner_type/owner_id` | varchar | frozen snapshot |
| `profile_revision/profile_hash/profile_ref` | bigint/varchar/jsonb | not null |
| `state_slot_key/state_slot_generation/state_schema_version` | varchar/bigint/int | resume identity |
| `control_request_id/control_generation/control_action` | varchar/bigint/varchar | pause/cancel 握手；普通结果须匹配 control generation |
| terminal code/reason | varchar/text | nullable |
| `version` | bigint | optimistic lock |
| lifecycle timestamps | timestamptz | created not null，其他 nullable |

约束与索引：

- unique `(tenant_id,execution_id)`；scope check：DIRECT 的 task/plan/node 全空且 attemptNo=1；TASK_ROOT 仅 task 非空；TASK_NODE 的 task/plan/revision/node 全非空。`correlation_id/input_ref/public_context_ref` 非空且创建后不可变。
- TASK_ROOT 以 `(tenant_id,task_id)` FK 引用 Task；TASK_NODE 以 `(tenant_id,task_id,plan_id,plan_revision,node_id)` 复合 FK 引用 Node，数据库保证 node 属于同一 Task/Plan。
- unique `(tenant_id,task_id,plan_id,plan_revision,node_id,execution_id)` 与 `(tenant_id,task_id,plan_id,plan_revision,node_id,purpose,attempt_no)`，供 Node.currentExecution、TASK_NODE predecessor 与 compensation 引用。
- TASK_ROOT 提供 unique `(tenant_id,task_id,scope,execution_id,attempt_no)`，并建立 partial unique `(tenant_id,task_id,attempt_no) WHERE scope='TASK_ROOT'`；Task.currentRoot 通过 DEFERRABLE 复合约束/约束触发器引用同 task 的 TASK_ROOT execution+attempt。
- `predecessor_execution_id`：DIRECT 必须为空；TASK_NODE 通过 `(tenant_id,task_id,plan_id,plan_revision,node_id,purpose,predecessor_execution_id)` DEFERRABLE FK/约束触发器引用同 node、同 purpose Execution；TASK_ROOT 通过 `(tenant_id,task_id,scope,predecessor_execution_id)` DEFERRABLE FK/约束触发器引用同 Task 的 TASK_ROOT。fresh attempt 事务统一校验 predecessor.attemptNo+1=current.attemptNo，禁止跨 purpose、scope、task 或跳号。
- `compensates_execution_id` 仅 purpose=COMPENSATION 可用，并以 tenant/task/plan/node 复合 FK 引用 purpose=NORMAL 且已接受结果/receipt 的 Execution；NORMAL 必须为空。DIRECT/TASK_ROOT 的 purpose 固定 NORMAL。
- CHECK/受限写端保证 DIRECT 初始 `promotion_state=ELIGIBLE,side_effect_epoch=0`，只允许 gate 协议单调迁移和递增；非 DIRECT 固定 `NOT_APPLICABLE`。`PROMOTING/PROMOTED` 下禁止新 side-effect intent。
- `parent_execution_id` 通过 `(tenant_id,parent_execution_id)` self FK 保证同 tenant；它仅表达分解，不参与 attempt 约束。
- parent/predecessor 不等于自身；所有 FK `ON DELETE RESTRICT`。
- partial unique `(tenant_id,task_id,plan_id,plan_revision,node_id) WHERE status IN active states`。
- partial unique `(tenant_id,task_id) WHERE scope='TASK_ROOT' AND status IN active states`；fresh attempt 事务必须先终结/替代旧 root 并 CAS Task.currentRoot，再插入/启用新 root，循环 FK 使用 DEFERRABLE。
- index `(tenant_id,conversation_id,created_at DESC)`、`(tenant_id,task_id,created_at)`、`(tenant_id,parent_execution_id)`、`(tenant_id,predecessor_execution_id)`。

### `ai_task_dispatch`

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `dispatch_id` | varchar(128) | PK |
| `tenant_id/execution_id` | varchar | not null；复合 FK 到同 tenant Execution |
| `status` | varchar(16) | PENDING/CLAIMED/DONE/CANCELED |
| `next_run_at` | timestamptz | not null |
| `lease_owner/lease_until` | varchar/timestamptz | CLAIMED 时同时非空 |
| `generation/fencing_token` | bigint | monotonic，>=0 |
| `delivery_attempts` | integer | >=0 |
| `last_error` | text | nullable |
| `version` | bigint | optimistic lock |
| timestamps | timestamptz | not null |

`UNIQUE(tenant_id,dispatch_id)` 供 event 复合 FK 引用；`(tenant_id,execution_id)` FK → `ai_task_execution(tenant_id,execution_id)`，`ON DELETE RESTRICT`；Dispatch 不存 taskId/nodeId/planRevision，查询和 claim 从 Execution scope 推导身份，避免冗余漂移：DIRECT 仅校验 Execution/profile/gate；TASK_ROOT join Task 且要求 currentPlanRevision 为空；TASK_NODE join Node→Plan→Task。partial unique `(tenant_id,execution_id) WHERE status IN ('PENDING','CLAIMED')`；due queue partial index `(next_run_at,dispatch_id) WHERE status='PENDING'`；lease recovery partial index `(lease_until) WHERE status='CLAIMED'`。`generation` 对同 Execution 从 1 严格递增；`fencing_token` 由数据库全局序列 `ai_task_fence_seq` 分配，禁止应用计算或从 event/Execution 恢复。claim 使用 `FOR UPDATE SKIP LOCKED` + expected version/generation CAS，并在同事务按 scope 校验可运行状态、current plan 与并行度；renew/release/complete 只接受当前 active dispatch identity，零行即 stale。

普通简单只读 DIRECT 不创建 TaskDispatch，满足 AC1。只有 DIRECT 执行需要从请求内执行权转为 durable worker 且仍获准继续时，才在任何 side-effect intent 前创建唯一 active Dispatch并 fence 原请求内 caller；若已判定需要持久目标则优先 promotion，不能为了绕过 promotion 建 DIRECT Dispatch。

### 辅助表改造

- `ai_side_effect_intent`：保存 executionId/dispatchId/sideEffectEpoch/dispatch generation+fence 快照/providerIdempotencyKey/requestHash/status/version；对 `(tenant_id,execution_id,provider_idempotency_key)` 唯一，复合 FK 到 Execution/Dispatch 均 RESTRICT。DIRECT intent 只能由锁同一 Execution 的 gate 事务创建；NORMAL/COMPENSATION Task intent 同样先持久化 intent+provider key+outbox，再允许外部调用。
- `ai_task_event`：`task_id`、`plan_id`、`plan_revision`、`node_id`、`execution_id`、`dispatch_id` 均按下文 aggregate 身份矩阵可空；`attempt_no/execution_sequence/dispatch_generation/fencing_token` 仅在相应身份存在时可用。保留全局 `event_offset`，新增所有事件必填的 `aggregate_sequence`、`producer_idempotency_key`、event/aggregate type、aggregate id/version 与 public payload。禁止为 Task/Plan 事件制造占位 Execution。
- profile snapshot/prompt envelope 以 executionId 为主；direct 时 taskId nullable。
- approval/clarification/input/receipt 增加 `(taskId,planId,planRevision,nodeId,executionId,attemptNo,stateSlotGeneration,dispatchId,dispatchGeneration,fencingToken)`；后三者仅为接受事务快照，操作时必须 join active TaskDispatch 重验。
- `ai_task_transition_outbox` 直接重建为 canonical event outbox；唯一 `event_id`，包含 publish status、attempt、nextAttemptAt、lease。不能与旧 outbox 双写。
- `ai_task_recovery_command` 的恢复真理并入 execution/dispatch command，不保留“最新命令”旁路。

### 跨表删除与保留规则

业务与审计 FK 默认 `ON DELETE RESTRICT/NO ACTION`，关键环形引用使用 `DEFERRABLE INITIALLY DEFERRED`，禁止 cascade 静默删除审计证据。hard purge 只允许在 retention 到期、Task 已终态且合规策略明确后执行。

**purge 闭包**只包含以目标 taskId 为所有权根的 Plan、Node、Dependency、TASK_ROOT/TASK_NODE Execution、它们的 Dispatch、Task-scoped interrupt/intent/receipt/event/outbox/projection。`Task.originExecutionId` 指向的独立 DIRECT Execution、其 Run/ConversationMessage、Conversation 公开历史、prompt/input 证据不属于闭包，默认保留且绝不连带删除；purge 只清空 Task→origin 引用，并按各自 retention 对跨保留域公开引用做匿名化/令牌化。只有单独针对 DIRECT/Conversation 的合规命令才可清除它们。

预归档 manifest 必须列出 taskId、closure graph/hash、每类行数/主键摘要、保留的 originExecutionId/Conversation 证据、将清空或匿名化的引用、未发布 outbox、外部引用扫描结果、archive hash、策略版本和操作者。发现闭包外的 automation/learning/billing/legal-hold/其他 Task/Execution 引用时默认中止；仅允许依据已审核 retention 规则先合规解除/匿名化引用后重试，禁止 CASCADE 或临时禁 FK。

若 `Task.originExecutionId`、Conversation 证据或其他必须保留对象以 RESTRICT 反向引用 Task，则优先在短事务内合规解除/匿名化该引用；不能解除时不得 hard delete Task，改为不可执行 tombstone（清除可删除 payload、保留最小身份/hash/manifestId）。事件采用以下二选一策略：

- **可删除事件**：先将 canonical event 与公开 payload 归档到无在线 FK 的只读合规存储并校验清单/hash，再删除在线 event，之后才允许删除被其引用的实体。
- **必须在线保留事件**：禁止 hard delete Task/Plan/Node/Execution/Dispatch；仅匿名化 public payload 与主体标识，并保留不可执行 tombstone 聚合。不得临时禁用 FK。

可删除事件策略下，每个 Task 的在线 purge 使用一条受审计命令；预归档在事务外完成，归档确认后进入一个短、确定性的在线删除事务。顺序如下：

| 顺序 | 删除/改写对象 | FK 行为与失败处理 |
|---|---|---|
| 1 | Task→PURGING，取消 PENDING、请求关闭 CLAIMED，提升 execution/dispatch fence并停止该 Task relay | 新 claim/result/event append 闸门关闭；未全部 fence 则中止，不开始删除 |
| 2 | 归档 event、receipt 与公开审计摘要并持久化 archive manifest | 事务外 I/O；hash/数量不一致即中止，在线数据不变 |
| 3 | notification delivery、projection cursor/cache、event publish delivery | 先删 event/outbox 的消费者侧引用；`RESTRICT` 失败则整段在线事务回滚 |
| 4 | canonical event outbox、notification outbox、其他引用 event_id 的 outbox | online event 删除前清除引用；未发布事件须进入 archive manifest，不得静默丢弃 |
| 5 | `ai_task_event` 在线行与 `ai_execution_sequence` | **必须早于任何聚合/Execution/Dispatch 删除**；事件 FK 为 `ON DELETE RESTRICT`，删除失败则事务回滚 |
| 6 | interrupt resolution/input、approval/clarification/recovery、tool/connector receipt、authorization、profile snapshot、prompt/input envelope、automation/learning 外围引用 | 按各自 child→parent 顺序显式删除；若合规要求保留则改走 tombstone 策略 |
| 7 | `ai_task_dispatch` | 仅 DONE/CANCELED 且 fence 已关闭；FK→Execution 为 RESTRICT |
| 8 | 清空 `Task.currentRootExecutionId/currentRootAttemptNo`、root-result Execution 来源、`Task.originExecutionId` 与 `Node.currentExecutionId` | 在 DEFERRABLE 事务内打断 Task/Node↔Execution 环；归档 manifest 已保留公开谱系 |
| 9 | `ai_task_execution`（含 promotion origin DIRECT 与 predecessor/parent 子链） | 先按后继到前驱删除或先清空 self FK；任何外部 Execution 引用存在则中止 |
| 10 | `ai_task_dependency` | 必须先于 Node；前后端 FK 均 RESTRICT |
| 11 | `ai_task_node` | currentExecution 已清空且 event 已删除 |
| 12 | 清空 `Task.currentPlanRevision`，删除 `ai_task_plan` | 先断 Task↔Plan DEFERRABLE 环；Plan FK RESTRICT |
| 13 | `ai_task` | 仅全部反向引用为零后删除；提交 purge manifest id/完成状态到合规审计存储 |

在线步骤 3–13 对单 Task 在同一数据库事务中完成；任何一步报错整体回滚，归档 manifest 保持“已归档、未清除”并可安全重试。大 Task 若无法满足短事务上限，禁止拆成暴露半删除聚合的普通请求；必须在维护窗口按隔离分区/tenant 批次执行。正常状态转换和 plan supersede 不做 hard delete。

## 事务、outbox、lease 与 fencing

每个状态命令只做一个短事务。通用固定锁序如下；DIRECT promotion/intent 特例都从同一 Execution 行开始，避免反向加锁：

```text
Task → current Plan → Nodes（nodeId 升序）→ Execution → active Dispatch → HITL/intent/receipt
→ 校验 tenant/owner/业务 version/attempt/stateSlotGeneration
→ 从 active TaskDispatch 校验 dispatchId/generation/fencingToken/lease（不读 Execution/event 快照作当前值）
→ 调用领域转换
→ 写业务行
→ append canonical event
→ 写 event outbox / notification or side-effect intent
→ commit
```

提交后：

- outbox relay 发布 canonical event；至少一次投递，消费者按 eventId 幂等。
- `TaskDispatchSignalPort` 唤醒 worker；worker 仍以 DB due queue 为准。
- worker claim 后调用 Agent/模型/Tool；任何外部调用不得持数据库事务。
- Tool/connector/compensation 在调用前必须先持久 side-effect intent、provider idempotency key、event/outbox；worker 调用前再次复核 Execution gate（DIRECT）及 active Dispatch epoch/generation/fence。重试先查同 key receipt；已确认不重复，UNKNOWN/IN_FLIGHT fail-closed。
- worker 的 state load/save、interrupt ACK、receipt 与结果提交必须锁并匹配当前 active Dispatch。stale 结果拒绝并追加安全审计事件，不更新 node/result，不释放后继。
- lease renew 只延长当前 generation；旧 owner 无权 release/complete 新 active Dispatch。

并行上限在 claim 事务中计算当前 plan 的 CLAIMED/RUNNING 节点数；不能先查后写。必要时锁 plan 行作为低粒度并行度闸门。

## 控制命令握手与传播顺序

### Pause 与 same-attempt resume

pause 不是“先杀 worker 再尝试保存”，而是受控两阶段握手：

1. **pause-request 事务**：按锁顺序将 Task 置 `PAUSING`，活跃 TASK_NODE 的 Node/Execution 或 TASK_ROOT Execution 记录 `pauseRequestId/controlGeneration`，取消尚未 claim 的 Dispatch；已 claim Dispatch 暂时保留当前 fence，只允许提交匹配 pauseRequestId 的 state-saved ACK，普通完成/结果提交因 controlGeneration 已变化而拒绝。该事务提交 `task.pause-requested`、`execution.pause-requested` 与 outbox 后立即返回 202。
2. **提交后中断**：outbox/worker 调用 `interruptPreservingState(executionId, pauseRequestId, currentFence)`；Harness 在当前 state slot 保存 AgentState/checkpoint，保存成功后返回含 state hash/schema/slot generation 的 ACK。禁止 controller 手工 subscribe，也禁止只用 `takeUntil` 截断。
3. **pause-ack 事务**：校验 request、execution、state slot、旧 claim fence 后，Execution 与对应 Node（TASK_NODE）→PAUSED；TASK_ROOT 则以 Task.currentRootExecutionId/currentRootAttemptNo 校验当前性。随后关闭旧 TaskDispatch，并按单调分配规则创建/预备新 generation 的 active Dispatch 以 fence 旧 worker；全部活跃分支均 ACK/关闭后 Task→PAUSED，追加 `execution.state-saved`、`execution.paused`、`task.paused`。
4. **resume 事务**：仅 PAUSED Task 可恢复。完整身份与状态校验通过则复用 executionId/sessionId/attempt/state slot，创建新 Dispatch generation/fence；校验失败则按 fresh-attempt 合同只替换受影响 TaskNode 或当前 TASK_ROOT。Task/Node 恢复 RUNNING/READY 后再重算 readiness；TASK_ROOT 以 Task current-root CAS 恢复 RUNNING，不存在 DAG readiness。

若 state 保存超时、worker 丢失、ACK hash 不匹配或旧 worker 越权完成，系统关闭旧 Dispatch、提升 fence，并将该 Execution 标记 `SUPERSEDED`（终态失败则保持 FAILED）；Task 仍安全进入 PAUSED。TASK_NODE 记录 `FRESH_ATTEMPT_REQUIRED`，TASK_ROOT 则在 Task checkpoint/statusCode 记录同一机器码并保留 current-root predecessor。后续 resume 只能为该 Node 或 root 创建 fresh attempt，从 receipt/checkpoint 重建；不能猜测加载旧 AgentState。

### Cancel、take-over 与 hand-back

- **cancel**：事务 Task→CANCELING，取消 PENDING Dispatch、阻止新 claim/readiness、给 CLAIMED/RUNNING Execution 写 cancel request；提交后调用 runtime cancel。每个关闭 ACK 事务 fence 旧 worker并将 Execution/Node CANCELED；全部关闭后 Task→CANCELED。超时也提升 fence并审计 `cancel.force-fenced`，绝不恢复派发。
- **take-over**：先执行与 pause 相同的保存握手；PAUSED 后在独立短事务改变 owner/controlMode 并记录 takeover event。由于责任主体变化，原 Execution 不得 same-attempt 继续；TASK_NODE 标记 fresh attempt required，TASK_ROOT 在 Task checkpoint 标记并保留 current-root predecessor。人工处理本身不伪装为 Agent Execution。
- **hand-back**：只允许已接管且 PAUSED 的 Task；先冻结新的 owner/profile/authorization boundary，再为受影响 TaskNode 或当前 TASK_ROOT 创建 fresh Execution attempt 和新 sessionId，最后 enqueue。TASK_ROOT 必须原子更新 Task.currentRoot 指针；不得复用接管前 AgentState。
- 所有命令均以 `(tenantId, taskId, commandId/idempotencyKey, expectedVersion)` 幂等；pending/claimed Dispatch 与活跃 Execution 的传播次序固定为“先持久阻断新执行权 → 提交后中断运行时 → ACK 事务关闭并 fence → 必要时新 generation”。

## TaskAnalysis 与创建策略

```java
public record TaskAnalysis(
        Decision decision,
        Set<ReasonCode> reasons,
        boolean requiresPersistence,
        boolean requiresStructuredHitl,
        boolean hasSideEffectPotential,
        PlanningMode planningMode,
        double confidence,
        AnalysisSource source,
        FrozenPolicyBoundary policyBoundary) {
    public enum Decision { DIRECT, TASK, TEAM }
    public enum PlanningMode { NONE, SINGLE_AGENT, DAG }
    public enum AnalysisSource { DETERMINISTIC, MODEL }
}
```

确定性规则优先：

- 问候、简单只读问答、自然澄清 → DIRECT。
- 客户端明确 TASK/TEAM 产品入口、后台长任务、可恢复暂停、外部副作用、审批、结构化澄清、多节点 → TASK/TEAM。
- 只有开放复杂目标且确定性规则无法判断时调用模型；置信度不足转自然澄清或人类，不默认升级权限。
- 客户端 mode/complexity 仅表达入口意图，不是授权事实。
- analysis 结果必须与冻结 policy boundary 求交，不能扩大 owner、controlMode、Role、Skill、Tool allowlist、tenant、预算和 grant。

direct Execution 必须在调用模型前先持久化最小 execution 事实和 event；若判定 Task，则直接创建 Task。若执行中才发现结构化持久边界，必须在第一次外部副作用前 promotion。

## DIRECT 副作用授权闸门

`Execution.promotionState/sideEffectEpoch` 是 DIRECT 是否仍可创建新外部副作用 intent 的唯一授权事实；receipt、event、内存标志或 worker 本地状态都不能开放闸门。副作用 intent 表至少保存 `intentId,tenantId,executionId,dispatchId,sideEffectEpoch,dispatchGeneration,fencingToken,providerIdempotencyKey,status(PREPARED|IN_FLIGHT|CONFIRMED|UNKNOWN|REJECTED),requestHash,version`，并以 `(tenantId,executionId,providerIdempotencyKey)` 唯一。

**创建 side-effect intent 的短事务**固定锁 `DIRECT Execution SELECT FOR UPDATE → active TaskDispatch SELECT FOR UPDATE`，仅当 `promotionState=ELIGIBLE` 且 expected sideEffectEpoch 与当前值相等、Dispatch 当前有效时，CAS `sideEffectEpoch=sideEffectEpoch+1`，以新 epoch 持久化 intent、provider idempotency key、canonical event 和 outbox 后提交。外部调用绝不能发生在该事务中，也不能先调用后补 intent。

worker 消费 outbox 后、每次 provider 调用/状态查询前重新读取并锁定 Execution+active Dispatch，要求 `promotionState=ELIGIBLE`、intent.epoch 等于 Execution.sideEffectEpoch、dispatch generation/fence/lease 当前且 intent=PREPARED；随后才以已持久化 provider key 调用。任一不匹配、已有 UNKNOWN/IN_FLIGHT、无法证明调用未发生或 fence 失效均 fail-closed，不得生成新 key、不得自动重放。

promotion 使用同一互斥点：`SELECT FOR UPDATE` 锁 origin DIRECT Execution。仅 `promotionState=ELIGIBLE` 且不存在任何 PREPARED/IN_FLIGHT/CONFIRMED/UNKNOWN intent 或 receipt 时，先 CAS 为 `PROMOTING` 并递增 sideEffectEpoch，使所有旧 worker/intent 失效；随后在同一事务创建 Task/Plan/Node/Execution/Dispatch 与事件，最后置 `PROMOTED`。`PROMOTING/PROMOTED` 永久关闭 DIRECT side-effect gate。发现任何既有、未知或 in-flight intent 一律写 `REJECTED`/拒绝审计并 fail-closed；promotion 不等待、不猜测、不接管其副作用。

崩溃恢复时，持有 `PROMOTING` 的事务要么整体回滚到 ELIGIBLE，要么提交 PROMOTED，不存在可观察半态；提交后 worker 只能按 TaskNode 的新 Dispatch 工作。该协议使 promotion 与 side-effect intent 创建在同一 Execution 行上严格串行。

## Promotion

promotion 在一个短事务内：

- 按上述 gate 协议锁 origin DIRECT Execution；只有 `promotionState=ELIGIBLE`、无任何已有/未知/in-flight intent 或 receipt 才能 ELIGIBLE→PROMOTING→PROMOTED。任何不能证明“零副作用”的情况均以 `PROMOTION_AFTER_SIDE_EFFECT` fail-closed，不创建 Task/Plan/Node/Dispatch、不转移执行权；客户端不能覆盖判断。
- “receipt 证明的安全点转最小 plan”仅适用于**已经存在 Task 的 TASK_ROOT→TASK_NODE 内部形态转换**，绝不适用于 DIRECT→Task promotion。若产品未来要求副作用后 promotion，必须先修改 requirement/AC12 并重新审核。
- 以 `(tenantId, originExecutionId)` 唯一约束创建 Task；冲突时读取已创建 Task 并返回同一结果。`PromoteExecutionCommand.idempotencyKey` 只标识 command receipt；每个 canonical event 的 `producerIdempotencyKey` 稳定派生为 `sha256(tenantId|originExecutionId|PROMOTION|eventType|aggregateType|aggregateId)`，不依赖随机新实体或投递次数。拒绝事件 `execution.promotion-rejected` 使用同一派生规则，因此 command/outbox 重放不会重复追加。
- 从已锁定 origin DIRECT Execution 及其不可变 prompt/input envelope 服务端读取 conversationId、runId、correlationId、inputRef、publicContextRef；command body 不接受这些字段。Task 写入 `originRunId/originCorrelationId/originExecutionId/originInputRef/publicContextRef`，新 TASK_NODE Execution 复制同一不可变谱系引用。
- 总是创建 revision=1 的最小一节点 TaskPlan、稳定 TaskNode 与新的 TASK_NODE Execution；若分析已产生多节点 DAG，则该节点集合可以大于一。promotion 不得创建无 plan 的 TASK_ROOT。
- 最小节点继承冻结 owner/profile/tool/authorization 的交集，不继承隐式状态；其 nodeId 成为后续 structured interrupt 与恢复的精确身份。
- 原 direct Execution → PROMOTED。
- 新 Execution 使用独立 executionId 与 sessionId；保留上述 conversation/run/correlation/原始输入/公开上下文谱系。
- 写 `execution.promoted`、`task.created`、`plan.frozen`、`node.ready`、outbox 与 dispatch；各事件使用稳定 producer key。
- 不复制 chain-of-thought、scratchpad、未纳入 profile 的 AgentState 或隐式历史。

promotion 之后原 direct stream 只投影 promotion/accepted 事件，后续长任务由 task event stream 异步提供，HTTP/SSE 线程不等待完成。

## 自然澄清与结构化澄清

### 自然澄清

DIRECT Execution 输出普通 assistant message 后 COMPLETED；不创建 Task、不发 interrupt、不保留 AgentState 作为下一轮恢复点。下一条用户消息创建新 DIRECT Execution，使用公开 Conversation history 重新 TaskAnalysis。

### 结构化澄清与审批

必须先有 Task 和 TaskNode；若源于 DIRECT，先 promotion 并冻结最小一节点 plan。若无 plan 的 TASK_ROOT 执行中首次发现 structured HITL，则不得发 `nodeId=null` interrupt：仅在首次副作用前或 receipts 证明的安全点创建最小一节点 plan，以新 TASK_NODE Execution 接管并将 root Execution 标记 SUPERSEDED；无法证明安全点时 fail-closed 暂停并转人工。interrupt 必须绑定：

- taskId、planRevision、nodeId、executionId、attemptNo、sessionId。
- owner type/id、authorization boundary。
- profile revision/hash、stateSlotKey/generation/schemaVersion。
- dispatch generation/fencing。

短事务提交 node/execution/task waiting 状态、clarification/approval、canonical event 和 outbox。提交成功后调用统一 `interruptPreservingState`，要求 Harness 走与显式 PAUSE 相同的“保存成功后停止”路径；禁止仅靠 `takeUntil` dispose。输入按 interruptId 和完整身份加载，禁止查“最近 waiting”。

## same-attempt resume 与 fresh attempt

### 恢复决策矩阵

| 条件 | same attempt | fresh attempt |
|---|---|---|
| interrupt 为 approval/structured clarification/PAUSE/graceful shutdown | 可 | 状态无效时必须 |
| task/node/execution/attempt/session 全匹配 | 必须 | 任一不匹配 |
| owner/责任主体与授权边界不变 | 必须 | 变化 |
| frozen profile revision/hash/tool surface 不变 | 必须 | 变化 |
| state/checkpoint 存在、完整、schema 兼容 | 必须 | 缺失/损坏/不兼容 |
| stateSlotGeneration 匹配 | 必须 | 不匹配 |
| dispatch fence 当前有效 | 恢复命令必须校验 | 旧 generation 不迁移 |
| Execution 已终态失败或策略显式 fresh retry | 禁止 | 必须 |

same attempt：

- 复用 executionId、sessionId、attemptNo、stateSlotKey/stateSlotGeneration。
- 创建新 dispatch generation/fencing，但不改变 state slot identity。
- load AgentState 前完整校验 task/node/execution/owner/profile/state generation；load/save/result 时再次校验 current fence。
- 将 `requireNoHiddenPersistentHistory` 改为 resume-aware：DIRECT/fresh attempt 仍拒绝隐藏持久历史；validated same-attempt resume 只允许匹配槽中的非空历史。

fresh attempt 通用规则：

- 只处理受影响 execution scope；原 FAILED 保持终态，原等待/暂停 Execution 标记 SUPERSEDED。
- 创建 `attemptNo + 1`、新 executionId、新 sessionId、新 state slot；不迁移旧 AgentState，旧 slot 异步删除或隔离保留至审计 TTL。
- `predecessorExecutionId=oldExecutionId`；`parentExecutionId` 复制旧值以保持分解关系。
- 从对应 checkpoint、receipt、公开输入和 canonical event 重建；先读 receipt 再执行外部副作用。

TASK_NODE fresh attempt：

- 只替换受影响 Node，CAS `TaskNode.currentExecutionId/currentAttempt/generation/version`；已完成兄弟与未受影响活跃节点不重启。
- 完成后仅重新计算本节点后继 readiness；join 仍等待全部 REQUIRED predecessor。

TASK_ROOT fresh attempt：

- 仅适用于无 plan 的持久 Task 因 PAUSE/优雅停机后 state 缺失、损坏、不兼容，owner/授权/profile 变化，worker 丢失或策略明确 fresh retry；structured HITL 仍禁止。
- 在同一短事务锁 Task→旧 root Execution→Dispatch：验证 Task.currentPlanRevision 为空、currentRootExecutionId/attempt 指向旧 root、expectedVersion/generation/fence 匹配；关闭旧 dispatch 并提升 fence，旧非终态 root→SUPERSEDED（FAILED 保持），创建 `attemptNo+1` 的新 TASK_ROOT，predecessor 指向旧 root，CAS Task.currentRoot 指向新 execution/attempt，再 enqueue。
- 新 root 使用新 sessionId/state slot，不读取旧 AgentState；从 Task checkpoint、receipt、不可变 input/public context 重建。Task 中没有 Node，不创建伪 Node 或伪 plan，也不触发 DAG readiness。
- root 完成提交必须同时校验 Task.currentRootExecutionId/currentRootAttemptNo、Execution attempt/status、dispatch generation/fence、CompletionCriteria 和 `rootResultRef IS NULL`；Task 根结果 CAS 与 Execution COMPLETED 同事务恰好一次。旧 root/旧 generation 的迟到完成只写幂等 `execution.result-rejected(STALE_ROOT_ATTEMPT|STALE_GENERATION|STALE_FENCE)`，不覆盖结果或完成 Task。
- cancel/take-over/hand-back 只传播当前 root；历史 predecessor attempts 保持终态审计事实。因本次 root 恢复不存在兄弟节点重放问题；若后续内部转换为最小 plan，则先 fence/supersede current root，再清空 currentRoot 并设置 currentPlan，不能两种执行权并存。

### AgentState 槽与 fence 解耦

```text
stateSlotKey = hash(tenant, owner, executionId, sessionId, agentId)
stateSlotGeneration = immutable per Execution attempt
dispatchGeneration/fencingToken = increments per claim/resume
```

Redis value metadata 必须包含上述身份和最近接受的 dispatch 快照。dispatch fence 不进入 key；每次读写都必须查询并锁定 Execution 对应的唯一 active TaskDispatch，校验 dispatchId/generation/token/lease，event 或 Redis 中的快照不得开放权限。旧 worker 即使知道 key 也不能覆盖。若 AgentScope 扩展点无法实现该校验，则 fail-closed 创建 fresh attempt，不允许无校验加载。

## DAG 冻结与调度

freeze 前确定性校验：

- nodeKey 唯一、安全字符；所有边、input binding、completion evidence、aggregation 引用存在。
- 无 self-edge、无 cycle；拓扑排序覆盖所有节点。
- `maxParallelism` 在 1..系统硬上限内，且预算允许。
- assignment 的 Assistant/Role/Skill/Tool 是已发布冻结 revision，并且只做授权衰减。
- `AggregationContract` 必须声明且仅声明一个 `terminalResultNodeId`；该节点属于当前 plan，并且只能读取 `allowedInputNodeIds` 中声明的结果。

freeze command 在单一短事务中锁 Task→Plan，递增 `freezeAttemptNo` 后执行全部校验：

- **成功**：清空上次拒绝详情，写 graphHash/frozen actor/time，Plan→FROZEN，Task→READY，追加 `plan.frozen` 与 outbox；随后才允许 readiness/Dispatch。
- **失败**：Plan 保持 DRAFT，Task 保持或回到 PLANNING 并写 `statusCode=PLAN_FREEZE_REJECTED`；持久化有序、机器可读的 `lastFreezeFailures[{reasonCode,nodeId,relatedNodeId,safeDetail}]`、actor、time，reason code 至少包括 `CYCLE`、`SELF_EDGE`、`MISSING_NODE`、`DUPLICATE_NODE_KEY`、`INVALID_AGGREGATION_REFERENCE`、`INVALID_TERMINAL_RESULT_NODE`、`MAX_PARALLELISM_EXCEEDED`、`BUDGET_EXCEEDED`、`ASSIGNMENT_NOT_AUTHORIZED`。同事务追加 `plan.freeze-rejected` 与 outbox。
- 失败事务不得创建 Execution/Dispatch，不得调用 Agent、模型或 Tool；修正草稿后以新 commandId 再次 freeze。按 command idempotency key 重放只返回原拒绝结果，不重复追加事件。

SQL 无法用简单 check 保证无环；上述确定性拓扑校验与 graphHash 是 freeze 的权威门。worker 只执行 current revision、FROZEN 且 hash 匹配的图。

### 唯一 ready materialization 事务

`materializeReadyNode(MaterializeReadyNodeCommand)` 是首次执行、前驱完成释放、resume 后重算和 fresh retry 创建可执行节点事实的**唯一入口**；任何 scheduler、event consumer 或 command handler 都不得自行创建 Execution/Dispatch 或直接写 READY。

固定锁序为 `Task → current FROZEN Plan → REQUIRED predecessor Nodes（按 nodeId 排序）→ target Node → target current Execution（如有）→ active Dispatch（如有）`。单一短事务必须：

1. 重验 tenant、Task version/status 可运行、`currentPlanRevision`、Plan status/graphHash/contractHash，以及 target node 的 plan revision/version。
2. 逐一重验全部 REQUIRED predecessor 属于同一 frozen plan、状态 COMPLETED、result contract 有效且其结果来自当前已接受 attempt；OPTIONAL predecessor 仅按冻结 input contract 处理。
3. 首次物化要求 Node=PENDING、currentAttempt=0/currentExecutionId=null；fresh retry 要求显式 retry command、旧 Execution 为 FAILED/SUPERSEDED、无 active Dispatch，并携带期望 Node version/旧 executionId。二者都以 Node version CAS 获得一次性物化权。
4. 幂等创建 purpose=NORMAL 的 `TASK_NODE Execution`：首次 attempt=1；fresh retry attempt=old+1 且 predecessorExecutionId=old。创建时冻结 owner snapshot、profile revision/hash/tool surface、inputRef/publicContextRef 与新的 state slot；禁止运行时从可变 Task/Node 猜测。
5. CAS Node 的 `currentExecutionId/currentAttempt/status/version`，并创建该 Execution 唯一 `PENDING TaskDispatch`。Dispatch generation/fence 仅按 TaskDispatch 单调规则分配。
6. 在同一事务追加 `execution.created`、`node.ready`、`dispatch.enqueued` 等 canonical events 及对应 outbox；所有 producer key 从 materialization command/node/attempt 稳定派生。

Node 唯一 attempt 约束、唯一 active Dispatch partial index、Node CAS 与 producer idempotency key 四层共同保证并发调用、事件重放和数据库重试只物化一次。CAS/唯一约束冲突必须读取并返回已存在的同一 attempt；身份不一致则审计拒绝，禁止偷偷创建下一 attempt。

`maxParallelism` **不在 materialize 阶段消耗名额**：ready 节点可拥有 PENDING Dispatch。名额只在 claim 短事务中通过锁 Plan 行并统计同 plan 当前 CLAIMED/RUNNING 的 NORMAL 与 COMPENSATION（分别受各自冻结上限）执行来强制；超限保持 PENDING，不改变 generation/fence。claim 还必须重验 Task/Plan/Node/current Execution/active Dispatch，避免先查后写。

- Join 只有全部 REQUIRED predecessor 成功后由该事务恰好物化一次。
- 必需依赖不可重试失败时 successor→BLOCKED，并按 frozen failure/compensation policy 推进 Task。
- 恢复同 attempt 只创建新 active Dispatch，不经过 fresh retry 分支；fresh retry 必须经过本事务。

场景：

- 串行：A→B→C。
- 并行：A、B 均无依赖，受 maxParallelism 限制。
- join：C dependsOn A、B。
- fan-out/fan-in：A 完成释放 B/C/D；Aggregator dependsOn B/C/D。
- 恢复 C 不影响已完成 A/B；C 完成后重新计算 join，不能提前释放。

## 冻结补偿合同与执行

`failurePolicy=COMPENSATE` 时 `CompensationContract` 必填并随 plan freeze 不可变；其他策略可为空。freeze 必须验证每个 compensation step 引用当前 plan 中可补偿节点、handler/profile/tool 在原授权边界内、receipt 类型存在、input selector 只读公开 input/accepted result/receipt、provider idempotency 模板确定性、`maxParallelism` 在系统硬上限内。缺失或越权以 `INVALID_COMPENSATION_CONTRACT` 拒绝 freeze，不能运行到失败时再猜。

### 触发与可补偿集合

- REQUIRED 节点达到不可重试失败且 trigger 包含 `REQUIRED_NODE_FAILED`，或 cancel 时已有已确认副作用且包含 `TASK_CANCELED_AFTER_SIDE_EFFECT`，Task 原子进入 `COMPENSATING`，停止 normal materialization/claim 并 fence 未关闭的 normal Dispatch。
- 可补偿集合仅含：当前 frozen revision 中 NORMAL Execution 已成功、存在合同要求的 confirmed receipt、且 CompensationContract 为该 node 声明 step 的节点。无 receipt、只读节点、失败/未开始节点记为 `compensation.skipped`，禁止臆测调用撤销操作。
- 以成功执行子图的**逆拓扑**生成依赖：原图 `A→B` 且 A/B 均在可补偿集合时，必须先补偿 B 再补偿 A；不相关分支可并行。该依赖由 frozen graph+eligible set 确定，运行中不得由模型增删。

### 补偿 Execution、Dispatch 与副作用

补偿复用原 TaskNode 身份，但创建独立 `Execution(scope=TASK_NODE,purpose=COMPENSATION,compensatesExecutionId=<accepted NORMAL execution>)` 与独立 TaskDispatch；第一次 compensation attempt=1，fresh compensation retry 通过 predecessorExecutionId 串联同 purpose/同 node。Node currentExecution/currentAttempt CAS 指向补偿 attempt，并进入 `COMPENSATING`；历史 NORMAL Execution/result/receipt 保持不可变。补偿 input 仅由 frozen selector 从原 inputRef、accepted resultRef 和 confirmed receipt 生成，禁止加载原 AgentState 或私有历史。

每个补偿外部调用必须走与普通副作用相同的 intent 协议：先在短事务锁 Execution→active Dispatch，复核 current fence，持久化 compensation intent、稳定 provider idempotency key（至少包含 tenant/task/planRevision/node/compensatesExecution/contractHash/action）、outbox 后提交；worker 调用前再次复核 active Dispatch。provider receipt 按该 key 幂等，未知/in-flight 状态 fail-closed 转人工，不得另造 key 重试。

`CompensationContract.maxParallelism` 在 compensation claim 事务中锁 Plan 并统计 CLAIMED/RUNNING COMPENSATION Dispatch 强制；normal maxParallelism 与 compensation maxParallelism 分别计算，COMPENSATING 时禁止 normal claim。只有逆拓扑前驱全部 `COMPENSATED/SKIPPED` 的 step 才能由 `materializeCompensation` 短事务创建；其锁序、Node/Execution/Dispatch CAS、canonical event/outbox 原子性与 `materializeReadyNode` 相同。

### 暂停、恢复、失败与终态

- pause/cancel control handshake 同样传播到补偿 Execution；暂停后 Task=PAUSED 且 checkpoint 记录 `resumePhase=COMPENSATING`。resume 只能回到 COMPENSATING，不得恢复 normal DAG。
- owner/profile/state 不变且 state slot 完整时可 same-attempt resume，但所有 load/save/interrupt/result 仍校验当前 active Dispatch；缺失、损坏、责任或授权变化时只为该补偿 step 创建 fresh COMPENSATION attempt，新 session/state slot，不迁移旧 AgentState。
- stale worker、旧 normal/compensation attempt、旧 dispatch generation/fence 的 ACK/receipt/result 均拒绝且不释放下一个逆拓扑节点。
- 全部 eligible step `COMPENSATED/SKIPPED` 后：原因为失败则 Task→FAILED 且 terminalCode=`FAILED_COMPENSATED`；原因为取消则 Task→CANCELED 且 terminalCode=`CANCELED_COMPENSATED`。补偿不伪造业务成功或根结果。
- 任一补偿 step 达到不可重试失败或 provider 状态未知且无法安全确认时，Task→`COMPENSATION_FAILED` 终态，保留失败 step、intent、receipt 与人工处置证据；禁止继续补偿其上游节点。

canonical events 至少包含 `task.compensation-started/completed/failed`、`node.compensation-ready/started/completed/failed/skipped`、`execution.compensation-created/resumed/superseded` 与副作用 intent/receipt 事件；信封携带 purpose、compensatesExecutionId 及 TaskDispatch generation/fence 快照。

### 补偿验收矩阵

| 场景 | 必须结果 |
|---|---|
| REQUIRED 节点不可重试失败 | 仅合同声明且有 confirmed receipt 的成功节点进入逆拓扑补偿 |
| `A→B` 均已产生副作用，后续失败 | B 补偿确认后 A 才可 claim |
| 两个无依赖分支可补偿 | 可并行但不超过 compensation maxParallelism |
| 重复 trigger/事件/数据库重试 | 每 node/原执行只创建一个当前 compensation attempt 与 active Dispatch |
| provider timeout/未知 | 使用原 provider key 查询；无法确认则 COMPENSATION_FAILED，不盲重试 |
| pause/resume | 保持 COMPENSATING phase；合法状态同 attempt，否则只 fresh 当前补偿 step |
| 旧 fence 迟到 | 不写 receipt/result，不释放逆拓扑后继，追加 stale audit |
| 全部补偿成功 | Task 为 FAILED_COMPENSATED 或 CANCELED_COMPENSATED，不得 COMPLETED |
| 补偿不可重试失败 | Task=COMPENSATION_FAILED，停止后续上游补偿并保留证据 |

## 根结果与完成证据提交

冻结 plan 时 `AggregationContract.terminalResultNodeId` 必须唯一；fan-in 场景该节点必须为 AGGREGATOR，简单最小 plan 可指定唯一 EXECUTOR/EVALUATOR，但 selector 和 OutputContract 必须确定性。运行中不得改 selector 或新增输入节点。

根结果提交事务固定执行：锁 Task→current Plan→terminal Node→Execution→Dispatch；校验 Task.currentPlanRevision、contractHash、terminalResultNodeId、Execution scope/attempt、Node.currentExecutionId、dispatch generation/fence、Node output contract 与所有 completion evidence；随后先执行 Task 根结果 CAS，再以预期 Node version CAS 写 resultRef，两次更新必须都恰好一行，否则整个业务事务回滚：

```sql
UPDATE ai_task
SET root_result_ref = :resultRef,
    completion_evidence_ref = :evidenceRef,
    root_result_plan_revision = :revision,
    root_result_node_id = :nodeId,
    root_result_execution_id = :executionId,
    root_result_attempt_no = :attemptNo,
    root_result_generation = :generation,
    root_result_fencing_token = :fence,
    version = version + 1
WHERE tenant_id = :tenantId
  AND task_id = :taskId
  AND current_plan_revision = :revision
  AND root_result_ref IS NULL
  AND version = :expectedTaskVersion;
```

只有 Task 与 Node 两次 CAS 均更新一行者可在同事务追加 `task.result-committed` 并推进 VERIFYING；任一次零行意味着重复、迟到或冲突，必须回滚全部 Task/Node 业务写入，再以独立、幂等的短审计事务追加 `node.result-rejected`（reason=`DUPLICATE_ROOT_RESULT|STALE_PLAN|UNDECLARED_PRODUCER|STALE_ATTEMPT|STALE_GENERATION|STALE_FENCE|CONTRACT_MISMATCH`）。

无 plan 的 TASK_ROOT 使用冻结 `CompletionCriteria`，在同一事务 CAS Execution→COMPLETED 与 Task 根结果：`WHERE current_plan_revision IS NULL AND current_root_execution_id=:executionId AND current_root_attempt_no=:attemptNo AND root_result_ref IS NULL AND version=:expectedTaskVersion`，并额外校验 dispatch generation/fence。只有两次 CAS 均恰好一行才追加 `task.result-committed`；旧 root attempt、旧 generation/fence、重复结果以 `execution.result-rejected` 审计，绝不覆盖 current root 结果。

旧 generation 和未声明节点即使内容相同也不视为成功。Task 根结果字段是查询与完成判定唯一权威；event/AG-UI 只是投影。

## Canonical 事件

```java
public record TaskEventEnvelope(
        EventId eventId,
        long eventOffset,
        TenantId tenantId,
        AggregateType aggregateType,
        String aggregateId,
        long aggregateVersion,
        long aggregateSequence,
        String producerIdempotencyKey,
        TaskId taskId,
        PlanId planId,
        Integer planRevision,
        NodeId nodeId,
        ExecutionId executionId,
        DispatchId dispatchId,
        Integer attemptNo,
        Long executionSequence,
        Long dispatchGeneration,
        Long fencingToken,
        String eventType,
        CorrelationId correlationId,
        CausationId causationId,
        ActorRef actor,
        Map<String, Object> publicPayload,
        Instant occurredAt) {}
```

身份与事件类别合同：

| aggregateType | 合法事件示例 | 必填身份 | 必须为空/条件字段 |
|---|---|---|---|
| `TASK` | `task.created/status-changed/owner-changed/budget-updated/result-committed` | taskId；aggregateId=taskId | plan/node/execution/dispatch 可空；Task 自身事件不得为满足 schema 伪造 Execution |
| `PLAN` | `plan.frozen/freeze-rejected/superseded` | taskId、planId、planRevision；aggregateId=planId | node/execution/dispatch 为空；freeze-rejected 合法发生在零 Execution 时 |
| `NODE` | `node.ready/claimed/status-changed/result-recorded/result-rejected/blocked` | taskId、planId、planRevision、nodeId；aggregateId=nodeId | ready/blocked 可无 execution；claimed/result 类必须携带同 node execution、attempt、generation/fence |
| `EXECUTION` | `execution.created/dispatched/started/paused/resumed/completed/failed/canceled/promoted/superseded` | executionId；aggregateId=executionId | DIRECT 要求 task/plan/node 全空；TASK_ROOT 仅 task 非空；TASK_NODE 要求 task/plan/revision/node 全非空 |
| `INTERRUPT` | `interrupt.requested/resolved` | taskId、planId、planRevision、nodeId、executionId、attemptNo；aggregateId=interruptId | 禁止 DIRECT/TASK_ROOT interrupt；requested 必带 state slot 与 generation/fence 的 public-safe ref |
| `DISPATCH` | `dispatch.enqueued/claimed/lease-expired/completed/stale-rejected` | dispatchId、executionId；aggregateId=dispatchId | task/plan/node 不在 Dispatch 行冗余保存；event 通过 Execution scope 校验：TASK_NODE 时信封携带完整 task/plan/node，DIRECT 时均空 |

数据库和事务约束：

- `event_id` 全局唯一；`UNIQUE(tenant_id,aggregate_type,aggregate_id,aggregate_sequence)` 保证每个 aggregate 严格递增，适用于没有 Execution 的 Task/Plan/Node 事件。
- `UNIQUE(tenant_id,producer_idempotency_key)` 保证 command/outbox 重放不重复追加；普通命令 producer key 由 `commandId:eventType:aggregateType:aggregateId` 稳定生成。promotion 是显式例外，按 `tenantId|originExecutionId|PROMOTION|eventType|aggregateType|aggregateId` 派生，使并发 commandId 不会重复产出 promotion 事件。
- `execution_sequence` 对携带 executionId 的事件由 `ai_execution_sequence` 分配；CHECK 要求 executionId 与 executionSequence 同时为空或同时非空，并使用 partial unique `(tenant_id,execution_id,execution_sequence) WHERE execution_id IS NOT NULL AND execution_sequence IS NOT NULL`。非 Execution 事件不得依赖该序列排序。
- CHECK 约束落实同一行可空矩阵；FK 明确为 `(tenant_id,task_id)`→Task、`(tenant_id,task_id,plan_id,plan_revision)`→Plan、`(tenant_id,task_id,plan_id,plan_revision,node_id)`→Node、`(tenant_id,execution_id)`→Execution、`(tenant_id,dispatch_id)`→Dispatch，均 `ON DELETE RESTRICT`。同一 append 事务/约束触发器还必须校验 aggregateId 等于对应实体 ID、Execution scope、Node 所属 plan、Dispatch→Execution identity 与 eventType 允许集合；不能只靠 DTO 校验。
- `event_offset` 仅用于全局/SSE cursor，不表达 aggregate 内因果顺序；消费端以 aggregateSequence 检 gap，出现 gap 后 refetch 权威查询。
- Task/Plan/Node 级命令在尚无 Execution 时合法追加事件；严禁创建伪 Execution、伪 attempt 或零值 generation 来满足 envelope。

public payload 使用白名单；完整内部错误、prompt、CoT、凭证和 AgentState 不进入 AG-UI。

## REST、SSE 与 AG-UI

### Task REST

- `GET /api/ai/tasks?conversationId=&status=&cursor=` → Task summaries，服务端 tenant/owner 过滤。
- `GET /api/ai/tasks/{taskId}` → 一次返回 Task + current plan + nodes + dependencies + current executions。
- `POST /api/ai/tasks` → 202，显式创建持久 Task。
- `POST /api/ai/tasks/{taskId}/pause|resume|cancel|take-over|hand-back` → 202 command receipt。
- `POST /api/ai/tasks/{taskId}/nodes/{nodeId}/retry` → 202 + new executionId。
- `GET /api/ai/task-interrupts/pending?type=APPROVAL|CLARIFICATION&taskId=&cursor=` → 当前 tenant/actor 可处理的 pending interrupt；返回完整 task/plan/node/execution/attempt/state-slot/dispatch generation 身份和 expected versions，不返回私有 AgentState。
- `GET /api/ai/tasks/{taskId}/interrupts/{interruptId}` → 单个 interrupt 权威详情，强制 tenant/owner/assignee 校验。
- `POST /api/ai/tasks/{taskId}/interrupts/{interruptId}/resolutions` → body=`ResolveTaskInterruptCommand`，typed resolution 为 APPROVE/DENY/INPUT；统一返回 `202 Accepted + commandReceipt`。
- `GET /api/ai/tasks/{taskId}/events?after=` → 有界快照/补读。
- `GET /api/ai/tasks/{taskId}/events/stream?after=` → `Flux<ServerSentEvent<...>>`。

旧资源直接删除，不保留 adapter：

| 旧 endpoint | 目标 | 删除/替代结论 |
|---|---|---|
| `GET /api/ai/approvals/pending` | `GET /api/ai/task-interrupts/pending?type=APPROVAL` | `HumanApprovalVO` 改统一 `TaskInterruptView`；服务端按 assignee 过滤 |
| `POST /api/ai/approvals/{approvalId}/decision` | `POST /api/ai/tasks/{taskId}/interrupts/{interruptId}/resolutions` | approvalId→interruptId；APPROVED/REJECTED 映射 APPROVE/DENY；同步 `200 Result` 改 202 receipt |
| `POST /api/ai/approvals/{approvalId}/recover` | 无用户级替代 endpoint | 删除；resolution outbox 与 durable dispatch 自动重试。管理员若需重放走受审计运维命令，不暴露业务恢复旁路 |
| `GET /api/agui/approvals/{approvalId}/events` | `GET /api/ai/tasks/{taskId}/events/stream?after=` 或统一 AG-UI run stream | 删除 approval 专属 SseEmitter；按 canonical cursor 续读 |
| `POST /api/ai/tasks/{taskId}/inputs` | 统一 resolution endpoint，type=INPUT | 删除“最近 waiting”输入语义，必须指定 interruptId 与完整 identity |
| `/delegated`、`/{taskId}/delegation`、`/{taskId}/executor-plans` | Task list/detail | 删除 delegated/executor-plan 资源名与 DTO |

重复 resolution 使用 idempotencyKey 返回同一 receipt；相同 interrupt 的冲突决策返回 409，不产生第二次恢复。DENY 只提交拒绝事实并按 frozen failure policy 推进；APPROVE/INPUT 在事务提交后 enqueue durable Dispatch。GraphQL 继续无任务入口，避免平行 API。

### Execution/AG-UI

`/agui/run` 可保留协议 URL，但内部先创建 DIRECT Execution/TaskAnalysis，不再无条件创建 Task。SSE 是非阻塞事件投影，不持事务、不手工 subscribe。复杂运行返回 task accepted/promotion event，客户端可继续订阅 canonical task stream；断线按 eventOffset 重连。

approval/clarification 决策接口提交命令后立即返回，恢复由 dispatch worker 异步执行，不在请求中等待 Agent。

## 前端迁移

- `useTaskBoard(conversationId)` 改为服务端过滤的 `useTasks({conversationId})`；不再拉全量后过滤。
- `TaskBoardPanel` 只接收 `TaskSummaryView`，direct Execution 不进入 query。
- 展开 detail 一次加载 plan/node/dependency/current execution；删除 `ExecutorPlanSummary` 3 秒独立轮询。
- DAG 使用同一 detail projection 表示串行、并行与 join；节点显示 attempt、waiting、blocked 和 stale/retry 信息。
- AG-UI `aaf.task.*`/node activity 仅 invalidation，或在 event aggregateVersion 连续时做安全增量；出现 gap 立即 refetch。
- TanStack Query 继续管理权威服务端状态；Zustand 只保存选中节点、面板开合、瞬时 activity，不复制 Task/Plan/Node。
- 切 conversation、刷新和 SSE 重连都以 task query + cursor 重建，不从消息正文或 AgentState 恢复。

## 无兼容层切换顺序

人类审核通过后按以下单次切换实施：

- 运维确认旧表数据与可接受停机窗口，协调者分配 Flyway 号段。
- 停止入口、旧 scheduler、recovery worker 和 outbox relay，等待 lease 过期或显式 fence 全部旧 worker。
- 备份数据库；若存在需保留数据，先在离线脚本中转换并由人类审核，不在应用内 dual-write。
- 一次 Flyway 变更创建六张目标表、重建辅助表/约束、转换或清空旧开发数据、删除三张旧主表。
- 同批部署只认识新 schema 的后端；旧 binary 不得重新启动。
- 同批部署新 REST DTO、AG-UI projector 和前端；删除旧 endpoints/types/query keys。
- 启动 outbox relay 与 dispatch worker，做静态/迁移/故障注入验收后开放流量。

不能采用“先双写、再慢慢切读”或旧 API adapter。需要跨部署时只能使用维护窗口与版本原子切换。

## 回滚

Flyway 不做运行时 down migration。回滚边界是整套 release：

- 开放流量前失败：停止新进程，恢复迁移前 DB 快照，启动旧 binary。
- 开放流量后若已产生新格式事实：立即停写并 fencing；导出公开 task/event/receipt 摘要供审计；由人类选择恢复快照并丢弃新环境数据，或修正后向前发布。
- 禁止为了回滚长期保留旧表双写。
- 外部副作用不能通过 DB 回滚撤销；依赖 receipt/provider idempotency 与 compensation policy。
- AgentState 是可删除工作态，回滚不承诺迁移；恢复后按旧版本合同处理或人工终止。

## 风险与控制

| 风险 | 影响 | 控制 |
|---|---|---|
| 旧部署已有真实数据 | 迁移丢失 | 上线前运维确认、快照、离线转换审核 |
| state key 当前包含 fence | same-attempt 无法加载 | slot generation 与 dispatch fence 解耦；做不到则 fresh attempt fail-closed |
| hidden history 守卫无条件拒绝 | pause/HITL 不能续接 | resume-aware 完整身份校验 |
| takeUntil 提前 dispose | structured HITL 删除状态 | 提交后统一 interrupt-preserve handshake |
| 迟到 worker 覆盖 | 重复副作用/提前 join | generation+fence+version 四重 CAS、stale audit |
| DAG 并发超限 | 资源耗尽 | plan 行闸门、claim 同事务计数、系统硬上限 |
| event/outbox 至少一次 | UI/通知重复 | eventId、aggregateVersion、cursor 幂等 |
| JPA 阻塞 event loop | 延迟和线程饥饿 | 专用 persistence scheduler/durable worker；禁止 block/subscribe |
| 根状态与节点派生不一致 | 再生双真理 | 唯一 command service 写根；读侧只校验告警 |
| ExecutorPlan 继续竞争根计划 | 计划语义冲突 | 改名 NodeExecutionPlan 或并入 node payload |
| API 一次破坏性切换 | 短时不可用 | 维护窗口、前后端同批部署，不加兼容层 |
| promotion 已发生副作用 | 重复副作用或双执行权 | 任一 receipt/intent 即拒绝；仅零副作用 DIRECT 可 promotion |
| TASK_ROOT 状态损坏 | 无 plan Task 无法恢复或旧结果覆盖 | current-root CAS、fresh attempt predecessor、新 session/state、旧 fence 拒绝 |
| event RESTRICT 与 hard purge | 删除失败或审计丢失 | 先归档/删在线 event 后删实体；需保留则 tombstone、禁止 hard delete |

## 复审问题闭环

| 编号 | 修订结论 | 设计落点 |
|---|---|---|
| B1 | Task/Plan/Node 事件不再强制 executionId；按 aggregate 身份矩阵、复合 FK、aggregateSequence 与 producer idempotency 约束 | Canonical 事件、辅助表改造 |
| M1 | 采用最小一节点 plan：promotion/structured HITL 必须 TASK_NODE；TASK_ROOT 禁止结构化 interrupt | 核心决策、Execution scope、Promotion、结构化澄清 |
| M2 | AggregationContract 冻结唯一 terminal producer/selector；Task 根结果字段以双 CAS 只接受一次 | Task 不变量、根结果与完成证据提交 |
| M3 | 非法 freeze 保留 DRAFT/PLANNING，持久 reason 列表并发 `plan.freeze-rejected`，零 Dispatch/外部调用 | DAG 冻结与调度、plan 表 |
| M4 | claim 只接受可运行 Task；pause 采用 request→state ACK→fence→resume，超时 fresh-attempt fail-closed；控制命令顺序统一 | 状态机、控制命令握手 |
| M5 | pending/decision/input 统一 Interrupt API；recover 与 approval 专属 SSE 删除，durable dispatch 异步恢复 | 应用接口、REST 迁移矩阵 |
| M6 | Task→current plan、Dependency→Node、Execution→Node、Node→current Execution、Dispatch→Execution 均有 tenant-scoped 复合约束与显式 purge 顺序 | 数据模型、跨表删除规则 |
| R2-M1 | DIRECT promotion 严格限制在零外部副作用；receipt 安全点仅限既有 Task 的 TASK_ROOT→TASK_NODE；事件 key 稳定派生 | Promotion、Task/Execution 不变量 |
| R2-M2 | Task/Execution 增加 correlation/input/public-context 权威谱系，服务端从 origin DIRECT 复制且不可变 | 领域模型、应用接口、ai_task/ai_task_execution |
| R2-M3 | TASK_ROOT 支持 same-attempt 与 fresh attempt，增加 current-root 指针、attempt/predecessor、完成 CAS 与 stale fence 拒绝 | Execution scope、pause 握手、fresh attempt、根结果、schema |
| R2-M4 | event 先归档并删除在线行，再删受 RESTRICT 保护的实体；保留事件则使用 tombstone、禁止 hard delete | 跨表删除与保留规则 |

本表只保留历史修订索引。独立 QA 仍为 NEEDS_CHANGES；人类已于 2026-09-11 批准按“保守决策收口”进入开发，未伪造 QA CLEAR。

## 验收与实现门

本设计仅定义后续实现与测试合同，本轮不修改 schema、业务代码或测试。后续 #11503～#11509 必须覆盖 requirement.md 全部 Gherkin，重点包括：direct 不建 Task、幂等 promotion、非法 DAG、串并行/join、同 attempt resume、fresh attempt、已完成兄弟不重跑、旧 generation 拒绝、SSE 重连与前端单一真理。

**进入开发结论**：人类已批准按保守决策开发，后续由代码审查验证；独立 QA 的 NEEDS_CHANGES 作为历史记录继续保留。
