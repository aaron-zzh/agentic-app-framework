---
level: Practice
layer: Model
purpose: AAF-115 统一 Task、TaskPlan、Execution 与 DAG 编排技术任务
status: draft
version: 1.3.0
date: 2026-09-11
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
- 普通 CHAT 只创建 Execution；复杂度、控制模式、工具权限、Task promotion 和副作用边界必须由服务端判定。
- AgentScope 原生 subagent 保持关闭；子智能体继续由 AAF TaskPlan 编排并通过统一 `AssistantCommandPort → AssistantApplicationService → AgentExecutionPort` 执行。
- 数据库当前未发布时直接调整目标 schema，不新增兼容迁移链；实际执行前由 #11502 复核部署状态。

## 技术任务

### #11501 现状盘点与需求评审

- **优先级**：P0
- **风险**：🔴 高
- **依赖**：无
- **状态**：✅ 已完成（2026-09-11）— Kiro
- **产出**：[inventory.md](inventory.md)
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
- **状态**：待开始
- 删除独立 `AssistantTask`/`DelegatedTask` 领域模型及重复 Repository/Port。
- 实现 Task、Execution、TaskDispatch 及其唯一事务写入口，将原合同、预算、owner、checkpoint、恢复和调度字段归位。
- 普通 Run 允许 `Execution.taskId=null`；持久 Task 的节点执行必须携带 taskId/nodeId 和稳定谱系；Task 对 originExecutionId 建立幂等唯一约束，并持有服务端复制的 origin run/correlation/input/public-context 谱系。Execution attempt 记录 predecessorExecutionId 而不复用分解用 parentExecutionId。
- 无 plan 的 TASK_ROOT 以 Task.currentRootExecutionId/currentRootAttemptNo 为唯一当前指针；状态无效、owner/画像变化或 worker 丢失时允许新 executionId/sessionId/state slot 的 fresh attempt，predecessor 约束同 Task/scope，旧 generation 结果必须拒绝。
- **完成标准**：不存在两个根 status 写入者；直接执行和持久执行均可独立完成、失败、取消并审计。

### #11504 实现 TaskAnalysis、自然澄清与直接执行 promotion

- **优先级**：P0
- **风险**：🔴 高
- **依赖**：#11503
- **状态**：待开始
- CHAT 简单场景确定性走 direct Execution，不创建 Task；缺少普通信息时允许以自然消息结束当前 Execution，下一条用户消息创建新 direct Execution 并通过 Conversation 历史重新分析。
- 自然澄清不创建 Task、不发 canonical interrupt；Task 创建可延迟到信息足以判定。达到 accepted/planned/authorized/long-running/multi-node/structured-input 任一边界后必须已有 Task。
- TASK、TEAM、长任务、副作用、结构化 HITL 与显式恢复场景创建 Task；structured clarification 必须先建 Task 或完成 promotion，再绑定准确 taskId/nodeId/executionId 发中断。
- 设计服务端 TaskAnalysis；仅开放复杂目标允许模型分析，禁止客户端控制 complexity、promotion 或提权。
- 支持首次外部副作用前按 originExecutionId 幂等 promotion：锁定 origin DIRECT 后必须证明不存在任何已提交/未知副作用 receipt 或 intent；存在即 fail-closed 拒绝。原 direct Execution→PROMOTED，新 Task 记录 originExecutionId，新 TaskNode Execution 使用独立 ID，并由服务端复制 conversation/run/correlation/原始输入谱系；客户端不得覆盖。
- promotion 只引用公开且已冻结的上下文，不持久化隐藏 chain-of-thought、私有 scratchpad 或 AgentState；canonical event producer key 由 tenant+originExecutionId+event+aggregate 稳定派生。
- **完成标准**：自然澄清和简单问答不进入任务列表；补充信息后会重新分析；结构化澄清前必有唯一 Task；并发 promotion 不重复建任务且不扩大授权。

### #11505 实现 TaskPlan DAG 与串并行调度

- **优先级**：P0
- **风险**：🔴 高
- **依赖**：#11503
- **状态**：待开始
- 将 TaskBoard 能力收敛为 TaskPlan/TaskNode/TaskDependency，不保留第二根任务。
- 实现 DAG 冻结校验、环检测、缺失引用拒绝、ready 计算、maxParallelism、原子领取和 generation fencing。
- 覆盖 `A→B→C` 串行、`A||B` 并行、`C dependsOn A+B` join，以及 fan-out/fan-in + Aggregator。
- 依赖失败按冻结策略阻断、补偿、暂停或失败；迟到事件不得释放后继节点。
- **完成标准**：四类 DAG 场景与非法图均有确定性结果；相同节点不被并发重复执行。

### #11506 迁移子智能体、ExecutorPlan、HITL 与恢复链

- **优先级**：P0
- **风险**：🔴 高
- **依赖**：#11504、#11505
- **状态**：待开始
- Coordinator/Executor/Evaluator/Aggregator 改用稳定 TaskNode + Execution，保持 AgentScope 原生 subagent 关闭；ExecutorPlan 归属于 taskId/nodeId/executionId，不与 TaskPlan 竞争根计划真理源。
- 冻结并实现续接/重启矩阵：同 owner/责任、授权画像、ExecutionProfile 和 generation 且 AgentState/checkpoint 有效时，approval、structured clarification、PAUSE、优雅停机恢复复用原 executionId/sessionId；终态失败、owner/授权变化、状态缺失/损坏/不兼容或 fresh retry 策略只为受影响 TaskNode 或当前 TASK_ROOT 创建新 attempt。TASK_ROOT 以 Task current-root CAS 切换 attempt，仍禁止 structured HITL。
- 迁移 approval、clarification、pause、cancel、take-over、resume、retry、budget 和 completion validation；canonical interrupt 与输入提交必须精确绑定 taskId/nodeId/executionId，禁止按最近等待项猜测。
- 修复现有两项接线矛盾：structured HITL 的 `takeUntil`/流终止必须进入状态槎保留语义；恢复时只允许通过完整身份与冻结画像校验的持久历史，不能继续由 `requireNoHiddenPersistentHistory` 无条件拒绝合法非空历史。
- 新 attempt 记录 predecessorExecutionId/attempt 谱系，不迁移旧 fence AgentState；恢复正确性继续由 receipt、TaskNode checkpoint、事件顺序、lease 和 fencing 保证。
- 恢复只作用于中断节点，不重启整个 TaskPlan 或已完成兄弟节点；节点完成后重新计算 ready，join 仍等待全部必需依赖完成，旧 generation 事件不得释放后继。
- 恢复继续异步派发，HTTP/SSE 请求线程不等待长任务完成。
- **完成标准**：续接/重启矩阵每格有实现和证据；合法同责任主体中断可续接原 AgentState；降级新 attempt 不重复副作用；已完成兄弟不重放；节点计划、TaskPlan 和 AgentState 各自只有一个权威归属。

### #11507 统一事件、查询与前端任务投影

- **优先级**：P1
- **风险**：🔴 高
- **依赖**：#11503、#11505、#11506
- **状态**：待开始
- 统一 Task/Plan/Node/Execution/Dispatch 事件信封和 cursor；移除旧 task/board 并行事件。
- Task API 和 TaskBoardPanel 只返回真实 Task；普通聊天 Execution 不进入任务列表。
- 前端使用 TanStack Query 管权威状态，CUSTOM/Activity 事件只触发失效或安全增量，不复制到 Zustand。
- 串行、并行、join 和聚合节点在任务 UI 中使用同一 DAG 投影。
- **完成标准**：刷新、切线程、断线重连后任务拓扑与状态一致；无旧 API、旧类型或双查询路径。

### #11508 删除旧实现并完成静态与迁移审查

- **优先级**：P0
- **风险**：🔴 高
- **依赖**：#11503～#11507
- **状态**：待开始
- 删除 `AssistantTask`、`DelegatedTask`、旧 TaskBoard 持久实体、无引用适配器、旧表、旧索引、旧事件和兼容 DTO。
- 复核 schema、seed、Repository、事务锁、租约、outbox、权限、tenant 和 owner 对称性。
- 执行格式、lint、编译、typecheck、单测与静态安全审查，修复全部失败。
- **完成标准**：全仓搜索无遗留双模型；破坏性变更文档完整；developer 门禁全绿。

### #11509 DAG、澄清恢复验收与故障注入质量门

- **优先级**：P0
- **风险**：🔴 高
- **依赖**：#11508
- **状态**：待开始
- 对全部 Gherkin AC 建立验收覆盖矩阵，区分自然澄清、新消息重新分析、structured clarification/promotion 和 TaskNode resume/fresh attempt。
- 注入中断子节点、worker 崩溃、lease 过期、旧 generation 迟到、重复事件、并发 resume/cancel/promotion 和数据库重试。
- 分别注入 owner/授权画像变化、AgentState 缺失、损坏、不兼容和合法可恢复状态，验证只在符合矩阵时复用 executionId，否则只为受影响 TaskNode 或当前 TASK_ROOT 创建新 attempt；TASK_ROOT 还须断言 current-root CAS、predecessor 同 task/scope、新 session/state slot、旧 generation 迟到拒绝与根完成恰好一次。
- 在并行 DAG 中中断单个分支，验证已完成兄弟节点不重放、未受影响节点不被整板重启、已确认副作用不重复；恢复节点完成后 join 仅在全部依赖完成时释放一次。
- 验证旧 worker/旧 Execution/旧 fencing generation 的迟到事件不能更新节点、释放后继或覆盖聚合结果。
- 验证普通对话延迟与数据库写放大下降，复杂任务吞吐受 maxParallelism 约束且 join 不早启。
- 完成 architect review、tester acceptance、qa 审计和人类验收。
- **完成标准**：全部续接/重启故障矩阵和 Gherkin AC 有证据；blocker=0、major≤2；`pnpm check` 与 `pnpm acceptance` 全绿。

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
