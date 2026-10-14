---
level: Practice
layer: Principle
purpose: 明确 Task 执行形态（单协调者循环、直答出口、分解策略）、产物保存边界与全环节流式可视化
status: proposed
version: 1.0.0
date: 2026-10-11
author: AaronZZH & Kiro
changelog:
  - 2026-10-11 | 1.0.0 | 初稿：整理 AAF-107 / AAF-115 之后文案生成链路暴露的执行形态与流式问题
---

# ADR-008: Task 执行形态、产物保存边界与全环节流式可视化

## Context and Problem Statement

文案生成页（`/studio/create/copy`）以 `interactionMode=TASK`、`routeConstraint=FIXED`、`AUTO_SAVE_DRAFT` 调用 `POST /api/agui/run`，前端没有收到流式正文。排查发现三类互相关联的问题：

- **传输断裂**：AAF-115 把 TASK 路径改为"提交 Task 后由独立 worker 执行"，入口 `AssistantExecutionService` 提交后返回空 `Flux`，SSE 随即闭合，worker 产出的增量没有送到连接上。
- **协调者契约矛盾**：`InvocationPolicy.COORDINATOR` 要求协调者只做规划、必须调用 `submit_coordination_plan`、不得生成最终业务内容；内置技能 `builtin-task-decomposition` 与 AAF-107 的文档却允许协调者"简单任务直接给出最终答案"。同时 `AssistantCommand.nodeIdentityOf` 把协调者恒定为内部节点（`delivery=false`），其文本在 AG-UI 投影中降级为只含 `contentLength` 的 `CUSTOM` 事件。实际运行中协调者按技能直答，正文既不被交付，也没有保存草稿。
- **写放大**：每个模型文本增量都作为一行 `MESSAGE_DELTA` 写入事件表，一次回复约 100 次 INSERT，而完整文本已由 `MESSAGE_COMPLETED` 携带。

此外 `runtime.md`、`coordination.md` 仍描述 AAF-107 的"协调者三档判断、`TaskBoard.single` 不再有触发路径"，与 AAF-115 的代码和设计不一致。需要在不推翻现有 harness 的前提下，给出一致的执行形态定义。

## Decision Drivers

- 保持 AAF-107 选项 B 的结论：复杂度判断在协调者的单次 ReAct 循环内完成，不恢复独立的前置判断模型调用（后者只看得到目标文本，判断质量低于拥有完整上下文的协调者）
- 简单任务应一个循环完成并交付，不为"汇总"多付一次模型调用
- 边界（路由、权限、工具可见性、产物保存）由代码强制，不依赖提示词
- 业务产物落库方式由业务决定，框架不感知表结构
- 每个环节的输出都能实时观察，但不能把内部节点的输出误当作交付结果
- 高频文本增量不应成为数据库写入热点

## Considered Options

- 保持现有 harness 流程，在其上修正契约与出口
- 恢复独立前置复杂度判断环节（两个协调者循环）
- 所有 TASK 统一固定三段（协调者、执行者、聚合者），简单任务也不跳过

## Decision Outcome

**Chosen option**：保持现有 harness 流程，在其上做契约修正，理由：单循环判断已是 AAF-107 的明确结论，问题出在 AAF-115 重构留下的契约冲突和传输断裂，而不是流程形态本身。

### 执行形态

- 区分两类约束，不要混用：
  - **harness 结构化约束（代码）**：DIRECT/TASK/TEAM 分流、`routeConstraint` 校验、Role/Skill 与授权包络冻结、工具可见性、分解预算（执行者 1..5）、`CompletionValidator`。这些由代码强制，模型无法改变
  - **前注意（协调者，提示词驱动）**：理解意图、判断是否缺关键输入需要澄清、判断需要哪些技能、**判断复杂度**。复杂度只是前注意的一个判断点，不单独成环节、不单独调用模型，与规划在协调者同一个 ReAct 循环内完成，规则集中写在 `builtin-task-decomposition` 技能
- 前注意发生在协调者循环内，所以 Role 与授权包络必须在循环**开始前**由 harness 冻结，协调者只能在包络内选择和规划（`submit_coordination_plan` 已校验"不得更改已冻结的 Role/Skill"）。AUTO 路由当前由 `DefaultRoleSelector`、`ModelSkillSelectionPort` 通过 `PromptInvocationGateway` 做 L0 模型调用选择 Role/Skill，属于循环外的独立模型调用；是否收敛进协调者见"待核实"
- 复杂度判断由协调者在**同一个 ReAct 循环**内完成，三档处理；不增加第二个协调者循环
  - **简单直答**：协调者直接产出最终回复，1 个循环
  - **拆步骤**：协调者调用 `submit_executor_plan` 提交有序步骤，并在**同一循环内**按步骤执行、调用 `report_executor_step` 上报，仍是 1 个循环，不新增节点。`submit_executor_plan` 在代码中只要求"在编排板节点内调用"，协调者在原则上可用
  - **拆多智能体**：协调者调用 `submit_coordination_plan` 后本轮结束（节点标记完成），之后各执行者各自一个独立循环，系统追加的聚合者再一个独立循环，共 N+2 个循环（N 为执行者数，至少 3）。协调者无法在自己的循环内等待执行者完成，因为 Task 是持久 DAG，执行者按派发异步运行；聚合者使用协调者的 Role、Skill 与模型，语义上是"协调者在汇总阶段继续工作"
- DIRECT / TASK / TEAM 只表示**执行容器**，按请求属性硬分流，顺序为 TEAM、TASK、DIRECT：
  - DIRECT：对话式、只返回结果，不建 Task，必要时由智能体调用 `promote_direct_task` 升级
  - TASK：显式任务式或带持久产物，建持久 Task，从协调者开始
  - TEAM：绑定已发布 Team，roster 冻结，不做复杂度判断
- 协调者没有提交计划就完成时，计划保持"仅协调者"形态，协调者即交付节点；提交计划后由系统追加聚合者，聚合者使用协调者的 Role、Skill 与模型，是交付节点
- 统一 `InvocationPolicy.COORDINATOR` 与 `builtin-task-decomposition`：允许简单任务直答，规则只在技能中维护一份

### 协调者工具与上下文边界

现状（`AssistantApplicationService` 有效工具解析）：

- `internalPlanner = coordinator || aggregator` 时，Role/Skill 解析出的工具被**整体丢弃**，协调者只剩 `builtinControlTools` 硬编码的 `submit_coordination_plan` 与 `request_clarification`；执行者只有 Role/Skill 工具加 `request_clarification`
- `submit_executor_plan`、`report_executor_step` 不在 `builtinControlTools` 中，`builtin-task-decomposition` 技能是 `INHERIT` 模式且不声明工具，因此"拆步骤"一档**事实上没有可用工具**，技能文案引用的工具模型看不到
- 协调者的上下文披露为 `SUMMARY_ONLY`（预算 1024），不能读取原始内容

决策：

- 协调者的有效工具 = Role/Skill 解析出的工具（经 `ToolGateway` 授权与审批）+ 控制工具 `submit_coordination_plan`、`submit_executor_plan`、`report_executor_step`、`request_clarification`。这四个控制工具加入 `builtinControlTools` 与 `builtinControlTool`，免于 ToolPolicy 逐项登记
- 执行者同样补充 `submit_executor_plan`、`report_executor_step`，保证"节点内拆步骤"在任意节点可用
- 聚合者**不放开**，保持无业务工具，只读取冻结的聚合输入
- 分解策略为 `NONE` 时从协调者控制工具中移除 `submit_coordination_plan`；`REQUIRED` 时只保留 `submit_coordination_plan` 与 `request_clarification`
- 协调者要自己直答或按步骤执行，必须能读取完整内容，因此协调者的上下文披露从 `SUMMARY_ONLY` 放宽为允许内容。放宽的代价是协调者直接暴露在原始内容与附件的提示注入面前，这个风险由既有 harness 约束兜底：工具走 `ToolGateway` 授权、计划校验保证执行者 Role/Skill 不超出冻结基准、执行者数量与并行度受预算限制、完成由 `CompletionValidator` 判定。该取舍需要人工审核确认

### AUTO 路由的选择

- L0 选择器（`DefaultRoleSelector`、`ModelSkillSelectionPort`）只看到用户输入文本和技能摘要，没有协调者拥有的完整上下文、记忆和材料，判断质量低于协调者；它选的是 Role/Skill，工具是由 Skill 与 Role 交集间接决定的。AAF-107 对复杂度判断已经得出同样结论，选择判断应与之统一，由拥有完整上下文的协调者负责
- 约束：`ExecutionProfileSnapshot` 在执行开始时冻结 `ActivatedSkill` 与有效工具，不能在一个循环中途改变。因此协调者的选择只能落在**下一次执行**上，不能改变自己当前循环的技能集
- 决策：TASK 路径取消 L0 选择，协调者自己决定
  - 协调者自身循环使用 ALWAYS 技能与 Role 基线，不预选业务技能
  - 需要业务技能时，协调者在 `submit_coordination_plan` 里为每个执行者指定 Role 与 Skill，候选目录由 harness 提供（Assistant 已发布且 Role 允许的技能摘要），harness 校验成员资格与授权衰减。现有"执行者必须等于协调者冻结基准"的校验放宽为"必须属于候选目录且不扩大授权"
  - 简单任务需要某个业务技能时，由协调者提交单执行者计划承接，多出的开销由分解策略 `NONE` 与 FIXED 路由规避（固定技能的请求不经过选择）
- DIRECT 路径本期保留 L0 选择，待轻量态协调者具备同等能力后再统一
- FIXED 路由不受影响，仍由请求直接指定

### 内置技能划分

现状：`builtin-task-decomposition`（任务复杂度判断与拆分）把三档判断、拆步骤、拆子智能体写在同一个技能里，向所有智能体 ALWAYS 绑定，名称无法区分"拆步骤"与"拆子智能体"，执行者也会读到只有协调者能用的委派说明。

决策：按职责拆成三个内置技能，命名沿用现有 `builtin-*` 风格：

| 技能 | 职责 | 适用智能体 | 关联工具 |
|---|---|---|---|
| `builtin-task-triage` | 前注意：对请求做初步判断——意图类型、关键输入是否缺失需要澄清、是否需要某类技能或知识、是否涉及需要授权的动作、复杂度三档。输出是判断，不是动作 | 仅协调者 | `request_clarification` |
| `builtin-step-planning` | 拆步骤：节点内提交有序步骤并逐步上报，单循环执行 | 所有智能体 | `plan_steps`、`report_step` |
| `builtin-agent-delegation` | 拆子智能体：派生执行者、并行度、依赖与聚合方式 | 仅协调者 | `delegate_agents` |

- 前注意与任务拆分分开：前注意只做判断，可以随认知模型扩充更多简单判断项而不影响拆分规则；拆分技能只在判断结果为"需要拆"之后使用
- `report_step` 沿用现有 `report_executor_step` 的语义：一个工具，入参 `stepKey` 加 `outcome`（`STARTED`、`COMPLETED`、`FAILED`），`COMPLETED` 可带 `resultRef`，`FAILED` 必带 `failureCode`；不拆成 `start_step`、`complete_step`、`fail_step` 三个工具，避免增加模型可见工具数，状态机的顺序与依赖由 harness 校验
- 任务控制工具（`inspect_tasks`、`pause_task` 等）与 `promote_to_task` 只给轻量态（无 `taskId`）的协调者：它们是"在对话里管理用户的任务"的能力，任务态内的协调者、执行者、聚合者不应能暂停或取消所属任务

- 工具的可见性由 harness 按智能体类型强制（`submit_coordination_plan` 仅协调者），技能正文只是指导，两者必须一致
- 绑定粒度：现有 `ai_system_skill_binding` 是否支持按智能体类型限定绑定范围尚未核实；不支持时需要扩展绑定模型，或由 harness 在解析激活技能时按智能体类型过滤
- 技能名称已确认，落地时同步 `v12__init_seed_data.sql` 的种子和引用；项目未部署，已确认直接修改该种子文件，不新增迁移

### 内置控制工具命名

现状问题：`submit_executor_plan` 实际是"节点为自己提交步骤"，名字却像"给执行者提交计划"；`submit_coordination_plan` 实际是"把任务委派给子智能体"，`coordination` 含义含糊；`report_executor_step` 同样带有误导的 `executor`；`promote_direct_task` 暴露了内部路由术语 `direct`。另外内置控制工具使用 `snake_case`（`pause_task`），业务工具使用点分命名（`content.draft.upsert`、`knowledge.search`），`context.load` 又是点分的内置工具，约定不一致。

决策：模型可见的工具名按"动词加宾语"表达行为，与技能名对应；内置控制工具统一 `snake_case`，业务工具保持点分命名空间。`context.load` 是否并入 `snake_case` 另行评估。

| 现名 | 新名 | 对应技能 | 说明 |
|---|---|---|---|
| `submit_executor_plan` | `plan_steps` | `builtin-step-planning` | 当前节点提交有序步骤并开始执行 |
| `report_executor_step` | `report_step` | `builtin-step-planning` | 上报步骤开始、完成、失败 |
| `submit_coordination_plan` | `delegate_agents` | `builtin-agent-delegation` | 把任务委派给子智能体并声明聚合方式 |
| `promote_direct_task` | `promote_to_task` | 协调者（轻量态） | 对话升级为持久 Task |
| `request_clarification` | 不变 | `builtin-task-triage` | 已表达清楚 |
| `inspect_tasks` 等任务控制工具 | 不变 | 无 | 已符合约定 |

- 项目未发布，直接替换，不保留旧名别名（遵守"禁兼容层"）
- `delegate_agents` 不沿用 AgentScope 的 `agent_spawn`：后者语义是"立即派生一个子智能体并同步或异步返回结果"，而 AAF 的是"声明一个持久 DAG（多个执行者、依赖、聚合合同）并立刻结束本轮"，同名会让模型误以为能拿到即时结果
- 领域模型名 `ExecutorPlan`、`ExecutorPlanStep`、事件类型 `EXECUTOR_PLAN_*` 同样带有 `executor` 误导，是否随之改为 `StepPlan` 涉及数据表和事件契约，单独评估，不与工具重命名绑定

### 与 AgentScope 原生工具的对照

AgentScope 原生的任务与子智能体工具是分开的多个工具（见 `docs/design/audit/2026-08-30-agentscope-boundary` 与 `tmp/agentscope-java` 源码）：

| 原生工具 | 位置 | 作用 | AAF 对应 | 取舍 |
|---|---|---|---|---|
| `todo_write` | core | 模型整表替换待办清单，存入 `AgentState.tasksContext` | `plan_steps`、`report_step` | 审计结论为不启用：整表替换、无一等变更事件，与 `TaskPlan` 冲突；AAF 用带状态机和一等事件的版本 |
| `plan_enter`、`plan_write`、`plan_exit` | harness Plan Mode | 执行内的阶段闸门，从只读切到可写 | `plan_steps`（提交即批准并进入执行） | AAF-107 / ADR-006 已把闸门合并为一次工具调用 |
| `agent_spawn` | harness | 派生子智能体并发送任务 | `delegate_agents` | AAF 派生的是持久 DAG 节点，不是循环内的临时实例 |
| `task_output`、`wait_async_results` | harness | 读取或等待后台子任务结果 | 无，由聚合者节点承担 | 原生由父智能体**在自己的循环内等待并聚合**；AAF 为了持久恢复，改为协调者循环结束、聚合者另起一轮 |
| `agent_generate` | harness | 动态生成子智能体定义 | 无 | 审计结论为关闭 |

- 原生模式的取舍：父循环内等待聚合，循环数少，但子任务不可跨进程恢复；AAF 选择持久 DAG，代价是 N+2 个循环。短时、不需要持久的并行委派，后续可评估是否提供循环内模式

### DIRECT 与 TASK 的关系

- 两者在决策层应当一致：都由一个"主协调智能体"做前注意与三档判断，共用 `builtin-task-triage`，规则只写一份
- 差异在**控制层**：DIRECT 只读、无 Task、无持久恢复、无授权类动作；TASK 有持久对象、暂停恢复、HITL、产物保存、节点级事件
- 现状两者的实现路径不同：DIRECT 走 `executeDirect`（`agentKind=PRIMARY`、无 `nodeIdentity`，工具为任务控制与升级），TASK 走持久节点（`agentKind=COORDINATOR`、有 `nodeIdentity`，工具为计划类）；指令文本（`InvocationPolicy.PRIMARY` / `COORDINATOR`）、上下文披露（`primaryMinimal` / `coordinatorSummary`）也各自独立
- 决策：统一为**同一个协调者**，DIRECT 与 TASK 是它的两种**容器状态**，不再有 PRIMARY 这个独立角色
  - 轻量态（原 DIRECT）：没有 Task，只读，快速响应
  - 任务态（原 TASK 内的协调者节点）：有持久对象、暂停恢复、HITL、产物保存、节点级事件
  - 轻量态升级为任务态沿用现有 `promoteDirect`：原 Execution 标记 `PROMOTED`，生成 `TaskPlan.coordinated` 并以升级命令重新派发协调者节点（`Source.PROMOTION`）。升级是新开一个执行，不是在原循环里原地继续
- 命名：`AgentKind.PRIMARY`、`InvocationPolicy.PRIMARY`、`primaryMinimal` 并入 `COORDINATOR`，指令文本与上下文披露合为一套；文档与对外术语统一称"协调者"（原因：`COORDINATOR` 已贯穿节点类型、保留节点键 `coordinator`、计划与事件，改名成本最低；"主智能体"可在文档中作为协调者的说明性别名）
- 轻量态支持 HITL：授权审批与结构化澄清依赖持久中断点与恢复，必须有 Task，所以轻量态内遇到"需要授权的工具调用、结构化澄清、多步骤持久"时必须**升级**。现状只靠指令文本要求模型在外部副作用前调用升级工具，建议增加 harness 约束：轻量态下 `ToolGateway` 判定某调用需要授权时，自动触发升级而不是直接拒绝。该约束需要核实能否保留已产生的输出与待执行的工具调用，升级后协调者重新执行，不保证延续原循环中间态
- 轻量态不支持 `plan_steps`：`ExecutorPlan` 以 `taskId` 与 `nodeId` 为键持久化，没有 Task 无法使用；多步骤诉求走升级
- 升级补充技能与工具：升级产生新的执行，画像在新执行开始时重新冻结，因此任务态独有的技能（`builtin-step-planning`、`builtin-agent-delegation`）与工具（`plan_steps`、`report_step`、`delegate_agents`）随新执行自然补入，不需要在原循环中途追加；轻量态独有的任务控制工具在任务态不再提供
- 升级继承技能：`TaskCommandService.promoteDirect` 构造 `TaskPlan.coordinated` 时 `skillKey` 目前传 `null`，需改为继承轻量态执行画像中已激活的 FIXED 技能（已确认要继承）
- 方向：协调者在任务态内的上下文与工具边界向轻量态对齐（见"协调者工具与上下文边界"），`promote_to_task` 是协调者"需要持久化"的判断出口；两条执行路径暂不合并实现，因为容器能力不同，合并会把持久化开销带给普通聊天

### 聚合方式

现状（读 `TaskPlan.applyTaskPlanDraft`、`AssistantApplicationService.executionContext`）：

- 聚合者是系统追加的独立节点，依赖全部执行者，通过 `inputBindings`（`result.<执行者 nodeId>`）取得各执行者结果，由调度器在依赖满足后派发；它**不在任何循环里等待**，等待由 DAG 调度完成
- 聚合者使用协调者的 Role、Skill、模型，但拥有自己的 `executionId` 与 AgentState 槽位，上下文披露为 `SUMMARY_ONLY`；协调者先前的推理过程与消息**不会带入**聚合者
- 聚合者实际收到的输入（`JpaTaskMaterializationAdapter` 经 `TaskPlan.resolveInput` 生成）= 固定的聚合指令文本 + 各执行者完整结果（`result.<nodeId>`）。`forNode` 从根命令继承 `contextCandidates`、`memorySubject`、模型选择，但命令的 `input` 被替换为上述文本，因此**原始用户请求全文与协调者提交的 `goal` 都不在聚合者输入里**；聚合者只能通过 `SUMMARY_ONLY`（至多 8 个来源、预算 1024）的受控上下文间接获得一部分。`PromptAssembler` 也不注入任务目标。对 `AGGREGATOR_REDUCE`（需要理解目标才能综合）这是缺口，对 `PASS_THROUGH` 与 `ORDERED_CONCAT` 影响小

决策：聚合者的上下文用**确定性的结构化输入**补齐，不重放协调者的完整循环

- 聚合者输入契约由 harness 组装：原始用户请求全文（取自根命令 `input`，`Task.inputRef` 已记录）、协调者提交的 `goal` 与聚合方式说明、计划摘要（执行者清单、各自职责与依赖）、各执行者完整结果
- `delegate_agents` 入参增加协调者撰写的**聚合要求**（`aggregationContract.requirements`），在分配子智能体的同一次调用里产出，因为此刻协调者拥有完整上下文：用户真正要什么、输出风格与篇幅约束、各执行者结果应如何综合、需要核对什么。聚合要求随计划冻结，由 harness 原样放入聚合者输入。命名区分：`aggregationContract` 的 `kind`、`executorOrder` 是结构性的聚合合同，`requirements` 是自由文本的聚合要求
  - `AGGREGATOR_REDUCE` 必填；`PASS_THROUGH`、`ORDERED_CONCAT` 可选
  - 强制方式分三层，不只靠提示词：
    - 工具入参模式：`requirements` 写入 `delegate_agents` 的 `inputSchema`，字段说明里写清应包含的内容（用户目标、综合方式、格式与篇幅、需核对项）
    - 工具校验：`AGGREGATOR_REDUCE` 下为空白或超长时直接抛出带明确原因的错误，ReAct 循环收到错误后让协调者补充重试；JSON Schema 无法表达"按 `kind` 条件必填"，所以由代码校验而不是 `required` 数组
    - 技能指导：`builtin-agent-delegation` 正文给出写法示例，`InvocationPolicy.COORDINATOR` 的指令同步更新工具入参清单
  - 长度只设宽松的安全上限，默认 8000 字符（与 `AssistantExecutionService.MATERIAL_BUDGET` 同量级），不设业务意义上的短上限；超限由工具校验拒绝并要求协调者精简。理由：聚合要求会整体进入聚合者输入、也占用协调者的输出 token，不设界会被异常输出撑爆；正常的综合说明远小于该值，超过说明协调者在代写结果而不是写要求
  - 上限可由业务调整，以**请求参数**方式传入，不放技能配置：现有技能结构（`SkillVO`、`SkillCreateDTO`）里没有通用参数字段，只有仅用于界面 placeholder 的 `instancePrompt`，放技能配置需要新增字段与表结构变更，而请求参数只改 DTO。做法与文案页已有的篇幅参数一致（前端把长度选项作为 `output.maxCharLen` 传入）：在 `AssistantExecutionRequest.ExecutionOptions` 增加可选的聚合要求长度字段，不传则取系统默认
  - 解析顺序：请求参数、系统默认（`AiProperties`，`aaf.ai` 配置前缀，默认 8000 字符）；有硬顶 32000 字符（与 `MAX_OUTPUT_CHAR_LEN` 同量级），请求值超过硬顶按硬顶处理并记日志，防止客户端绕过保护。请求参数来自客户端，因此硬顶是必须的
  - 注意区分：`output.maxCharLen` 是**最终输出**的篇幅提示，聚合要求长度是**协调者写给聚合者的指令**的上限，两者不是同一个参数，不能复用
  - 实际生效的上限写入 `delegate_agents` 的字段说明，让协调者在调用前就知道边界
  - 协调者还可在每个执行者的任务描述里写明结果格式要求，便于聚合者综合
  - 聚合要求是协调者模型生成的内容，对聚合者只作指导，不具备授权效力；聚合者的 Role、Skill、工具与预算仍由冻结基准决定
- 聚合者对这些结构化条目允许内容级披露，原始附件与不可信大段材料仍只给摘要
- 这比重放协调者完整消息的 token 成本低，也保持聚合者与协调者之间的信任边界

候选方向（待评估，本期不实施）：**协调者挂起并恢复**

- `delegate_agents` 调用后协调者节点进入等待态，类似现有的授权或澄清中断；全部执行者完成后，用现有的 `RESUME` 与 `stateSlot` 机制恢复同一个协调者的 AgentState，把各执行者结果作为 `delegate_agents` 的工具结果注入，协调者继续并产出最终回复，成为交付节点
- 好处：协调者保留之前的上下文，聚合即协调者本人完成，符合"协调者统一负责"的模型，可取消独立聚合者节点
- 代价：需要改动 `TaskPlan` 的 finalizer 约束（保留键 `aggregator`、`RootResult.sourceNodeId` 必须是 `aggregator`、完成证据唯一性）；恢复时重放协调者的完整上下文，token 成本高于只看摘要的聚合者；并行节点完成的竞态与 fencing 需要核实
- 短期保持现状：聚合者节点仍存在，但其交付标志、Role、Skill 与结果绑定按本 ADR 其它决策调整

### 分解策略

- 分解策略只通过**请求参数**指定，不由技能或角色配置声明：取值 `AUTO`（助理自行判断，不传时的默认）、`NONE`（协调者不得拆分，强制单段）、`REQUIRED`（必须拆分）
- 字段放在 `AssistantExecutionRequest.ExecutionOptions`，与 `interactionMode`、`routeConstraint` 等执行选项同处，可选，缺省为 `AUTO`
- 谁决定策略：调用方。文案页这类入口明确知道"单段直答"，由前端随请求传 `NONE`；通用任务入口不传，由协调者自行判断。技能不携带策略，同一技能在不同入口可用不同策略
- 策略通过**工具可见性**强制：`NONE` 时不向协调者暴露 `delegate_agents`；`REQUIRED` 时仅提供 `delegate_agents` 与 `request_clarification`。不依赖提示词约束
- 程序化提交（`submitTask`，服务端触发）同样通过请求参数传入，不传按 `AUTO`
- 策略随根命令冻结，节点重试、恢复沿用同一策略，协调者不能在循环内改变

### 产物保存

- 内容类产物（文案、脚本、文章）默认保存为**文档草稿**，使用文档模块统一入口；业务通过产物引用（`artifactId`、`artifactVersionId`）在自己一侧建立关联
- 结构化业务动作（蓝图提案提交、项目生成动作）不属于内容保存，继续使用各自业务工具
- 协调者不获得业务工具，保持"不调用业务工具"的边界
- 保存由 harness 在**交付节点完成后**通过 `ToolGateway` 调用业务注册的保存处理器，授权、审批、审计与完成证据 `DRAFT_COMMITTED` 不变；不再让模型把全文作为工具参数重新输出一遍
- `ArtifactPolicy.saveTool` 保持可替换，特殊业务可注册自己的处理器

### 流式传输

- `MESSAGE_DELTA` 属于瞬态展示数据，**不写入事件表**，只经实时事件端口转发；完整文本由 `MESSAGE_COMPLETED` 携带并落库
- 实时端口 `ExecutionLiveEventPort` 在 `AssistantApplicationService.invoke` 出口统一发布事件，入口订阅方必须在触发执行**之前**登记订阅
- SSE 结束条件按交付节点收口，或失败、取消、等待授权、澄清、暂停时结束，不再以第一个节点收口为准
- 当前实现为单实例进程内通道，已确认当前不需要多实例；将来要多实例部署时替换为跨进程通道（如 Redis Streams），端口契约不变
- `TEXT_MESSAGE_CONTENT` 的空 `delta` 在投影层丢弃，避免前端校验失败中断整个流

### 全环节流式与过程可视化

- 协调者、执行者、聚合者的文本增量都经实时端口流出，每个事件携带 `nodeIdentity`（`nodeId`、`kind`、`roleKey`）与合成的 `source` 路径，参考官方 `SubagentEventConverter` 的约定：`source != null` 的事件不作为交付正文
- 内部节点的文本增量增加带文本的过程事件（`CUSTOM`，按节点归类），仅走实时通道，不落库；交付节点仍走标准 `TEXT_MESSAGE_*`
- 前端"执行过程"按节点分组，**默认折叠**，展开后与查看协调者一致，流式显示该节点输出
- 只有交付节点的输出作为最终结果展示并保存产物

### Positive Consequences

- 简单任务回到一个循环，减少模型调用、延迟与积分消耗
- 边界由代码强制，模型违规不再静默变成"无交付"
- 事件表写入量由每次回复约 100 次降到与状态事件同量级
- 业务产物落库方式解耦，框架只依赖产物引用
- 并行子任务的进度可观察，且不影响交付语义

### Negative Consequences

- 协调者直答要求修改 `nodeIdentityOf`、`InvocationPolicy` 与完成校验，涉及 `TaskPlan` 交付规则，需要人工审核
- delta 不落库后，崩溃时正在生成的那一块中间文本会丢失，由任务重试策略重新生成
- 命中重放守卫时回放的 AGENT 事件不再包含增量，`MESSAGE_COMPLETED` 在 AG-UI 投影里不产生正文，该边缘场景下前端看不到逐字内容
- 内部节点文本进入过程事件，需要确认其披露边界仅限任务所有者
- 单实例进程内通道不支持多实例部署

### Reversal Triggers（反向选择触发条件）

仅当出现以下之一时考虑回切：

1. 协调者单循环判断的质量在 `AUTO` 下持续不达标，且无法通过技能文案和分解策略约束改善，此时再评估独立前置判断
2. 部署形态变为多实例，进程内实时通道无法满足，需要先落地跨进程通道
3. 合规或审计要求保留逐字增量，此时需要重新引入 delta 持久化（可按块合并写入）

## Pros and Cons of the Options

### 保持现有 harness 流程，在其上修正契约与出口

- Good: 与 AAF-107 的结论一致，改动集中在交付标志、指令、保存与传输
- Good: 简单任务可降为一个循环
- Bad: 需要人工审核 `TaskPlan` 与 `NodeIdentity` 相关规则的调整

### 恢复独立前置复杂度判断环节

- Good: 判断步骤显式、可单独审计
- Bad: 多一次模型调用，且只看得到目标文本，判断质量低于协调者
- Bad: 与 AAF-107 已删除 `TaskComplexityAnalyzer` 的决定相反

### 所有 TASK 统一固定三段

- Good: 流程统一，无分支
- Bad: 单执行者加 `PASS_THROUGH` 时聚合者只是逐字复述，白白多一次模型调用
- Bad: 文案类简单任务延迟和消耗翻倍

## More Information

- 起因与相关设计：[AAF-115 requirement](../../task/v0.13/AAF-115/requirement.md)、[AAF-115 design](../../task/v0.13/AAF-115/design.md)、[ADR-006](ADR-006-executor-plan-mode.md)、[runtime.md](../framework/intelligent/runtime.md)
- 已实施（待验证）：`MESSAGE_DELTA` 不落库并经 `ExecutionLiveEventPort` 转发；`AssistantExecutionService` 先订阅后提交；审批与澄清恢复流程改为实时订阅；`TextMessageEventConverter` 丢弃空 delta。SSE 结束条件仍是"第一个节点收口"，需按本决策调整
- 待实施：协调者交付标志、COORDINATOR 指令与技能统一、分解策略、harness 驱动的产物保存、SSE 结束条件、节点过程事件与前端折叠展示
- 待核实：AUTO 路由下 L0 选择在 TASK 路径的确切调用点（`resolveExecutionProfile`）；单执行者加 `PASS_THROUGH` 时聚合者是否仍执行一次模型调用（未见短路逻辑）；harness 主动发起 `ToolGateway` 调用在现有结构中的可行性；保存标题的来源；`REQUEST_ON_DEMAND` 下保存触发审批的暂停点
- 文档同步：`runtime.md` 与 `coordination.md` 中"协调者简单直答、`TaskBoard.single` 已删除"的描述与 AAF-115 代码不一致，需在 `improvements.md` 记录并由协调者更新
