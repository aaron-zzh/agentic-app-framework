---
level: Practice
layer: Model
purpose: AAF-115 统一 Task、TaskPlan、Execution 与 DAG 编排技术任务
status: draft
version: 1.6.10
date: 2026-09-12
author: AaronZZH & Kiro
tags:
  - AAF-115
  - Task
  - DAG
  - 技术任务
related:
  - requirement.md
---

# AAF-115 统一任务模型与 DAG 编排任务

## 任务约束

- 需求真理源：[requirement.md](requirement.md)。
- 🔴 高风险架构、接口和数据模型重构，#11502 设计及人类审核通过前不得编码。
- 最终只允许一个根 Task 状态真理源，不保留 `AssistantTask`、`DelegatedTask`、旧 TaskBoard 持久模型的双写、fallback 或兼容层。
- TaskPlan 必须支持串行、并行、fan-out、fan-in、join、环检测、有界并行和 fencing；不能用代码分支硬编码固定三步流程。
- 每条普通用户消息创建新的 Run/Execution；当前 Assistant 负责语义复杂度、分解与最终完成判断，服务端只执行确定性硬门和安全校验，不引入独立 classifier/judge LLM。
- 普通 CHAT 只创建 Execution；控制模式、工具权限、Task promotion 和副作用边界必须由服务端约束。
- 失败策略仅允许 FAIL_TASK/PAUSE_TASK；保留用户可见业务数据，只由 Tool/runtime 所有者释放不可见临时资源。
- AgentScope 原生 subagent 保持关闭；子智能体继续由 AAF TaskPlan 编排并通过统一 `AssistantCommandPort → AssistantApplicationService → AgentExecutionPort` 执行。
- 数据库当前未发布时直接调整目标 schema，不新增兼容迁移链；实际执行前由 #11502 复核部署状态。

## 技术任务

### #11501 现状盘点与需求评审

- **优先级**：P0
- **风险**：🔴 高
- **依赖**：无
- **状态**：✅ 已完成（2026-09-11）— Kiro
- **产出**：迁移前盘点的长期有效结论已精简并入 [design.md](design.md)，过时调用链与迁移矩阵不再单独保留。
- 盘点 `AssistantTask`、`DelegatedTask`、`TaskBoard`、`ExecutorPlan`、TaskControl/Recovery/Dispatch/Event 端口及数据库表的读写者；单列 approval/clarification/PAUSE/take-over、`takeUntil` 流截断、AgentState 删除/保留和 `requireNoHiddenPersistentHistory` 的真实调用路径。
- 建立字段、状态、事件、接口、前端查询和测试的迁移矩阵，明确唯一写入者与可删除重复能力。
- 对照 requirement.md 的 Gherkin AC 完成 product、architect、tester、qa 评审。
- **完成标准**：现状盘点及迁移结论已产出；architect、tester、qa 与人类审核仍按后续流水线执行，不在本阶段声明 CLEAR。

### #11502 冻结统一模型、状态机与 ADR

- **优先级**：P0
- **风险**：🔴 高 — 已获人类批准进入开发
- **依赖**：#11501
- **状态**：✅ 人类批准进入开发（2026-09-11）
- **产出**：[design.md](design.md)
- 设计唯一 `Task` 聚合、可选 `TaskPlan`、`TaskNode`、`TaskDependency`、`Execution` 和 `TaskDispatch`。
- 冻结 Task/Node/Execution 状态机、状态推导责任、事务边界、乐观锁、outbox、lease/fencing、预算、恢复和删除策略；明确同一 Execution 的可恢复分段、fresh attempt、originExecutionId、predecessorExecutionId 与 parentExecutionId 各自语义。
- 明确 DIRECT、TASK、TEAM、promotion、Workflow 与 AIGC 业务 Task 的边界。
- 定义旧表直接替换、数据清理和回滚方案，不建立双写。
- **完成标准**：人类已批准按保守决策开发，后续由代码审查验证；独立 QA 的 NEEDS_CHANGES 历史如实保留。

### #11503 实现唯一 Task 与 Execution 持久模型

- **优先级**：P0
- **风险**：🔴 高
- **依赖**：#11502
- **状态**：✅ 生产实现完成（2026-09-11；待后续统一验证与测试迁移）
- 删除独立 `AssistantTask`/`DelegatedTask` 领域模型及重复 Repository/Port。
- 实现 Task、Execution、TaskDispatch 及其唯一事务写入口，将原合同、预算、owner、checkpoint、恢复和调度字段归位。
- 普通 Run 允许 `Execution.taskId=null`；持久 Task 的节点执行必须携带 taskId/nodeId 和稳定谱系；Task 对 originExecutionId 建立幂等唯一约束，并持有服务端复制的 origin run/correlation/input/public-context 谱系。Execution attempt 记录 predecessorExecutionId 而不复用分解用 parentExecutionId。
- 无 plan 的 TASK_ROOT 以 Task.currentRootExecutionId/currentRootAttemptNo 为唯一当前指针；状态无效、owner/画像变化或 worker 丢失时允许新 executionId/sessionId/state slot 的 fresh attempt，predecessor 约束同 Task/scope，旧 generation 结果必须拒绝。
- **完成标准**：不存在两个根 status 写入者；直接执行和持久执行均可独立完成、失败、取消并审计。

### #11504 实现 TaskAnalysis、自然澄清与直接执行 promotion

- **优先级**：P0
- **风险**：🔴 高
- **依赖**：#11503
- **状态**：✅ 生产实现完成（2026-09-11；TaskAnalysis、自然澄清边界、DIRECT promotion 与 TaskNode structured clarification 创建/interrupt 生产闭环已完成，待后续统一验证与测试迁移）
- CHAT 简单场景确定性走 direct Execution，不创建 Task；缺少普通信息时允许以自然消息结束当前 Execution，下一条用户消息创建新 direct Execution 并通过 Conversation 历史重新分析。
- 自然澄清不创建 Task、不发 canonical interrupt；Task 创建可延迟到信息足以判定。达到 accepted/planned/authorized/long-running/multi-node/structured-input 任一边界后必须已有 Task。
- TASK、TEAM、长任务、副作用、结构化 HITL 与显式恢复场景创建 Task；structured clarification 必须先建 Task 或完成 promotion，再绑定准确 taskId/nodeId/executionId 发中断。
- 设计服务端确定性 `TaskAnalysisPolicy` 与当前 Assistant 的结构化决策接线；同一 Assistant 判断 direct/task/team 并在需要时分解，禁止额外 classifier LLM，也禁止客户端控制 complexity、promotion 或提权。
- 每条普通消息必须产生新 Run/Execution；只有显式绑定 canonical interrupt 的输入可恢复既有 Task，自然澄清回复继续创建新 Execution。
- 支持首次外部副作用前按 originExecutionId 幂等 promotion：锁定 origin DIRECT 后必须证明不存在任何已提交/未知副作用 receipt 或 intent；存在即 fail-closed 拒绝。原 direct Execution→PROMOTED，新 Task 记录 originExecutionId，新 TaskNode Execution 使用独立 ID，并由服务端复制 conversation/run/correlation/原始输入谱系；客户端不得覆盖。
- promotion 只引用公开且已冻结的上下文，不持久化隐藏 chain-of-thought、私有 scratchpad 或 AgentState；canonical event producer key 由 tenant+originExecutionId+event type 稳定派生。
- **完成标准**：自然澄清和简单问答不进入任务列表；补充信息后会重新分析；结构化澄清前必有唯一 Task；并发 promotion 不重复建任务且不扩大授权。

### #11505 实现 TaskPlan DAG 与串并行调度

- **优先级**：P0
- **风险**：🔴 高
- **依赖**：#11503
- **状态**：✅ 生产实现完成（2026-09-11；DAG 调度、universal Owner Aggregator finalizer、VERIFYING 与一次性根结果 CAS 已完成，待后续统一验证与测试迁移）
- 将 TaskBoard 能力收敛为 TaskPlan/TaskNode/TaskDependency，不保留第二根任务。
- 实现 DAG 冻结校验、环检测、缺失引用拒绝、ready 计算、maxParallelism、原子领取和 generation fencing。
- 覆盖 `A→B→C` 串行、`A||B` 并行、`C dependsOn A+B` join，以及 fan-out/fan-in + Aggregator。
- 依赖失败按冻结策略阻断并进入暂停或失败；不生成通用反向业务动作，迟到事件不得释放后继节点。
- **完成标准**：四类 DAG 场景与非法图均有确定性结果；相同节点不被并发重复执行。

### #11506 迁移子智能体、ExecutorPlan、HITL 与恢复链

- **优先级**：P0
- **风险**：🔴 高
- **依赖**：#11504、#11505
- **状态**：✅ 生产实现完成（2026-09-12；双层计划、安全重规划、Owner Aggregator 最终语义完成、durable CANCELING、TASK_NODE/TASK_ROOT durable PAUSING、canonical durable take-over/forced-fresh hand-back、canonical HITL AgentState 保留及 exactly-once 事件交接、same-attempt 稳定 stateSlot 与独立 Dispatch authority 均已完成；动态验证归 #11509）
- 保留 `ExecutorPlan/ExecutorPlanStep` 及 `submit_executor_plan/report_executor_step`；TaskPlan 管理节点 DAG，ExecutorPlan 管理单节点有序步骤。
- PRIMARY Assistant 通过 `inspect_tasks` 读取任务与步骤，通过 `amend_task` 调整 TaskPlan 或指定节点步骤，通过 `cancel_task` 停止整个 Task；目标不唯一时必须追问。
- TaskPlan 调整创建新 revision 并关闭旧 revision 执行权；ExecutorPlan 调整取消旧局部计划并创建目标节点 fresh attempt；旧计划、审计事实与用户业务数据均保留。
- Coordinator/Executor/Evaluator/Aggregator 改用稳定 TaskNode + Execution，保持 AgentScope 原生 subagent 关闭；ExecutorPlan 归属于 taskId/nodeId/executionId，不与 TaskPlan 竞争根计划真理源。
- 冻结并实现续接/重启矩阵：同 owner/责任、授权画像、ExecutionProfile 和 stateSlot 且 AgentState/checkpoint 有效时，approval、structured clarification、PAUSE、优雅停机恢复复用原 executionId/sessionId/stateSlotId，并以新 Dispatch generation/fence 重新取得执行权；终态失败、owner/授权变化、状态缺失/损坏/不兼容或 fresh retry 策略只为受影响 TaskNode 或当前 TASK_ROOT 创建新 attempt。TASK_ROOT 以 Task current-root CAS 切换 attempt，仍禁止 structured HITL。
- 迁移 approval、clarification、pause、cancel、take-over、hand-back、resume、retry、budget 和 completion validation；cancel 先提交 `CANCELING` 并立即关闭旧 Dispatch 执行权、提升 generation/fence，再由恢复调度幂等推进 `CANCELED`，不等待 runtime AgentState ACK；用户显式 pause/take-over 先提交 `PAUSING` 并按 executionId 记录 AgentState ACK，take-over 只在稳定 `PAUSED` 后切换 Human owner；普通 pause 全部成功冻结 `SAME_ATTEMPT`，失败、deadline 超时或 owner 变化冻结 `FRESH_ATTEMPT`；hand-back 从冻结合同恢复 Owner Assistant 并只为受影响节点/root 创建 fresh attempt；canonical interrupt 与输入提交必须精确绑定 taskId/nodeId/executionId，禁止按最近等待项猜测。
- Task Owner Assistant 在冻结的同一 identity/definition/revision/routing boundary 下负责最终语义完成判断与综合；CompletionValidator 只做 DAG、receipt、证据、版本、fencing 与授权校验，不调用独立 judge LLM。EVALUATOR 仅作为显式计划证据。
- structured HITL 的流终止已进入 AgentState 保留语义，`resumeStateRequired` 允许通过完整身份与冻结画像校验的合法非空历史。DELEGATED state key 使用稳定 stateSlotId，不含 Dispatch fence；所有自动 load/save 由 `DispatchGuardedAgentStateStore` 在 TaskUnitOfWork 行锁内校验 authority，HITL 最终快照与异步清理分别使用 exact waiting 和 latest-dispatch 精确授权，不提供旧 key fallback。
- 新 attempt 记录 predecessorExecutionId/attempt 谱系并使用新 stateSlotId，不迁移旧 attempt AgentState；恢复正确性继续由 receipt、TaskNode checkpoint、事件顺序、lease 和 fencing 保证。
- 恢复只作用于中断节点，不重启整个 TaskPlan 或已完成兄弟节点；节点完成后重新计算 ready，join 仍等待全部必需依赖完成，旧 generation 事件不得释放后继。
- 恢复继续异步派发，HTTP/SSE 请求线程不等待长任务完成。
- **完成标准**：续接/重启矩阵每格有实现和证据；合法同责任主体中断可续接原 AgentState；降级新 attempt 不重复副作用；已完成兄弟不重放；节点计划、TaskPlan 和 AgentState 各自只有一个权威归属。

### #11507 统一事件、查询与前端任务投影

- **优先级**：P1
- **风险**：🔴 高
- **依赖**：#11503、#11505、#11506
- **状态**：✅ 生产实现完成（2026-09-11；canonical Task/Plan/Node/Execution/Dispatch 与 RootResult 安全投影已完成，按用户要求未执行构建/测试）
- 统一使用锚定真实 Execution 的 `ExecutionEvent`：同 Execution 按 sequence 排序，跨 Execution 的 Task 事件流按 eventOffset 断线续读；移除旧 task/board 并行事件。
- Task API 和 TaskPanel 只返回真实 Task；普通聊天 Execution 不进入任务列表。列表按必填 conversationId 在服务端过滤，WebUI 与 inspect_tasks 复用唯一查询边界；控制命令返回 `200 + TaskDetails` 当前投影，Task 由对话分析或 promotion 创建。
- 前端使用 TanStack Query 管权威状态，CUSTOM/Activity 事件只触发失效或安全增量，不复制到 Zustand。
- 串行、并行、join 和聚合节点在任务 UI 中使用同一 DAG 投影；任务行以列表图标和数量展开 TaskPlan 节点，具有 ExecutorPlan 的节点再展开最新 revision 的安全只读步骤。
- AAF-115 只投影失败/暂停事实和已有公开引用；标准化失败任务资源链接、实体动作入口及用户驱动 CRUD 延后到 AAF-116。
- **完成标准**：刷新、切线程、断线重连后任务拓扑与状态一致；无旧 API、旧类型或双查询路径。

### #11508 删除旧实现并完成静态与迁移审查

- **优先级**：P0
- **风险**：🔴 高
- **依赖**：#11503～#11507
- **状态**：✅ 生产实现完成（2026-09-12；旧双模型静态审查、死控制分支删除与不安全人工交接下线完成，动态门禁归 #11509）
- 全仓生产代码与 Flyway 未发现 `AssistantTask`、`DelegatedTask`、旧 TaskBoard 类型、适配器、旧表、旧索引、旧事件或兼容 DTO。
- 删除无生产构造方的 `AssistantCommand.PAUSE/CANCEL` 与 `controlTask(...)`；pause/cancel 只保留 `TaskCommandService → TaskMaterializationPort` durable 路径。
- 删除绕过 stable-pause、Dispatch 失权和 fencing 的 `SupportHandoffTool`；fresh seed 直接不注册或授权 `support.handoff`，并已承载最终 catalog、Role、Assistant 与 Persona 状态。因尚未部署，不保留额外前向清理 migration。
- 复核 canonical 六表的 tenant/org 隔离、复合外键、状态、current plan/root、RootResult、active Dispatch 与乐观锁约束，当前已覆盖关键正确性风险；Flyway migration 与 JPA Entity 是物理 schema 唯一真理，不按旧目标 DDL增加平行字段或约束。附加查询索引仅在运行指标证明存在扫描或延迟问题时另行迁移。
- 确认 TaskPlan 失败策略只保留 `FAIL_TASK/PAUSE_TASK`，不存在通用反向业务执行器或 Task 失败自动删除用户业务数据的路径；临时资源仍由创建方 Tool/runtime 生命周期负责。
- 按当前授权完成 diagnostics、定向搜索和 `git diff --check`；未执行 format、lint、build、typecheck 或测试，统一动态验证由 #11509 承接。
- **完成标准**：生产实现无遗留双模型或重复控制路径；破坏性清理与迁移事实已同步，动态质量门集中在 #11509。

### #11509 DAG、澄清恢复验收与故障注入质量门

- **优先级**：P0
- **风险**：🔴 高
- **依赖**：#11508
- **状态**：🟡 已调整本轮受新合同影响的既有测试（2026-09-12；未新增测试文件或场景，动态 check/acceptance 待授权）
- 仅盘点并调整已存在且与 canonical 合同冲突的测试，不新增测试文件或测试场景。
- 对 Gherkin AC 建立“已有测试证据/未覆盖”映射，区分自然澄清、新消息重新分析、structured clarification/promotion 和 TaskNode resume/fresh attempt；没有现有覆盖的项目如实标记未验证。
- 注入中断子节点、worker 崩溃、lease 过期、旧 generation 迟到、重复事件、并发 resume/cancel/promotion 和数据库重试。
- 分别注入 owner/授权画像变化、AgentState 缺失、损坏、不兼容和合法可恢复状态，验证只在符合矩阵时复用 executionId，否则只为受影响 TaskNode 或当前 TASK_ROOT 创建新 attempt；TASK_ROOT 还须断言 current-root CAS、predecessor 同 task/scope、新 session/state slot、旧 generation 迟到拒绝与根完成恰好一次。
- 在并行 DAG 中中断单个分支，验证已完成兄弟节点不重放、未受影响节点不被整板重启、已确认副作用不重复；恢复节点完成后 join 仅在全部依赖完成时释放一次。
- 验证旧 worker/旧 Execution/旧 fencing generation 的迟到事件不能更新节点、释放后继或覆盖聚合结果。
- 验证连续普通消息各自创建独立 Run/Execution，只有显式 canonical interrupt 输入恢复既有 Task；同一 Task Owner Assistant 完成复杂度、分解和最终语义判断，不出现独立 classifier/judge。
- 注入 Task 失败与暂停，断言项目、文档、草稿、生成图片等用户可见数据保留；仅 Tool/runtime 自有的不可见临时资源按 finally/TTL 生命周期释放。
- 验证普通对话延迟与数据库写放大下降，复杂任务吞吐受 maxParallelism 约束且 join 不早启。
- 完成 architect review、tester acceptance、qa 审计和人类验收。
- **完成标准**：现有测试与 canonical 合同无冲突，已有覆盖证据通过；未有现成测试的续接/重启矩阵和 Gherkin AC 明确标记未验证，不为补齐矩阵新增测试。后续质量门仍要求 blocker=0、major≤2，并在获得授权后执行约定的 check/acceptance。

## 评审状态

| 阶段 | 执行次数 | 最后执行 | 状态 | 必须 |
|------|---------|---------|------|------|
| product（需求细化） | 1 | 2026-09-11 | ✅ CLEAR | 🔴 是 |
| architect（技术设计产出） | 3 | 2026-09-11 | ✅ 人类批准按保守决策开发，后续代码审查验证 | 🔴 是 |
| qa-design-review（独立设计复审） | 2 | 2026-09-11 | ❌ NEEDS_CHANGES，待复审 | 🔴 是 |
| Human（高风险设计审核） | 1 | 2026-09-11 | ✅ 人类批准进入开发 | 🔴 是 |
| designer（UI 审查） | 0 | — | ⏳ PENDING | 涉及任务 UI 时 |
| developer（编码） | 0 | — | ⏳ PENDING | 🔴 是 |
| architect（代码审查） | 0 | — | ⏳ PENDING | 🔴 是 |
| tester（验收测试） | 0 | — | ⏳ PENDING | 🔴 是 |
| qa（过程审计） | 0 | — | ⏳ PENDING | 🔴 是 |

## 新增任务

> 开发过程中发现需要新增的任务，由协调者评估后按 `#115NN` 追加。
