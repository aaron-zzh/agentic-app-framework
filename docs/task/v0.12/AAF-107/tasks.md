---
level: Practice
layer: Product
purpose: 拆分 AAF-107 EXECUTOR 先规划再执行的聚合建模、planning execution、审批与恢复任务
status: draft
version: 1.0.0
date: 2026-09-01
author: AaronZZH & Kiro
tags:
  - AAF-107
  - EXECUTOR
  - 计划模式
  - 技术任务
related:
  - ../../../design/audit/2026-09-01-harness-landing-plan.md
gains:
  - 能让被标记任务的执行者先产出可审计计划再执行
  - 能让未批准计划零副作用且跨重启可恢复
---

# AAF-107 EXECUTOR 持久计划任务

## 任务约束

- 技术真理源：[Harness 落地计划 · EXECUTOR 先规划再执行](../../../design/audit/2026-09-01-harness-landing-plan.md)。
- 范围收窄：**只对 policy 显式标记需要计划的任务生效**，未标记任务行为完全不变。
- 计划由**执行 Agent 自己产出**，作为同一 Agent 定义下的**独立 planning execution**；不新建专用 planner Agent，不用 `ToolSuspendException` 在同一次 execution 内审批（会破坏 per-execution 画像冻结不变量）。
- 自动批准只由**确定性白名单**驱动；模型给出的 `risks` 只作展示与审计，不作门控输入。
- 审批复用 `ai_hitl_approval` + `ai_hitl_recovery`，不新建审批表；不调用官方 `plan_exit`，不用 `PLAN.md` / `tasksContext` 作为计划事实。
- 风险等级：🔴 高（DB schema + 权限 + 状态机）。
- **阶段约束**：新表直接追加进 `apps/service/aaf-api/src/main/resources/db/migration/v16__intelligent_runtime_schema.sql`，不新增迁移文件、不写回滚脚本；断言补进既有测试，不新增测试文件；不执行 `check` / `acceptance`；准出最低要求 `pnpm nx compile service` 通过。

## 技术任务

### #10701 立 ADR：EXECUTOR plan 表与审批策略

- **状态**：✅ 已完成（2026-09-01，人类已审核通过）— Kiro
- **负责人**：architect
- **依赖**：无
- **范围**：
  - ✅ 已产出 [ADR-006](../../../design/adr/ADR-006-executor-plan-mode.md)（`accepted`）：记录两表设计、不可变 revision 的推论依据、确定性白名单自动批准、审批复用 `ai_hitl_approval`、只对 policy 标记任务生效。
  - ✅ 已说明为何不塞进 `TaskBoardEntity.board_payload`（需独立版本/审批/乐观锁/步骤状态与审计查询）。
- **完成标准**：🔴 人类审核通过后方可启动 #10702。
- **实际结果**：人类已审核通过，#10702 解锁。

### #10702 计划聚合、两表与状态机

- **状态**：✅ 已完成（2026-09-01）— developer-service
- **负责人**：developer-service
- **依赖**：#10701
- **范围**：
  - ✅ 追加 `ai_executor_plan`、`ai_executor_plan_step` 两表到 v16，含 `UNIQUE(tenant_id, task_id, revision)`、`UNIQUE(plan_id, step_key)`、`UNIQUE(plan_id, ordinal)`、`lock_version`；额外加 `uk_executor_plan_step_running` 部分唯一索引在 SQL 层强制"同 plan 最多一个 RUNNING"。
  - ✅ 实现 `ExecutorPlan` / `ExecutorPlanStep`（`assistant/model/plan/`）与状态机（`DRAFT→PLANNING→SUBMITTED→REVIEW_REQUIRED→APPROVED→EXECUTING→终态`），全部转换经 `JpaExecutorPlanAdapter` 显式比对 `lockVersion` 后 CAS。
  - ✅ 步骤不变量：`startStep` 校验依赖已全部 `COMPLETED`、同 plan 无其它 `RUNNING`；`required_tools`/`completionCriteria` 落库但白名单子集校验留给 #10703（届时才有冻结工具集合可比对）。
  - ✅ 定义 `ExecutorPlanPort`（`assistant/port/plan/`）；`TaskBoard` 暂不引入 `activeExecutorPlanId` 快照字段——`findActive` 直接查询最新未终止 revision，避免在 #10703 前就改动 `TaskBoard` 结构。
  - ✅ `ExecutionPolicy` 新增 `requiresPlan` 布尔字段（默认 `false`，二级构造器保证既有 6 参数调用点不变）。
- **完成标准**：DB 约束阻止重复 revision/step；`SUBMITTED` 后正文不可变；`compile` 通过。
- **实际结果**：两表 + `ExecutorPlan`/`ExecutorPlanStep`/`ExecutorPlanPort`/`JpaExecutorPlanAdapter` 落地；Spring Bean 已注册（`AssistantInfrastructureAutoConfiguration.executorPlanPort`）。`TaskBoard.activeExecutorPlanId` 快照引用推迟到 #10703（那时才真正需要从编排层读取"当前计划是哪个"）。

### #10703 planning execution 与 acting gate

- **状态**：✅ 已完成（2026-09-01）— developer-service
- **负责人**：developer-service
- **依赖**：#10702
- **范围**：
  - ✅ **关键发现**：acting gate 不需要新建 AgentScope middleware——AAF 已有 `ControlMode.READ_ONLY` 经 `DefaultToolGateway.invoke` → `ToolAuthorizationContext.requireVisible` 强制拒绝非只读工具，与官方 `PlanModeMiddleware` 的白名单拒绝语义等价，直接复用。
  - ✅ `SubmitExecutorPlanTool`（`submit_executor_plan`）：`ContextAwareToolHandler` 实现，只暴露 `goal`/`risks`/`verification`/`steps`；`planId`/`expectedLockVersion` 由工具自己按 `(tenantId, taskId, boardId)` 现查现用，不接受模型传入。
  - ✅ `AssistantCommand.forSubTask` 新增 `ControlMode` 覆盖重载，让 planning execution 单独跑 `READ_ONLY` 而不影响任务整体 `controlMode`。
  - ✅ ADR-006 增补节（三处人类拍板）：① `ExecutorPlan`/`ExecutorPlanStep` 适用范围从"仅 EXECUTOR"放宽为"任何自行执行的节点"（含 COORDINATOR）；② 是否规划改为"协调者建议 + AAF 侧 `PlanRequirementPolicy` 确定性规则兜底"两层判定，纠正 #10702 把 `requiresPlan` 放进 `ExecutionPolicy`（core 执行边界参数）的层错误；③ 确认不需要 `plan_enter`/`plan_write`/`plan_exit` 工具（AAF 是两次独立 execution 而非同次调用内切换）。
  - ✅ `PlanRequirementPolicy`（新增判定接口，与 `ExecutorPlanAutoApprovalPolicy` 并列独立）+ `TaskBoard.SubTask.requiresPlan` 字段 + `CoordinationPlan.ExecutorAssignment.suggestsPlan`（协调者建议信号，可空）。
  - ✅ `DelegatedTaskCoordinator.executeSubTask` 按 `subTask.requiresPlan()` 分流：`executePlannedSubTask` 三段式（`runPlanningExecution` → 人工审批/自动批准判定 → `executeApprovedPlanSteps`），未标记节点走原有路径零改动。
  - ✅ **建表阶段发现并修复 #10702 遗留 bug**：`ai_executor_plan` 的 revision 唯一约束缺 `board_id`，导致同任务多节点规划会撞号；已改为 `(tenant_id, task_id, board_id, revision)`，`ExecutorPlanPort.findActive` 同步加 `boardId` 参数。
  - ✅ 板状态转换补全：新增 `TaskBoardPort.awaitSubTaskAuthorization`（按 `subTaskId` 转 `AWAITING_AUTHORIZATION`，不建 `HumanApproval` 记录——那部分留给 #10704），配合 `TaskBoard.awaitAuthorization(String subTaskId)` 重载。
  - ⚠️ `submit_executor_plan` 尚未接入 `ai_tool_catalog`——**已发现但不属于本任务范围的缺口**：全仓 migration 无任何 `ai_tool_catalog` seed 数据，`support.handoff`/`context.load` 等既有内置工具在生产环境同样缺注册，需要管理员后台手工注册才可用。已记录，不在本任务处理。
  - ⚠️ `AssistantInfrastructureAutoConfiguration.delegatedTaskCoordinator` Bean 仍用 13 参构造器（不含 `planRequirement`/`plans`），本次未启用规划能力，只是让代码可编译且向后兼容——正式启用是独立决定，留给后续任务或人类明确指示。
- **完成标准**：未 APPROVED 前无任何写工具调用；未标记任务路径行为不变；`compile` 通过。
- **已知限制（记入 dev-log 供 #10704/#10801 评估）**：
  - `runPlanningExecution` 结束后把节点转 `RETRYABLE`（自动批准）或 `AWAITING_AUTHORIZATION`（转人工）时复用了既有 `interrupted(true)`/`awaitingAuthorization()` 领域方法，两者都会消耗 `attempts` 重试预算，多次规划-执行往返可能提前耗尽预算——不是本次引入的新机制缺陷，但组合使用后影响会被放大，需要评估是否要为 planning 往返单独计数。
  - `beginPlanning` 的 revision 分配（`max+1`）非事务串行化，同节点理论上的并发建计划请求可能因 `uk_executor_plan_revision` 冲突而失败（fail-closed），当前业务场景不存在真正并发触发，未做额外加锁。

### #10704 计划步骤幂等推进 —— 范围因决策推翻与架构发现三次收窄，最终判定为独立设计缺口

- **状态**：⚠️ 已重新定义，实际归入 #10705 一并解决（2026-09-02）
- **原范围**（已作废，见下方推翻说明）：
  - ~~白名单命中则系统原子 `SUBMITTED → APPROVED`；未命中进 `REVIEW_REQUIRED` 并走 `PersistentHitlCoordinator`~~
  - ~~executor 不得自行从 `REVIEW_REQUIRED` 进入执行~~
  - ~~任务重启/接管后从计划正确步骤继续、不重复已完成步骤副作用（"步骤级恢复"整体）~~
- **推翻说明一**：ADR-006 新增「决策推翻」章节（2026-09-01）——对齐官方 `permission-system.html` 后确认"计划提交"这个动作不需要独立审批关卡，风险已由协调者派发子节点时的既有审批点与步骤执行阶段的工具授权链路覆盖。`ExecutorPlanAutoApprovalPolicy`（白名单判定）已随 #10703 一并删除，`REVIEW_REQUIRED` 状态在正常路径下不再产生。
- **推翻说明二**（2026-09-01）：核实 AgentScope [Context & AgentState](https://java.agentscope.io/v2/zh/docs/building-blocks/context.html) 文档后确认——**"中断后续跑"从来不是 AAF 侧要维护的显式步骤状态机**，而是 AgentScope core 自带能力：`AgentState.getContext()` 按 `(userId, sessionId)` 持久化完整对话历史，同一 `(userId, sessionId)` 重新发起 `call()` 时 core 自动续接。真正的缺口在于 `HarnessAgentExecutionAdapter` 当前无差别在 `doFinally` 删除 `AgentState`（"结束即删"，AAF-103 遗留），未区分"真正终态"与"被中断但会话应继续"，导致中断后历史被误删——这是**执行器核心机制**层面的问题，不是 EXECUTOR Plan Mode 独有，已整体拆出为独立故事 **AAF-110**（登记于 `docs/task/backlog.md`），本任务不再包含"中断续跑"本身。
- **推翻说明三**（2026-09-02，AAF-110 完成后重新核实 #10705 时发现）：原判断"`startStep`/`completeStep`/`failStep` 只需要幂等接线"建立在错误假设上——核实 `DelegatedTaskCoordinator.executeApprovedPlanSteps` 完整代码后发现，这个方法让模型在**一次 execution 内自由推进全部已批准步骤**（不逐步骤显式驱动，只在最终收敛处判断整体 `EXECUTION_COMPLETED`/`EXECUTION_FAILED`），`startStep`/`completeStep`/`failStep` **在任何路径下都从未被调用**，不存在"重复上报"的幂等问题——真正缺失的是"调用点"本身，不是"调用点的幂等性"。第一性原理判断：AAF 侧不介入模型的推理过程，`ExecutorPlanStep.Status` 要有真实数据，必须由模型自己显式上报步骤边界，而非从底层工具调用事件流反推（一个 step 可能对应 0~N 次工具调用，无法可靠映射回步骤边界）。**归入 #10705 一并解决**：新增 `ReportExecutorStepTool`（`report_executor_step`），复用 `SubmitExecutorPlanTool` 已确立的"模型主动上报"模式；工具内部按 `outcome` 分流调用 `startStep`/`completeStep`/`failStep`，`expectedLockVersion` 每次现查现用（`findSteps` 读最新行），天然具备幂等——重复上报同一 `outcome` 会因状态机校验失败（如 `completeStep` 要求当前必须是 `RUNNING`）而 fail-closed，不产生非法跳转或重复副作用记录，原任务"幂等接线"的诉求已通过"调用点即状态机守卫"的方式满足，不需要额外幂等层。
- **完成标准**：随 #10705 一并验收。

### #10705 计划事件与 AG-UI 投影 —— 含 #10704 步骤上报缺口

- **状态**：✅ 已完成（2026-09-02）— developer-service
- **负责人**：developer-service
- **依赖**：#10703、AAF-110（均已完成）
- **设计结论（第一性原理，人类已确认按此推进）**：
  - **计划级 6 个事件**（对齐 `ExecutionEventType` 现有"终态一律拆成独立枚举值"模式，即 `SUBTASK_*` 五值先例，不用 outcome 字段合并）：`EXECUTOR_PLAN_CREATED`（`beginPlanning`）/`EXECUTOR_PLAN_SUBMITTED`（`submit`）/`EXECUTOR_PLAN_EXECUTION_STARTED`（`claimApproved`）/`EXECUTOR_PLAN_COMPLETED`（`complete`）/`EXECUTOR_PLAN_FAILED`（`fail`）/`EXECUTOR_PLAN_CANCELLED`（`cancel`）。不设 `EXECUTOR_PLAN_APPROVED`/`EXECUTOR_PLAN_REJECTED`——ADR-006 固定自动批准，`REVIEW_REQUIRED`/`REJECTED` 转换是死代码路径，`SUBMITTED` 与"批准"在当前实现里永远同时发生，拆分没有独立观察价值。
  - **步骤级 3 个事件**：`EXECUTOR_PLAN_STEP_STARTED`/`EXECUTOR_PLAN_STEP_COMPLETED`/`EXECUTOR_PLAN_STEP_FAILED`，由新增 `ReportExecutorStepTool` 驱动（见 #10704 推翻说明三）。
  - **事件持久化机制**：`ExecutorPlan` 是独立聚合根，不经过 `TaskTransition`（那是 `AssistantTask`/`TaskBoard` 级别机制）。复用 `SupportHandoffTool` 已确立的"工具直接注入 `ExecutionEventStorePort` 自行 `append`"模式；`DelegatedTaskCoordinator` 侧新增同名 `eventStore` 字段与 `emitPlanEvent` 辅助方法，在持有完整 `InvocationContext` 的方法内（`runPlanningExecution`/`executePlannedSubTask`/`executeApprovedPlanSteps` 终态收敛）构造并发出对应事件。
  - **已知限制并接受**：`cancelRunningChildren`（`stop`/`takeOver` 级联取消场景，调用 `plans.cancel(...)`）方法作用域内没有 `InvocationContext`（只有 `TenantId`/`UserId`/`TaskId`/`Lease`，`AssistantTask` record 本身也不携带 `conversationId`/`sessionId`/`runId`），无法构造完整 `ExecutionEvent`。判断：审计事件流的完整性是可观测性问题，不是正确性问题（`ExecutorPlan.status` 数据库字段本身已经正确更新为 `CANCELLED`）——不为凑齐上下文引入额外查询，`EXECUTOR_PLAN_CANCELLED` 事件在此路径下不发出，只做状态转换。
- **实施内容**：
  - `ExecutionEventType` 新增 9 个枚举值；`AafAiTaskEventRegistry.descriptor` 穷举 switch 补齐 9 个分支（全仓核实这是唯一一处对 `ExecutionEventType` 做穷举 switch 的地方，其余 switch 均带 `default`）。
  - 新增 `ReportExecutorStepTool.java`（`report_executor_step` 工具）。
  - `SubmitExecutorPlanTool` 新增 `ExecutionEventStorePort` 依赖，`submit(...)` 成功后发出 `EXECUTOR_PLAN_SUBMITTED`。
  - `DelegatedTaskCoordinator` 新增 `ExecutionEventStorePort eventStore` 字段（新构造器重载，向后兼容，`plans`/`eventStore` 均允许为 `null`）+ `emitPlanEvent`/`planValues` 辅助方法；4 个调用点（`beginPlanning`/`claimApproved`/`complete`/`fail`）接入事件发出。
  - `executeApprovedPlanSteps` 的子执行 prompt 追加步骤上报指令（引导模型调用 `report_executor_step`）。
  - Spring 配置：`submitExecutorPlanTool`/新增 `reportExecutorStepTool` 两个 Bean 追加 `ExecutionEventStorePort` 依赖前提；`delegatedTaskCoordinator` Bean 接入 `eventStore`（`plans` 仍传 `null`——是否启用规划能力是独立决定，见 #10703 dev-log，未启用时 `emitPlanEvent` 调用路径本身走不到）。
  - **意外发现并修复的 AAF-110 测试回归**：`pnpm nx test service` 首次运行暴露 `HarnessAgentExecutionAdapterTest` 12 处失败——AAF-110 新增的 `GracefulShutdownManager.bindStateSaver(agent, ...)` 内部用 `ConcurrentHashMap.put(agent.getAgentId(), ...)`，测试 mock 的 `agent.getAgentId()` 未 stub 返回 `null`，`ConcurrentHashMap` 不允许 `null` key 直接抛 `NullPointerException` 中断整条执行链（AAF-110 当时只跑了 `compile`，未跑 `test`，遗漏了这批回归）。用 `git stash` 隔离验证确认这些失败在 AAF-110 提交（`f08a2a29`）之后即已存在，与 #10705 改动无关；修复方式：`HarnessAgentExecutionAdapterTest` 补充 `lenient().when(agent.getAgentId()).thenReturn("agent-test")` 公共桩（生产环境 `agentId` 由 `AgentBase` 构造时赋值永不为空，纯粹是 mock 契约缺失）。
  - **意外发现并修复的 AAF-105 #10506 测试缺陷**：`SubmitCoordinationPlanToolTest.should_accept_plan_within_frozen_baseline` 同样在 stash 验证中确认为独立于本次改动的既有失败——测试 fixture 用 `TaskBoard.coordinated(...)` 构造协调者节点，初始态是 `PENDING`，但 `TaskBoard.applyCoordinationPlan` 要求协调者必须 `RUNNING`。改为手工构造 `RUNNING` 态的 `SubTask`（对齐 `DelegatedTaskCoordinatorAggregatorCompletionTest` 已确立的 fixture 模式）。
- **完成标准**：UI 可从 snapshot 重建计划进度；`compile` 通过；`pnpm nx test service` 全绿（415 个 aaf-framework 测试 + aaf-api/aaf-auto-dev 全部测试，0 失败 0 错误）。

## 新增任务

> 开发过程中发现需要新增的任务，由开发者提出，协调者评估后写入

### #10706 架构改造：协调者能力扩展为父集，两阶段规划合并为单次 execution

- **状态**：✅ 已完成（2026-09-02）— Kiro
- **负责人**：developer-service
- **依赖**：#10703、#10705（均已完成）
- **提出背景**：#10705 落地后发现两个进一步收窄机会——(1) "只有 EXECUTOR 分支能规划"与"协调者能力应为父集"存在张力；(2) 独立前置复杂度分类步骤（`TaskComplexityAnalyzer`）与协调者自主判断三档语义重复，且判断质量更差（只看目标文本，无完整上下文）。完整决策依据见 [ADR-006](../../../design/adr/ADR-006-executor-plan-mode.md) 新增章节「架构改造：协调者能力扩展为父集，两阶段规划合并为单次 execution」。
- **范围**：
  - ✅ `TaskBoard.SubTask.requiresPlan`、`CoordinationPlan.ExecutorAssignment.suggestsPlan` 字段删除；`PlanRequirementPolicy.java` 接口整体删除。
  - ✅ `DelegatedTaskCoordinator.executeSubTask` 重写为单次 execution 模式；`executePlannedSubTask`/`runPlanningExecution`/`executeApprovedPlanSteps` 三方法整体删除，替换为 `finalizeSubTaskExecution` 统一四态判断收尾（新增 `coordinatorSubmittedPlan`/`finalizePlannedStepExecution`/`failOrRecordPlanFailure` 三个辅助方法）。
  - ✅ `SubmitExecutorPlanTool.submit(...)` 自持三步（`beginPlanning`+`submit`+`claimApproved` 同一次调用内完成），不再有独立 `APPROVED` 等待中间态。
  - ✅ `TaskComplexityAnalyzer`/`ModelDrivenTaskComplexityAnalyzer`/`TaskAnalysis` 三个类型及全部消费方彻底删除；`AssistantExecutionService.analyzedBoard` 简化为始终返回 `coordinated`。
  - ✅ 新建内置 Skill `builtin-task-decomposition` 承载三档判断指导文案（`v12__init_seed_data.sql`），`tool_access_mode=INHERIT`，`ai_system_skill_binding` `ALWAYS` 全局绑定，不限定到具体 Role。
  - ✅ 真理源文档同步：`runtime.md`「任务复杂度判定」章节与流程图重写、`architecture.md`/`coordination.md` 关联表述更新、ADR-006 追加决策章节。
- **完成标准**：`pnpm nx compile service` 通过（main+test）；不留独立前置分类步骤；协调者/执行者能力边界仅剩"是否允许派生子节点"这一权限点。
- **实际结果**：全部范围完成，`pnpm nx compile service` BUILD SUCCESS。评估放开 `AssistantApplicationService` 两处"SYSTEM Skill 禁止声明工具要求"校验后判定不必要（`INHERIT` 机制已覆盖需求），未采用，两处校验维持原状。

## 评审状态（🔴 高风险适用）

| 阶段 | 执行次数 | 最后执行 | 状态 | 必须 |
|------|---------|---------|------|------|
| product（需求细化） | 0 | — | ⏳ PENDING | 🔴 是 |
| architect（技术设计） | 0 | — | ⏳ PENDING | 🔴 是 |
| designer（UI 审查） | 0 | — | ⏳ PENDING | 计划进度展示时 |
| developer（编码） | 0 | — | ⏳ PENDING | 🔴 是 |
| architect（代码审查） | 0 | — | ⏳ PENDING | 🔴 是 |
| tester（验收测试） | 0 | — | ⏳ PENDING | 🔴 是 |
| qa（过程审计） | 0 | — | ⏳ PENDING | 🔴 是 |
