---
level: Practice
layer: Model
purpose: 定义 L4 群体的协作模型、版本冻结与发布校验、调度与运行限制
status: draft
version: 1.1.1
date: 2026-08-25
author: Kiro
tags:
  - L4 Team
  - 版本冻结
  - 协作
dependencies:
  - ../architecture.md
  - ../runtime.md
related:
  - ../assistant/coordination.md
scope:
  includes:
    - Team 的协作模型与状态归属
    - 定义、发布校验与版本冻结
    - 运行入口、调度与可见性
    - 与单助理委派的边界
  excludes:
    - TaskBoard 与聚合契约机制（见 assistant/coordination.md）
    - 助理自身的领域模型（见 assistant/assistant.md）
gains:
  - 能判断一个目标该用 Team 还是停留在单助理委派
  - 能发布一个合法的 Team 版本并预判校验结果
  - 能识别当前 Team 的能力边界与未实现部分
---

# L4 群体

> Team 是多个独立 Assistant 围绕共同目标形成的版本化协作组织。Team 持有项目级目标、分工与仲裁责任，不执行具体动作；行动仍由成员 Assistant 调度 L2 Agent 完成。

## 定位与边界

当前只支持 `LEADER_COORDINATED`：一个 Leader、1..8 个静态 Worker、冻结的 Assistant/Role/Skill/工具 target，以及 TaskBoard 确定性聚合。

| 当前合同 | 非当前能力 |
|---|---|
| 静态 roster、版本发布、Leader 计划、Worker 隔离执行 | 动态成员、自动冲突仲裁、项目级独立运行时 |
| 统一 Assistant 入口、TaskBoard、事件与恢复 | Pipeline、Peer、MsgHub、外部 A2A Worker |
| `ai_definition_lifecycle` + TaskBoard | 独立 `ai_team*` 表或 Team 专属事件流 |

右列均为目标态能力，当前不得声称已执行，也不得以旁路运行时启用。

### 与单助理委派的边界

| 场景 | 归属 |
|---|---|
| 子任务只需能力分工，无独立人格或长期责任 | L3 对 L2 的有界委派 |
| 协作者需要独立 Persona、长期责任、跨助理目标对齐或冲突仲裁 | L4 Team |

不得把 Team 成员降格为 TaskBoard 中临时生成的普通子智能体。

## 领域模型

### TeamDefinition

| 字段 | 类型 | 不变量 |
|---|---|---|
| `teamId` | string | 非空稳定标识，与生命周期 `definitionId` 相同 |
| `version` | long | ≥ 1，与生命周期 `definitionVersion` 相同 |
| `strategy` | enum | 当前只能为 `LEADER_COORDINATED` |
| `leader` | `Member` | 恰好一个，`role=LEADER` |
| `workers` | `List<Member>` | 1..8，全部 `role=WORKER` |

`Member` 字段：

| 字段 | 合同 |
|---|---|
| `memberKey` | Team version 内唯一，且不能与 Leader 冲突 |
| `role` | `LEADER` / `WORKER`，位置与角色必须一致 |
| `assistantId`、`assistantRevision` | 精确引用已发布 Assistant revision |
| `roleKey`、`skillKey` | Role 必须存在且包含固定 Skill |
| `allowedToolKeys{}` | 集合元素不得为空白；只能收窄 Assistant ToolPolicy 与 Role 工具上限 |

Worker 列表顺序不具有运行语义；调度与默认聚合使用 `memberKey` 字典序形成确定性 canonical order。

### 生命周期与持久化

Team 作为 `DefinitionKind.TEAM` 保存于统一 `ai_definition_lifecycle`，不建独立 Team 表：

| 列 / payload | 内容 |
|---|---|
| 复合唯一键 | `tenant_id + TEAM + teamId + version` |
| `state` | `DRAFT`、`PUBLISHED`、`DEPRECATED`、`DISABLED` |
| `lifecycle_payload` | review、compatibilityImpact、rollbackVersion、完整 `teamDefinition`、updatedAt |
| 数据库闭世界校验 | id/version 一致、策略固定、Leader/Worker 字段完整、Worker 1..8、memberKey 唯一、未知字段拒绝 |

PostgreSQL JSONB 是 Team version 的唯一配置真理源；代码 record 是反序列化后的领域快照，不是第二份配置。

### 版本冻结内容

发布后以下内容不可被运行请求覆盖：

| 冻结项 | 说明 |
|---|---|
| Team | `teamId`、`version`、`strategy` |
| roster | Leader 与全部 Worker 的 `memberKey`、角色和 canonical order |
| Assistant target | `assistantId`、精确 revision |
| 能力 target | `roleKey`、`skillKey`、`allowedToolKeys` |
| 聚合基线 | 全部 Worker 都是完成证据，默认按 canonical order 拼接 |

修改任一冻结项必须发布新 Team version；运行中的旧 version 不随新版本变化。

## 契约

### 发布校验

Draft、publish 和 rollback 均执行相同 fail-closed 校验：

| 校验 | 失败条件 |
|---|---|
| 结构 | 策略非 `LEADER_COORDINATED`、Leader 数不为一、Worker 不在 1..8、memberKey 重复 |
| Assistant | 不存在、非 `PUBLISHED`、revision 不精确匹配 |
| Role/Skill | Role 不存在，或固定 Skill 不属于该 Role |
| 工具 | allowlist 超出 Assistant ToolPolicy 或 Role 工具上限 |
| 审核 | publish/rollback 的目标版本未 APPROVED |
| 不可变性 | 试图用 draft 覆盖已非 DRAFT 的版本 |

运行时只接受 `PUBLISHED` Team version，并再次解析成员 target 与当前主体可执行性。请求不得覆盖 Assistant、Role、Skill、工具、交互模式、Route 或产物策略。

### 运行与身份

Team 经统一 `POST /api/agui/run` 启动，身份、事件、正文和恢复只引用 [runtime.md](../runtime.md) 与 [runtime-event.md](../runtime-event.md)。

```text
已发布 Team version
→ 解析并校验 Leader / Worker target
→ TaskBoard.teamCoordinated
→ Leader 生成严格 CoordinationPlan
→ 计划必须且只能覆盖全部 Worker
→ Worker 独立执行
→ canonical order 聚合
→ CompletionValidator
```

Leader 不得增加、跳过或替换 Worker，也不得改变其 Role、Skill、工具上限或模型策略。

### Worker 槽位分配

| 规则 | 合同 |
|---|---|
| 初始槽位 | 只有 Leader 可运行；Worker 依赖 coordinator |
| 计划冻结 | Worker 数保持等于 roster；每个 Worker 获得独立 executionId、sessionId 和画像 |
| 就绪判定 | 依赖全部完成，状态可领取，且满足迭代边界 |
| 领取顺序 | 按 `memberKey/subTaskId` 字典序 |
| 并行槽位 | `running < maxParallelism` 才领取；资源不足时等待，不改变冻结计划 |
| 聚合 | 全部 Worker 是完成证据；缺失结果不得静默聚合 |

### 分解预算一致性

`DecompositionBudget` 的字段、默认值与硬上限只由 [architecture.md 的“有界任务分解预算”](../architecture.md#有界任务分解预算) 定义；本节仅增加固定 Team 的 roster 一致性不变量。固定 Team roster 为 `R` 时：

```text
1 <= R <= 8
maxChildrenPerPlan >= R
maxParallelAgents >= R
TaskBoard.maxParallelism = R
```

该预算提升只允许已发布固定 Team 调用；普通动态任务不得借 Team 规则扩容。若基础设施瞬时容量小于 `R`，只能排队，不得改写合同中的并行上限。

### 成员级预算

> **🎯 目标态，远期不动，需重新评估**（2026-08-27 人类决策）。本节描述的成员级预留预算在当前身份与计费模型下价值不足，暂不排期，也不作为实现缺口记账。
>
> 依据：Team 成员只能是调用者自己的 Assistant 或系统助理（`AssistantExecutionService.resolveTeamMember` → `requireExecutableBy`），费用全部结算到发起者单一用户账户（`JpaTokenMeteringAdapter.record` 取 `context.userId()`），不存在跨用户或团队积分池；任务级上限与暂停已由 `ExecutionContract.BudgetLimit` + `DelegatedTask.BudgetUsage` + `pauseAndThrow` 覆盖。有无成员切分的差别仅在"撞上限时手上有多少半成品"——因为聚合要求全部 Worker 结果，两种情况最终都转人工。
>
> 重新评估的触发条件：出现跨用户 Team、团队级积分池，或需要按成员计费/配额时。

根任务预算是总账，未来若引入成员级预算，每个 Worker 需冻结 `MemberExecutionBudget`：

| 字段 | 规则 |
|---|---|
| `memberKey`、`executionId` | 预算归属唯一节点 |
| `maxAttempts`、`maxModelRetries`、`maxHarnessIterations` | 均不得超过根任务及成员执行策略上限 |
| `maxToolInvocations`、`tokenLimit`、`deadline` | 调度前预留；不足则节点不启动 |
| `reservedAt`、`policyVersion` | 可审计且重放稳定 |

成员不能静默借用其他成员额度；重新分配必须形成新 TaskBoard version 和决策事件。Harness 迭代/模型重试不计入 `DecompositionBudget`，但计入成员执行预算。

当前生效的约束只有任务级：`ExecutionContract.BudgetLimit(modelTokens, toolUnits, credits)` 为上限，`DelegatedTask.BudgetUsage` 为累计量，越界经 `pauseAndThrow` 置任务 `PAUSED` 并在 checkpoint 记 `budgetPause` 原因，转人工接管。

### 失败隔离

| 失败 | 处置 |
|---|---|
| 瞬时失败且 attempts 未耗尽 | 仅该 Worker 进入 `RETRYABLE`，使用新 execution/session 重试 |
| 等待授权或澄清 | 仅该 Worker 暂停；不修改其他 Worker 画像与结果 |
| 终止失败 | 阻断依赖它的节点；无依赖的已领取 Worker可完成；父任务最终失败 |
| Leader 计划非法 | 不创建 Worker 新执行，父任务失败关闭 |
| 预算耗尽或 deadline 到达 | 不转派、不降级、不扩权，形成失败或人工接管 |

MVP 不自动把失败 Worker 路由给其他成员，也不以部分聚合冒充完成。

## 实现态

| 契约 | 实现态 |
|---|---|
| TeamDefinition 仅支持 `LEADER_COORDINATED`、静态 roster 1..8 与唯一 memberKey | ✅ 已实现 · 策略拒绝见 `TeamDefinition.java:17-21`，roster 上限见 `TeamDefinition.java:23-25`，成员角色与唯一键见 `TeamDefinition.java:26-34` |
| 统一 JSONB 生命周期持久化与数据库闭世界校验 | ✅ 已实现 · `DefinitionLifecycleEntity.java:14-53`、`JpaAutomationStore.java:181-214`、`v16__intelligent_runtime_schema.sql:650-790` |
| Draft/publish/rollback 的成员发布与能力校验 | ✅ 已实现 · `DefinitionLifecycleService.java:23-170` |
| 运行只引用已发布 Team version | ✅ 已实现 · `DefinitionLifecycleService.java:52-58`、`AssistantExecutionService.java:806-834` |
| 请求不能覆盖冻结成员 target | ✅ 已实现 · `AssistantExecutionService.java:95-145` |
| 计划必须且只能覆盖全部 Worker | ✅ 已实现 · `TaskBoard.java:169-273`、`DelegatedTaskCoordinator.java:949-1018` |
| Worker 独立 target、执行身份与确定性领取 | ✅ 已实现 · `TaskBoard.java:100-166`、`TaskBoard.java:275-318` |
| roster 6..8 的 children/parallel 预算上限提升且硬上限为 8 | ✅ 已实现 · `DecompositionBudget.java:12-45`、`DelegatedTaskCoordinator.java:1019-1036` |
| 冻结后的 `TaskBoard.maxParallelism` 始终覆盖完整 roster | ✅ 已实现 · `TaskBoard.applyCoordinationPlan` 对固定 Team 断言 `plan.maxParallelism == roster`，低于或高于均拒绝（不静默串行化，容量不足由领取时排队解决）；`DelegatedTaskCoordinator.java:1019-1036` 继续校验不超过提升后的形状上限；协调者提示词同步声明该约束（`InvocationPolicy.java:13-16`）；用例见 `CoordinationPlanTest` |
| 节点级状态、瞬时重试与终止失败隔离 | ⚠️ 部分实现 · `TaskBoard.java:327-329`、`TaskBoard.java:467-473`、`TaskBoard.java:858-868`；节点状态已隔离，但成员预算预留和预算转移审计尚未实现 |
| Team 父任务统一经 `CompletionValidator` 完成判定 | ⚠️ 部分实现 · Worker 的通用 Agent 链已调用验证器（`AssistantApplicationService.java:740-785`）；父 Team TaskBoard 仍直接提交终态（`DelegatedTaskCoordinator.java:554-613`） |
| `MemberExecutionBudget` 字段级冻结 | 🎯 目标态 · **远期不动，需重新评估**（见成员级预算节的决策依据）；当前只有任务级上限与暂停生效 |
| 动态成员与 Team 运行链自动仲裁 | 🎯 目标态 · 当前不得声称已执行；现有 `ConflictArbitrator.java:19-86` 是未接入 Team/TaskBoard 的孤立服务，不能据此声称自动仲裁已落地 |
| Pipeline / Peer 协作形态 | 🎯 目标态 · 当前不得声称已执行 |
| 外部 A2A Worker | 🎯 目标态 · 当前不得声称已执行 |

## 验收基线

- 未发布、revision 失配或能力越界的 Team version 无法启动
- 运行请求和 Leader 计划均无法增加、遗漏或改变冻结 Worker target
- roster 为 6..8 时，生效 children、并行预算和 TaskBoard 槽位均至少覆盖 roster，且任何层都不超过 8
- 单个 Worker 的失败、澄清、授权与重试不改写其他 Worker 的画像、预算和结果
- 缺失任何 Worker 完成证据时父任务不能完成或输出部分聚合
- Team 与单助理运行使用同一身份、TaskBoard、事件、正文与恢复模型
