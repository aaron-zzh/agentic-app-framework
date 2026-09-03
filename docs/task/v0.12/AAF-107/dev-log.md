- ✅ 2026-09-02 — Kiro：架构改造（选项 B）——协调者能力扩展为父集，两阶段规划合并为单次 execution，`TaskComplexityAnalyzer` 前置判断彻底删除

### 架构改造：协调者一次 execution 内自主判断三档，删除独立前置复杂度分类步骤

源于 #10705 讨论中发现的进一步简化机会，实质已超出 #10705 原定"计划事件与 AG-UI 投影"范围，是 ADR-006 的下一步演进（决策记录见 ADR-006 新增章节「架构改造：协调者能力扩展为父集，两阶段规划合并为单次 execution」）。

**核心变更**：
- `DelegatedTaskCoordinator.executeSubTask` 不再判断 `subTask.requiresPlan()`（字段已删），改为始终发起单次"完整能力 execution"——`submit_coordination_plan`/`submit_executor_plan`/全部业务工具同时可见，模型自主判断该调哪个。原 `executePlannedSubTask`/`runPlanningExecution`/`executeApprovedPlanSteps` 三方法整体删除，替换为统一的 `finalizeSubTaskExecution` 四态判断收尾（授权等待/澄清等待/失败或未完成/成功，成功再细分协调派生成功、步骤计划成功、简单直答成功）。
- `SubmitExecutorPlanTool.submit(...)` 自持三步：无活跃计划先 `beginPlanning`，随后同一次调用内依次 `submit`+`claimApproved`，一步到位转 `EXECUTING`，不再有独立 `APPROVED` 等待中间态。
- `TaskComplexityAnalyzer`/`ModelDrivenTaskComplexityAnalyzer`/`TaskAnalysis`/`PlanRequirementPolicy` 四个类型及全部消费方彻底删除（含 `AssistantInfrastructureAutoConfiguration` 对应 Bean、`AssistantExecutionServiceTest` 构造参数）；`AssistantExecutionService.analyzedBoard` 简化为始终返回 `TaskBoard.coordinated`。
- `TaskBoard.SubTask.requiresPlan`、`CoordinationPlan.ExecutorAssignment.suggestsPlan` 字段删除；`TaskBoard.applyCoordinationPlan` 简化为单一方法（不再需要判定函数参数）。
- 协调者三档判断依据改为新建内置 Skill `builtin-task-decomposition` 承载（对比 `builtin-self-learning` 既有模式，行为指导走 Skill 正文不硬编码 Java）；`tool_access_mode=INHERIT`（复用既有"已审核系统 Skill 默认工具"机制，跳过 `skillRequiredToolNames` 限制直接放行 Role/Agent 交集，不需要逐个 Role 配置白名单），`ai_system_skill_binding` `ALWAYS` 全局绑定不限定到具体 Role。

**已核实不受影响**：AAF-110 中断续跑机制、事件投影机制均是通用机制，不依赖单/双 execution 模式；"前注意"（Role/Skill 路由解析，`runtime.md` 步骤三）与"任务复杂度判断"（步骤六）是两个独立职责，本次只删步骤六，前注意完整保留。

**已核实的关键设计纠错**：最初尝试放开 `AssistantApplicationService` 两处"SYSTEM Skill 初版禁止声明工具要求"硬性校验，评估后判定不必要——核实 `DefaultEffectiveToolResolver.resolve` 发现 `INHERIT` 模式已是为此场景设计的既有机制（`requireInheritOnlyForBuiltIn` 门禁要求 `built_in=true`），复用比放开现有安全校验更贴合"优先已有模式"原则，两处校验已撤销改动维持原状。

**已核实不需要采纳的方案**：手动给 `system.role.platform-guide.tool_whitelist` 追加工具名——用户指出"默认技能/工具不应该要求先绑定角色"，核实后确认 `INHERIT` 模式下 Role 白名单为空也不限制，该手动配置已撤销。

**保留未删（可复议）**：`TaskBoard.single` 工厂方法、`DelegatedTaskCoordinator.submit(AssistantCommand)` 单参数重载——全仓无真实调用方，判断为公开 API 按不做任务外重构原则保留。

**实现文件**：`TaskBoard.java`、`CoordinationPlan.java`、`DelegatedTaskCoordinator.java`、`SubmitExecutorPlanTool.java`、`AssistantInfrastructureAutoConfiguration.java`、`AssistantExecutionService.java`（aaf-api）、`v12__init_seed_data.sql`（新增 `builtin-task-decomposition` Skill + `INHERIT` 访问模式）；删除 `TaskComplexityAnalyzer.java`/`ModelDrivenTaskComplexityAnalyzer.java`/`TaskAnalysis.java`/`PlanRequirementPolicy.java`。同步更新真理源文档：`runtime.md`「任务复杂度判定」章节与流程图、`architecture.md`/`coordination.md` 关联表述、`ADR-006-executor-plan-mode.md` 新增决策章节。

**验证**：`pnpm nx compile service` BUILD SUCCESS（main+test，多次验证，含最终 `INHERIT` 方案版本）。测试文件同步修复：`DelegatedTaskCoordinatorAggregatorCompletionTest.java`、`SubmitCoordinationPlanToolTest.java`。



- ✅ 2026-09-02 — Kiro：#10704 三次收窄+#10705 完成，`report_executor_step` 工具补齐步骤上报缺口，6+3 个计划事件落地

### #10704 第三次推翻：从"幂等接线"改判为"补齐缺失调用点"

核实 `DelegatedTaskCoordinator.executeApprovedPlanSteps` 完整代码后发现，原判断"`startStep`/`completeStep`/`failStep` 需要幂等接线"建立在错误假设上——这三个方法在任何路径下**从未被调用**，不是"可能重复调用需要防重"，是"完全没有调用点"。该方法让模型在一次 execution 内自由推进全部已批准步骤，AAF 侧不逐步骤显式驱动，只在最终收敛处判断整体完成/失败。

第一性原理判断（对齐行业最佳实践：LangGraph plan-and-execute、Claude Code TodoWrite 模式）：AAF 侧不介入模型推理过程，`ExecutorPlanStep.Status` 要有真实数据必须由模型自己显式上报步骤边界，不能从底层工具调用事件流反推（一个 step 可能对应 0~N 次工具调用，无法可靠映射）。解法：新增 `ReportExecutorStepTool`（`report_executor_step`），复用 `SubmitExecutorPlanTool` 已确立的"模型主动上报"模式。`expectedLockVersion` 每次现查现用，天然幂等——重复上报同一 `outcome` 因状态机校验失败（如 `completeStep` 要求当前是 `RUNNING`）fail-closed，原"幂等接线"诉求已通过"调用点即状态机守卫"满足，不需要额外幂等层。#10704 归入 #10705 一并解决。

### #10705 事件清单与持久化机制设计

- 计划级 6 个（对齐 `SUBTASK_*` 五值先例，终态独立枚举值不用 outcome 合并）：`CREATED`/`SUBMITTED`/`EXECUTION_STARTED`/`COMPLETED`/`FAILED`/`CANCELLED`。不设 `APPROVED`/`REJECTED`——ADR-006 固定自动批准，两者是死代码路径。
- 步骤级 3 个：`STEP_STARTED`/`STEP_COMPLETED`/`STEP_FAILED`，由 `ReportExecutorStepTool` 驱动。
- **架构约束发现**：`ExecutorPlan` 是独立聚合根，不经过 `TaskTransition`（那是 `AssistantTask`/`TaskBoard` 级机制）。核实 `SupportHandoffTool` 后确认现成模式——工具直接注入 `ExecutionEventStorePort` 自行 `append`。`DelegatedTaskCoordinator` 新增同名字段+`emitPlanEvent` 辅助方法，在持有完整 `InvocationContext` 的方法内发出事件。
- **已知限制（评估后接受，不处理）**：`cancelRunningChildren`（`stop`/`takeOver` 级联取消）作用域内没有 `InvocationContext`（`AssistantTask` record 不携带 `conversationId`/`sessionId`/`runId`），无法构造完整事件。判断审计事件完整性是可观测性问题不是正确性问题（`ExecutorPlan.status` 数据库字段本身已正确更新），不为凑上下文引入额外查询，此路径下 `EXECUTOR_PLAN_CANCELLED` 事件不发出。
- **意外发现并修复的 AAF-110 测试回归**：`pnpm nx test service` 暴露 `HarnessAgentExecutionAdapterTest` 12 处失败——`GracefulShutdownManager.bindStateSaver` 内部 `ConcurrentHashMap.put(agent.getAgentId(), ...)`，mock 未 stub `getAgentId()` 返回 `null` 直接 NPE。`git stash` 隔离验证确认失败在 AAF-110 提交（`f08a2a29`）后即存在，与 #10705 无关（AAF-110 当时只跑 `compile` 未跑 `test`）。修复：补 `lenient().when(agent.getAgentId()).thenReturn("agent-test")`。
- **意外发现并修复的 AAF-105 #10506 测试缺陷**：`SubmitCoordinationPlanToolTest` fixture 用 `TaskBoard.coordinated(...)`（协调者初始态 `PENDING`），但 `applyCoordinationPlan` 要求 `RUNNING`。改为手工构造 `RUNNING` 态 `SubTask`。
- 验证：`pnpm nx compile service` BUILD SUCCESS；`pnpm nx test service` BUILD SUCCESS（415 个 aaf-framework 测试 + aaf-api/aaf-auto-dev 全部测试，0 失败 0 错误）。

---

## #10704 设计对比：AAF 侧步骤驱动 vs AgentScope core 会话续接（2026-09-01）

- ⚠️ 2026-09-01 — Kiro：核实 AgentScope [Context & AgentState](https://java.agentscope.io/v2/zh/docs/building-blocks/context.html) 后推翻此前"AAF 侧维护当前第几步驱动执行"的设计方向

### 结论

`AgentState.getContext()` 按 `(userId, sessionId)` 自动持久化完整对话历史，同一 `(userId, sessionId)` 重新发起 `call()` 时 core 自动续接——这是 core 自带能力，不需要 AAF 侧另建"当前第几步"状态机来驱动执行。`ExecutorPlanStep.status` 因此定位为**模型主动上报后的只读审计事实**（进度展示 + 幂等边界），不是流程驱动信号。

### 发现的真实缺口

`HarnessAgentExecutionAdapter`（AAF-103 落地）当前在 `doFinally` 无差别删除 `AgentState`（`release`→`deleteExecutionState`），未区分"真正终态完成/失败"与"被中断但会话应该继续"。核实官方文档得知：状态保存发生在 `call()` **正常返回**时（含 `interrupt()` 触发的提前返回，官方文档："推理循环...保存状态并返回部分结果"），不是每条消息增量落盘。这意味着"中断"在 core 层走的是"正常返回"路径，状态会被保存——但 AAF 侧无差别删除的逻辑会在中断后立即清空刚保存的历史，导致下次同 `sessionId` 重新发起时历史已经不在了。

### 任务拆分决定（人类拍板）

该问题是**执行器核心机制**层面的缺口，影响面不止 EXECUTOR Plan Mode（任何依赖"中断后续跑"的场景都会撞到），不适合塞进 AAF-107 #10704。已拆出为独立故事 **AAF-110（执行中断续跑）**，登记于 `docs/task/backlog.md`，依赖 AAF-103。#10704 收窄为"计划步骤幂等接线"，依赖 AAF-110 完成后才可验证，详见 tasks.md 更新后的范围描述。

### 修正（2026-09-01，第一性原理复核）

上面"结论"一度误判为"AAF 新 execution 重建"和"续接历史"是两种互斥的恢复哲学，需要二选一。拆解到底层发现二者不在同一维度：恢复**正确性**只能锚定 receipt/TaskBoard/事件流（不变），恢复**效率/体验**（是否延续 AgentState 历史）与正确性无关，因为 receipt 幂等已独立挡住重复副作用。已修正 `docs/design/framework/intelligent/task-durability.md`「可恢复粒度」「冻结画像」两表：**同责任主体的步骤级中断续跑复用原 `executionId` 延续历史；责任主体变化（接管/换权限）才强制新 execution**。AAF-110 的任务范围也从"评估要不要引入续接"改为"实现 `doFinally` 按责任主体是否变化分流删除状态槽"，是确定性任务，不再是开放问题。

### #10703 收尾确认（2026-09-01）

- ✅ 复核确认 #10703 两项遗留标记（`ai_tool_catalog` 缺注册、`delegatedTaskCoordinator` 仍用 13 参构造器）均为已记录的刻意保守限制，非未完工代码——已把 `tasks.md` 对应记号由 `[ ]` 改为 `⚠️` 避免误读为待办事项；`#10703` 状态正式标记为 ✅ 已完成。

---


## #10701 立 ADR：EXECUTOR plan 表与审批策略

- ✅ 2026-09-01 — Kiro：产出并经人类审核通过 [ADR-006](../../../design/adr/ADR-006-executor-plan-mode.md)

## #10702 计划聚合、两表与状态机

- ✅ 2026-09-01 — developer-service：`ExecutorPlan`/`ExecutorPlanStep` 聚合 + 两表 + `ExecutorPlanPort`/`JpaExecutorPlanAdapter` 落地
- ⚠️ 2026-09-01 修正 — 本任务曾短暂给 `ExecutionPolicy` 加 `requiresPlan` 字段，#10703 期间发现放错层（`ExecutionPolicy` 是 core 执行边界参数，与编排层"是否需要规划"决策无关），已撤销并改为 `TaskBoard.SubTask.requiresPlan` + `PlanRequirementPolicy`，详见 #10703 记录与 ADR-006 增补节

执行者：AI/developer-service

## 实现文件

| 文件 | 说明 |
|------|------|
| `docs/design/adr/ADR-006-executor-plan-mode.md` | 新增：四议题 ADR，人类已审核通过 |
| `apps/service/aaf-api/.../db/migration/v16__intelligent_runtime_schema.sql` | 追加 `ai_executor_plan`、`ai_executor_plan_step` 两表 |
| `.../assistant/model/plan/ExecutorPlan.java` | 新增：计划头聚合 + 状态机枚举 |
| `.../assistant/model/plan/ExecutorPlanStep.java` | 新增：步骤明细 |
| `.../assistant/port/plan/ExecutorPlanPort.java` | 新增：状态机唯一写入边界 + 命令/异常类型 |
| `.../infrastructure/assistant/persistence/plan/ExecutorPlanEntity.java` | 新增：JPA 映射（显式关系列，非单一 JSONB） |
| `.../infrastructure/assistant/persistence/plan/ExecutorPlanStepEntity.java` | 新增：步骤 JPA 映射 |
| `.../infrastructure/assistant/persistence/plan/ExecutorPlanRepository.java` | 新增：悲观锁读 + 活跃计划查询 |
| `.../infrastructure/assistant/persistence/plan/ExecutorPlanStepRepository.java` | 新增：步骤悲观锁读 + RUNNING 查询 |
| `.../infrastructure/assistant/persistence/plan/JpaExecutorPlanAdapter.java` | 新增：`ExecutorPlanPort` 实现，CAS 语义 |
| `.../infrastructure/assistant/spring/AssistantInfrastructureAutoConfiguration.java` | 注册 `executorPlanPort` Bean（#10703 追加 `SubmitExecutorPlanTool`/`ExecutorPlanAutoApprovalPolicy`） |
| ~~`.../agent/model/ExecutionPolicy.java`~~ | ~~新增 `requiresPlan` 字段~~ **已撤销（放错层，见 #10703）** |

## 实现决策

> **两表选显式关系列而非单一 JSONB**：对比 `TaskBoardEntity.board_payload` 整板一次性 JSONB 写回的模式，`ExecutorPlan`/`ExecutorPlanStep` 需要 `UNIQUE(tenant_id, task_id, revision)`、`UNIQUE(plan_id, step_key)`、`UNIQUE(plan_id, ordinal)` 以及"同 plan 最多一个 RUNNING"（`uk_executor_plan_step_running` 部分唯一索引）这类 SQL 层不变量，JSONB 表达式索引无法直接承担唯一约束。策略/风险/验证/依赖/工具/判据五个可变形状字段仍用 JSONB，兼顾灵活性与查询需求的平衡点在"是否需要唯一约束或状态过滤"。

> **CAS 显式比对而非依赖 JPA `@Version` 异常**：`lockPlan`/`requireStepLockVersion` 先悲观锁读出当前行，显式比对调用方携带的 `expectedLockVersion`，不一致立即抛 `StaleExecutorPlanException`。比等 `@Version` 自动比对在 flush 时抛 `OptimisticLockException` 更早、更明确，且不依赖 Hibernate 内部异常类型对外层调用方暴露契约。

> **`ExecutorPlanPort` 命令与结果类型集中定义在接口内**：`BeginPlanningCommand`/`SubmitPlanCommand`/`StepDraft` 等 8 个 record 只服务于这一组状态转换，不值得拆成 8 个独立文件，符合"不为一次性代码创建过多文件"的简洁原则；对比 `TaskTransitionPort` 把命令类型放在 `TaskTransition`/`AssistantCommand` 里的既有做法，本次收敛到端口自身，因为这些命令类型没有被 `ExecutorPlanPort` 之外的任何模块引用。

> **`ExecutionPolicy` 用二级构造器承接新字段，不破坏既有 6 参数调用点**：`requiresPlan` 加为第 7 个字段后，原 6 参数构造器改为委托新的 7 参数规范构造器并把 `requiresPlan` 固定为 `false`。全仓搜索确认只有一处直接 `new ExecutionPolicy(...)`（`HarnessAgentExecutionAdapterTest`）与内部 `withDefaultTimeouts` 引用规范构造器，两者均已验证签名兼容，未标记 `requiresPlan` 的既有调用方行为完全不变（ADR-006 议题二的直接推论）。

> **`TaskBoard.activeExecutorPlanId` 快照引用推迟到 #10703**：ADR-006 与 harness 落地计划都提到 TaskBoard 应保存活跃计划快照引用，但 `#10702` 的 `findActive` 已能通过 `(tenant_id, task_id)` 直接查询最新未终止 revision，暂不需要在 `TaskBoard` 聚合里加字段。等 #10703 把 `DelegatedTaskCoordinator.executeSubTask` 的 EXECUTOR 分支改为 `requiresPlan → ensureApprovedPlan → executeApprovedPlan` 三段式时，才能确定编排层是否真的需要在 board 上缓存这个引用，还是每次都查表足够——避免为未验证的读性能需求预先加字段。

> **`required_tools` 子集校验推迟到 #10703**：`ExecutorPlanStep.requiredTools` 当前只落库，不在 `#10702` 校验是否是冻结工具白名单子集，因为白名单要到 planning execution 真正跑起来（`#10703`）才有 `FrozenToolDescriptor` 集合可比对；`#10702` 只负责持久化结构，不提前实现只有下游任务才能验证的规则。

> **`ExecutorPlan`/`ExecutorPlanStep` 状态机不提供状态转换方法**：与 `ExecutorPlanPort` 的关系是"聚合是不可变数据视图，端口是唯一写入边界"——枚举本身不提供 `submit()`/`approve()` 之类的实例方法，避免业务代码绕过 `JpaExecutorPlanAdapter` 的 CAS 检查直接在内存里改状态再序列化回库。

## 验证

- 按人类要求本次不执行 `pnpm nx compile service` / `pnpm nx test service`；已人工核对：
  - `ExecutorPlan`/`ExecutorPlanStep` canonical 构造器参数类型与 `JpaExecutorPlanAdapter.toDomain` 的调用实参逐一比对（`revision: int` ← `Integer` 自动拆箱、`lockVersion: long` ← `Long` 自动拆箱，均合法）。
  - `ExecutionPolicy` 新增字段后的 6 参数直接调用点（`HarnessAgentExecutionAdapterTest:336`）与 `withDefaultTimeouts` 内部引用均已核对签名兼容。
  - SQL 迁移片段的表名、列名、CHECK 约束值集与 Java 端 `ExecutorPlan.Status`/`ExecutorPlanStep.Status` 枚举常量逐一比对一致。
- 待 AAF-108 #10801 统一补齐：`compile` + `test`；届时应补齐 `JpaExecutorPlanAdapter` 的 CAS 冲突、依赖未满足、重复 stepKey 等负向测试。

---

## #10703 planning execution 与 acting gate（2026-09-01）

执行者：AI/developer-service

### 实现文件（新增/修改）

| 文件 | 说明 |
|------|------|
| `.../assistant/application/SubmitExecutorPlanTool.java` | 新增：`submit_executor_plan` 工具，`ContextAwareToolHandler` 实现 |
| `.../assistant/application/PlanRequirementPolicy.java` | 新增：是否需要规划的最终判定接口（ADR-006 补充决策二） |
| `.../assistant/application/AssistantCommand.java` | `forSubTask` 新增 `ControlMode` 覆盖重载 |
| `.../assistant/model/TaskBoard.java` | `SubTask` 新增 `requiresPlan` 字段；`applyCoordinationPlan` 新增带 `planRequirement` 判定函数的重载；新增 `awaitAuthorization(String subTaskId)` 重载 |
| `.../assistant/model/CoordinationPlan.java` | `ExecutorAssignment` 新增 `suggestsPlan`（协调者建议信号，可空） |
| `.../assistant/port/TaskBoardPort.java` | 新增带 `planRequirement` 的 `applyCoordinationPlan` 重载；新增 `awaitSubTaskAuthorization` |
| `.../assistant/port/plan/ExecutorPlanPort.java` | `findActive` 签名加 `boardId` 参数；`BeginPlanningCommand` 去掉 `revision`（改为内部自动分配） |
| `.../infrastructure/assistant/persistence/JpaTaskBoardAdapter.java` | 实现上述两个新 Port 方法 |
| `.../infrastructure/assistant/persistence/plan/JpaExecutorPlanAdapter.java` | `findActive` 加 `boardId` 过滤；`beginPlanning` 内部按 `(tenant, task, board)` 现有最大值 +1 分配 revision |
| `.../infrastructure/assistant/persistence/plan/ExecutorPlanRepository.java` | `findActiveCandidates` 加 `boardId` 参数 |
| `.../infrastructure/assistant/persistence/plan/ExecutorPlanEntity.java` | 唯一约束改为 `(tenant_id, task_id, board_id, revision)` |
| `apps/service/aaf-api/.../v16__intelligent_runtime_schema.sql` | 同步修正 `uk_executor_plan_revision`、`idx_executor_plan_active` 索引定义 |
| `.../assistant/application/DelegatedTaskCoordinator.java` | 新增 `executePlannedSubTask`/`runPlanningExecution`/`executeApprovedPlanSteps` 三方法；`executeSubTask` 按 `subTask.requiresPlan()` 分流；构造器新增 `planRequirement`/`plans` 依赖（新增第三重载，旧两个构造器向后兼容） |
| `.../infrastructure/assistant/spring/AssistantInfrastructureAutoConfiguration.java` | 新增 `submitExecutorPlanTool`/`executorPlanAutoApprovalPolicy` Bean；`delegatedTaskCoordinator` Bean 未改（仍用 14 参构造器，规划能力未启用） |
| `docs/design/adr/ADR-006-executor-plan-mode.md` | 追加「勘误与决策补充」，记录三处人类拍板 |

### 关键发现

> **acting gate 不需要新建 AgentScope middleware**：AAF 已有 `ControlMode.READ_ONLY`，经 `DefaultToolGateway.invoke` → `ToolAuthorizationContext.requireVisible` 对每次工具调用强制执行"非只读工具一律拒绝"，与官方 `PlanModeMiddleware` 的白名单拒绝语义等价。planning execution 只需要用 `READ_ONLY` 命令调度，不需要仿造官方三个 plan 工具或新建拦截层。

> **`submit_executor_plan` 要能在 READ_ONLY 下被调用，靠 `ToolPolicy.ActionEffect=GENERATED_CONTENT` 而非新机制**：`AssistantApplicationService` 现有逻辑 `readOnly = switch (rule.effect()) { case READ, GENERATED_CONTENT, HUMAN_HANDOFF -> true; ... }`——只要把该工具的 `ActionEffect` 配置为 `GENERATED_CONTENT`（产出计划草稿，非业务动作），它在系统眼里就"等效只读"，可在 `READ_ONLY` 下调用。这一步的实际生效仍依赖 Skill/Role 配置层把该工具纳入 `ToolPolicy.rules`，属运营配置，不在本任务代码范围。

> **发现已存在、不属于本任务范围的缺口**：全仓 migration 无任何 `INSERT INTO ai_tool_catalog`，`support.handoff`/`context.load` 等既有内置工具在生产环境同样没有目录种子数据，需要管理员后台手工注册才可用。`submit_executor_plan` 会遇到同样的问题，属于既有缺口的自然延伸，不因本任务而恶化，已如实记录不处理。

### 三处 ADR-006 增补决策（人类拍板）

1. **`ExecutorPlan`/`ExecutorPlanStep` 适用范围从"仅 EXECUTOR"放宽为"任何自行执行的节点"**：协调者判断"复杂任务但不需要派生子节点"时，也能用同一套状态机管理自己的执行步骤。`ExecutorPlan.boardId`/`executorAgentId` 本身是纯字符串，不引用 `NodeIdentity.NodeKind`，结构无需改动，只是调用方约束放宽。
2. **是否规划从"纯静态 policy 标记"改为"协调者建议 + AAF 侧确定性规则兜底"两层判定**：`CoordinationPlan.ExecutorAssignment.suggestsPlan` 是协调者给出的建议信号（可空，地位等同 `risks` 字段——仅供参考不直接决定）；`PlanRequirementPolicy.requiresPlan(...)` 给出最终判定，不得直接返回协调者建议值。当前默认实现 `respectCoordinatorSuggestion()` 完全尊重建议（最小可用起点），但两个旧版 `DelegatedTaskCoordinator` 构造器固定传入恒 `false` 的策略（见下方"设计纠正"）。
3. **不需要 `plan_enter`/`plan_write`/`plan_exit` 工具**：AAF 是两次独立 execution（规划/执行）而非官方"同一次调用内切换阶段"，execution 边界本身就是状态切换点，不需要模型显式声明进出。`plan_write` 由一次性的 `submit_executor_plan` 取代。

### 设计纠正

> **`ExecutionPolicy.requiresPlan` 放错层，已撤销**：#10702 曾短暂加入该字段，本任务发现 `ExecutionPolicy` 是 core `ReActAgent.builder()` 的执行边界参数（迭代数/超时），与"是否需要规划"这个编排层决策毫无关系。改为 `TaskBoard.SubTask.requiresPlan`（编排层节点属性，由 `PlanRequirementPolicy` 计算后写入）+ `TaskBoard.applyCoordinationPlan` 的函数式参数重载完成"建议 → 最终判定"的转换，不让领域模型直接依赖应用层策略接口类型。

> **两个旧版 `DelegatedTaskCoordinator` 构造器固定传入恒 `false` 的 `planRequirement`，而非"尊重协调者建议"**：若默认尊重协调者建议，未显式传入 `plans` 依赖的部署一旦协调者建议规划就会在 `executePlannedSubTask` 触发空指针。只有显式调用新增的完整构造器并提供 `plans` 时才有意义启用非恒 false 的策略——这是刻意的保守默认，不是遗漏。

> **#10702 建表遗留 bug：revision 唯一约束缺 `board_id`**：`ai_executor_plan` 原唯一约束是 `(tenant_id, task_id, revision)`，但一个任务的板上可能有多个节点各自需要规划（多个 EXECUTOR 都 `requiresPlan=true`），revision 序号应该是"每个节点独立递增"而不是"整个任务共用一组序号"，否则并发/顺序建计划会互相撞号，`findActive` 也无法区分是哪个节点的计划。发现于设计 `runPlanningExecution` 时反查 `findActive` 语义才暴露，已修正唯一约束为 `(tenant_id, task_id, board_id, revision)`，`ExecutorPlanPort.findActive`/`findActiveCandidates` 同步加 `boardId` 参数。`revision` 分配也从"调用方传入"改为"adapter 内部按 `(tenant, task, board)` 现有最大值 +1 自动分配"，避免调用方猜错序号。

> **板状态转换：`RUNNING` 节点不能悬空**：设计三段式时最初疏漏了一点——`runPlanningExecution` 跑完一次 planning execution 后，如果什么都不做，`subTask` 会停留 `RUNNING`（既不在 `readyForClaim()` 范围内也不是终态），导致外层 `executeBoard` 递归的下一轮 `claimReady()` 找不到可运行节点，误判整板"无可运行节点"而把整个委托任务 `PAUSED`——这会连带影响其它并行节点，不是我们想要的"只暂停这一个规划中的子任务"语义。修正为：`REVIEW_REQUIRED` 转 `AWAITING_AUTHORIZATION`（新增 `TaskBoardPort.awaitSubTaskAuthorization`，只做状态转换，不建 `HumanApproval` 记录——那部分接入 `PersistentHitlCoordinator` 留给 #10704）；`APPROVED`（自动批准）转 `RETRYABLE`（复用现有 `interruptSubTask(..., retryable=true)`），让外层下一轮递归重新 `claim` 该节点进入执行阶段；"只说不做"（规划期结束但未提交）按现有 `failSubTask(..., transientFailure=true)` 处理。

> **`executePlannedSubTask` 里 `REVIEW_REQUIRED` 分支是死代码，已删除**：最初写了 `if (plan.status() == REVIEW_REQUIRED) return Flux.empty();`，但节点在该状态下已经是 `AWAITING_AUTHORIZATION`（被 `runPlanningExecution` 转的），不在 `readyForClaim()` 范围内，本方法根本不会在该状态下被外层重新调度进来。发现于逐条核对状态转换路径时，删除死代码并补充注释说明为何不需要。

### 已知限制（记入供 #10704/#10801 评估）

- `runPlanningExecution` 结束后转 `RETRYABLE`/`AWAITING_AUTHORIZATION` 复用了既有 `interrupted(true)`/`awaitingAuthorization()` 领域方法，两者都会消耗 `attempts` 重试预算，多次规划-执行往返可能提前耗尽预算——不是本次引入的新缺陷，但组合使用后影响被放大，需评估是否要为 planning 往返单独计数，不占用业务执行的重试预算。
- `beginPlanning` 的 revision 分配（`max+1`）非事务串行化，理论并发建计划请求可能因 `uk_executor_plan_revision` 冲突失败（fail-closed），当前业务场景不存在真正并发触发，未做额外加锁。
- `AssistantInfrastructureAutoConfiguration.delegatedTaskCoordinator` Bean 仍用 14 参构造器，本次改动可编译且向后兼容，但规划能力尚未在生产 Bean 图里正式启用——是否启用、何时启用留给人类明确指示或后续任务。

### 已发现但不在本任务处理的缺口：内置工具生产可用性（`ai_tool_catalog` 种子数据 + `effect()` 猜测逻辑）

人类要求先评估是否补种子数据，评估后确认不在本任务处理（选项 C），记录如下供后续单独立项：

1. **`ai_tool_catalog` 无任何种子数据**：全仓 migration 无 `INSERT INTO ai_tool_catalog`，`support.handoff`/`context.load`/`submit_executor_plan` 等全部内置工具在生产环境都要经该表才能被模型看见，目前只能靠管理员后台手工注册。
2. **`ToolPolicy.ActionEffect` 不是从 `ai_tool_catalog.read_only` 读出来的，而是按工具名字符串关键词猜的**：`JpaAssistantDefinitionAdapter.effect(String tool)` 用 `tool.toLowerCase()` 匹配 "handoff"/"delete"/"publish"/"create"/"update"/"upsert"/"write"/"generate" 等关键词分类，其余全部默认 `READ`。`submit_executor_plan` 这个名字不含任何关键词，会被猜成 `ActionEffect.READ`——功能上恰好不影响（`READ` 在 `READ_ONLY` 模式下同样允许调用），但语义分类不准确（它是"提交"动作，不是纯读）。
3. **两层配置必须一致，否则直接拒绝执行**：`DefaultToolGateway.invoke` 里 `ToolAuthorizationContext.requireVisible` 会核对 `rule.readOnly() != catalogReadOnly || rule.reversible() != catalogReversible`，不一致直接抛异常。这意味着即使补了 `ai_tool_catalog` 种子数据，如果 `read_only` 值和 `effect()` 猜出来的分类不匹配，工具会在运行时被拒绝，而不是在配置时报错——排查成本高。
4. **两个问题耦合，不建议分两次改**：种子数据解决"有没有这条记录"，`effect()` 逻辑解决"分类准不准"，两者必须一起核对才能保证不产生"配置了但运行时被拒"的情况。建议单独立项统一处理，而不是本次或 #10704/#10705 顺手补一半。

## 决策推翻：计划提交不再需要独立审批关卡（2026-09-01，ADR-006「决策推翻」章节）

对齐官方 `permission-system.html` 后，人类拍板推翻了 ADR-006 议题四原结论（"确定性白名单驱动自动批准，未命中转 `REVIEW_REQUIRED` 人工审批"），改为**计划提交后固定批准，不设独立审批关卡**。完整依据见 ADR-006 新增的「决策推翻」章节，此处记录代码层面的连锁改动。

### 推翻依据（摘要，完整论证见 ADR-006）

官方权限系统按**具体工具调用**分级（ALLOW/DENY/ASK），没有"计划本身要不要审"这一层概念。`submit_executor_plan` 提交的是步骤列表，不新增执行面、不引入新 Agent 身份，风险已在两处覆盖：① 协调者产出 `CoordinationPlan` 派生新节点时的既有审批点（"拆分子智能体"这个动作，风险高，需要用户确认——这个点位不受本次改动影响，继续存在）；② 步骤执行阶段具体工具调用前的 `DefaultToolGateway`/`AuthorizationGrant` 授权链路（按需 ASK，逐次调用该怎么审还是怎么审）。为"提交计划"这一动作单独建一层白名单+人工审批是重复建设。

### 代码改动

| 文件 | 改动 |
|------|------|
| `SubmitExecutorPlanTool.java` | 删除 `ExecutorPlanAutoApprovalPolicy` 依赖；`SubmitPlanCommand.autoApproved` 固定传 `true`；输出文案不再区分"自动批准"/"等待审批" |
| `ExecutorPlanAutoApprovalPolicy.java` | **整体删除**（不再需要白名单判定） |
| `AssistantInfrastructureAutoConfiguration.java` | 删除 `executorPlanAutoApprovalPolicy` Bean；`submitExecutorPlanTool` Bean 去掉该依赖参数；清理未使用的 `Set` import |
| `TaskBoardPort.java`/`JpaTaskBoardAdapter.java` | 删除 `awaitSubTaskAuthorization`（原为计划待审批时转板状态，不再需要——不存在"待审批"这一状态） |
| `TaskBoard.java` | 删除 `awaitAuthorization(String subTaskId)` 重载（同上，唯一调用方已移除） |
| `DelegatedTaskCoordinator.java` | `executePlannedSubTask`/`runPlanningExecution` 删除 `REVIEW_REQUIRED` 分支；提交后只有"批准（转 `RETRYABLE` 待重新领取）"与"未提交（`failSubTask`）"两条路径 |
| `docs/design/adr/ADR-006-executor-plan-mode.md` | 追加「决策推翻」章节 |

### 保留但当前正常路径不会触达的代码

`ExecutorPlan.Status.REVIEW_REQUIRED`/`REJECTED` 枚举值、`ExecutorPlanPort.review`/`JpaExecutorPlanAdapter.review` 方法**均未删除**——这是通用状态机的一部分（`SubmitPlanCommand.autoApproved` 参数仍然存在，只是本工具固定传 `true`），为将来可能出现的其它触发路径保留，不因为当前唯一调用方固定传 `true` 就删除整套状态机分支。删除的只是"白名单判定"这一层逻辑和"计划待审批时转板状态"这个中间态的编排层接线。

### 附带发现：AAF 工具权限机制对比官方 Permission System

核实官方文档时确认 AAF 现有 `ToolAuthorizationContext`/`AuthorizationGrant`/`ai_hitl_approval` 在持久化、多副本、资源粒度授权、幂等 receipt 几个生产级维度上比官方内存态方案更完整；但缺官方的 Built-in Checks（工具按真实参数动态检查，不可绕过）与危险资源硬编码黑名单两项运行时安全加固。已记入改进意见池（`docs/prd/improvements.md`），不在本任务处理。

## 验证

- 按人类要求本次不执行 `pnpm nx compile service` / `pnpm nx test service`。
- 人工复核：全局搜索确认 `ExecutorPlanAutoApprovalPolicy`/`awaitSubTaskAuthorization`/`awaitAuthorization(String)` 均无残留引用（除已同步更新的 javadoc 文本）；`SubmitExecutorPlanTool`/`AssistantInfrastructureAutoConfiguration` 的构造器参数数量与调用点逐一核对一致。
- 待 AAF-108 #10801 统一补齐：`compile` + `test`；届时应补齐 `executePlannedSubTask` 简化后的状态转换测试（提交即批准、未提交转失败两条路径）。

### 验证

- 按人类要求本次不执行 `pnpm nx compile service` / `pnpm nx test service`。
- 人工复核：全仓搜索所有 `findActive`/`ExecutorAssignment`/`SubTask` 构造点，确认签名变更后的调用点（`DelegatedTaskCoordinatorAggregatorCompletionTest`、`CoordinationPlanTest` 等）均已同步或天然兼容（向后兼容重载覆盖全部既有测试调用）。
- 待 AAF-108 #10801 统一补齐：`compile` + `test`；届时应补齐 `executePlannedSubTask` 三段式的状态转换测试（尤其"只说不做"分支、自动批准后重新 claim、REVIEW_REQUIRED 转 AWAITING_AUTHORIZATION）。


## #10708 CHAT 模式 Role/Skill 独立锁定 + #10709 角色选定事件投影

- ✅ 2026-09-03 — developer-service
- `executionIntent(...)` CONVERSATIONAL 分支重写：role/skill 各自独立判断，支持只锁角色/只锁技能/都锁/都不锁四种组合
- 只锁技能场景新增 `roleByExplicitSkill` 复用 `DefaultRoleSelector.selectByExplicitSkill` 匹配规则，歧义 fail closed（`EXECUTION_SKILL_AMBIGUOUS_ROLE`）
- 修复 `AssistantApplicationService` FIXED 分支隐藏 bug：`route.skillKey()==null` 时 `.equals(null)` 永远不匹配导致误报错
- 架构评估：不放开非 Team 模式 Role/Skill 冻结约束（扩大攻击面）；Team 前端入口记录为 AAF-111 独立故事
- `#10709` 核实纠正：`ROLE_RESOLVED` 事件与投影链路早已存在，真实缺口只是 `ExecutionEventPublicMapper.safeData` 缺 case 导致字段被过滤，非需要新枚举/新 converter
- 前端连带发现并修复：`/ai/assistants/available` 后端从未实现导致角色下拉框长期靠假数据运行；3 处硬编码假 Role key；技能 code 误传 `agentRole` 字段的历史错配
