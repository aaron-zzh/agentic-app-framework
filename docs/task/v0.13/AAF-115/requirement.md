---
level: Practice
layer: Product
purpose: AAF-115 统一 Task、TaskPlan 与 Execution 任务模型及 DAG 编排需求规格
status: draft
version: 1.3.6
date: 2026-09-12
author: AaronZZH & Kiro
tags:
  - AAF-115
  - Assistant
  - Task
  - DAG
related:
  - tasks.md
  - ../../../design/adr/ADR-005-agentscope-boundary-and-orchestration.md
  - ../../../design/audit/2026-09-01-harness-landing-plan.md
  - ../../v0.12/AAF-110/tasks.md
  - ../../../design/framework/intelligent/task-durability.md
---

# AAF-115 统一任务模型与 DAG 编排

## 背景

当前登录对话的每次 AG-UI Run 都会先创建 `DelegatedTask + TaskBoard.single`，即使用户只是问候或简单问答；随后 `AssistantApplicationService` 又为实际 Agent 执行维护 `AssistantTask`。两套模型重复表达 taskId、状态、责任主体、恢复点和转换历史，导致普通消息被过度任务化、状态存在双真理源、任务列表噪声增多，也使复杂度判断发生在持久任务创建之后，无法实现“只有复杂任务才创建 Task”。

AAF 已由 `DelegatedTaskCoordinator + TaskBoard` 承担外层子智能体编排，并关闭 AgentScope 原生 subagent 旁路。本故事不新增第三套编排器，而是将现有能力收敛为唯一根 `Task`、可选 `TaskPlan`、计划节点 `TaskNode`、实际运行 `Execution` 和独立调度事实 `TaskDispatch`。

## Epic

**目标**：建立唯一、可恢复、可审计的任务模型，使普通对话只产生 Execution，复杂或持久目标才产生 Task；复杂 Task 可通过 TaskPlan DAG 安全表达串行、并行、fan-out、fan-in、join 与有界子智能体编排。

**范围**：移除独立 `AssistantTask` 和 `DelegatedTask` 业务模型，收敛根任务状态机；将 `TaskBoard` 降级为 Task 内的可选执行计划并演进为 `TaskPlan`；统一 TaskNode、Execution、调度租约、预算、恢复、事件与前端任务投影；不保留双写、fallback 或兼容层。

## 用户故事

作为使用助理完成问答和复杂工作的用户，我希望简单消息立即得到回复且不生成任务噪声，复杂目标则自动形成可查看、可暂停、可恢复的 Task，并按依赖关系串行或并行调度子智能体，以便系统既保持轻量对话体验，又能可靠完成多步骤工作。

## 业务规则

### 唯一任务真理源

- `Task` 是复杂或持久目标的唯一根业务实体，统一持有 goal、status、owner、controlMode、ExecutionContract、CompletionCriteria、预算、截止时间、优先级和 checkpoint。
- 删除独立 `AssistantTask` 和 `DelegatedTask` 领域模型；原有有效字段按职责迁移到 Task、Execution 或 TaskDispatch，不保留同义状态双写。
- `TaskPlan` 是 Task 的可选计划，不是第二个根任务；没有编排需求的单 Agent Task 可以没有 TaskPlan。
- `TaskNode` 是计划内的工作节点，不是独立根 Task；节点类型首批支持 COORDINATOR、EXECUTOR、EVALUATOR、AGGREGATOR。
- `Execution` 表示一次 Agent 运行或重试。普通聊天 Execution 的 taskId 为空；TaskNode 每次尝试拥有独立 Execution。
- `TaskDispatch` 只承载 nextRunAt、lease、fencing 和 worker 等调度事实，不拥有业务任务状态。
- AgentState 只保存某次 Execution 的可删除工作态，不保存 TaskPlan 或 Task 状态真理。

### 简单对话与复杂任务分流

- 每条普通用户消息都创建新的 Run 和 Execution，独立分析、独立终结；前一轮简单回复、后一轮复杂目标和自然澄清回复互不复用执行现场。
- 只有显式绑定 canonical interrupt 的审批、结构化澄清或恢复输入才能续接既有 Task；普通对话回复不得按“最近任务”隐式恢复。
- CHAT 中的问候、简单问答和单轮只读回复只创建 ConversationMessage/Run/Execution，不创建 Task、TaskPlan 或 TaskNode。
- 明确 TASK、TEAM、后台长任务、持久恢复、外部副作用、审批、结构化澄清或多节点编排是服务端确定性硬门，必须创建 Task；权限、预算、tenant、owner 与工具边界仍由服务端裁决。
- 其余开放目标由当前 Run 的 Assistant 在自身执行中判断直答、自然澄清或提升为 Task，并在需要时完成分解；该 Assistant 在提升后成为 Task Owner Assistant。不得为复杂度分类或分解再调用独立 classifier LLM。
- 服务端 `TaskAnalysisPolicy` 只实施确定性硬门、授权交集和结构校验，不替代 Assistant 做语义推理，也不接受客户端用 mode/complexity 绕过安全边界。

### 自然澄清、结构化澄清与延迟建 Task

- 直接 Execution 允许因缺少信息而进行自然对话式澄清。该轮只输出普通 Assistant 消息后结束，不创建 Task，不发 canonical Task interrupt，也不把本轮伪装成可恢复执行。
- 用户在下一轮补充信息时创建新的 Run 和直接 Execution，并由 Conversation 历史提供公开上下文；当前 Assistant 重新独立判断，结果仍简单则继续直答，达到复杂任务边界才创建 Task。
- 仅仅“问过一个问题”不构成 Task 创建理由。信息不足时可以延迟创建 Task，直到目标足以被服务端判定、冻结和授权。
- 需要精确绑定输入字段、审批、恢复点、长时间等待或节点执行现场的澄清属于结构化/可恢复澄清。系统必须先创建 Task，或先将直接 Execution 幂等提升为 Task，再发出 canonical interrupt。
- canonical interrupt 必须绑定准确的 taskId、nodeId、executionId、责任主体、冻结画像和 fencing generation；后续输入不得按“最近一个等待中的任务”猜测归属。
- 一旦请求已被接受为持久目标、已形成计划、已通过授权、已进入长时运行/多节点执行，或正在等待结构化输入，Task 必须存在，不能继续以无 Task 的直接 Execution 承载。

### 直接执行 promotion 边界与谱系

- promotion 只能由服务端在第一次外部副作用前校验，客户端不能要求或跳过 promotion，也不能借此扩大 controlMode、工具、Role、Skill、tenant、owner、预算或授权。
- promotion 以 originExecutionId 作为幂等键；并发分析、事件重放和请求重试对同一原始 Execution 最多创建一个 Task。
- 原直接 Execution 进入 `PROMOTED` 终态；新 Task 记录 `originExecutionId`，新 TaskNode Execution 使用独立 executionId，并继承 conversationId、runId、correlationId、原始输入和允许公开的冻结上下文谱系。
- promotion 不复制或持久化隐藏 chain-of-thought、模型私有 scratchpad 或未纳入冻结画像的隐式历史；恢复正确性继续锚定 receipt、Task/TaskPlan/TaskNode 和事件事实。

### TaskPlan DAG

- TaskPlan 必须是有向无环图；服务端拒绝环、自依赖、缺失节点、重复节点、非法聚合引用和超预算并行度。
- 无依赖且 ready 的节点可以并行执行，但同时运行数不得超过 `maxParallelism`。
- 节点只有在全部前置依赖达到允许的成功终态后才能变为 ready；任一必需依赖失败时，后继节点不得执行，并按冻结策略暂停或失败。
- 串行通过依赖链表达，例如 `A → B → C`。
- 并行通过多个无互相依赖节点表达，例如 `A || B`。
- join 通过一个节点依赖多个前置节点表达，例如 `C dependsOn [A, B]`，只有 A、B 均完成后 C 才能执行。
- fan-out/fan-in 允许一个节点完成后释放多个并行节点，再由聚合节点等待全部分支。
- 同一节点只能被一个 worker 在一个 fencing generation 下领取；重复派发、恢复和事件重放不得重复产生副作用。
- 聚合必须使用冻结的 `AggregationContract`，且只能消费计划声明的节点结果；模型不得在运行中静默增加未授权节点或依赖。

### 双层计划与对话式调整

- `TaskPlan` 管理 TaskNode 之间的 DAG、子智能体分工、依赖和聚合；`ExecutorPlan` 只管理某个 TaskNode 的有序执行步骤，两者不得互相替代。
- PRIMARY Assistant 通过 `inspect_tasks` 读取当前用户的 Task、节点和局部步骤；无法唯一定位目标时必须追问，不按“最近任务”猜测。
- TaskPanel 采用两级只读展示：任务行用列表图标和数量展开 TaskPlan 节点；存在 ExecutorPlan 的节点可继续展开最新 revision 的步骤标题、状态、结果或失败。投影不得公开内部 instruction、工具权限、policy snapshot、审批身份或 lock version，也不提供直接编辑。
- 新增、修改或删除子智能体任务、依赖或整体目标时，通过 `amend_task(TASK_PLAN)` 创建新 TaskPlan revision；旧 revision 保留并标记为被替代。
- 调整指定节点的 ExecutorPlan 步骤时，通过 `amend_task(EXECUTOR_PLAN)` 关闭该节点旧执行权并创建 fresh Execution attempt；旧 ExecutorPlan 保留并取消，新 attempt 通过 `submit_executor_plan` 提交下一 revision，并继续用 `report_executor_step` 上报步骤。
- 计划切换必须原子关闭被替代 Execution 和 active Dispatch、提升 generation/fencing，并拒绝旧 revision、attempt 或 fence 的迟到结果；不得删除历史计划或用户业务数据。
- 用户通过 `cancel_task` 停止整个 Task；取消关闭运行控制面，但不创建计划 revision，也不删除历史计划、审计事实或用户业务数据。

### 状态、恢复与事件

- Task、TaskNode 和 Execution 分别拥有且只拥有本层状态；根 Task 状态由唯一应用服务依据节点、Task Owner Assistant 的语义结论和确定性完成门推进，不由多个 Repository 独立写入。
- Task Owner Assistant 负责最终语义完成判断和结果综合；它可以在同一 Assistant identity/definition/revision/routing boundary 下多次执行，但不得换成隐藏的独立 judge LLM。
- 服务端 CompletionValidator 只校验 DAG、receipt、证据、版本、generation/fencing、授权和结果合同；它可以拒绝不合法提交并保持 Task 未完成，但不自行进行目标是否完成的语义推理。
- EVALUATOR 仅可作为冻结计划中显式声明的证据节点，不是默认最终裁判；fan-in 的 AGGREGATOR 默认由 Task Owner Assistant 承担最终综合。
- Task 暂停、取消、人工接管和恢复必须传播到可运行节点与活跃 Execution，并保留原因、操作者、时间和恢复点。
- approval、clarification 和 resume 继续使用统一异步事件链；HTTP/SSE 请求线程不得等待长任务完成。
- Task、TaskPlan revision、TaskNode、Execution 和 Dispatch 的变化通过同一事务/outbox 产生 `ExecutionEvent`；AG-UI、任务页和通知只做安全投影。
- 每个事件必须锚定引起变化的真实 Execution；DIRECT 允许 taskId 为空，Task/Plan/Node 事实通过 taskId 与 nodeIdentity 关联，不为没有执行过程的事实制造占位事件。
- 同一 Execution 以 sequence 严格排序，跨 Execution 的 Task 事件流以全局 eventOffset 断线续读；Task、Plan、Node、Execution 和 Dispatch 关系表才是权威状态，不要求从事件重建聚合。
- Task REST 保留 list/get/pause/resume/take-over/hand-back/cancel；list 必须提供 conversationId，并在装配安全投影前按当前 tenant、user 与 conversation 过滤；控制命令完成短事务后返回 `200 + TaskDetails` 当前投影，中间态不代表后台工作已经终结。
- Task 由对话分析或 promotion 创建，不提供平行 create API；approval、clarification 继续通过现有 AG-UI/HITL，工具层确认继续使用 ToolApproval，不合并为统一 TaskInterrupt REST。
- 前端 TaskPanel 只展示真实 Task，并直接查询当前 conversation；普通聊天 Run 不进入任务列表。TanStack Query 管服务端任务状态，Zustand 只管理瞬时 UI。当前没有独立任务中心，不要求 status/cursor 查询；出现对应产品入口时再单独设计。

### TaskNode 与子智能体中断续跑

- TaskNode 身份在 TaskPlan revision 内稳定；可恢复中断是同一节点、同一次 Execution attempt 的分段继续，不等同于失败重试。
- 当责任主体/owner、授权边界、冻结 ExecutionProfile 与工具面均未变化，且 AgentState/checkpoint 完整、兼容并属于同一 stateSlot 时，approval、structured clarification、PAUSE 和优雅停机恢复必须复用原 executionId/sessionId/stateSlotId；恢复创建的新 Dispatch 可以使用更高 generation 和新 fence。
- 同一 Execution 续接前必须同时校验 taskId、nodeId、executionId、owner、profile revision、状态槽身份和当前 Dispatch id/generation/fence/lease owner；状态 key 只使用稳定 stateSlot 身份，读写执行权独立校验，任一不匹配均 fail-closed。
- 终态失败、责任主体/owner 或授权画像变化、AgentState/checkpoint 缺失/损坏/不兼容，或恢复策略明确要求 fresh retry 时，只为受影响 TaskNode 创建新的 Execution attempt，并记录 predecessorExecutionId/attempt 谱系；`parentExecutionId` 继续表达任务分解关系，不得被重试关系复用。
- 新 attempt 使用新 executionId/sessionId/stateSlotId，不迁移旧 attempt 的 AgentState；恢复正确性由 receipt、TaskNode checkpoint 和事件流保证，允许在状态槽不可用时从安全边界重建，但不得重复已确认副作用。
- 恢复或重启不得重跑整个 TaskPlan。已完成兄弟节点保持完成，未受影响的活跃节点按各自 generation 处理，只有中断节点继续或创建新 attempt。
- 中断节点完成后必须重新计算 DAG readiness；join 仍须等待全部必需依赖成功完成，不能因某一节点刚恢复就提前释放。
- 旧 worker、旧 lease、旧 fencing generation 和被替代 Execution 的迟到事件不得更新节点、释放后继或覆盖聚合结果。
- AgentState 只承载 Execution 工作态，不是 Task、TaskPlan、TaskNode 状态或恢复正确性的真理源。
- AAF-115 保留 AAF-110 的同责任主体恢复合同：structured clarification/approval 与显式 PAUSE 均保存 call-scoped AgentState，`resumeStateRequired` 允许合法非空历史。所有 DELEGATED 状态读写由统一受控 Store 在 Task/Execution/Dispatch 行锁内授权；HITL 最终快照只允许 exact waiting 且尚无新 active Dispatch，旧 worker 的迟到保存或删除必须 fail-closed。

### 失败处置与资源生命周期

- AAF-115 的失败策略仅包含 `FAIL_TASK` 与 `PAUSE_TASK`；失败、取消或中断不会自动反向调用业务 Tool，也不会自动删除或归档业务数据。
- 已创建的项目、文档、草稿、生成图片和其他用户可见数据继续保留。Task 只报告失败或中断；标准化资源链接、人工处置入口和对话式实体操作由 AAF-116 实现。
- 仅不可见临时运行资源可由其所有者自动释放：未提交事务回滚，内存缓冲、流订阅、锁和租约通过 finally/TTL 释放，临时分片或 provider 临时作业由创建它们的 Tool/provider 生命周期清理。
- Harness 不提供通用任务级业务清理器，也不得把删除用户数据伪装成运行时清理。

## 概念登记表

| 产品概念 | 目标实体 | 主表 | 说明 |
|---------|---------|------|------|
| 根任务 | `Task` | `ai_task` | 复杂或持久目标的唯一业务真理源 |
| 执行计划 | `TaskPlan` | `ai_task_plan` | Task 的可选版本化 DAG 计划 |
| 计划节点 | `TaskNode` | `ai_task_node` | Coordinator/Executor/Evaluator/Aggregator 节点 |
| 节点依赖 | `TaskDependency` | `ai_task_dependency` | TaskNode 间有向依赖边 |
| 局部执行计划 | `ExecutorPlan` | `ai_executor_plan` | 单个 TaskNode 内的版本化有序步骤计划 |
| 局部计划步骤 | `ExecutorPlanStep` | `ai_executor_plan_step` | 由 `report_executor_step` 推进的步骤状态 |
| 实际执行 | `Execution` | `ai_task_execution` | 普通 Run 或 TaskNode 的一次执行/重试 |
| 调度事实 | `TaskDispatch` | `ai_task_dispatch` | worker、租约、fencing 与下次执行时间 |

> 物理字段、索引、事务边界与旧表直接替换方式由 #11502 技术设计冻结；概念名称和一概念一主表约束不得在实现中绕开。

## 验收标准

```gherkin
Scenario: 简单对话不创建持久任务
Given 用户在 CHAT 模式发送问候或简单只读问题
When Assistant 返回本轮答案
Then 系统创建可审计 Execution 和消息记录
And 不创建 Task、TaskPlan、TaskNode 或 TaskDispatch
And 任务列表不出现本轮聊天
```

```gherkin
Scenario: 自然对话澄清不创建 Task
Given 直接 Execution 发现回答简单请求仍缺少必要信息
When Assistant 以普通消息询问用户
Then 当前 Execution 在消息发送后结束
And 不创建 Task、TaskPlan、TaskNode 或 canonical interrupt
And 不把 AgentState 当作下一轮恢复入口
```

```gherkin
Scenario: 用户补充信息后重新分析并延迟创建 Task
Given 上一轮只进行了自然对话澄清且没有 Task
When 用户在新消息中补充所需信息
Then 系统创建新的直接 Execution 并使用 Conversation 公开历史
And 当前 Assistant 在新 Execution 中重新独立判断
And 仅当新分析达到复杂或持久任务边界时创建一个 Task
And 若请求仍可简单直答则继续不创建 Task
```

```gherkin
Scenario: 结构化澄清先建立 Task 再中断
Given 直接 Execution 发现后续输入必须绑定持久节点和恢复点
When 系统准备发出 structured clarification interrupt
Then 服务端在第一次副作用前幂等创建或 promotion 为 Task
And interrupt 精确绑定 taskId、nodeId、executionId、owner 和 fencing generation
And 原直接 Execution 进入 PROMOTED 终态
And Task 记录 originExecutionId
And 不持久化隐藏 chain-of-thought
```

```gherkin
Scenario: 复杂目标提升为唯一 Task
Given 用户提出需要多步骤、恢复或受控副作用的复杂目标
When 当前 Assistant 的语义判断或服务端确定性硬门要求持久任务
Then 系统只创建一个根 Task
And Task 持有冻结的目标、控制模式、合同、预算和完成条件
And 不存在 AssistantTask、DelegatedTask 或同义状态双写
And 全部节点、执行和事件可通过 taskId 追溯
```

```gherkin
Scenario: 三个子智能体严格串行执行
Given TaskPlan 包含 A、B、C 三个节点
And B dependsOn A
And C dependsOn B
When 调度器执行计划
Then 任意时刻只有当前依赖已满足的节点可领取
And B 不早于 A 成功完成
And C 不早于 B 成功完成
And 重试或恢复不改变依赖顺序
```

```gherkin
Scenario: 两个并行节点完成后执行第三个节点
Given TaskPlan 包含 A、B、C 三个节点
And A 与 B 无互相依赖
And C dependsOn A and B
And maxParallelism 至少为 2
When 调度器执行计划
Then A 与 B 可以并行运行
And A 或 B 仅完成一个时 C 不可领取
And A 与 B 均成功完成后 C 恰好变为 ready 一次
```

```gherkin
Scenario: fan-out 与 fan-in 使用冻结聚合合同
Given A 完成后释放 B、C、D 三个并行节点
And Aggregator dependsOn B、C、D
When 所有分支完成
Then Aggregator 只读取声明的三个分支结果
And 按冻结 AggregationContract 产生唯一根结果
And 未声明节点、重复结果和迟到旧 generation 事件被拒绝
```

```gherkin
Scenario: 非法 DAG 在执行前失败
Given TaskPlanDraft 包含环、自依赖、缺失引用或超过硬上限的并行度
When 服务端校验候选计划
Then 候选计划被确定性拒绝
And 不创建 TaskPlan revision 或可执行 Dispatch
And 不调用 Agent、模型或业务 Tool
And 当前调用方收到明确校验错误
```

```gherkin
Scenario: 节点失败阻止依赖节点误执行
Given C 依赖 A 与 B 且 B 达到不可重试失败
When 调度器重新计算 ready 节点
Then C 不执行
And Task 按冻结策略进入 FAILED 或 PAUSED
And 不伪造聚合成功或根任务完成
```

```gherkin
Scenario: 暂停恢复与重复派发保持单一执行权
Given Task 有活跃节点并持有有效 lease 和 fencing token
When 用户暂停后恢复且旧 worker 迟到提交结果
Then 暂停期间不领取新节点
And 恢复只产生一个当前 generation
And 旧 generation 结果被拒绝
And 已完成节点和业务副作用不重复执行
```

```gherkin
Scenario: 对话中发现复杂度后安全提升
Given CHAT 起初按直接执行处理且尚未产生外部副作用
When Assistant 判定目标需要多节点或持久恢复
Then 系统以 originExecutionId 幂等创建一个 Task
And Task 保留原 conversationId、runId 和 correlationId 谱系
And 后续工作由新的 TaskNode Execution 承担
And 原直接 Execution 以 PROMOTED 终结
And 不丢消息、不重复回答、不扩大授权
And 不复制隐藏 chain-of-thought
```

```gherkin
Scenario: 中断 TaskNode 在状态有效时续接同一 Execution
Given TaskNode 因 approval、structured clarification、PAUSE 或优雅停机中断
And owner、授权画像、冻结 ExecutionProfile 与 generation 均未变化
And 原 AgentState/checkpoint 完整且兼容
When 系统恢复该节点
Then 系统复用原 executionId 和 sessionId
And 恢复只作用于准确的 taskId 和 nodeId
And receipt 证明已完成的副作用不重复执行
```

```gherkin
Scenario: 状态无效或责任主体变化时只重启受影响节点
Given 一个 TaskNode 处于可恢复中断
And AgentState/checkpoint 缺失、损坏或不兼容，或 owner/授权画像已经变化
When 系统执行恢复前校验
Then 原 Execution 不再继续
And 系统只为该 TaskNode 创建新的 Execution attempt
And 新 attempt 记录 predecessorExecutionId 谱系且不迁移旧 AgentState
And 其他节点不因本次恢复而重启
```

```gherkin
Scenario: 恢复节点不重放已完成兄弟节点
Given TaskPlan 的 A 与 B 已完成且 C 被中断
When C 继续原 Execution 或以新 attempt 重启
Then A 与 B 保持完成且不再领取
And 已确认的结果与副作用不重复生成
And 调度器只处理 C 及其尚未满足的后继
```

```gherkin
Scenario: 恢复节点完成后按全部依赖释放 join
Given Join 依赖 A、B、C
And C 从中断状态恢复并完成
When 调度器重新计算 DAG readiness
Then 只有 A、B、C 均成功完成时 Join 才恰好变为 ready 一次
And 任一依赖未完成或失败时 Join 不可领取
```

```gherkin
Scenario: 恢复后拒绝旧 generation 迟到事件
Given TaskNode 已由当前 lease 和 fencing generation 恢复
When 旧 worker、旧 Execution 或旧 generation 迟到提交事件
Then 事件被拒绝且留下可审计记录
And 不更新 TaskNode 结果
And 不释放后继节点或覆盖聚合结果
```

```gherkin
Scenario: Task 页面和 AG-UI 投影同一事实
Given TaskPlan 节点状态和 Execution 进度发生变化
When AG-UI 与任务页收到事件或断线重连
Then 两端按 canonical taskId、plan revision、nodeId 和 executionId 重新查询权威状态
And 不从 Zustand、消息正文或 AgentState 恢复 Task 真理
And 普通聊天 Execution 不被展示为 Task
```

```gherkin
Scenario: Task Owner Assistant 完成最终语义判定
Given Task 的必需节点、receipt 和确定性证据已经齐备
When 系统准备提交根结果
Then Task Owner Assistant 在冻结的同一身份与版本边界内判断目标是否完成并综合结果
And CompletionValidator 只验证结构、证据、权限、版本和 fencing
And 不调用独立 classifier 或 judge LLM
And 只有校验通过的结果可由服务端以 CAS 写入并完成 Task
```

```gherkin
Scenario: Task 失败保留用户可见业务数据
Given Task 已创建项目、文档、草稿、生成图片或其他用户可见资源
When Task 失败、取消或因安全原因暂停
Then 系统不自动删除、归档或反向修改这些业务资源
And Task 保留失败事实和已有业务 receipt
And 仅创建方 Tool 或 runtime 释放其拥有的不可见临时资源
And 用户驱动的资源链接与处置能力留给 AAF-116
```

```gherkin
Scenario: 对话调整 TaskPlan 安全切换 revision
Given 用户在普通对话中明确指定一个运行中的 Task 并调整子智能体分工
When PRIMARY Assistant 调用 amend_task 且 scope 为 TASK_PLAN
Then 系统关闭旧 revision 的 Execution 与 active Dispatch
And 旧 TaskPlan 保留并标记为 SUPERSEDED
And 新 Coordinator revision 获得唯一当前执行权
And 旧 revision 或旧 fence 的迟到结果被拒绝
```

```gherkin
Scenario: 对话调整指定节点的 ExecutorPlan 步骤
Given 用户明确指定 Task 与 TaskNode 并调整其内部步骤
When PRIMARY Assistant 调用 amend_task 且 scope 为 EXECUTOR_PLAN
Then 系统只关闭目标节点的旧 Execution 与 active ExecutorPlan
And 为目标节点创建 fresh Execution attempt
And 新 attempt 可通过 submit_executor_plan 提交下一 ExecutorPlan revision
And 其他节点、历史计划和用户业务数据不被删除
```

```gherkin
Scenario: 对话停止整个 Task
Given 用户明确指定需要停止的 Task
When PRIMARY Assistant 调用 cancel_task
Then Task、非终态节点、Execution 和 active Dispatch 进入取消终态
And 不创建新的 TaskPlan 或 ExecutorPlan revision
And 历史计划、审计事实和用户业务数据继续保留
```

## 非目标

- 不把每条聊天消息包装成 `TaskPlan.single`。
- 不用消息长度、前端布尔参数或客户端提供的复杂度直接决定 Task 创建。
- 不启用 AgentScope 原生 subagent、workspace task 或第二套 DAG。
- 不把 TaskPlan、Execution、AgentState、审批流或 AIGC 业务 Task 合并成一张无边界的大表。
- 不在本故事引入新工作流产品能力；预定义 Workflow 仅作为 TaskPlan 节点可调用的既有执行机制。
- 不保留 AssistantTask/DelegatedTask 双写、旧 API fallback、影子状态或长期兼容迁移层。
- 不实现通用业务回滚、失败后自动删除用户数据或跨领域资源清理编排。
- 不实现项目、文档等业务实体的通用对话式 CRUD、失败任务资源链接和人工归档/删除入口；这些能力属于 AAF-116。Task/TaskPlan/ExecutorPlan 的读取、调整与停止属于本故事。

## 依赖与顺序

- 依赖 AAF-107 的 Executor Plan、AAF-110 的中断续跑和 AAF-114 的任务/计划安全投影能力；AAF-115 保留 AAF-110 的“同责任主体复用原 executionId、责任变化创建新 execution”合同，并在 #11506 修复 structured HITL 与持久历史守卫的未闭环接线。
- AAF-115 可与 AAF-112 的项目先行闭环并行设计，但必须在 AAF-113 开放对话式项目写 Tool 前完成核心模型迁移和验收。
- 本故事属于高风险架构与数据模型重构，#11502 技术设计和人类审核通过前不得编码。

## 相关文档

- [技术任务](tasks.md)
- [ADR-005 AgentScope 边界与编排](../../../design/adr/ADR-005-agentscope-boundary-and-orchestration.md)
- [Harness 落地计划](../../../design/audit/2026-09-01-harness-landing-plan.md)

## 变更记录

| 日期 | 变更内容 | 原因 | 影响评估 |
|------|---------|------|---------|
| 2026-09-11 | 创建 AAF-115，冻结唯一 Task、可选 TaskPlan、TaskNode DAG、Execution 与 TaskDispatch 概念边界 | 消除每条消息建任务及 AssistantTask/DelegatedTask 双真理源 | 高风险；影响 Assistant、调度、持久化、AG-UI、任务 UI 与恢复链 |
| 2026-09-11 | 补充自然/结构化澄清、延迟建 Task、promotion 谱系与 TaskNode 续接/重启矩阵 | 保留 AAF-110 同责任主体续接语义，并明确子智能体恢复边界 | 高风险不变；#11506 增加 HITL 状态槎接线与故障恢复门禁 |
| 2026-09-11 | 将复杂度、分解和最终语义完成责任收归 Task Owner Assistant；失败策略收窄为失败/暂停并保留用户业务数据 | 避免隐藏 classifier/judge 与通用业务清理越界 | AAF-115 仅保留 Harness 基础能力；资源链接与受控 CRUD 拆分至 AAF-116 |
| 2026-09-11 | 明确 TaskPlan/ExecutorPlan 双层职责及 inspect/amend/cancel 对话控制 | 支持用户安全定位、调整和停止 canonical Task | 计划调整保留历史并关闭旧 execution/dispatch/fence；业务数据不删除 |
| 2026-09-12 | 非法 TaskPlanDraft 仅作为当前调用失败，不进入 TaskPlan 历史 | 当前不存在持久 DRAFT freeze command；避免重复审计事实 | 删除 freezeAttemptNo、lastFreezeFailures 与 plan.freeze-rejected 要求；合法计划仍原子持久化为 FROZEN revision |
| 2026-09-12 | 接受 execution-centric ExecutionEvent 为 canonical 事件合同 | 当前产品事件用于进度、审计、通知和断线恢复，不承担聚合重建 | 删除零 Execution 事件、aggregateSequence 和第二套 TaskEventEnvelope 要求；关系表继续承载权威状态 |
| 2026-09-12 | 保留当前 Task REST 与 AG-UI/HITL 交互合同 | 当前 WebUI 直接关注 Task 状态，尚无第三方命令凭证产品需求 | 删除全部 202 receipt、统一 TaskInterrupt REST 及强制 create/retry/events 要求；不建立平行 API |
| 2026-09-12 | Task 列表收敛为服务端 conversationId 过滤 | 当前 Chatter 与 inspect_tasks 只消费当前对话，客户端全量拉取会随任务积累放大轮询成本 | list 必填 conversationId 并在装配前过滤；暂无任务中心，不提前实现 status/cursor |
| 2026-09-12 | TaskPanel 增加 TaskPlan/ExecutorPlan 两级只读步骤列表 | 用户需要看清助理拆分的子任务及节点内部步骤，但直接编辑会混淆 revision/attempt 语义 | 任务行显示外层节点数量；节点展示最新局部步骤；过滤内部指令、工具与策略，调整继续走对话 |
