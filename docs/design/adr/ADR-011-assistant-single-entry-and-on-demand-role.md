---
level: Practice
layer: Principle
purpose: 明确助理对话的单入口与双容器关系，以及协调者按需加载角色、拆步骤、拆多角色子智能体的方式
status: proposed
version: 1.1.0
date: 2026-11-11
author: AaronZZH & Kiro
scope:
  includes:
    - DIRECT 与 TASK 是否合并、TEAM 的位置
    - 协调者的基础工具面与角色目录
    - 角色在执行中途切换的机制与延迟
    - 委派子智能体时的角色授权基线
  excludes:
    - TEAM 容器的实现
    - 聚合者挂起恢复（见 ADR-008 候选方向）
gains:
  - 判断一次对话该落在哪个容器、由谁决定
  - 判断换角色要不要重建智能体、成本在哪
  - 按分期清单拆出技术任务
changelog:
  - 2026-11-11 | 1.1.0 | 补充锁定与会话当前角色、升级后同一对话流、看板状态；人类确认按本设计实现第一版（AAF-127）
  - 2026-11-11 | 1.0.0 | 初稿：整理自动角色、升级任务、多角色委派三类问题
---

# ADR-011: 助理单入口、双容器与协调者按需加载角色

## Context and Problem Statement

期望的助理行为是三档：默认自动角色；简单问题直接回答；一般复杂问题自动切换到合适角色并拆步骤；特别复杂或用户明确要求时拆成多个不同角色的子智能体。

现状与期望的差距（代码核实于 2026-11-11）：

| 期望 | 现状 | 卡点 |
|---|---|---|
| 自动角色 | DIRECT 在循环开始前调用 `DefaultRoleSelector`，一次零工具 L0 模型调用，只看本次输入文本 | 判断依据弱；调用失败时静默回退默认角色（已补日志）；前端选过角色后回不到自动 |
| 一般复杂自动拆步骤 | `plan_steps` 只在任务态可用，DIRECT 需先 `promote_to_task` | 升级条件只写了"授权动作、结构化交互、多步骤持久"，模型很少主动升级；升级后协调者从头重跑一轮 |
| 多角色子智能体 | 任务态协调者用 `delegate_agents` 为执行者指定 `roleKey`/`skillKey` | 授权基线是协调者自己的角色，执行者工具集必须是其子集；AUTO 下协调者无技能，执行者必须显式给 `skillKey` |
| 用户要求多角色 | `DECOMPOSE_REQUIRED` 只能通过请求参数传入 | 只允许 TASK；TASK 又要求同时给 Role 和 Skill，与自动角色冲突，前端没有入口 |

需要回答四个问题：DIRECT、TASK、TEAM 是否需要拆开；DIRECT 与 TASK 能否合并为一个助理入口；协调者能否自己判断加载哪个角色；中途加载角色是否需要重建智能体、延迟多大。

### DIRECT 与 TASK 为什么拆开

文档记录的原因：

- AAF-107 之后所有对话一律建 coordinated 板。AAF-115 背景记录了后果：问候和简单问答也产生 Task，状态有双真理源，任务列表噪声多，复杂度判断发生在建 Task 之后（[AAF-115 requirement](../../task/v0.13/AAF-115/requirement.md)）。
- TASK 路径出过一次真实故障：提交 Task 后入口返回空流，SSE 立即闭合，前端收不到正文。AAF-117 #11701 至 #11705 已修复（[ADR-008](ADR-008-task-execution-shape-and-streaming.md)）。
- ADR-008 结论：两条路径暂不合并实现，合并会把持久化开销带给普通聊天。

"一上来就走任务会报错"，代码里目前还有两个来源：

- 请求 `interactionMode=TASK` 但没有同时给 Role 和 Skill，抛 `EXECUTION_TASK_ROUTE_INCOMPLETE`（7_003_016）。自动角色天然没有这两个值。
- DIRECT 中模型调用需要授权的工具时，`DefaultToolGateway` 在副作用前自动升级为 Task 并携带该调用，协调者节点首轮直接发起审批（AAF-128，取代原"拒绝并提示 promote"）。控制交接（`promote_to_task`、`activate_role`、网关自动升级）统一以工具证据 `controlHandoff=true` 挂起循环，不再按工具名集合判定；`EXECUTION_PROMOTED` 新增 `trigger=MODEL|TOOL_GATE` 与 `pendingToolName`。

没有找到"全部走任务"的首包延迟量化数据。按代码推断，TASK 首包比 DIRECT 多出：多表持久化事务、signal 与虚拟线程切换、dispatch 租约与会话租约两次领取、worker 侧重新冻结画像。signal 丢失时只能等 30 秒定时补偿。

### 智能体能否在执行中动态加载角色

核实结论：

- 一次 `execute()` 内，system prompt、工具集、已激活技能全部冻结。`HarnessAgentExecutionAdapter` 在订阅时编译一次 `ReActAgent`，`AgentScopeSpecCompiler.requireFrozenToolSurface` 断言工具面等于冻结白名单。
- `ExecutionProfileSnapshot.PerCallProfile` 的目标是"每次物理调用一份"，但注释写明当前仍整体冻结一次，只做了结构隔离。
- `context.load` 把技能正文作为工具结果返回，不改 system prompt，也不改工具集。按需技能声明的工具在同一执行里不会变得可用。
- 重建成本低：`buildAgent` 只是内存对象构造，加最多两次数据库查询（模型配置、非内置工具目录），无网络调用。编译结果按 prompt、工具集、模型、执行策略进 LRU 缓存（容量 128），命中时接近零。该数字为推断，未实测。
- 换角色的主要成本是多一次模型往返：新角色的 system prompt 不同，需要重新读一遍上下文，服务商的前缀缓存也会失效。
- AgentScope 原生有循环内动态能力：`onModelCall` 中间件可以在每次物理调用前改写 system 消息和工具 schema；工具组按会话激活，状态存在 `AgentState.toolContext`。AAF 全部关闭或未使用（`enableMetaTool(false)`、`dynamicSkillsEnabled(false)`），且工具必须在构建时注册，工具组只能隐藏或显示。隐藏的工具被模型直接调用时能否拦截，未核实。

## Decision Drivers

- 简单问题的首包延迟不能比现状差
- 角色判断应由拥有完整上下文、记忆、历史的协调者做，与 AAF-107 删除前置复杂度分类器的理由一致
- 不为普通聊天付持久化成本
- 权限、工具可见性、预算由代码强制，不依赖提示词
- 不加兼容层，不保留两套角色选择机制
- 改动可分期，低风险部分先落地

## Considered Options

入口与容器：

- 入口容器选项一：维持现状，前端按页面决定传 `CONVERSATIONAL` 或 `TASK`
- 入口容器选项二：DIRECT 与 TASK 完全合并为一个容器，所有对话都建 Task
- 入口容器选项三：单入口、双容器。一个协调者入口，容器由服务端硬门和协调者决定

角色加载：

- 角色选项 A：保留循环前 L0 预选（现状）
- 角色选项 B：协调者调用 `activate_role`，结束当前循环，用新角色重建智能体并继续同一次 Run
- 角色选项 C：循环内动态切换，借助 `onModelCall` 或工具组，在同一循环的下一次物理调用生效
- 角色选项 D：协调者从不切换自己，需要角色时一律委派单个执行者

## Decision Outcome

**Chosen option**："入口容器选项三"加"角色选项 B"，选项 C 作为后续优化保留。理由：容器差异是真实的持久化能力差异，不该合并；入口和决策层本来就是同一个协调者（ADR-008 已统一），没有理由让前端选容器。角色判断交给协调者，简单问题省掉一次 L0 调用；需要换角色时只多一次协调者首轮，重建是毫秒级。

### 单入口双容器

- 前端只有一个助理入口（`/api/agui/run`），普通聊天不再需要传 `interactionMode`。
- 容器选择分两层：
  - 服务端硬门（`TaskAnalysisPolicy`，保持确定性）：绑定 Team 进 TEAM；`AUTO_SAVE_DRAFT` 等持久产物进 TASK；请求带 `DECOMPOSE_REQUIRED` 进 TASK；显式 `interactionMode=TASK` 进 TASK；其余进 DIRECT。
  - 协调者判断：DIRECT 中判断需要多步骤、多角色或授权动作时调用 `promote_to_task`。
- TASK 允许自动路由：不给 Role/Skill 时协调者节点使用助理默认角色、不预置技能，不再抛 7_003_016。显式给 Role/Skill 的业务页（如文案页）行为不变。
- TEAM 保留为第三个硬门容器，本期不实现改动。

### 协调者基础工具面与角色目录

- 协调者在两种容器中都有一套与角色无关的基础工具，由 `BuiltinControlToolProfile` 按容器状态决定：
  - 轻量态：任务控制、`promote_to_task`、`context.load`，新增 `activate_role`
  - 任务态：`plan_steps`、`report_step`、`delegate_agents`、`request_clarification`、`context.load`（按分解策略裁剪，规则不变）
- 业务工具来自当前激活角色加助理级工具，仍经 `ToolPolicy`、用户功能权限（`toolVisibility`）、`ToolGateway` 授权三层过滤。
- 协调者 prompt 注入当前助理已发布的角色目录：`roleKey`、名称、职责、非职责、可用技能摘要。内容与现在 L0 选择器收到的候选一致，只是换给协调者。
- 未激活其他角色时，协调者以默认角色运行。

### 三档行为

| 档位 | 协调者动作 | 容器 | 角色 | 相对直答的额外模型调用 |
|---|---|---|---|---|
| 简单直答 | 直接回答 | DIRECT | 默认角色 | 0（现状每条消息多一次 L0 调用） |
| 需要特定角色 | `activate_role` 后由新角色回答 | DIRECT | 指定角色 | 1 次协调者首轮（通常只输出工具调用） |
| 一般复杂 | `promote_to_task`，任务态内 `plan_steps` 逐步执行 | TASK | 升级时指定，或沿用当前 | 1 次轻量态首轮，加任务持久化 |
| 特别复杂或用户要求 | `promote_to_task` 后 `delegate_agents`，执行者各带角色 | TASK | 每个执行者各自角色 | N 个执行者循环加聚合者 |

用户在对话里口头要求"多角色协作"时，`builtin-task-triage` 写明优先遵从，走第四档。调用方确定要强制拆分时传 `DECOMPOSE_REQUIRED`，由硬门直接进 TASK。

### activate_role 机制

- 只在轻量态可用，每个 Run 最多一次，必须在产出任何面向用户的正文之前调用，否则工具返回错误。
- 入参：`roleKey`（必须属于当前助理已发布角色）、`reason`。
- harness 收到调用后：
  1. 结束当前执行，写 `ROLE_SWITCHED` 审计事件（`selectedBy=COORDINATOR`）。
  2. 以同一 Run、新 `executionId`（记录前驱）和 `ROUTE_FIXED(roleKey, skill=null)` 重新冻结画像，技能保持开放选择。
  3. 输入为原始用户输入，加一条受控上下文，写明协调者的切换理由。
  4. 新执行的事件流直接接在同一条 SSE 上（`concatWith`），不建 Task，不持久化中间态。
- 不继承前一执行的工具调用历史，避免新工具集遇到不存在的工具调用记录。
- 删除 `DefaultRoleSelector`、`RoleSelector` 端口和 `modelRoleSelector` bean，不保留双路径。

### 锁定与会话当前角色

- 不单独做"自动角色"功能，切换是协调者的默认能力，所有助理通用。对话里不锁定角色：
  - 默认从会话当前角色起步，没有记录时用助理默认角色（通用助理）
  - 用户在下拉里选角色，只是指定起步角色：入口把它写入会话当前角色，请求仍按 `ROUTE_AUTO` 执行，协调者可以自主切换
  - 只有业务页面的固定调用（`EXECUTION` 模式同时指定角色与技能）走 `ROUTE_FIXED`，不暴露 `activate_role`
- 会话保存"当前角色"：`activate_role` 或带 `roleKey` 的 `promote_to_task` 成功后写入 `Conversation.agentRole`。下一条消息未锁定时从该角色起步，协调者仍可再切换；角色已不属于助理时回退默认角色
- 它只决定新一轮从哪个角色开始，不复用上一轮执行现场，与 AAF-115"每条消息独立 Run"不冲突
- 每个 Run 最多切换一次：续接执行使用 `ROUTE_FIXED`，看不到 `activate_role`

### 升级后的同一条对话流

- `promote_to_task` 与 `activate_role` 的工具结果返回后，harness 立即结束当前循环，不再让模型在轻量态继续输出
- `EXECUTION_PROMOTED` 不再映射为 `RUN_FINISHED`，改为 CUSTOM `aaf.task.promoted`
- 入口观察到升级事件后，在同一条 SSE 上订阅该任务的实时事件，并从事件库回补已落库事件、按事件 ID 去重，直到交付节点完成（`RunDeliveryTracker`）
- 回补窗口内的 `MESSAGE_DELTA` 不落库，只会以完整消息出现；任务事件序号与轻量态不同序，SSE id 可能回退，本期接受

### 看板状态

- 首页看板通过公共 SSE（ADR-009）接收 `task.status.changed`，事件集合补充执行开始、升级、角色切换，payload 带节点类型与角色
- 看板用新接口拉取当前用户未终结任务，收到事件只做查询失效，不把事件内容复制到客户端状态

### promote_to_task 携带意图

- 入参在 `reason` 之外增加可选 `roleKey`，以及可选 `shape`（`STEPS` 或 `DELEGATE`）。
- `TaskCommandService.promoteDirect` 用 `roleKey` 作为协调者节点角色，不给则沿用轻量态角色；`shape` 与 `reason` 作为受控上下文写进协调者输入，任务态协调者不必重新从头判断。
- `builtin-task-triage` 和工具描述的升级条件补充"语义上需要多个有序步骤"和"需要不同角色分工"。

### 委派的角色授权基线

- 现规则：执行者角色的候选工具集必须是协调者角色候选工具集的子集（`SubmitCoordinationPlanTool.requireDelegable`）。
- 新规则：执行者角色只需属于当前助理已发布角色目录，技能（如果给出）属于该角色的候选目录。
- 防提权由以下各层承担：
  - 执行者只拿到自己角色加助理级的工具，不拿并集
  - `ToolPolicy` 按 `controlMode` 裁剪
  - 用户功能权限过滤工具可见性
  - `ToolGateway` 对写操作走授权审批
  - 分解预算限制执行者数量与并行度
- 执行者 `skillKey` 改为可选。省略时执行者以该角色运行，技能走 `SELECT_AND_AUGMENT` 并通过 `context.load` 按需加载。
- 此项属于权限变更，是 🔴 高风险，需人类审核。

### 不采用循环内动态切换的理由

角色选项 C 延迟最低，但需要同时做到：

- 把画像改成每次物理调用一份（`PerCallProfile` 目标态），审计与恢复都要跟着改
- 构建时注册所有角色工具的并集，再按激活角色隐藏，并核实隐藏工具被直接调用时 `ToolGateway` 能按当前角色拒绝
- 缓存键、`requireFrozenToolSurface` 断言、提示词信封捕获的语义全部要调整

改动面大，且 B 的额外成本只是一次协调者首轮。先落地 B，实测切换延迟后再决定是否做 C。

### Positive Consequences

- 前端不再需要区分容器，自动角色不会因 TASK 校验报错
- 简单问题省掉每条消息一次 L0 调用
- 角色判断有完整上下文，切换理由进审计
- 多角色委派不再受协调者初始角色限制

### Negative Consequences

- 需要非默认角色时，协调者首轮比 L0 小调用更重（完整 system prompt 加记忆）
- 委派授权基线放宽后，防提权完全依赖 `ToolPolicy`、用户权限、`ToolGateway` 三层，需要补测试覆盖
- 协调者 prompt 增加角色目录，助理角色多时占用上下文
- 新增 `activate_role` 续接链路，同一 Run 内出现两个 Execution，前端消息投影需要确认只展示后一个的正文

### Reversal Triggers（反向选择触发条件）

仅当出现以下之一时考虑回切：

1. 实测需要切换角色的对话占比高，且 B 的切换延迟明显高于 L0 预选：改做选项 C，或在轻量态恢复 L0 预选
2. 授权基线放宽后出现越权调用：恢复子集校验，改为白名单式的角色委派矩阵
3. 协调者频繁误调用 `activate_role` 或 `promote_to_task`：收紧技能文案，必要时由硬门限定可切换的角色范围

## Pros and Cons of the Options

### 入口容器选项一：维持现状

- Good: 不改代码
- Bad: 前端要懂容器；自动角色进不了 TASK；用户要求多角色没有入口

### 入口容器选项二：完全合并为一个容器

- Good: 一套执行路径，升级概念消失
- Bad: 回到 AAF-107 的问题：每条消息建 Task、任务噪声、首包多出持久化与调度开销

### 入口容器选项三：单入口双容器

- Good: 前端简单；普通聊天保持轻量；需要持久时才付成本
- Bad: 升级仍需新开一次执行，协调者在任务态重跑一轮

### 角色选项 A：L0 预选

- Good: 已实现；调用小
- Bad: 只看输入文本；每条未固定角色的消息都多一次调用；失败静默回退

### 角色选项 B：activate_role 重建续接

- Good: 判断有完整上下文；简单问题零额外调用；重建毫秒级
- Bad: 切换时多一次协调者首轮；同一 Run 有两个 Execution

### 角色选项 C：循环内动态切换

- Good: 切换几乎零额外延迟
- Bad: 打破整体冻结的画像模型，工具授权、审计、缓存都要改；隐藏工具的拦截未核实

### 角色选项 D：一律委派单执行者

- Good: 不需要新机制
- Bad: 每次换角色都要建 Task、跑执行者和聚合者，简单问题也一样，延迟和成本最高

## 分期

| 分期 | 内容 | 风险 | 主要改动位置 |
|---|---|---|---|
| P1 | 升级条件文案补充多步骤与多角色分工 | 🟢 | `builtin-task-triage.md`、`PromoteDirectTaskTool.description` |
| P1 | TASK 允许自动路由，协调者用默认角色 | 🟡 | `ExecutionIntent` 构造校验、`AssistantExecutionService.executionIntent`、`analyzedBoard` |
| P1 | 执行者 `skillKey` 可选 | 🟡 | `SubmitCoordinationPlanTool.optionalText`/`requireDelegable`、`builtin-agent-delegation.md` |
| P1 | 前端角色下拉增加"自动角色"选项；排查页面配置 `agentRole` 是否把自动变成了固定 | 🟢 | `ChatterToolbar.tsx`、`GlobalChatter.tsx` |
| P2 | 委派授权基线改为助理级角色目录 | 🔴 | `SubmitCoordinationPlanTool.requireDelegable` |
| P2 | `activate_role` 工具与轻量态续接；协调者 prompt 注入角色目录；删除 L0 角色选择 | 🔴 | 新增 `ActivateRoleTool`、`BuiltinControlToolProfile`、`AssistantApplicationService`、`AssistantExecutionService.execute`、`PromptAssembler`、`AssistantInfrastructureAutoConfiguration` |
| P2 | `promote_to_task` 携带 `roleKey`、`shape` | 🔴 | `PromoteDirectTaskTool`、`TaskCommandService.promoteDirect` |
| P2 | `DECOMPOSE_REQUIRED` 作为进入 TASK 的硬门 | 🟡 | `TaskAnalysisPolicy`、`ExecutionIntent` 构造校验 |
| P3 | 循环内动态切换（选项 C），视 P2 实测决定 | 🔴 | `PerCallProfile`、`AgentScopeSpecCompiler`、`ToolGateway` |

P1 不改变授权边界，可以先做。P2 涉及权限变更和接口删除，按协作规范走完整派发并经人类审核。

## 待核实

- `activate_role` 续接时，前端 AG-UI 投影如何处理同一 Run 的两个 Execution，是否需要隐藏前一个的过程事件
- 同一 Run 中重新冻结画像，与 `executionProfiles` 的幂等键、重放守卫（`replaySettledExecution`）是否冲突
- 委派基线放宽后，现有 `requireDelegable` 的单元测试哪些需要改写，越权场景需要补哪些测试
- 角色较多时，角色目录注入协调者 prompt 的长度上限与截断策略

## More Information

- [ADR-008](ADR-008-task-execution-shape-and-streaming.md)：执行形态、"AUTO 路由的选择"、"DIRECT 与 TASK 的关系"。本 ADR 落实其中"DIRECT 路径本期保留 L0 选择，待轻量态协调者具备同等能力后再统一"，并修改其"不扩大授权"的委派基线
- [ADR-007](ADR-007-l0-model-invoker-unification.md)：L0 调用单栈化，P2 删除角色选择后少一个 L0 调用点
- [AAF-115 requirement](../../task/v0.13/AAF-115/requirement.md)：DIRECT 与 TASK 分流的历史背景，以及 promotion 边界
- AAF-117 tasks #11712：轻量态自动升级未完成项
- 代码依据：`DefaultRoleSelector`、`AssistantApplicationService.resolveExecutionProfile`/`effectiveRoute`、`SubmitCoordinationPlanTool.requireDelegable`、`TaskCommandService.promoteDirect`、`AgentScopeSpecCompiler`、`HarnessAgentExecutionAdapter`、`ContextLoadTool`
