---
level: Practice
layer: Principle
purpose: 定案 EXECUTOR 先规划再执行（AAF 版 Plan Mode）的数据模型、状态机与审批策略
status: accepted
version: 1.0.0
date: 2026-09-01
author: Kiro
---

---
status: accepted
date: 2026-09-01
deciders: [AaronZZH]
consulted: []
informed: []
related-tasks: [docs/task/v0.12/AAF-107/tasks.md, docs/design/audit/2026-09-01-harness-landing-plan.md]
---

# ADR-006: EXECUTOR 先规划再执行（AAF 版 Plan Mode）

## Context and Problem Statement

[Harness 落地计划](../audit/2026-09-01-harness-landing-plan.md) 已确认 AAF Harness 对齐官方 `harness/architecture.html` 能力表后，唯一真正的空白是**计划模式**——工作区/文件系统/沙箱/双层长期记忆/对话压缩/子 Agent 编排均已有 AAF 自研等价物或明确裁定不需要（见该计划「明确不做的事」）。官方 `HarnessAgent.enablePlanMode()` 让 Agent 在动手前先进入只读阶段、写计划、经 HITL 确认再执行；AAF 的 `DelegatedTaskCoordinator` 里已有 `EXECUTOR` 节点概念（`TaskBoard.SubTask.Kind.EXECUTOR`），但目前从协调者派发直接进入执行（`commands.execute(childCommand)`），没有自己的规划阶段。

本 ADR 回答："给 EXECUTOR 加一层先规划再执行的门控，数据落在哪、状态机怎么走、谁能批准、以及为什么不能直接照搬官方实现。"

## Decision Drivers

- **持久化边界**：AAF 原则是 AgentState/workspace 只是热状态，不是事实来源（`docs/design/framework/intelligent/architecture.md`）；官方 Plan Mode 把计划写进 `plans/PLAN.md` 文件、状态挂在 `AgentState.planModeContext`，与该原则冲突
- **审批复用**：AAF 已有生产可用的持久 HITL 链路（`ai_hitl_approval` + `ai_hitl_recovery` + `PersistentHitlCoordinator`），不应为计划审批新建平行审批体系
- **画像冻结不变量**：`ExecutionProfileSnapshot` 要求"单次执行冻结完整运行画像，恢复必须复用同一快照"，若在同一次 execution 内用 `ToolSuspendException` 挂起等待审批再继续，画像会在运行中途变化
- **范围收窄**：不是所有 EXECUTOR 都需要先规划——大量自动化流水线任务不应该每次都新增一次规划-执行往返
- **自动化优先，人工兜底**：AAF 场景下多数计划应可确定性自动通过，不能让每条计划都排队等人工，也不能让模型自己判断"这条够安全不用审批"

## Considered Options

### 议题一：计划载体

- A. 复用官方 `plans/PLAN.md` workspace 文件形式
- B. 新建 DB 表持久化结构化计划（plan + step 两表）
- C. 塞进现有 `ai_task_board.board_payload` JSONB，不新建表

### 议题二：谁进入规划阶段

- A. 所有 EXECUTOR 都强制先规划（对齐官方"模型自主决定进不进 plan mode"但反过来做成强制）
- B. 只对 policy 显式标记 `requiresPlan=true` 的任务生效，未标记任务行为完全不变

### 议题三：计划由谁产出

- A. 新建专用 planner Agent 定义，与执行 Agent 分离
- B. 由执行 Agent 自己产出，作为同一 Agent 定义下独立的一次 planning execution
- C. 在执行 execution 内用 `ToolSuspendException` 挂起等待审批，同一次 execution 继续

### 议题四：谁批准计划退出只读阶段

- A. 复用官方模式：模型调 `plan_exit` 触发 HITL，一律人工确认
- B. 确定性白名单驱动自动批准，未命中才走人工审批；模型自评的 risk 只做展示不做门控
- C. 让模型的自评风险分数直接决定是否需要人工审批

## Decision Outcome

**议题一选 B**：新建 `ai_executor_plan`（计划头）与 `ai_executor_plan_step`（步骤）两表，按当前阶段约束直接追加进 `v16__intelligent_runtime_schema.sql`，不新分配 `vN`、不写回滚脚本。

**议题二选 B**：范围收窄为 policy 显式标记的任务，`ExecutionPolicy` 新增 `requiresPlan` 布尔字段（默认 `false`），未标记任务的 `DelegatedTaskCoordinator.executeSubTask` 行为不变。

**议题三选 B**：计划由执行 Agent 自己产出，作为同一 Agent 定义下的独立 planning execution（新 `executionId`，复用同一 `AssistantId`/Role/Skill/冻结工具白名单），不新建专用 planner Agent，也不在同一次 execution 内挂起等待。

**议题四选 B**：`SUBMITTED → APPROVED` 由确定性白名单驱动（按任务类型 + 冻结工具集合声明的组合可自动过）；未命中转 `REVIEW_REQUIRED`，复用 `PersistentHitlCoordinator` + `ai_hitl_approval`/`ai_hitl_recovery`，不新建审批表。模型给出的 `risks` 字段只作展示与审计，不作为门控输入。

### 核心论据

1. **官方 Plan Mode 的"只读阶段 + 白名单工具 + HITL 退出"结构可以借鉴，但计划正文的持久化形式不能照搬**——`plans/PLAN.md` 是 workspace 文件，AAF 的 workspace 能力已被计划文档裁定不引入（避免与 TaskBoard/DB 形成并行事实源）。计划必须是可查询、可加乐观锁、可与 `ai_delegated_task`/`ai_task_board` 同事务提交的结构化行，因此选两表而非文件或 JSONB blob——`board_payload` 是整板一次性 JSONB 写回，放不下计划自己的版本号、审批引用和步骤级状态查询需求。
2. **"只对标记任务生效"避免了强制性侵入既有行为**——`DelegatedTaskCoordinator.executeSubTask` 是生产已跑通的路径（AAF-103/104 已交付），把 EXECUTOR 分支改为强制先规划会影响所有现有委托任务；用 `ExecutionPolicy.requiresPlan` 挂钩后，只有显式声明需要计划的任务改变行为，符合"精准修改"原则。
3. **独立 planning execution 而非同 execution 挂起，是画像冻结不变量的直接推论**——`ExecutionProfileSnapshot` 的存在前提是"一次 execution 对应一份不变的画像"；如果在同一 execution 内用 `ToolSuspendException` 等审批，画像会跨越"规划期"和"执行期"两个语义不同的阶段，且中途可能因配置变更而不一致。两次独立 execution（`planId/revision` 作为执行前显式输入）让每次 execution 依然只对应一份画像，恢复语义不变。
4. **确定性白名单优于"模型自评风险"，是权限系统的基本原则**——让模型自己判断"这个计划够安全，不需要人工看"等于把授权决策权交给被授权对象本身，是循环授权。白名单由 AAF 侧按任务类型 + 冻结工具集合的组合显式声明，模型的 `risks` 字段仅供人工审阅参考和事后审计，不读取它做分支判断。
5. **复用 `ai_hitl_approval` 而非新表，是因为审批语义完全一致**——"计划待审批"和"工具调用待审批"在 AAF 现有模型里都是"一个 Actor 对一件事做 approve/reject 决策，决策需要跨重启持久且可幂等重放"，`PersistentHitlCoordinator` 已经把这套语义做完，新建平行审批表是重复造轮子且会产生两套恢复逻辑。

### Positive Consequences

- 计划、步骤、审批全部落在既有事务与租约体系内，无需新的分布式一致性设计
- 未标记 `requiresPlan` 的任务零影响，AAF-103/104 的既有测试与行为保持不变
- 自动批准白名单让大部分自动化流水线任务无需人工介入，同时保留人工兜底
- 与官方 Plan Mode 的核心价值（先想清楚再动手、只读期强约束）对齐，便于团队理解和对照文档

### Negative Consequences

- 新增两表意味着 `DelegatedTaskCoordinator` 的 EXECUTOR 分支变复杂（`requiresPlan → ensureApprovedPlan → executeApprovedPlan` 三段式）
- planning execution 是额外一次模型调用，对需要计划的任务增加延迟与成本
- 白名单本身需要维护；初期覆盖不全时会有更多任务落入人工审批而非自动通过，属预期的保守起点
- 本阶段两表直接追加进 v16（无独立迁移文件），是已授权的阶段性偏离，回滚只能靠重建库

### Reversal Triggers（反向选择触发条件）

仅当出现以下之一时考虑回切：

1. AgentScope 官方为 Plan Mode 提供官方持久化桥接方案（DB 而非 workspace 文件），且桥接成本低于维护 AAF 自建两表
2. 实践证明"只对标记任务生效"的范围收窄不够用，大部分任务都需要计划，此时应重新评估是否默认开启而非白名单标记
3. 白名单规则复杂度增长到需要一套独立 DSL 才能维护，此时应重新评估是否要接入 `PolicyDslCompiler`/`PolicyExpressionEvaluator` 而非硬编码判定

## Pros and Cons of the Options

### 议题一 A（官方 workspace 文件）

- Good: 与官方文档完全对齐，用户可直接查阅官方 Plan Mode 文档理解概念
- Bad: 引入 workspace 能力，与「明确不做的事」冲突；无法用 SQL 查询步骤状态、无乐观锁
- Bad: 分布式部署下文件系统一致性需要额外沙箱/远端 KV 设计，AAF 明确不引入

### 议题一 C（塞进 `board_payload`）

- Good: 不新建表，改动面最小
- Bad: 整板 JSONB 一次性写回会放大锁竞争；无法用数据库约束保证"同 plan 最多一个 RUNNING 步骤"等不变量
- Bad: 计划版本、审批引用、步骤级查询全部需要在应用层手工维护一致性

### 议题三 A（专用 planner Agent）

- Good: 关注点分离，planner 逻辑独立于执行逻辑
- Bad: 计划步骤必须与执行 Agent 真实可用的工具白名单、Role、Skill 一致；专用 planner 要复制一份画像，属并行抽象，两者一旦漂移就产出不可执行的计划
- Bad: 需要额外定义与维护一个新 Agent 类型，增加配置面

### 议题三 C（同 execution 内挂起）

- Good: 技术上已具备能力（`ToolSuspendException` 挂起链路已存在）
- Bad: 直接违反 `ExecutionProfileSnapshot` 的单次执行单一画像不变量
- Bad: 挂起期间画像可能因配置变更漂移，恢复时无法判断该用规划时的画像还是当前配置

### 议题四 A（一律人工确认）

- Good: 最保守、最安全，与官方默认行为一致
- Bad: 不满足 AAF 大量自动化流水线场景的效率要求，每条计划都排队等人工不现实

### 议题四 C（模型自评决定是否审批）

- Bad: 循环授权——被授权对象自己评估是否需要被审查，安全设计上不可接受

## More Information

- 输入材料：[2026-09-01 Harness 落地计划](../audit/2026-09-01-harness-landing-plan.md)「EXECUTOR 先规划再执行」章节
- 官方参考：<https://java.agentscope.io/v2/zh/docs/harness/plan-mode.html>
- 现状代码：`apps/service/aaf-framework/.../assistant/application/DelegatedTaskCoordinator.java`（`executeSubTask` EXECUTOR 分支）、`apps/service/aaf-framework/.../assistant/model/ExecutionProfileSnapshot.java`、`apps/service/aaf-framework/.../assistant/application/PersistentHitlCoordinator.java`
- 现状表结构：`apps/service/aaf-api/src/main/resources/db/migration/v16__intelligent_runtime_schema.sql` 的 `ai_hitl_approval`、`ai_hitl_recovery`、`ai_delegated_task`、`ai_task_board`
- 后续动作：
  1. 人类审核本 ADR 通过后，启动 [AAF-107 #10702](../../task/v0.12/AAF-107/tasks.md) 落地两表与状态机
  2. #10703 落地 planning execution 与 acting gate
  3. #10704 落地审批与恢复
  4. #10705 落地计划事件与 AG-UI 投影

## 勘误与决策补充（2026-09-01 #10703 期间人类拍板）

#10702 落地后，#10703 实施期间发现两处需要修正的范围，均已获人类确认。**议题一、三（核心结论）、四不变**，只调整议题二的判定机制与议题三的适用节点范围。

### 补充决策一：`ExecutorPlan`/`ExecutorPlanStep` 适用节点从"仅 EXECUTOR"放宽为"任何自行执行的节点"

原议题三讨论隐含了一个未言明的前提——"局部执行计划"只服务 `TaskBoard.SubTask.Kind.EXECUTOR`。人类澄清：协调者（COORDINATOR）在"复杂任务但不需要派生子节点"的场景下，也应该能对自己接下来要做的事先规划、按步骤推进，而不是只有两个选项（直接执行 vs 派生 `CoordinationPlan`）。

这与原三层任务复杂度模型对齐：① 简单任务直接回复（`SINGLE_AGENT`，已支持）；② 复杂任务协调者拆解并派发给多个 EXECUTOR 并行执行（`CoordinationPlan`，已支持）；③ 复杂任务但协调者判断不需要派生子节点，自己按步骤推进（`ExecutorPlan`，本 ADR 范围）。三层递进，互不替代——③ 不会侵入 ②的职责：`CoordinationPlan` 回答"要不要派生子节点、派给谁"，`ExecutorPlan` 回答"这个节点（无论协调者还是执行者）自己动手时按什么步骤走"。

**结论**：`ExecutorPlan.boardId`/`executorAgentId` 字段本身是纯字符串，不引用 `NodeIdentity.NodeKind`，结构上无需改动；调整的是文档措辞与 #10703 调用方约束——不再限定"只有 EXECUTOR 分支能调用 `beginPlanning`"，COORDINATOR 分支判断"本轮不派生子节点、自己执行"时同样可以走本状态机。DB 表、`ExecutorPlanPort` 接口不变。

### 补充决策二：议题二从"纯静态 policy 标记"改为"协调者声明 + AAF 侧确定性规则兜底"两层判定

原议题二选 B 的措辞"只对 policy 显式标记 `requiresPlan=true` 的任务生效"隐含判定权在**运行前的静态配置**（Skill/Assistant 定义），协调者对是否规划没有输入权。人类澄清需求：协调者应该能根据任务复杂度**动态**建议是否需要规划——不能完全靠运行前的静态标记，因为任务复杂度往往在协调者拆解时才能判断。

**这条与议题四"模型自评 risk 不作门控输入"存在同类风险**（把决策权交给模型本身），因此不能改成协调者完全自主决定，必须保留 AAF 侧确定性规则作为最终仲裁：

- 协调者在产出 `CoordinationPlan.ExecutorAssignment`（或自己直接决定不派生节点）时，可以给出一个**建议性**信号（是否认为需要先规划），地位与 `submit_executor_plan` 的 `risks` 字段相同——仅供参考，不直接决定。
- AAF 侧规则可以：(a) 忽略协调者建议、强制要求规划（例如某些高预算/高风险动作组合）；(b) 尊重协调者建议；(c) 忽略建议、强制不要求规划（例如已被证明轻量的动作组合）。三种结果都由 AAF 侧规则给出最终判定，协调者的建议只是输入之一，不是决定本身。
- 该规则暂不接入 `PolicyDslCompiler`（与原 Reversal Trigger 3 一致，复杂度不足以立即接入独立 DSL），先以硬编码规则实现（对应 `ExecutorPlanAutoApprovalPolicy` 同层级的一个新判定接口，与自动批准白名单是两个独立判定，不要合并——一个判"要不要规划"，一个判"规划是否要人工审"）。

**结论**：`ExecutionPolicy.requiresPlan` 字段设计已确认放错层（#10702 曾短暂加入，已撤销）——`ExecutionPolicy` 是 core `ReActAgent.builder()` 的执行边界参数（迭代数/超时），与"是否需要规划"这个编排层决策无关，不应混用。是否规划的最终判定结果应该体现在 `TaskBoard.SubTask` 或等价的编排层结构上，由 AAF 侧规则计算后写入，不接受协调者直接写入决定值，只接受协调者的建议信号作为规则输入之一。具体字段落点与规则实现留给 #10703 剩余工作，本节只定决策方向。

### 补充决策三：不需要 `plan_enter`/`plan_write`/`plan_exit` 工具

人类曾询问是否需要对齐官方三个工具。结论：不需要。官方需要这三个工具是因为整个规划到执行的切换发生在**同一次** Agent 调用内，模型必须显式告知系统"现在进入/退出只读阶段"。AAF 已定（议题三选 B）规划与执行是两次独立 execution，execution 边界本身就是状态切换点，由 `DelegatedTaskCoordinator` 在两次 execution 之间判断和切换，不需要模型在调用内主动声明状态切换。`plan_write` 由一次性的 `submit_executor_plan` 工具取代（提交完整计划而非增量写文件）。

"进入/退出规划"对前端的展示事件（如 `EXECUTOR_PLAN_CREATED`）属于 #10705（计划事件与 AG-UI 投影）的范围，本 ADR 不涉及事件设计，只确认不需要工具层面的 enter/exit 调用。

## 决策推翻（2026-09-01，#10704 启动前人类拍板）

与前三条"增补/澄清"不同，本条是对**议题四结论的实质性推翻**，不是补充——原结论（"确定性白名单驱动自动批准，未命中转 `REVIEW_REQUIRED` 人工审批"）不再成立，记录如下以保留决策链完整性。

### 推翻依据：对齐官方 Permission System 后发现审批层级放错了对象

核实官方 `permission-system.html` 后确认：官方权限系统按**具体工具调用**（ALLOW/DENY/ASK）分级，没有"计划本身要不要审批"这一层概念——`EXPLORE` 模式（对应 AAF 的 planning execution）只是"放行读、拒绝写"，计划写完之后是否能执行，取决于**计划里每一步实际调用的工具**各自的权限判定，不是"这份计划"这个抽象产物本身要走一次独立审批。

对照 AAF 已有机制，"提交计划"这个动作被审批的风险点其实已经在别处被覆盖：

- **拆分子智能体**（协调者产出 `CoordinationPlan`，派生新 EXECUTOR 节点、扩大执行面）——风险点在"新增了谁能做什么"，由现有工具/动作授权链路（`AssistantExecutionService`/`ToolAuthorizationContext`）处理，属于协调者层面已有的审批点，不需要为 Plan Mode 再加一层。
- **普通任务步骤拆分**（单节点把自己的子任务拆成 `ExecutorPlanStep`）——不新增 Agent 身份、不扩大执行面，只是"怎么分几步做同一件事"，风险等级低，不需要审批。
- **步骤执行阶段的具体工具调用**——已有 `DefaultToolGateway` → `ToolAuthorizationContext.requireVisible` → `AuthorizationGrant` 的完整持久化授权链路（按需 ASK，`ai_hitl_approval` 承载），逐次调用时该怎么审还是怎么审，与是否走过 Plan Mode 无关。

`submit_executor_plan` 提交的是"步骤列表"，不是"新增执行面"或"具体写动作"，因此不需要独立的审批关卡。

### 新结论

- **计划提交后默认直接进入批准态（原 `APPROVED`），不再有 `REVIEW_REQUIRED` 分支**——`ExecutorPlan.Status` 状态机仍保留 `REVIEW_REQUIRED`/`REJECTED` 枚举值（避免破坏已落库数据与已写代码的穷举 switch），但正常路径不再产生该状态；`ExecutorPlanAutoApprovalPolicy`（确定性白名单接口）随之废弹——不再需要"命中放行、未命中转人工"的判定，因为不存在"转人工"这一分支。
- **`PersistentHitlCoordinator`/`ai_hitl_approval` 不需要为计划审批新增接入点**——原计划的 #10704（审批与恢复）范围因此大幅收窄或取消，计划相关的"恢复"只剩"跨重启恢复到正确的执行步骤"这一件事，不再有"恢复到审批结果"这一半。
- **风险控制责任边界更清晰**：Plan Mode 只负责"先想清楚步骤再执行"这一结构性约束（对应官方 `EXPLORE` 模式的价值），不负责"这个计划安不安全"——安全性判断继续完全交给已有的动作/工具授权体系，不重复建设。

### 后续动作

1. #10703 的 `runPlanningExecution` 需要修正：`submit_executor_plan` 提交后不再判定"是否命中白名单"，直接转 `APPROVED`（或跳过 `APPROVED` 中间态直接 `EXECUTING`，由后续实现决定是否需要区分这两态）。
2. 移除或废弹 `ExecutorPlanAutoApprovalPolicy` 及其 Spring Bean。
3. `TaskBoardPort.awaitSubTaskAuthorization`（#10703 新增，用于计划待审批时转板状态）大概率不再需要，因为不再产生 `REVIEW_REQUIRED` 这一状态；需要复核 `runPlanningExecution` 里判断"是否转人工"的分支是否可以整体删除。
4. #10704（审批与恢复）范围需要重新定义或与 #10705 合并——具体如何拆分留给下一轮任务规划。
5. 已发现的官方权限系统优势项（Built-in Checks 动态参数检查、危险资源硬编码黑名单）AAF 当前缺失，记入改进意见池单独评估，不在本次任务范围内补齐。
