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

### #10704 计划步骤幂等推进 —— 范围因决策推翻与架构发现两次收窄

- **状态**：⚠️ 原范围已失效（2026-09-01），待协调者重新定义
- **原范围**（已作废，见下方推翻说明）：
  - ~~白名单命中则系统原子 `SUBMITTED → APPROVED`；未命中进 `REVIEW_REQUIRED` 并走 `PersistentHitlCoordinator`~~
  - ~~executor 不得自行从 `REVIEW_REQUIRED` 进入执行~~
  - ~~任务重启/接管后从计划正确步骤继续、不重复已完成步骤副作用（"步骤级恢复"整体）~~
- **推翻说明一**：ADR-006 新增「决策推翻」章节（2026-09-01）——对齐官方 `permission-system.html` 后确认"计划提交"这个动作不需要独立审批关卡，风险已由协调者派发子节点时的既有审批点与步骤执行阶段的工具授权链路覆盖。`ExecutorPlanAutoApprovalPolicy`（白名单判定）已随 #10703 一并删除，`REVIEW_REQUIRED` 状态在正常路径下不再产生。
- **推翻说明二**（2026-09-01）：核实 AgentScope [Context & AgentState](https://java.agentscope.io/v2/zh/docs/building-blocks/context.html) 文档后确认——**"中断后续跑"从来不是 AAF 侧要维护的显式步骤状态机**，而是 AgentScope core 自带能力：`AgentState.getContext()` 按 `(userId, sessionId)` 持久化完整对话历史，同一 `(userId, sessionId)` 重新发起 `call()` 时 core 自动续接。真正的缺口在于 `HarnessAgentExecutionAdapter` 当前无差别在 `doFinally` 删除 `AgentState`（"结束即删"，AAF-103 遗留），未区分"真正终态"与"被中断但会话应继续"，导致中断后历史被误删——这是**执行器核心机制**层面的问题，不是 EXECUTOR Plan Mode 独有，已整体拆出为独立故事 **AAF-110**（登记于 `docs/task/backlog.md`），本任务不再包含"中断续跑"本身。
- **收窄后剩余范围**：`ExecutorPlanStep.status` 转换（`startStep`/`completeStep`/`failStep`，已在 #10702 定义但从未被调用）在续跑场景下的**幂等接线**——即当 AAF-110 让同一 `sessionId` 的 execution 续接上历史后，模型继续调用步骤完成类工具时，AAF 侧记录不能因为"这一步之前可能已经上报过一次（例如中断发生在上报后、`completeStep` 落库前）"而产生重复副作用或状态机非法跳转。这是纯粹的审计记录幂等问题，不涉及"驱动该执行哪一步"。
- **依赖**：AAF-110（✅ 已完成，2026-09-02——中断续跑机制已落地，`HarnessAgentExecutionAdapter` 现在按责任主体是否变化正确分流状态槎删除，"同一 execution 需要幂等接线"这个场景已具备可验证前提）。
- **完成标准**（收窄后）：`startStep`/`completeStep`/`failStep` 在重复调用（同一步骤上报两次）时不产生非法状态跳转或重复副作用记录；`compile` 通过。

### #10705 计划事件与 AG-UI 投影

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10703（不再依赖已大幅收窄的 #10704）
- **范围**：
  - ~~8~~ **6** 个领域事件（`EXECUTOR_PLAN_CREATED` / `SUBMITTED` / `APPROVED` / `EXECUTION_STARTED` / `STEP_*` / 终态）先进 task transition/outbox——原 8 个事件中 `REVIEW_REQUIRED`/`REJECTED`/`EXECUTOR_REPLAN_REQUESTED` 三个随审批关卡取消而失效或需重新评估是否仍需要。
  - AG-UI 侧投影为 Step / Activity / Custom；不再需要 interrupt outcome（原本对应"待人工审批"这个中间态，已不存在）。
  - 禁止 planner 直接向 SSE 发"计划已批准"；Custom 不含计划正文或内部 Prompt。
- **完成标准**：UI 可从 snapshot 重建计划进度；`compile` 通过。

## 新增任务

> 开发过程中发现需要新增的任务，由开发者提出，协调者评估后写入

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
