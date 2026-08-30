---
level: Practice
layer: Model
purpose: 定义多智能体协调编排——TaskBoard、协调计划、聚合契约与产物创建时序
status: draft
version: 1.3.1
date: 2026-08-25
author: Kiro
tags:
  - TaskBoard
  - 协调计划
  - 聚合契约
  - 产物
dependencies:
  - ../architecture.md
  - ../runtime.md
related:
  - ../agent/agent.md
  - ../team/team.md
scope:
  includes:
    - TaskBoard 三种类型与节点拓扑
    - 协调计划的结构、校验与冻结
    - 聚合契约与聚合身份
    - 产物创建时序与产物工具
    - 强制两阶段编排的适用范围
  excludes:
    - 执行身份的循环合同（见 agent/agent.md）
    - Team 成员冻结与发布（见 team/team.md）
    - 工具授权门禁（见 action-governance.md）
gains:
  - 能为一个复杂目标选择正确的 TaskBoard 类型与节点数
  - 能校验一份协调计划是否合法
  - 能为长短产物任务选择合适的创建时序
---

# 协调编排

> 协调编排是 L3 的独立机制；`CHAT` 与 `EXECUTION` 均可由 `TaskAnalysis` 判定进入协调形态，`TEAM` 由冻结 Team version 进入协调形态。三者共用 TaskBoard、计划冻结、聚合和产物契约。DAG 不允许真实环，循环只能用有界 `IterationGroup` 表达。

## 定位与边界

| 本篇负责 | 上游或相邻合同 |
|---|---|
| TaskBoard 当前编排状态、CoordinationPlan、聚合、产物动作 | 入口、ExecutionIntent、完成门禁见 [runtime.md](../runtime.md) |
| 节点拓扑与结果收敛 | 节点内 Harness 循环见 [agent.md](../agent/agent.md) |
| 产物工具业务 Schema 与时序 | 工具可见性、授权与幂等见 [action-governance.md](../action-governance.md) |
| 产物事件对应关系 | 公共事件与投影见 [runtime-event.md](../runtime-event.md) |

PostgreSQL TaskBoard 是当前编排状态的唯一真理源；事件流只追加变化事实，AgentState 只保存节点执行工作态。

## 领域模型

### TaskBoard

| 字段 | 合同 |
|---|---|
| `taskId` | 父任务稳定标识 |
| `goal` | `goalId`、描述、完成证据、聚合契约、可选迭代状态 |
| `maxParallelism` | 当前看板可同时 RUNNING 的节点上限，必须 ≥ 1 |
| `subTasks{}` | key 与 `subTaskId` 相同；引用存在；依赖无环 |

| 类型 | 初始拓扑 | 适用 |
|---|---|---|
| `single` | 一个 `EXECUTOR` | `TaskAnalysis` 判定 `SINGLE_AGENT`——目标单一、无需拆分 |
| `coordinated` | 一个无业务工具 `COORDINATOR`，冻结计划后替换为执行 DAG | `TaskAnalysis` 判定 `TASKBOARD`——多目标、跨领域或必须委派 |
| `teamCoordinated` | 一个 Leader `COORDINATOR` + 1..8 个冻结 Worker `EXECUTOR` | 已发布 Team version，不经复杂度判定 |

非 Team 的 `single` / `coordinated` 选择只依据 `TaskAnalysis`，与 `interactionMode` 无关；`TEAM` 则由已发布 Team version 直接选择 `teamCoordinated`，不执行复杂度判定。判定契约见 [runtime.md](../runtime.md#任务复杂度判定)。**对话式同样可以进入 `coordinated`**。

`SubTask` 字段合同：

| 字段组 | 字段 |
|---|---|
| 身份 | `subTaskId`、`kind`、独立 `executionId`、独立 `sessionId` |
| 任务 | `description`、`dependsOn{}`、`inputBindings{}` |
| 能力 | `roleKey`、`skillKey`、可选 `assistantTarget`、`modelSelection` |
| 重试 | `attempts`、`maxAttempts`、`retryable` |
| 状态 | `PENDING`、`RUNNING`、`AWAITING_AUTHORIZATION`、`AWAITING_CLARIFICATION`、`COMPLETED`、`RETRYABLE`、`FAILED`、`CANCELED` |
| 结果 | `result`、`failure`、`clarifiedParameters{}` |

可运行节点必须为待领取状态、全部依赖已完成且符合迭代边界；按 `subTaskId` 排序后占用剩余槽位。

### CoordinationPlan

Coordinator 只输出一个严格 JSON 对象，Assistant 校验后原子冻结：

| 字段 | 类型与约束 |
|---|---|
| `goal` | 非空字符串 |
| `executors` | 1..8 的结构边界；运行时数量不得超过冻结 `DecompositionBudget` |
| `maxParallelism` | 1..8 的结构边界；运行时并行度不得超过冻结 `DecompositionBudget` |
| `aggregationContract` | 必须且只能覆盖全部结果执行者 |
| `iterationGroup` | 可空；静态 DAG 上的有界循环组 |

`ExecutorAssignment`：

| 字段 | 约束 |
|---|---|
| `subTaskId`、`description` | 非空，subTaskId 在计划内唯一 |
| `dependsOn{}` | 只能引用 coordinator 或同计划节点，整体无环 |
| `inputBindings{name → sourceSubTaskId}` | source 必须属于 `dependsOn` |
| `roleKey`、`skillKey` | 不得改变 FIXED Route；Team 必须逐项匹配冻结 Worker |
| `modelSelection` | 仅可提议 `AUTO`，或保留根请求同一 `EXPLICIT` 模型 |
| `maxAttempts` | 1..3 |

`IterationGroup` 包含 `groupId`、非空唯一 `memberSubTaskIds`、独立 `evaluatorSubTaskId`、`maxIterations` 1..8。evaluator 依赖全部 members；依赖 member 的下游也必须依赖 evaluator。

### 聚合契约

| 模式 | 合同 |
|---|---|
| `PASS_THROUGH` | 只允许一个结果执行者 |
| `ORDERED_CONCAT` | 按 `executorOrder` 与最长 32 字符的 separator 确定性拼接 |
| `AGGREGATOR_REDUCE` | 创建独立 `AGGREGATOR` 子任务；只消费冻结结果，不补造未执行内容 |

代码枚举已收敛为 `AGGREGATOR_REDUCE`，不保留旧名与双语义。原 Coordinator 不得再次执行或生成最终业务内容。

## 契约

### 计划冻结与调度

```text
Coordinator 严格 JSON
→ 闭世界字段与大小校验
→ Route / Team target / model / DAG / aggregation / budget 校验
→ 原子完成 coordinator 并冻结执行 DAG
→ ready() 按依赖和槽位领取
→ 每节点独立画像执行
→ 确定性聚合或独立 Aggregator
→ CompletionValidator
```

计划预算的字段、默认值、Team 提升与硬上限只引用 [architecture.md 的“有界任务分解预算”](../architecture.md#有界任务分解预算)。协调器按该冻结预算拒绝超限计划，不得静默 clamp。

### 强制两阶段编排

判定为 `TASKBOARD` 的目标必须使用 `coordinated` 两阶段：Coordinator 只做阻塞识别、目标澄清、拆分与模型策略提议，不继承业务工具、连接器、文件或 Shell 能力；计划冻结后至少创建一个 Executor。

`TASK + FIXED` 的固定 Skill 任务在其 SkillVersion 声明需要产物编排时同样强制两阶段。该规则不绑定任何具体能力族——copywriting 是当前唯一已接入的实例，不是规则本身的条件。

系统不固定增加“文档 Agent”。保存是产物工具职责；只有多章节组装、引用编排、模板套用或独立审校确有必要时，才在预算内增加 Executor。

### 产物创建时序

| 时序 | 适用 | 流程 |
|---|---|---|
| 内容优先 | 短文案、单次生成 | 流式生成 → 验证 → `content.draft.upsert` |
| Reservation 优先 | 长任务、协同编辑、断线恢复 | `artifact.reserve` → 分段检查点 → `artifact.commit` / `artifact.fail` |

Reservation 只创建任务级产物或不可见 `GENERATING` 草稿；生成期间不逐 token 写数据库。正文只经 AG-UI 输出；产物事件的安全字段只引用 [runtime-event.md](../runtime-event.md#产物事件)。

> **状态机方案已撤销（2026-08-30 复核）**：排查发现当前无任何触发路径选中 Reservation 优先时序（`ArtifactPolicy.saveTool` 唯一真实赋值只有 `content.draft.upsert`），四工具做出来会是零调用方的死代码；长内容生成现状是流式一次性吐出+整体落库，不是四工具解决的"多次物理调用分段产出中断续跑"场景。以下状态机描述保留仅供未来若出现真实场景时参考，当前不实现，不得声称已按此设计。详见 [改进意见](../../../../prd/improvements.md)。

```text
不存在 → [reserve] → GENERATING → [checkpoint]* → GENERATING
                          ↓ [commit]              ↓ [fail]
                        DRAFT                    FAILED
```

- **状态主线**：发布不属于本状态机，`commit` 后的 `DRAFT` 与 `content.draft.upsert` 产出的 `DRAFT` 是同一发布前状态；`checkpoint` 只递增版本，不改变 `GENERATING` 状态
- **单写者**：lease/fencing + `expectedVersion` 双门禁；版本冲突直接失败，不做最后写覆盖；旧 fence 在资源提交点被拒绝
- **checkpoint 粒度**：服务端受控 `bufferRef`，按稳定段落/时间窗口有界写入，不逐 token；事件只存引用与摘要哈希，不存正文
- **commit 原子性**：`commit` 至少对同库的 artifact/version、最终 Document、`ARTIFACT_COMMITTED` 事件与 outbox 做单事务提交，消除当前 `content.draft.upsert` 已知的"外部动作成功、receipt 未完成"崩溃窗口
- **fail 与重试**："逻辑 artifact"与"generation attempt"分离：`fail` 只终结当前 attempt（保留最后 checkpoint 到审计/恢复 TTL），`retryable=true` 时重试创建新 attempt 并显式继承合法 checkpoint，不是原地复活旧 attempt
- **可见性**：`GENERATING` 状态的产物在普通列表不可见，仅任务上下文内可查

### 产物工具 Schema

可信的 `tenantId`、`userId/visitorId`、`workspaceId`、`taskId`、`executionId`、fencing token 和幂等 receipt 来自 ToolGateway 上下文，模型参数不得覆盖。

| 工具 | 参数 Schema | 返回 Schema | 实现态 |
|---|---|---|---|
| `artifact.reserve` | `kind`、`mediaType`、可选 `title` | `artifactId`、`artifactVersion`、`state=GENERATING`、`summaryHash` | 🎯 目标态 · 当前不得声称已执行 |
| `content.draft.upsert` | `title` 1..200、`content` 1..1,000,000、可选 `documentType` 1..50（默认 markdown） | `artifactId`、`artifactState=DRAFT`、`published=false`；metadata 含 `artifactType=DOCUMENT`、`reversible=true`、`completionEvidence=DRAFT_COMMITTED` | ✅ 已实现 · `ContentDraftUpsertToolHandler.java:20-120` |
| `artifact.checkpoint` | `artifactId`、`expectedVersion`、`checkpointNo`、`bufferRef`、`summaryHash` | `artifactId`、新 `artifactVersion`、`state=GENERATING`、`checkpointNo`、`summaryHash` | 🎯 目标态 · 当前不得声称已执行 |
| `artifact.commit` | `artifactId`、`expectedVersion`、`bufferRef`、`mediaType`、`summaryHash` | `artifactId`、新 `artifactVersion`、`state=DRAFT`、`summaryHash`、`resourceRef` | 🎯 目标态 · 当前不得声称已执行 |
| `artifact.fail` | `artifactId`、`expectedVersion`、`failureCode`、`reasonSummary`、`retryable` | `artifactId`、新 `artifactVersion`、`state=FAILED`、`failureCode`、`retryable` | 🎯 目标态 · 当前不得声称已执行 |

`bufferRef` 是受控服务端内容引用，不接受本地路径、URL 或任意对象键。`expectedVersion` 冲突必须失败，不得最后写入覆盖。

### 产物事件引用

产物状态转换必须形成事件，但事件名称、字段、安全投影与实现态只在 [runtime-event.md 的“产物事件”](../runtime-event.md#产物事件) 定义。工具 receipt 不能替代产物事件；正文、`bufferRef` 和失败堆栈不得进入公共事件。

## 实现态

| 契约 | 实现态 |
|---|---|
| 三种 TaskBoard 类型 | ✅ 已实现 · `TaskBoard.java:48-166` |
| 严格计划解码、Route/Team/model/budget 校验 | ✅ 已实现 · `DelegatedTaskCoordinator.java:903-1036` |
| Team 计划必须覆盖全部 Worker | ✅ 已实现 · `TaskBoard.java:169-273`、`DelegatedTaskCoordinator.java:949-1018` |
| 槽位领取与依赖调度 | ✅ 已实现 · `TaskBoard.java:275-318` |
| 独立 Aggregator 子任务与画像 | ✅ 已实现 · 节点创建见 `TaskBoard.java:220-273`；`AGGREGATOR` 映射专用画像见 `AssistantCommand.java:197-213`、`InvocationProfile.java:108-121` |
| 计划结构边界与冻结策略预算分工 | ✅ 已实现 · `CoordinationPlan.java:12-57` 自证 1..8、唯一性、DAG 与聚合完整性；默认 1..5 及 Team 提升由 `DecompositionBudget.java:12-63` 建模，并在 `DelegatedTaskCoordinator.java:1019-1036` 外置执行；这是策略与聚合根的预期分工，不是实现缺口 |
| `AGGREGATOR_REDUCE` 命名收敛 | ✅ 已实现 · 枚举定义见 `CoordinationPlan.java:176-180`；独立 Aggregator 创建见 `TaskBoard.java:226-264`；协调者提示词允许值同步为新名（`InvocationPolicy.java:13-15`）。旧名 `COORDINATOR_REDUCE` 已删除，不保留双语义；存量 `ai_task_board.board_payload` 按 Flyway 基线重建约定覆盖，不加数据迁移 |
| 两阶段编排作为通用规则由判定驱动 | 🎯 目标态 · 当前不得声称已执行；现状为 `interactionMode == TASK` 硬绑定且限定 copywriting 类目（`AssistantExecutionService.java:172,267-284`） |
| 父委派任务完成前校验业务证据 | ⚠️ 部分实现 · 通用 Agent 链已调用验证器（`AssistantApplicationService.java:740-785`）；`coordinated`/`teamCoordinated` 未接入独立 `CompletionValidator`，但 `Goal.completionEvidence` 引用的终态节点已按 `completionEvidenceSatisfied` 校验（`EXECUTOR`：`DelegatedTaskCoordinator.java:472-496` 原有；`AGGREGATOR`：`DelegatedTaskCoordinator.java:490-496` 已补齐，此前唯一漏检点）；`PASS_THROUGH`/`ORDERED_CONCAT` 完成证据只引用已受检 `EXECUTOR`，`AGGREGATOR_REDUCE` 完成证据引用 `AGGREGATOR`；`COORDINATOR`/`EVALUATOR` 不产出最终业务内容，不适用同一证据校验。不得声称已统一接入 `CompletionValidator` 门面 |
| `content.draft.upsert` 工具 | ✅ 已实现 · `ContentDraftUpsertToolHandler.java:20-120` |
| `artifact.reserve` | 🎯 目标态 · 当前不得声称已执行 |
| `artifact.checkpoint` | 🎯 目标态 · 当前不得声称已执行 |
| `artifact.commit` | 🎯 目标态 · 当前不得声称已执行 |
| `artifact.fail` | 🎯 目标态 · 当前不得声称已执行 |

## 验收基线

- 非 Team 的 `single` / `coordinated` 只由 `TaskAnalysis` 决定，不出现按 `interactionMode` 分支的调度代码；`teamCoordinated` 只由已发布 Team version 决定
- 计划字段闭世界、依赖无环、聚合覆盖完整，超预算直接拒绝
- Team 计划不能遗漏、增加或改变任一冻结 Worker 的 Role/Skill/model 策略
- 每个节点和重试节点冻结独立画像；原 Coordinator 不生成最终业务内容
- 五类产物工具均拒绝模型传入可信主体字段，并通过 ToolGateway、receipt 与 fencing
- 产物状态转换的事件验收只按 [runtime-event.md 的“产物事件”](../runtime-event.md#产物事件) 执行
- 长任务可从 checkpoint 续跑；普通列表不暴露 `GENERATING` reservation
