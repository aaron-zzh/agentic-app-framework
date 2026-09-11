---
level: Practice
layer: Product
purpose: AAF-115 统一 Task、TaskPlan 与 Execution 任务模型及 DAG 编排需求规格
status: draft
version: 1.1.0
date: 2026-09-11
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

- CHAT 中的问候、简单问答和单轮只读回复只创建 ConversationMessage/Run/Execution，不创建 Task、TaskPlan 或 TaskNode。
- 明确 TASK、TEAM、后台长任务、有副作用、需要审批/澄清/暂停恢复或多节点编排的请求必须创建 Task。
- 入口先形成最小 `TaskAnalysis`。确定性简单场景不得额外调用模型；只有开放复杂目标才允许模型参与复杂度和协调模式判断。
- 复杂度结果只能决定是否提升为 Task 以及 TaskPlan 形态，不能扩大 controlMode、工具、Role、Skill、tenant、owner、预算或授权边界。

### 自然澄清、结构化澄清与延迟建 Task

- 直接 Execution 允许因缺少信息而进行自然对话式澄清。该轮只输出普通 Assistant 消息后结束，不创建 Task，不发 canonical Task interrupt，也不把本轮伪装成可恢复执行。
- 用户在下一轮补充信息时创建新的直接 Execution，并由 Conversation 历史提供公开上下文；服务端重新执行 TaskAnalysis，结果仍简单则继续直答，达到复杂任务边界才创建 Task。
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
- 节点只有在全部前置依赖达到允许的成功终态后才能变为 ready；任一必需依赖失败时，后继节点不得执行，并按失败策略暂停、补偿或失败。
- 串行通过依赖链表达，例如 `A → B → C`。
- 并行通过多个无互相依赖节点表达，例如 `A || B`。
- join 通过一个节点依赖多个前置节点表达，例如 `C dependsOn [A, B]`，只有 A、B 均完成后 C 才能执行。
- fan-out/fan-in 允许一个节点完成后释放多个并行节点，再由聚合节点等待全部分支。
- 同一节点只能被一个 worker 在一个 fencing generation 下领取；重复派发、恢复和事件重放不得重复产生副作用。
- 聚合必须使用冻结的 `AggregationContract`，且只能消费计划声明的节点结果；模型不得在运行中静默增加未授权节点或依赖。

### 状态、恢复与事件

- Task、TaskNode 和 Execution 分别拥有且只拥有本层状态；根 Task 状态由唯一应用服务依据节点和完成合同推进，不由多个 Repository 独立写入。
- Task 暂停、取消、人工接管和恢复必须传播到可运行节点与活跃 Execution，并保留原因、操作者、时间和恢复点。
- approval、clarification 和 resume 继续使用统一异步事件链；HTTP/SSE 请求线程不得等待长任务完成。
- Task、TaskPlan revision、TaskNode、Execution 和 Dispatch 的变化通过同一事务/outbox 产生事件；AG-UI、任务页和通知只做安全投影。
- 前端 TaskBoardPanel 只展示真实 Task；普通聊天 Run 不进入任务列表。TanStack Query 管服务端任务状态，Zustand 只管理瞬时 UI。

### TaskNode 与子智能体中断续跑

- TaskNode 身份在 TaskPlan revision 内稳定；可恢复中断是同一节点、同一次 Execution attempt 的分段继续，不等同于失败重试。
- 当责任主体/owner、授权边界、冻结 ExecutionProfile 与工具面均未变化，且 AgentState/checkpoint 完整、兼容并属于当前 generation 时，approval、structured clarification、PAUSE 和优雅停机恢复必须复用原 executionId/sessionId，延续 AAF-110 的同责任主体语义。
- 同一 Execution 续接前必须同时校验 taskId、nodeId、executionId、owner、profile revision、lease/fencing 和状态槽身份；任一不匹配均 fail-closed，不得串接其他节点或其他主体的历史。
- 终态失败、责任主体/owner 或授权画像变化、AgentState/checkpoint 缺失/损坏/不兼容，或恢复策略明确要求 fresh retry 时，只为受影响 TaskNode 创建新的 Execution attempt，并记录 predecessorExecutionId/attempt 谱系；`parentExecutionId` 继续表达任务分解关系，不得被重试关系复用。
- 新 attempt 不迁移旧 fence 的 AgentState；恢复正确性由 receipt、TaskNode checkpoint 和事件流保证，允许在状态槎不可用时从安全边界重建，但不得重复已确认副作用。
- 恢复或重启不得重跑整个 TaskPlan。已完成兄弟节点保持完成，未受影响的活跃节点按各自 generation 处理，只有中断节点继续或创建新 attempt。
- 中断节点完成后必须重新计算 DAG readiness；join 仍须等待全部必需依赖成功完成，不能因某一节点刚恢复就提前释放。
- 旧 worker、旧 lease、旧 fencing generation 和被替代 Execution 的迟到事件不得更新节点、释放后继或覆盖聚合结果。
- AgentState 只承载 Execution 工作态，不是 Task、TaskPlan、TaskNode 状态或恢复正确性的真理源。
- AAF-115 必须保留 AAF-110 的目标合同，同时关闭现有接线缺口：显式 `PAUSE` 已能保留状态槎，但 structured clarification/approval 的流截断尚未统一进入保留路径，且恢复入口的隐藏持久历史守卫仍会拒绝非空历史。#11506 不得在这两项未解决时声称子智能体可原地续接。

## 概念登记表

| 产品概念 | 目标实体 | 主表 | 说明 |
|---------|---------|------|------|
| 根任务 | `Task` | `ai_task` | 复杂或持久目标的唯一业务真理源 |
| 执行计划 | `TaskPlan` | `ai_task_plan` | Task 的可选版本化 DAG 计划 |
| 计划节点 | `TaskNode` | `ai_task_node` | Coordinator/Executor/Evaluator/Aggregator 节点 |
| 节点依赖 | `TaskDependency` | `ai_task_dependency` | TaskNode 间有向依赖边 |
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
And 服务端重新执行 TaskAnalysis
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
When 服务端 TaskAnalysis 判定需要持久任务
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
Given TaskPlan 包含环、自依赖、缺失引用或超过硬上限的并行度
When 服务端校验并尝试冻结计划
Then 计划被确定性拒绝
And 不创建可执行 Dispatch
And 不调用 Agent、模型或业务 Tool
And Task 保留可审计失败原因
```

```gherkin
Scenario: 节点失败阻止依赖节点误执行
Given C 依赖 A 与 B 且 B 达到不可重试失败
When 调度器重新计算 ready 节点
Then C 不执行
And Task 按合同进入 FAILED、PAUSED 或补偿路径之一
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

## 非目标

- 不把每条聊天消息包装成 `TaskPlan.single`。
- 不用消息长度、前端布尔参数或客户端提供的复杂度直接决定 Task 创建。
- 不启用 AgentScope 原生 subagent、workspace task 或第二套 DAG。
- 不把 TaskPlan、Execution、AgentState、审批流或 AIGC 业务 Task 合并成一张无边界的大表。
- 不在本故事引入新工作流产品能力；预定义 Workflow 仅作为 TaskPlan 节点可调用的既有执行机制。
- 不保留 AssistantTask/DelegatedTask 双写、旧 API fallback、影子状态或长期兼容迁移层。

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
