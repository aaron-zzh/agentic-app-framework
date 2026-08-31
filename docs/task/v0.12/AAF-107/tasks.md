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

- **状态**：[ ] 待开始
- **负责人**：architect
- **依赖**：无
- **范围**：
  - 记录两表设计、不可变 revision、确定性白名单自动批准、审批复用 `ai_hitl_approval`、只对 policy 标记任务生效。
  - 说明为何不塞进 `TaskBoardEntity.board_payload`（需独立版本/审批/乐观锁/步骤状态与审计查询）。
- **完成标准**：🔴 人类审核通过后方可启动 #10702。

### #10702 计划聚合、两表与状态机

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10701
- **范围**：
  - 追加 `ai_executor_plan`、`ai_executor_plan_step` 两表到 v16，含 `UNIQUE(tenant_id, delegated_task_id, revision)`、`UNIQUE(plan_id, step_key)`、`UNIQUE(plan_id, ordinal)`、`lock_version`。
  - 实现 `ExecutorPlan` / `ExecutorPlanStep` 聚合与状态机（`DRAFT→PLANNING→SUBMITTED→REVIEW_REQUIRED→APPROVED→EXECUTING→终态`），全部转换 CAS。
  - 步骤不变量：同 plan 最多一个 `RUNNING`，依赖未完成不得启动，`required_tools` 必须是冻结工具集子集。
  - 定义 `ExecutorPlanPort`；TaskBoard 只保存 `activeExecutorPlanId/revision` 快照引用，同一 transition 事务更新，不双写正文。
- **完成标准**：DB 约束阻止重复 revision/step；`SUBMITTED` 后正文不可变；`compile` 通过。

### #10703 planning execution 与 acting gate

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10702
- **范围**：
  - `DelegatedTaskCoordinator.executeSubTask` 的 EXECUTOR 分支改为 `requiresPlan → ensureApprovedPlan → executeApprovedPlan`；`commands.execute(childCommand)` 只能出现在已 claim 的步骤内。
  - planning execution 复用同一 Agent 定义与冻结画像，acting gate 只放只读业务工具 + `submit_executor_plan`；写工具产出标准 DENIED tool result，不靠 prompt 约束。
  - 定义结构化 `ExecutorPlanningContext`（目标、完成判据、聚合契约片段、`FrozenToolDescriptor`（仅名称+用途）、Role/Skill 摘要、上游产物引用、预算、禁止事项）。
  - `APPROVED → EXECUTING` 冻结 `planId/revision/hash` 进 `ExecutionProfileSnapshot`；恢复只能复用同 revision。
  - 非自主 L0 轻量规划路径作为 policy 可选项，不作默认，且不允许 L0 失败后静默 fallback 到自主 Agent。
- **完成标准**：未 APPROVED 前无任何写工具调用；未标记任务路径行为不变；`compile` 通过。

### #10704 审批与恢复

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10703、AAF-104 #10404
- **范围**：
  - 白名单命中则系统原子 `SUBMITTED → APPROVED`；未命中进 `REVIEW_REQUIRED` 并走 `PersistentHitlCoordinator`。
  - 恢复从首个未完成步骤继续；步骤副作用用 receipt/CAS 保证不重复。
  - executor 不得自行从 `REVIEW_REQUIRED` 进入执行。
- **完成标准**：重启、接管、重复回调下审批恰好一次；AgentState 丢失不丢计划；`compile` 通过。

### #10705 计划事件与 AG-UI 投影

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10704
- **范围**：
  - 8 个领域事件（`EXECUTOR_PLAN_CREATED` / `SUBMITTED` / `REVIEW_REQUIRED` / `APPROVED` / `REJECTED` / `EXECUTION_STARTED` / `STEP_*` / 终态 / `EXECUTOR_REPLAN_REQUESTED`）先进 task transition/outbox。
  - AG-UI 侧投影为 Step / Activity / interrupt outcome / Custom；`EXECUTOR_REPLAN_REQUESTED` 交回 COORDINATOR，不自动扩权。
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
