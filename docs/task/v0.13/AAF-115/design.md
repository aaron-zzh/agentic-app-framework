---
level: Practice
layer: Model
purpose: 定义 AAF-115 canonical Task 运行时、双层计划、安全调度目标及当前实现状态
status: draft
version: 2.2.18
date: 2026-09-12
author: AaronZZH & Kiro
tags:
  - AAF-115
  - Task
  - DAG
  - Execution
related:
  - requirement.md
  - tasks.md
  - ../../../design/adr/ADR-005-agentscope-boundary-and-orchestration.md
  - ../../v0.12/AAF-110/tasks.md
---

# 设计：统一任务模型与 DAG 编排（AAF-115）

## 设计状态

本文已按 2026-09-12 生产代码完成静态一致性复核。正文区分“当前实现”和“仍然有效的目标设计合同”；原有大段 Java、SQL 或接口伪代码改为简洁文字约束，未实现设计不得因压缩文档而消失。

本轮已完成 structured clarification 创建端，Task 内嵌 RootResult、Owner Aggregator finalizer、VERIFYING 调度与一次性 current-fence CAS，canonical durable take-over/forced-fresh hand-back、same-attempt 稳定 stateSlot 与独立 Dispatch authority，以及 #11508 静态迁移清理。生产代码和 Flyway 未发现 `AssistantTask`、`DelegatedTask`、旧 TaskBoard 或旧表；不可达 `AssistantCommand.PAUSE/CANCEL` 已删除；绕过 stable-pause、Dispatch 失权和 fencing 的 `support.handoff` 已从实现、自动配置和 fresh seed 下线。因尚未部署且原始 seed 已承载最终状态，不保留额外前向清理 migration。按当前授权只执行 diagnostics、代码搜索和 `git diff --check`，未运行 test、build、check 或 lint；独立 QA 仍未 CLEAR。

## 目标与边界

AAF 只保留一个持久任务根 `Task`。普通对话使用无 taskId 的 `Execution(DIRECT)`；复杂目标、持久恢复、结构化 HITL 或受控副作用通过显式入口或 `promote_direct_task` 进入 canonical Task 运行时。

当前任务真理链为：`Task` 持有根状态和 current plan/root 指针，`TaskPlan` 持有版本化 TaskNode DAG，`Execution` 表示一次真实运行或 fresh attempt，`TaskDispatch` 独占 lease、generation 与 fencing 当前事实；AgentState 只属于 Execution 工作现场，不承载 Task 真理。

AIGC 业务任务、Flowable 工作流、系统异步任务和 AgentScope 原生 subagent 不并入本模型。AAF 未发布 v1.0，不保留 `AssistantTask`、`DelegatedTask`、`TaskBoard` 的兼容层、双写或 fallbac
## Canonical 模型

| 模型 | 当前职责 | 当前主要状态/身份 |
|---|---|---|
| `Task` | 持久目标唯一根，保存 owner、controlMode、合同、预算用量、checkpoint、current plan/root 与一次性 RootResult | `DRAFT/PLANNING/READY/RUNNING/VERIFYING/AWAITING_*/PAUSING/PAUSED/CANCELING/COMPLETED/CANCELED/FAILED` |
| `TaskPlan` | Task 内不可变 revision DAG，保存目标、并行度、失败策略和 graphHash | `DRAFT/FROZEN/SUPERSEDED` |
| `TaskNode` | Plan revision 内的 Coordinator/Executor/Evaluator/Aggregator 工作节点 | `PENDING/READY/CLAIMED/RUNNING/AWAITING_*/PAUSED/VERIFYING/COMPLETED/RETRYABLE/FAILED/CANCELED/BLOCKED` |
| `TaskDependency` | TaskNode 间 REQUIRED/OPTIONAL 有向依赖 | 同一 tenant、Task、plan revision 内约束 |
| `Execution` | DIRECT、TASK_ROOT 或 TASK_NODE 的一次真实运行/attempt | `CREATED/READY/DISPATCHED/RUNNING/AWAITING_*/PAUSED/COMPLETED/FAILED/CANCELED/PROMOTED/SUPERSEDED` |
| `TaskDispatch` | durable 调度、lease、generation、fencing 和投递次数 | `PENDING/CLAIMED/DONE/CANCELED` |

`Task.currentPlanId/currentPlanRevision` 与 `currentRootExecutionId/currentRootAttemptNo` 互斥。TASK_NODE 必须携带完整 task/plan/revision/node 身份；TASK_ROOT 只绑定 taskId；DIRECT 不绑定任何 Task 身份。

## 双层计划

`TaskPlan` 管理“哪些子智能体协作、依赖如何组织、结果如何聚合”；`ExecutorPlan` 管理“某个 TaskNode 内部按哪些有序步骤完成工作”。两者都保留，不能互相替代。

- Coordinator 使用 `submit_coordination_plan` 将 coordinator-only TaskPlan 转换为完整 DAG revision。
- Coordinator 或 Executor 可在自己的节点 Execution 中调用 `submit_executor_plan` 创建局部 `ExecutorPlan` revision。
- `report_executor_step` 按依赖和顺序推进 `ExecutorPlanStep`。
- ExecutorPlan 提交后自动批准；终态 `COMPLETED/FAILED/CANCELLED/REJECTED` 不再视为 active，新 revision 从全部历史 revision 的最大值递增。
- TaskPanel 以列表图标和数量展开外层 TaskPlan 节点；节点存在 ExecutorPlan 时再展开其最新 revision 的安全步骤。UI 只读且不混层，调整仍通过 `amend_task(TASK_PLAN|EXECUTOR_PLAN)` 形成新 revision/attempt。

## 创建、promotion 与调度

`TaskAnalysisPolicy` 在 `AssistantExecutionService` 中执行确定性硬门：显式 Team、持久任务或副作用边界进入 Task/Team，其余走 DIRECT。开放语义目标由当前 PRIMARY Assistant 自行决定是否调用 `promote_direct_task`，没有独立 classifier/judge LLM。

promotion 锁定 origin DIRECT Execution，要求其仍为 `ELIGIBLE` 且 `sideEffectEpoch=0`；服务端从 origin command 和冻结画像派生 Task、最小 coordinator TaskPlan、新 TASK_NODE Execution 与 Dispatch，原 DIRECT Execution 进入 `PROMOTED`。TaskId 由 tenantId 与 originExecutionId 稳定派生。

`JpaTaskMaterializationAdapter` 是 planned Task 的物化、claim、commit、recovery、cancel 和计划调整事务边界。ready 节点先形成 PENDING Dispatch；claim 时锁定 current Task/Plan/Node/Execution/Dispatch，校验 Task 可运行、Plan 为 current FROZEN revision、节点 ready、并行度和当前 dispatch 身份后，才授予 worker 执行权。

`TaskDispatch` 是 lease、generation 与 fencingToken 的唯一当前来源。结果提交必须匹配 dispatchId、generation、fence、lease owner、TaskPlan revision、node current Execution 和各层 version；迟到或失去执行权的结果返回拒绝原因，不更新节点，也不释放后继。

## DAG 与结果聚合

`TaskPlan` 构造和 `TaskDagService` 负责引用、自依赖、缺失前驱、输入绑定、聚合引用、并行度和环检测。ready 只在 FROZEN plan 中计算，并要求所有 REQUIRED 前驱已完成；并行名额在 claim 事务中控制。

`TaskPlanDraft` 只是当前调用中的候选值；只有完整校验成功后才原子持久化为 FROZEN TaskPlan revision。非法草案向当前调用方返回明确校验错误，不创建 TaskPlan revision、Dispatch、独立 freeze command、持久拒绝字段或 `plan.freeze-rejected` 事件。

串行、并行、join、fan-out/fan-in 由 TaskNode 的 dependsOn 和冻结聚合合同统一表达。Coordinator 完成规划后不再承担执行节点工作；coordinator-only planning revision 不含 finalizer，`TaskPlan.single(...)` 和 draft freeze 后的正式 revision 均由系统追加唯一 Aggregator。

最后前驱完成后 Task 进入 `VERIFYING`，只允许该 Aggregator finalizer 物化和 claim。finalizer 使用 Task Owner Assistant 的冻结身份与 AGGREGATOR 画像；成功 Execution 即语义完成确认。`PASS_THROUGH/ORDERED_CONCAT` 的 RootResult 由服务端调用 `TaskPlan.aggregateResults()` 确定性生成，`AGGREGATOR_REDUCE` 使用 Aggregator 输出；finalizer 文本同时保留为 Owner completion evidence。根结果提交复用 NodeResultCommand 的 current plan、node、execution、attempt、dispatch generation/fence、lease owner 与各层 version 校验，只允许第一次 current 提交成功。

TASK_NODE 的 Assistant 生命周期不再直接保存根 Task owner/status/version：普通节点只校验本次 Execution 的显式业务事件，Aggregator 使用“current plan + 本次输出”的内存预完成视图校验整张 DAG 与完成证据；实际节点完成、失败策略、VERIFYING、RootResult 和根终态全部由同一个 fenced materialization commit 事务写入。

planned Task 只有在 RootResult 与 finalizer 节点同事务提交后才能进入 `COMPLETED`。TaskDetails 仅投影 result、planRevision、sourceNodeId 和 committedAt，不暴露 dispatch、generation、fence 或内部 version；TASK_ROOT 完成语义不强制 RootResult。

## 对话式读取、调整与停止

### 异步任务与持续对话

来自对话的复杂目标采用“短接受、长执行”合同：PRIMARY 分析出 TASK/TEAM 后，只在当前 AG-UI run 中原子创建 canonical Task、冻结 TaskPlan、物化首批 Execution/Dispatch 并发送低延迟唤醒；durable 提交成功即闭合当前 run，不在 HTTP/SSE 中 claim 或等待 TaskNode。`TaskDispatchScheduler` 和 `AgentTaskRuntime` 独立领取 PENDING Dispatch，数据库事实而非原 SSE 连接决定任务是否继续。

当前对话 run 闭合后 Composer 恢复输入。后续用户消息创建新的 PRIMARY DIRECT Execution：控制意图通过 `inspect_tasks/amend_task/pause_task/resume_task/cancel_task` 修改目标 Task，无关意图可与后台 Task 并行处理。新消息不得追加到旧 Agent prompt，也不得重新开放旧 generation/fence；调整、暂停或取消必须先由 canonical 控制事务关闭旧执行权，迟到 worker 继续按 current Dispatch identity fail-closed。

该异步合同只适用于 `Source.CONVERSATION` 的用户对话入口。Flowable `AgentNode` 是有界同步工作流节点，仍可显式等待 `submitAndDispatch` 的节点结果；自动化入口只提交 durable Task，由既有 scheduler 消费。这是调用方完成语义差异，不是兼容层或同一入口 dual-write。

客户端在每次 AG-UI `RUN_FINISHED` 后刷新 Task 查询，并在存在非终态 Task 时短轮询，因此初次接受和后台状态变化不依赖已经闭合的 SSE。当前 Task 最终结果以 canonical TaskDetails/节点结果为权威投影；把后台终态结果自动追加成新的聊天消息需要独立的持久消息投递合同，不在本次解耦中伪造 ExecutionEvent 或保持旧 run 长连接。

PRIMARY direct conversation 内置六个 Task 控制工具：

- `inspect_tasks`：读取当前对话中当前用户的 Task、TaskNode，并可读取指定节点 active ExecutorPlan 与步骤。
- `promote_direct_task`：把符合条件的 DIRECT Execution 提升为 Task。
- `amend_task`：以 `TASK_PLAN` 或 `EXECUTOR_PLAN` scope 提交 MODIFY 输入。
- `pause_task`：提交 durable `PAUSING` 请求并等待逐 Execution AgentState ACK。
- `resume_task`：按冻结的 `SAME_ATTEMPT/FRESH_ATTEMPT` 模式恢复已暂停 Task。
- `cancel_task`：停止明确指定或当前对话唯一活跃的 Task。

没有显式 taskId 时，服务端只在当前 conversation 恰有一个非终态 Task 时自动定位；多个或零个候选均拒绝，不按“最近任务”猜测。ExecutorPlan 调整必须明确 nodeId。

### TaskPlan 调整

外层调整在一个事务中锁 Task/current plan，关闭旧 revision 全部非终态节点的 Execution 和 active Dispatch，取消其 active ExecutorPlan，把旧 TaskPlan 标记 `SUPERSEDED`，再创建 coordinator-only 的下一 revision。Coordinator 随后调用 `submit_coordination_plan` 形成完整执行 revision，因此实际 revision 链为“旧执行 revision → replanning revision → 新执行 revision”。

这是保守的全 revision 重规划，不复用旧 revision 中无冲突但尚未完成的结果；历史 TaskPlan、Execution、ExecutorPlan、事件和用户业务数据均保留。

### ExecutorPlan 调整

局部调整只接受未完成 TaskNode。事务关闭目标节点当前 Execution/Dispatch，取消 active ExecutorPlan，把修改说明加入节点输入，将节点置为 PENDING 或 RETRYABLE，再由下一次节点 Execution（已有 attempt 时为 fresh attempt）调用 `submit_executor_plan` 创建下一局部 revision。其他节点不重启。

已完成节点不能局部改写；如其结果必须失效，应走外层 TaskPlan 调整，以免已完成后继继续消费旧结果。

### MODIFY 幂等

MODIFY inputId 在持久化后由重规划事务行锁；已消费输入直接返回，不重复创建 revision。审计事件 producer identity 使用稳定 inputId，不依赖时间戳。

### Task 暂停与停止

用户显式 pause 进入 durable `PAUSING`：请求事务按 executionId 冻结 targets，关闭 active Dispatch、提升 generation/fence；Harness 在发出 `EXECUTION_PAUSED` 前显式保存 call-scoped AgentState，并在事件 payload 中返回 slot/schema/savedAt。ACK 按 executionId 幂等聚合；全部成功收敛为 `PAUSED + SAME_ATTEMPT`，任一失败或 deadline 超时收敛为 `PAUSED + FRESH_ATTEMPT`，超时缺失 ACK 也会写成逐 Execution 失败事实。预算、HITL、handoff 等系统暂停继续使用原直接 PAUSED 语义，不伪装成用户无损 pause。

resume 读取已冻结模式：TASK_NODE same-attempt 复用 executionId/sessionId 并要求状态槽存在、slot/schema 兼容，fresh-attempt 只重建受影响节点 Execution 并保留 predecessor 谱系。TASK_ROOT same-attempt 同样复用 executionId/sessionId/stateSlotId，但总是创建更高 generation 和新 fence 的 PENDING Dispatch；fresh-attempt 将旧 root 标记 SUPERSEDED，以新 executionId/sessionId/runId/stateSlotId 和 predecessor 创建 attempt，并通过 Task current-root CAS 切换唯一执行权。普通 START/fresh attempt 若发现隐藏持久历史仍 fail-closed，same-attempt 预期状态缺失也不得静默降级。

TASK_ROOT 与 TASK_NODE 复用同一 `ai_task_dispatch` durable queue，但 due/expired 查询必须按 Execution scope 隔离。root resume 即时取得 conversation lease 失败时保持 PENDING；scheduler 每轮恢复 TASK_ROOT 过期 CLAIMED lease并重新扫描 due PENDING，随后仍由 current-root、Task READY 状态、dispatch generation/fence 和 conversation lease 共同完成 claim。每次 root 调度使用唯一 lease owner，重叠轮询或多实例不得以同名 owner 误释放其他执行的 lease。

cancel 请求先进入 `CANCELING`，立即关闭 active Dispatch 并提升 generation/fence；恢复调度再幂等终结 Node/Execution/Task 为 `CANCELED`。暂停、恢复和取消均不创建新计划 revision，也不删除历史计划、事件或用户业务数据。

## HITL 与恢复

`request_clarification` 是 structured clarification 的唯一模型创建入口，只向已有 taskId 和 nodeIdentity 的 canonical TaskNode 暴露；DIRECT 必须先调用 promotion。工具从受限 questions schema 构造 `ClarificationRequest`，事务原子关闭 active Dispatch、推进 Task/Node/Execution 等待态并以稳定 `clarification-request-<requestId>` 写入包含 `questions/requiredFields` 的 canonical event/outbox；成功提交后抛 typed `ClarificationRequiredException`，绕过工具成功路径在旧 Dispatch 上的二次 current 校验。补充输入在领域层同时校验字段集合、非空值和枚举 options，再按 requestId、用户、conversation lease 与 current identity 恢复原 Execution并重新物化 Dispatch。

授权与结构化澄清共用一套 exact 已存 interrupt 交接。`PortBackedAgentTool` 把 typed 挂起信号转成 `ToolSuspendException`，evidence 只保存当前 execution/toolCall 对应的 `approvalId/authorizationRequired` 或 `requestId/clarificationRequired`。Harness 对非成功 `TOOL_RESULT_END` 按 evidence 推导稳定 eventId，从 `ExecutionEventStorePort.readExecution` 读取 exact 已存事件，逐项校验 tenant、conversation、session、Task、Execution、Run、node、用户、owner 与事件类型/状态，再保存 call-scoped AgentState并消费同一 evidence。校验通过后当前 Flux 直接发出该已存事件，并按 eventId 跳过普通 `append`；因此旧 Dispatch 始终关闭，每个 interrupt 事件只持久化一次，当前 AG-UI run 仍收到标准 `tool_call` 或 `input_required` interrupt。canonical waiting event 发出后立即截断旧执行尾流；任何非 exact 工具结果及其他边界事件继续执行原 current/fencing 校验并 fail-closed。

统一 release 只有在 canonical HITL 状态保存成功且取消终态未胜出时才保留状态槽；取消胜出或保存失败仍清理状态，保存失败另行记录，后续 same-attempt resume 继续因预期状态缺失而 fail-closed，不自动降级 fresh attempt，也不建立第二套状态真理。

fresh attempt 使用新 executionId、sessionId 和 stateSlotId，并通过 predecessorExecutionId 保留 attempt 谱系；parentExecutionId 只表达任务分解关系。ExecutorPlan 局部调整与 durable pause 的失败/超时降级均复用该 fresh-attempt 机制。

`ExecutionResumePolicy` 已接入节点物化。same-attempt command 显式携带 `resumeStateRequired` 到 Harness；合法持久历史允许恢复，预期状态缺失、slot/schema 不兼容或 owner 不一致时不复用旧 attempt。

## 事件、查询与 UI

当前唯一事件契约是 `ExecutionEvent`。每条事件都必须绑定 executionId、sessionId、runId 和 execution sequence；Task/Plan/Node 控制事实通过该 Execution 锚点和 payload 表达。`ai_task_event` 使用 eventId、tenant、executionId、sequence 与 fencingToken 持久化，outbox 负责至少一次发布。

`TaskQueryPort` 返回 `TaskDetails` 安全投影，包括 Task、RootResult 摘要、current TaskPlan/Nodes、Execution、最新 Dispatch，以及每个当前节点的最新 ExecutorPlan revision 与安全步骤。RootResult 只公开 result、planRevision、sourceNodeId、committedAt；ExecutorPlan 只公开 revision、status、goal 和步骤 ordinal/title/status/result/failure；不暴露 tenant/user、合同、checkpoint、lease、generation、fence、instruction、工具权限、policy snapshot、审批身份或内部 version。`inspect_tasks` 继续支持 PRIMARY Assistant 按 nodeId 读取局部计划并进行对话式解释或调整定位。

当前 REST 提供按 conversationId 查询当前用户 Task 列表、详情、pause、resume 和 cancel；`TaskQueryPort` 在装配计划与执行详情前按 tenant、user、conversation 过滤，WebUI 的 TanStack Query 缓存键也按 conversationId 隔离，`inspect_tasks` 复用同一查询边界。每次 AG-UI run 结束都会刷新 Task 查询；`PAUSING/CANCELING` 以 1 秒轮询，其余非终态以 3 秒轮询，终态停止轮询，因此后台 Task 的接受、运行和控制收敛均不依赖原 SSE；DIRECT Execution 不进入 Task API。当前产品没有独立任务中心，status/cursor 留待对应产品入口出现后单独设计。

## 持久化

canonical runtime 使用 `ai_task`、`ai_task_plan`、`ai_task_node`、`ai_task_dependency`、`ai_task_execution` 和 `ai_task_dispatch`，局部计划使用 `ai_executor_plan` 与步骤表。实体继承 BaseEntity 的 Long 主键和 `org_id`，跨 API/事件/调度继续使用稳定字符串业务 ID。

实际字段、索引、复合外键和 CHECK 以 Flyway migration 及对应 JPA Entity 为唯一物理真理，设计文档不再复制易漂移的目标 DDL。当前 schema 已用 `org_id` 复合身份、Task/Plan/Node/Execution 外键、active Dispatch 唯一索引和 `runtime_lock_version` 覆盖关键隔离与并发风险；`org_id` 是 BaseEntity 的持久化组织事实，不与旧表的 `tenant_id` 双写。conversation Task 列表和最新 ExecutorPlan 等查询的附加索引仅在运行指标证明存在扫描或延迟问题时另行迁移，不作为 AAF-115 正确性要求。正常调整、失败、暂停或取消均不 hard delete，用户可见业务数据由所属业务域继续持有。

## 响应式与事务边界

Controller/应用入口不在数据库事务内执行模型、Agent、Tool 或网络调用。JPA 操作由 `@Transactional` adapter 完成短事务；Reactive 调用处使用 boundedElastic 隔离阻塞工作，即时 Dispatch 通过虚拟线程交给 `AgentTaskRuntime`，定时恢复以数据库 durable queue 为事实来源，`TaskDispatchSignalPort` 只负责低延迟唤醒。专用 task persistence scheduler 不是 AAF-115 正确性前提，仅在运行指标证明公共线程池发生资源竞争时另行治理。

## 仍然有效的目标设计合同

以下约束来自已批准设计。它们即使尚未实现也必须保留，直到人类明确决定补代码、收窄需求或移出 AAF-115。

### 聚合根与结果提交

- Task、TaskPlan revision、TaskNode、Execution、TaskDispatch 各自只写本层状态，根 Task 只能由统一命令/物化事务推进。
- TaskPlan 必须冻结唯一结果生产者和确定性聚合合同；根结果只接受 current plan/root、current attempt、current dispatch generation/fence 的一次性提交，重复或迟到提交只能审计拒绝。
- Task Owner Assistant 承担最终语义完成和结果综合；服务端 CompletionValidator 只校验 DAG、证据、receipt、授权和执行身份，不能替代语义判断。

### 短事务、锁顺序与 fencing

- 影响 Task 的写命令必须使用稳定锁序：Task → current Plan → Nodes → Execution → active Dispatch → HITL/intent/receipt；同类集合按稳定业务键排序。
- 事务内只做身份校验、状态转换、事实/event/outbox 写入；Agent、模型、Tool、网络和通知只能在提交后执行。
- lease、generation 和 fencingToken 只以 active TaskDispatch 为当前真理；AgentState、Execution 快照和 event 不能重新开放执行权。
- claim、ACTIVE state load/save、side-effect intent、receipt 和 result commit 都必须重验 current dispatch；关闭后的 HITL 最终快照只允许 exact waiting 且确认没有新 active Dispatch。旧 revision、attempt、generation、fence 或 lease owner 的写入 fail-closed。

### promotion 与副作用互斥

- DIRECT promotion 和 side-effect intent 必须锁同一个 origin Execution，并以 promotionState/sideEffectEpoch 互斥。
- 只有仍为 ELIGIBLE、sideEffectEpoch=0 且不存在已提交、未知或进行中副作用事实的 DIRECT Execution 才能 promotion。
- promotion 原子创建 Task、最小 TaskPlan、TaskNode Execution、Dispatch、event/outbox，并让 origin Execution 进入 PROMOTED；不能复制隐藏 CoT、scratchpad 或未冻结 AgentState。

### pause、cancel 与责任主体切换

- 无损 pause 的目标合同是 request → runtime 保存状态 ACK → 关闭旧 Dispatch并提升 fence → PAUSED；保存失败必须标记 fresh-attempt required，不能假装可原地续接。
- cancel 请求事务先提交 `CANCELING`，阻断新 claim/readiness，关闭 active Dispatch 并提升 generation/fence；finalizer 完成前旧 worker 也不得提交结果。
- take-over 复用 durable pause：先进入 `PAUSING` 并关闭旧 Dispatch authority，ACK/timeout 收敛为稳定 `PAUSED` 后才切换为当前 Human owner；已稳定 `PAUSED` 可直接原子接管，普通 `PAUSING` 不允许静默升级。
- hand-back 只允许当前 Human owner 持有的 `PAUSED` Task，Owner Assistant 必须从冻结 `ExecutionContract.responsibleOwner` 恢复；责任主体变化后强制 fresh attempt，不复用旧 AgentState。

### same-attempt 与 fresh-attempt

- approval、structured clarification、PAUSE 和优雅停机只有在 task/node/execution/session、owner、授权画像、profile、state slot 和 current fence 全部匹配时才能 same-attempt。
- same-attempt 的 state slot 身份独立于 Dispatch generation/fence：换代 Dispatch 继续使用同一 executionId/sessionId/stateSlotId。ACTIVE 状态访问在数据库行锁内重验 current dispatch、generation、fence 与 lease owner；HITL 快照和终态清理使用更窄的精确授权。不能把 fence 拼入稳定状态槽 key，也不能因 key 变化把合法恢复静默降级为 fresh attempt。
- 任一身份变化、状态缺失/损坏/不兼容、终态失败或显式 fresh 策略都只为受影响节点/root 创建新 executionId、sessionId 和 state slot，并以 predecessorExecutionId 记录谱系。
- fresh attempt 不迁移旧 fence 的 AgentState；已完成兄弟节点不重跑，恢复节点完成后仍按全部 REQUIRED 前驱重新计算 join。

### DAG 校验与持久边界

- 持久化前必须确定性校验节点安全键、引用、自依赖、环、input binding、聚合引用、并行上限、预算和授权衰减；失败时不创建 TaskPlan revision、Execution 或 Dispatch，也不调用外部能力。
- 只有合法候选计划才成为 FROZEN TaskPlan；非法草案向当前调用方返回明确错误，不建立独立拒绝历史。

### canonical ExecutionEvent 与 outbox

- Task、Plan、Node、Interrupt 和 Dispatch 变化统一写为锚定真实 Execution 的 `ExecutionEvent`；DIRECT 的 taskId 可空，TaskNode 身份通过 nodeIdentity 表达，不建立第二套聚合事件模型。
- 同一 Execution 使用严格递增 sequence；跨 Execution 的 Task 读取使用全局 eventOffset。Task、Plan、Node、Execution 和 Dispatch 关系表是权威状态，事件不负责重建聚合。
- producer command 使用稳定 eventId/幂等身份；outbox 至少一次投递，消费者按 eventId 幂等。
- public payload 使用白名单，禁止暴露 prompt、CoT、凭证、完整内部错误或 AgentState。

### API、查询与 UI

- Task 控制命令提交短事务后返回 `200 + TaskDetails` 当前投影，不在 HTTP/SSE 请求中等待后台 Task 终态；后续状态由查询和现有事件刷新。
- Task 由对话分析或 promotion 创建；approval、clarification 和工具确认分别沿用现有 AG-UI/HITL/ToolApproval，不新增 create/retry/events 或统一 TaskInterrupt 平行 REST。
- interrupt resolution 必须指定 task/plan/node/execution/attempt/state-slot/dispatch identity 和幂等键，不允许按“最近 waiting”猜测。
- Task 查询必须在服务端按 tenant、owner、conversation 过滤；TaskDetails 是权威安全投影，TanStack Query 管服务端状态，Zustand 只管 UI。当前无独立任务中心，不提前要求 status/cursor。
- TaskPlan DAG 与节点内 ExecutorPlan 必须保持两级只读展示和调整边界，不能把步骤伪装成外层子智能体节点；UI 不直接编辑计划，调整通过对话命令形成新 revision/attempt。

### schema、保留与删除

- 物理 schema 以 Flyway migration 与 JPA Entity 为唯一真理，并用 tenant/org 范围的唯一约束、复合外键、乐观锁和 active Dispatch 唯一索引落实领域身份；任何新增约束先通过前向迁移落地，再同步本文。
- 查询索引属于指标驱动的性能治理；没有扫描量、延迟或资源竞争证据时，不为旧目标 DDL 提前增加索引。
- ExecutionEvent 与 AgentState 的在线保留周期、归档方式和 payload 上限属于上线前容量与合规参数；未冻结参数前不得宣称具备无限历史保留能力，也不得以运行失败触发业务数据清理。
- 正常调整、失败、暂停和取消只关闭运行执行权，保留历史计划、事件、receipt 和用户业务数据。
- hard purge 若保留为目标，必须有 retention、归档 manifest、外部引用扫描和 tombstone/显式删除顺序；不能通过 CASCADE 或运行失败自动触发。

## 实现映射

| 设计职责 | 当前生产实现 |
|---|---|
| 根状态、根结果和执行模型 | `Task`、`Task.RootResult`、`Execution`、`TaskDispatch`、`TaskSnapshot` |
| DAG、Owner finalizer 和 revision | `TaskPlan`、`TaskDependency`、`TaskDagService`、`JpaTaskPlanAdapter` |
| 物化、claim、root-result CAS、durable ownership、recovery | `TaskMaterializationPort`、`JpaTaskMaterializationAdapter` |
| 命令协调 | `TaskCommandService`、`TaskIngress` |
| 局部步骤计划 | `ExecutorPlanPort`、`JpaExecutorPlanAdapter`、`SubmitExecutorPlanTool`、`ReportExecutorStepTool` |
| 对话控制 | `InspectTasksTool`、`PromoteDirectTaskTool`、`AmendTaskTool`、`PauseTaskTool`、`ResumeTaskTool`、`TakeOverTaskTool`、`HandBackTaskTool`、`CancelTaskTool` |
| HITL | `RequestClarificationTool`、`HitlTransitionPort`、`JpaHitlTransitionAdapter`、`PersistentHitlCoordinator` |
| AgentState | `InvocationContext.stateSlotId`、`AgentScopeRuntimeContextMapper`、`DispatchGuardedAgentStateStore`、`TaskUnitOfWork.accessAgentState` |
| 查询/API | `TaskQueryPort`、`JpaTaskQueryAdapter`、`TaskController` |
| 前端 | `useTaskList`、`TaskPanel`、canonical Task API client |

## 实现差异与未实现项

此前静态审查识别的七项差异及后续发现的 same-attempt AgentState 正确性差异均已按人类确认方案收敛；hard purge 仍不属于 AAF-115。

| 已收敛项 | 当前实现 | 正确性边界 |
|---|---|---|
| same-attempt state slot | `InvocationContext` 显式携带 canonical `stateSlotId`；`AgentScopeRuntimeContextMapper` 以 tenant/user/task-or-direct/agent/stateSlot 形成稳定 key，不含 Dispatch fence | `DispatchGuardedAgentStateStore` 统一代理 AgentScope 自动读写；`TaskUnitOfWork.accessAgentState` 让 Task/Execution/Dispatch 行锁覆盖 Redis 操作。ACTIVE 逐次校验 current Dispatch；HITL 快照要求 exact waiting 且无新 active Dispatch；异步清理要求原 Dispatch 仍是最新代。旧 worker fail-closed，不提供旧 key fallback、迁移或 dual-write |

## 保守决策

- 外层 TaskPlan 调整关闭旧 revision 全部非终态执行，不跨 revision 复用结果。
- 局部 ExecutorPlan 调整只重启一个未完成节点；已完成节点改动升级为外层重规划。
- cancel 先提交 durable `CANCELING` 并立即关闭旧 Dispatch 执行权，再由 scheduler finalizer 推进 `CANCELED`；不等待 runtime ACK，也不删除计划历史、审计事实或用户业务数据。
- promotion 只允许零已登记副作用的 DIRECT Execution。
- 不新增兼容层、dual-write、旧 API adapter 或第二套任务根。

## 后续验证门

当前代码尚未执行 test/build/check/lint。后续获得授权后只调整已存在且与 canonical 合同冲突的测试，不新增测试文件或测试场景；现有测试未覆盖的验收项如实标记未验证。至少核对：

- TaskPlan/ExecutorPlan 调整的 revision、旧 Dispatch 关闭和迟到结果拒绝。
- MODIFY inputId 重放不重复创建 revision。
- CANCELING 请求后旧 Dispatch 立即失权、迟到 commit 被拒绝，且进程中断后 recovery scan 能幂等推进 CANCELED。
- 结构化 HITL 在非空 AgentState 下的 same-attempt/fresh-attempt 行为。
- list/get/cancel 与 WebUI TaskPanel 的真实接口契约。
- Flyway schema、JPA Entity 和 Repository 查询的一致性。

后续若扩大范围，必须先更新 requirement/tasks，并继续遵守“无兼容层、无业务数据自动删除”的边界。
