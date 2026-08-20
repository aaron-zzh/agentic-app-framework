---
level: Practice
layer: Model
purpose: 定义指定角色与技能的任务式 Assistant 如何复用统一运行时并实现有界工具、产物、委派与审计
status: draft
version: 1.2.0
date: 2026-08-20
author: Kiro
tags:
  - Assistant
  - 任务执行
  - 工具治理
  - 多智能体
  - AG-UI
dependencies:
  - ../architecture.md
  - ./skill-progressive-loading-design.md
related:
  - ../team/team-tech.md
scope:
  includes:
    - 对话式与任务式 Assistant 的统一执行模型
    - 固定角色与技能路径的工具和权限计算
    - 任务产物、文档草稿与多智能体协调
    - 执行事件、决策审计与渐进落地路线
  excludes:
    - 具体页面视觉设计
    - 工具目录和 Skill 管理界面的 CRUD 设计
    - 自动化模板触发器的完整设计
    - AgentScope 内部实现细节
gains:
  - 能为固定角色与技能配置可验证的任务式执行策略
  - 能区分交互模式、用户控制模式和内部编排模式
  - 能计算基础工具与技能工具的最小权限交集
  - 能设计可恢复、可审计的文档产物和多智能体任务流
---

# 任务式 Assistant 统一执行路径设计

> 任务式 Assistant 不是另一套运行时，而是统一 Assistant 运行时上的一种受约束执行策略：路由更固定、工具更聚焦、交互更少、完成条件和产物更明确。

## 当前对外契约

Assistant 唯一正文启动入口是 `POST /api/agui/run`。请求必须使用当前用户拥有的既有 AI `Conversation.threadId`；`state.mode` 只能为 `CHAT`、`EXECUTION` 或 `TEAM`。`threadId` 同时是 `ConversationId` 与 `SessionId`，每轮独立 `runId` 同时是 `TaskId`、`ExecutionId` 与 `RunId`。`TEAM` 只能引用已发布的 Team version，不能由调用方覆盖冻结成员目标。

AG-UI 是唯一正文 SSE：内部 `MESSAGE_DELTA` 仅由 `AgUiProjector` 产生文本 delta。`AafAiTaskEvent`、Snapshot 和任务控制接口只公开状态、审计摘要与 cursor；不存在 Headless 或同步 REST 正文接口，也不保留旧路径或双协议。

## 执行流水线术语

任务式与对话式入口共享一条可循环、可恢复的智能任务流水线；文案只是固定 Role/Skill 的场景适配器，不拥有专用运行时。执行模块依次是：`TaskIngress/InputBuffer` 接收、幂等和合并输入；`Coordinator` 完成前注意、澄清、计划与 `ContextRequest`；L1 Cognition 按授权返回冻结的知识/记忆摘要或局部内容；Assistant 校验并冻结 Route、模型、工具、计划与聚合契约；Coordinator、Executor、Aggregator 在独立 Harness ReAct Loop 中执行；ToolGateway/HITL 处理动作授权；Assistant 验证、聚合、恢复并产生父终态；Learning Pipeline 只生成版本化候选。L0 Core 是 Coordinator、Executor 和学习评估共同使用的推理横切能力，L4 Team 仅用于独立 Assistant 的长期协作和仲裁。

`ClarificationPolicy` 只控制信息不足时的提问、默认值或失败策略；`ActionAuthorizationPolicy` 只控制缺少动作授权时请求 HITL、仅接受预授权或拒绝动作，二者不能互相替代。Coordinator 通过 L1 `ContextRequest` 获取 `SUMMARY_ONLY` 的受控上下文，不把知识库或记忆作为开放业务工具；Executor 仅获得本任务、依赖结果和预算允许的最小上下文。并行/串行通过 TaskBoard DAG 表达；循环使用有界 iteration group，不允许依赖图出现真实环。

## 设计目标与原则

### 问题背景

AAF 当前的 `CHAT`、`EXECUTION` 与 `TEAM` 都经唯一 AG-UI 入口进入同一条领域主链：

```text
入口适配器
  → AssistantCommand / AssistantInvocation
  → AssistantApplicationService
  → AgentExecutionPort
  → ToolGateway / TaskBoard / HITL
  → ai_task_event
```

三种运行模式的差异只存在于请求组装和冻结执行意图：CHAT 以多轮对话为中心，EXECUTION 以单轮任务为中心，TEAM 使用已发布 Team 的冻结成员目标。它们不产生第二个正文协议。固定 Skill 的文案生成暴露出两个问题：

- Skill 的工具访问模式没有完整进入运行时，空工具要求可能被误解为继承 Role 全部工具；
- 入口固定为 `READ_ONLY`，但执行画像先装入写工具再校验，导致模型开始前失败。

目标不是为文案、命令行或任务页创建专用 Agent，而是补齐统一执行契约，使同一 Assistant 可以安全支持对话式和任务式体验。

### 第一性原理

- **目标决定执行，不由界面决定执行**：聊天框、命令行和任务页只是入口；领域执行由目标、路由约束、授权和完成条件决定。
- **执行责任与过程形态分离**：SkillRoute `handlingMode` 冻结 `DIRECT/DELEGATE` 执行责任，主 Assistant 再根据复杂度选择 `AUTONOMOUS/PREDEFINED_WORKFLOW` 过程形态和 `NONE/SINGLE_AGENT/TASKBOARD` 协调形态；工作流不能绕过 `DELEGATE` 必须创建 Agent execution 的约束。
- **能力可见不等于动作获权**：工具进入模型上下文只表示可选择；每次调用仍须通过控制模式、任务授权、主体权限和参数策略。
- **默认行为必须结构化**：Role 与 Skill 可以声明默认保存、产物格式和委派策略，并注入系统提示；不能只靠自然语言提示词承担授权和一致性。
- **Agent Loop 决定开放过程，工作流定义固定过程**：协调者与 Agent 自主决定研究、生成、审校和工具调用顺序；步骤必须稳定、跨系统或包含高风险副作用时使用预定义工作流。

### 五个正交维度

| 维度 | 可选值 | 回答的问题 |
|---|---|---|
| 交互模式 | `CONVERSATIONAL` / `TASK` | 用户如何参与和查看过程 |
| 用户控制模式 | `READ_ONLY` / `COLLABORATIVE` / `DELEGATED` / `AUTOMATED` | 用户授予多大副作用与自主权 |
| 执行责任模式 | `DIRECT` / `DELEGATE` | 主 Assistant 直接负责，还是必须创建 L2 Agent execution |
| 过程模式 | `AUTONOMOUS` / `PREDEFINED_WORKFLOW` | 开放过程自主推进，还是由确定性骨架约束 |
| 协调模式 | `NONE` / `SINGLE_AGENT` / `TASKBOARD` | 不委派、委派一个 Agent，还是协调多个 Agent |

五者不能互相替代。`ownerMode` 来自已校验的 SkillRoute `handlingMode`，TaskAnalysis 只能在其约束内选择 `processMode` 和 `coordinationMode`。任务页可以运行 `READ_ONLY`，AG-UI 对话也可以发起委托任务；SSE、AG-UI 或非流式响应属于传输投影，不是权限参数。

### 目标体验

任务式体验对标命令行单次任务：提交一次，持续展示可审计状态，最终返回结果或最少量阻塞信息。

```text
提交任务 → 分析目标 → 冻结责任/过程/协调模式 → Agent Loop / Workflow → 验证 → 返回结果与产物
                                      └→ 仅硬阻塞时请求用户
```

默认不追问偏好类信息；可采用安全默认值并在结果中标注假设。只有缺少凭证、关键输入、不可替代授权，或即将执行不可逆动作时才进入 `AWAITING_INPUT` / `AWAITING_AUTHORIZATION`。

## 统一执行模型

### 统一执行意图

在现有 `AssistantCommand` 之上补充不可变的执行意图，不新增平行的任务运行时：

```text
ExecutionIntent
├─ interactionMode         CONVERSATIONAL | TASK
├─ routeConstraint         AUTO | FIXED(roleKey, skillKey)
├─ clarificationPolicy     INTERACTIVE | MINIMAL | FAIL_ON_BLOCKER
├─ artifactPolicy          DEFAULT | RETURN_ONLY | AUTO_SAVE_DRAFT | UPDATE_DRAFT
├─ delegationPolicy        NONE | BOUNDED | CONTRACT_DRIVEN
├─ baseToolProfile         基础工具档案版本
├─ requestToolCeiling      调用方只可进一步收窄的工具上限
└─ completionContract      输出、产物和完成证据
```

`ExecutionIntent` 描述本次任务如何执行；`AssistantDefinition` 仍是能力上限和稳定配置；`ExecutionContract` 继续承载 `DELEGATED` 的预算、期限、动作、重试、通知和接管规则。`DEFAULT` 表示由用户显式意图、TaskProfile、Skill 和 Role 的结构化策略按优先级解析，不表示无条件保存。

调用方可以请求任务意图，但服务端必须根据 Assistant 支持模式、入口策略、主体权限和风险策略解析最终控制模式。客户端不能直接获得任意提权能力。

### 协调者任务分析

每个任务都必须形成可审计的 `TaskAnalysis`，但不等于每次都额外调用大模型。普通问候和简单回复由前注意结果确定性生成最小分析快照；边界清晰的单一任务可由规则与 Schema 组合；只有开放复杂任务才由主 Assistant 结合 Role、Skill、用户提示词、附件和受控上下文生成完整结构。固定 Skill 只固定能力方向，不预判任务复杂度。

```json
{
  "taskType": "CONTENT_CREATION",
  "complexity": "OPEN_COMPLEX",
  "executionPlan": {
    "ownerMode": "DELEGATE",
    "processMode": "AUTONOMOUS",
    "coordinationMode": "TASKBOARD"
  },
  "route": {
    "roleKey": "system.role.content-creator",
    "skillKey": "ip-position"
  },
  "artifacts": [
    {
      "kind": "DOCUMENT",
      "format": "MARKDOWN",
      "persistence": "DEFAULT"
    }
  ],
  "requiredCapabilities": ["web.search", "content.generate"],
  "delegationCandidates": ["research", "content-review"],
  "workflow": null,
  "assumptions": [],
  "confidence": 0.91
}
```

该 JSON 使用受约束输出 Schema。后端先把 SkillRoute `handlingMode` 冻结为 `ownerMode`，再校验 `processMode`、`coordinationMode`、工具能力、workflow key/version 和节点责任。`DIRECT + AUTONOMOUS + NONE` 可展示为 Assistant direct response；`DELEGATE + AUTONOMOUS + SINGLE_AGENT/TASKBOARD` 可展示为单 Agent/协调式多 Agent；`PREDEFINED_WORKFLOW` 只是过程形态，仍必须保留 ownerMode，并保证 `DELEGATE` 路径至少创建一个 Agent execution。分析结果不直接授予工具或写权限，非法结果不得靠猜测修复后继续。

### 任务类型

| 类别 | 任务类型 | 典型处理 |
|---|---|---|
| 交互 | `CONVERSATION_REPLY` | 普通问答，只产生消息，不默认保存 |
| 内容 | `CONTENT_CREATION` / `CONTENT_TRANSFORMATION` | 生成、改写文案，默认产生文档草稿 |
| 多模态 | `MEDIA_GENERATION` | 生成图像、音频、视频，默认登记 AIGC 素材 |
| 推理 | `RESEARCH_ANALYSIS` / `OPEN_COMPLEX` | 协调者按需检索、拆分、审校和聚合 |
| 编排 | `WORKFLOW_TASK` | 进入已发布工作流，节点内仍可使用 Agent Loop |

任务类型是本次分析结果，不等同于 Skill 分类。同一个 `ip-position` Skill 在简短标题生成时可能是直接任务，在竞品研究、平台策略和半年规划组合目标下可能是开放复杂任务。

### 两种交互模式的差异

| 关注点 | 对话式 Assistant | 任务式 Assistant |
|---|---|---|
| Route | 默认自动选择 Role + Skill，可随新消息重新路由 | 通常固定 Role + Skill；非法组合直接拒绝 |
| Complexity | 协调者逐轮判断 | 协调者在任务开始分析并冻结初始策略，可审计调整 |
| Identity | `conversationId` 及主 Assistant `sessionId` 在会话内稳定 | 创建独立 `taskId`；委派 Agent 使用独立执行槽位，恢复是否复用 `sessionId` 由执行谱系决定，不强制新建 conversation |
| Clarification | 可以自然追问 | 仅阻塞条件追问，其余使用安全默认值 |
| Completion | 回复一轮即可结束本轮 | 必须通过结果、产物和完成证据验证 |
| UI | 消息、工具卡片、输入框 | 状态、步骤、产物、最终结果，输入框可隐藏 |

两种交互模式必须使用相同的 `AssistantApplicationService`、执行画像、ToolGateway、TaskBoard、CompletionValidator 和 `ai_task_event`。入口只负责转换交互上下文，出口只向 AG-UI 投影正文，或向任务页投影无正文状态、审计与 cursor。身份关系固定为 `ConversationId = SessionId = threadId` 与 `TaskId = ExecutionId = RunId = runId`；恢复和 fencing 只在这一持久化任务模型内进行。

### 固定角色、技能与协调者

#### 文案 Skill 族合同

当前八个内置文案 Skill 以 `copywriting` category 作为共同能力族标记，不新增平行的文案运行时或 `SkillFamily` 领域对象。它们共享以下结构化执行合同：

```text
Role                    system.role.content-creator
Interaction / Route     TASK + FIXED(roleKey, skillKey)
handlingMode            DELEGATE
processMode             默认 AUTONOMOUS；确定性过程可显式选择 PREDEFINED_WORKFLOW
coordination            TaskBoard.coordinated + Coordinator + 1..8 Executor
outputKind               DOCUMENT
canonicalMediaType       text/markdown
defaultPersistence       AUTO_SAVE_DRAFT；显式 RETURN_ONLY 优先
publishPolicy            NEVER_BY_DEFAULT
```

共同合同由 category、Role、`ExecutionIntent`、SkillVersion 和任务授权共同表达，不靠 Role Prompt 或页面常量成为权威。各 Skill 的差异只进入其不可变 SkillVersion：执行正文、输入/输出 Schema、输出契约、专属工具及用途、模型能力要求、references、知识绑定和声明式完成证据。通用 `CompletionValidator` 根据这些约束与实际事件验证结果；只有无法声明式表达的稳定领域规则才允许注册专用验证逻辑，不能为每个文案 Skill 默认创建一套 Java Validator。

Skill 可以声明目标平台及交付约束，但平台导出器是内容服务层的确定性适配能力，不属于 Skill Prompt、Assistant 核心运行时或五层智能组件。模型不能通过选择导出格式改变规范正文、扩大工具权限或获得发布权。

#### 文案任务的强制两阶段编排

`TASK + FIXED + copywriting` 不是单个动态 Agent 的快捷路径。每次执行必须由 AAF 管理的 `TaskBoard` 创建并调度至少两个独立 Harness Agent：先运行 `COORDINATOR`，再运行至少一个 `EXECUTOR`。`COORDINATOR` 只执行前注意、阻塞项识别、目标澄清、任务拆分和模型策略提议，输出严格 `CoordinationPlan`，不继承业务工具、连接器、文件或 Shell 能力；`Assistant` 校验计划、Role、Skill、模型、预算和依赖图，并冻结计划后才创建 `EXECUTOR`。

```text
TASK/FIXED/copywriting
→ Coordinator HarnessAgent（AUTO，受限规划上下文，无业务工具）
→ 严格 CoordinationPlan 解码与 Assistant 校验
→ TaskBoard 原子冻结 coordinator → executor DAG
→ Executor HarnessAgent × N（每个节点独立冻结 Role/Skill/Model/Profile）
→ Assistant 聚合完成证据、产物与终态
```

`AgentScopeSpecCompiler` 必须继续关闭 native subagent 与 dynamic subagent。Harness 仅承担单个 Agent Loop；任务创建、取消、恢复、权限、预算和事件谱系完全由 AAF 的 `DelegatedTask`、`TaskBoard`、ToolGateway、HITL 与 `ai_task_event` 管理。`ExecutionMode.DELEGATE` 仅表示动态 Harness 编译，不能被解释为已完成多 Agent 委派。

协调者可为执行节点提议 `AUTO`，或在根请求是 `EXPLICIT` 时保留该同一显式模型；它不得任意指定其他 explicit model。当前 copywriting `CoordinationPlan` 允许 1..8 个 `EXECUTOR`：不可拆分目标使用一个执行者与 `PASS_THROUGH`，可拆分目标可使用多个执行者与 `ORDERED_CONCAT` 或 `COORDINATOR_REDUCE`。`AggregationContract` 必须且只能覆盖全部结果执行者，`PASS_THROUGH` 仅允许一个执行者；TaskBoard 聚合后由父任务形成唯一最终结果与终态。Assistant 对每个节点独立调用模型路由并把最终 `ModelSpec` 冻结到该节点的 `ExecutionProfileSnapshot`。计划策略保存在 TaskBoard，具体解析后的模型保存于该 execution 画像；协调者、执行者和重试节点不能复用父节点的画像。

`FIXED(roleKey, skillKey)` 表示调用方已经确定主能力方向，不再让通用助理替换主 Role 或主 Skill：

```text
验证 Assistant 包含 Role、Skill 与 handlingMode
→ 主 Assistant 生成或补全 TaskAnalysis
→ 后端校验并冻结主 Route
→ 编译模型、上下文、工具和授权
→ 冻结 ownerMode，再选择 processMode 与 coordinationMode
→ Workflow 另行校验 key/version、节点责任和 DELEGATE Agent execution
```

复杂度与执行计划仍由主 Assistant 判断，但不得突破冻结的 `ownerMode`。一般 Assistant 运行时中，`DIRECT` 可以采用自主过程或启动确定性 Workflow；`DELEGATE` 无论自主还是 Workflow 都至少创建一个任务级 Agent execution。当前 `TASK + FIXED + copywriting` 是明确例外：入口校验固定文案 Skill，并始终创建 `TaskBoard.coordinated`；简单文案使用 Coordinator + 一个 Executor，可拆分文案使用 Coordinator + 1..8 个 Executor。`CHAT` 的简单回复才使用 `TaskBoard.single`。主 Assistant 可以在白名单内继续委派研究、审校、文档组装等子任务，但不得替换主目标、切换到未授权 Role，或扩大工具和资源范围。

### 统一事件投影

内部执行只追加 `ExecutionEvent`，再安全映射为公共状态与审计契约 `AafAiTaskEvent`。AG-UI 是唯一正文投影；任务页只消费无正文状态、审计和 cursor，不在领域层拼装协议事件：

| 内部 `ExecutionEvent` | 公共事件 | AG-UI 正文投影 | 任务状态/Snapshot |
|---|---|---|---|
| `EXECUTION_STARTED` | `execution.started` | `RUN_STARTED` | 完整安全信封 |
| `TASK_ANALYZED` / `TASK_STATUS_CHANGED` | `task.analyzed` / `task.status.changed` | `STATE_DELTA` / STEP | 策略与进度 |
| `MESSAGE_STARTED` / `MESSAGE_DELTA` / `MESSAGE_COMPLETED` | `output.started` / `output.delta` / `output.completed` | TEXT MESSAGE START/CONTENT/END | 正文不公开 |
| `TOOL_CALL_*` | `tool.*` | TOOL CALL | 工具状态和安全摘要 |
| `AUTHORIZATION_*` | `authorization.*` | `INTERRUPT` / `aaf.*` CUSTOM | 待用户处理 |
| `SUBTASK_*` | `subtask.*` | STEP / `aaf.*` CUSTOM | 子任务 DAG 状态 |
| `ARTIFACT_*` | `artifact.*` | `STATE_DELTA` / `aaf.*` CUSTOM | 产物引用和状态 |
| `EXECUTION_COMPLETED` / `FAILED` | `execution.completed` / `execution.failed` | `RUN_FINISHED` / `RUN_ERROR` | 规范终态 |

`ExecutionEvent/ai_task_event` 是内部执行事实源，`AafAiTaskEvent` 是无正文的公共状态、审计与 cursor 契约，不形成第二条持久化时间线。任务式 UI 可以隐藏对话输入和消息气泡，但不能绕过公共事件或直接消费模型私有事件。

## 能力与权限治理

### 工具分层

任务式路径允许使用工具，但工具必须按用途分层：

| 层 | 示例 | 默认风险 |
|---|---|---|
| 基础只读工具 | Web 搜索、受控网页读取、知识检索、计算、时间 | 低 |
| Skill 专属工具 | 行业数据查询、格式转换、内容审校 | 由目录定义 |
| 产物工具 | 创建草稿、更新草稿版本、登记 AIGC 素材 | 可撤销写 |
| 外部动作工具 | 发布、发送、支付、删除、修改外部系统 | 高或不可逆 |

基础工具不是“所有通用工具”。`BaseToolProfile` 必须版本化，只包含无副作用且参数受控的能力。网络工具需要域名策略、SSRF 防护、响应大小和超时限制，并把外部内容视为不可信数据。

### 有效工具公式

固定 Skill 的候选工具是基础工具与 Skill 工具的并集，再逐层取交集：

```text
CandidateTools = BaseToolProfile ∪ SkillAllowedTools

EffectiveTools = CandidateTools
  ∩ RoleCapabilityCeiling
  ∩ AssistantToolPolicy
  ∩ AgentDeclaredTools
  ∩ RequestToolCeiling
  ∩ ExecutionContract.allowedActions（存在时）
  ∩ SubjectAndTenantPolicy
  ∩ ControlModePolicy
```

Skill 工具模式必须保留到运行时，不能再用裸空集合同时表达“禁止全部”和“未限制”：

```text
RESTRICT + 空集合       → 仅基础工具
RESTRICT + 声明工具     → 基础工具 + 声明工具
INHERIT                 → 基础工具 + Role 业务工具
```

`INHERIT` 只适用于经过审核的系统 Skill；用户 Skill 默认 `RESTRICT`。工具要求还要区分 required 与 optional：required 被任一层排除时在执行前明确失败，optional 被排除时只记录决策，不阻断执行。

### 默认保存策略与优先级

内容创作 Role 应声明结构化默认产物策略，并把同义说明编译进协调者和执行 Agent 的系统提示词：

```text
ContentCreatorArtifactDefaults
├─ CONVERSATION_REPLY      RETURN_ONLY
├─ DOCUMENT                AUTO_SAVE_DRAFT
├─ IMAGE                   AUTO_REGISTER_ASSET
├─ AUDIO / VIDEO           AUTO_REGISTER_ASSET
└─ publish                 NEVER_BY_DEFAULT
```

策略解析优先级从高到低为：

```text
平台与租户硬策略
→ 用户本次显式要求（包括“不保存、仅返回内容”）
→ TaskProfile 或已发布工作流策略
→ SkillVersion 产物策略
→ Role 默认产物策略
→ RETURN_ONLY 安全兜底
```

Role 默认只决定“应尝试保存何种产物”，不直接授予写权限。用户进入内容创作任务或启用内容角色时，产品契约可以预先授予任务级、可撤销、短时的草稿与素材登记权限；发布、发送、删除等动作仍需独立授权。

“不保存”优先使用结构化 `artifactPolicy=RETURN_ONLY`。自然语言中的“仅返回文案、不要保存”由协调者写入 `TaskAnalysis.artifacts[*].persistence` 并审计；不建议把隐藏标记或拼接字符串作为公开协议。受控系统上下文可把页面选项注入提示词，但后端仍以结构化字段为准。

### 控制模式与有限提权

| 场景 | 模式 | 行为 |
|---|---|---|
| 搜索、分析、普通回复 | `READ_ONLY` | 自动执行基础只读能力，不保存业务产物 |
| 内容创作并保存草稿/素材 | `COLLABORATIVE` | 使用任务级可撤销窄授权，默认少交互 |
| 后台多智能体长任务 | `DELEGATED` | 必须携带完整 ExecutionContract |
| 定时重复执行 | `AUTOMATED` | 仅由已审核自动化模板进入 |

内容创作任务的预授权示例：

```text
actions: content.draft.upsert, aigc.asset.register
resources: artifact:task/{taskId}/*
scope: TASK
reversible: true
expiresAt: 任务截止时间
```

模型、Skill 和前端不能签发授权；只能在已有授权内决定是否调用工具。工具调用仍须经过 `DefaultToolGateway` 的可见性、策略、grant、参数、lease、幂等 receipt 和预算门禁。

### Agent Loop 与代码边界

开放任务的过程由角色和 Skill 驱动的 Agent Loop 控制：协调者分析目标、选择执行单元，Agent 自主决定检索、生成、审校及产物工具的调用时机。系统代码只实现不可协商的机制：Schema 校验、能力交集、授权、幂等、预算、状态迁移、事件持久化和完成验证。

禁止在文案页面后端硬编码“生成完成后必调 createDocument”的专用流水线。默认保存通过 Role/Skill 策略影响协调者决策，并由 Agent Loop 显式调用 `content.draft.upsert`；调用仍是可观察、可拒绝、可恢复的工具动作。

工作流用于步骤、分支或副作用顺序必须确定的任务，但它只定义过程，不接管智能层责任：

| 执行单元 | 负责 | 禁止 |
|---|---|---|
| Assistant | 路由、执行计划、授权请求、委派、聚合、最终验证 | 直接写业务状态或绕过 ToolGateway |
| Core | 理解、推理和生成 | 持有任务状态或执行副作用 |
| Agent | 在受托任务中规划、执行动作并检查结果 | 扩大 Role、grant 或工具边界 |
| Workflow | 控制确定性步骤、分支、等待和补偿顺序 | 以 Service 节点绕过执行责任与授权 |
| ToolGateway | 校验策略、grant、参数、幂等、lease、receipt 和预算 | 决定用户目标或执行计划 |

`DIRECT` 只允许产生无副作用输出。需要业务写动作时，必须进入 Agent execution，或进入由授权 Agent/系统动作执行器经 ToolGateway 执行的 Workflow action node；Assistant Controller 和 Workflow Service 都不能直接落业务副作用。

```text
开放目标       → AUTONOMOUS + Agent Loop
稳定复杂过程   → PREDEFINED_WORKFLOW
混合过程       → Workflow 骨架 + 节点内 Agent Loop
```

## 任务编排与产物

### 内容格式与控制格式

“Markdown 还是 JSON”不是二选一：

- 文案规范正文默认使用 Markdown；内部 `MESSAGE_*` 经公共 `output.*` 流式展示，草稿以 `text/markdown` 保存；
- 协调决策、工具参数、产物引用和最终状态使用 JSON Schema；
- 复杂富文档需要块结构时，Skill 可声明结构化 Document DSL，由后端校验后渲染或保存；
- 图像、音频和视频返回 `mediaVersionId`、文件引用和安全元数据，不把二进制放入事件；
- 平台纯文本、富文本或其他交付格式由版本化平台导出器从规范产物确定性生成，不反向修改 Markdown 真理源，也不自动发布。

前端不得从 Markdown 中截取模型拼接的 JSON。Agent 调用产物或导出工具时提交结构化参数，ToolGateway 返回结构化结果；内部 `ARTIFACT_*` 先映射为公共 `artifact.*`，协议适配器不能把内部枚举暴露给前端。平台导出如果只是无副作用格式转换，可作为内容服务能力被调用；涉及发布、发送或外部写入时，必须使用独立动作、授权和审计。

### 文案 TaskBoard 与 Agent Loop

文案创作步骤可以自主推进，但 `TASK + FIXED + copywriting` 的 Coordinator 阶段和 coordinated TaskBoard 是固定运行时边界，不再按简单或复杂绕过：

```text
Assistant 校验固定 copywriting Route 与 ExecutionIntent
→ TaskBoard.coordinated 创建 Coordinator 节点
→ Coordinator 形成 1..8 Executor 的 CoordinationPlan 与 AggregationContract
→ Assistant 校验并冻结每个节点的 Role / Skill / Model / Profile / Budget
→ Executor 在各自 Harness Agent Loop 中按 Skill 检索、生成、审校和产生产物证据
→ PASS_THROUGH / ORDERED_CONCAT / COORDINATOR_REDUCE 收敛全部结果 Executor
→ 需要保存时由获权执行体经 ToolGateway 调用 content.draft.upsert
→ AG-UI 投影唯一正文，任务事件仅投影状态、审计和 cursor
→ CompletionValidator 验证输出、产物、工具和完成证据
```

简单文案由 Coordinator 规划一个内容 Executor，并使用 `PASS_THROUGH`；可拆分文案规划多个 Executor，并使用 `ORDERED_CONCAT` 或 `COORDINATOR_REDUCE`。系统不固定增加“文档 Agent”：保存数据库记录是产物工具职责，只有多章节组装、引用编排、模板套用或独立审校确有必要时，Coordinator 才在 1..8 上限内增加对应 Executor。若法规、审批、副作用顺序或跨系统补偿要求确定性骨架，可使用 `PREDEFINED_WORKFLOW`，但 Workflow 不能取代 Coordinator、TaskBoard、节点执行画像或 ToolGateway。

### 产物创建时序

协调者可在授权范围内选择两种时序，并记录原因：

| 时序 | 适用场景 | 流程 |
|---|---|---|
| 内容优先 | 短文案、单次生成 | 流式生成 → 验证 → Agent 调用草稿工具一次性保存 |
| Reservation 优先 | 长任务、协同编辑、断线恢复 | 预留 artifact → 流式生成与检查点 → 提交 DRAFT |

默认短文案采用内容优先，避免空文档。Reservation 只创建任务级产物或不可见 `GENERATING` 草稿，不在普通文档列表暴露。生成期间不逐 token 更新数据库；前端消费流式内容，后端按段落或时间窗口保存恢复检查点。

### 产物工具与返回

建议以通用产物动作替换当前直接创建正式文档的 `createDocument`：

```text
artifact.reserve
content.draft.upsert
artifact.checkpoint
artifact.commit
artifact.fail
```

Agent 工具调用的参数是结构化 JSON，正文可作为 Markdown 字段或受控 artifact buffer 引用；工具结果返回产物标识、版本、状态和摘要哈希。前端只处理公共安全信封：

```json
{
  "specVersion": "aaf.ai-task-event/1.0",
  "eventId": "evt-01J...",
  "eventType": "artifact.committed",
  "eventVersion": 1,
  "category": "ARTIFACT",
  "occurredAt": "2026-08-18T09:00:00Z",
  "delivery": "DURABLE_FACT",
  "cursor": "opaque-task-cursor",
  "task": {
    "taskId": "task-001",
    "executionId": "exec-001",
    "runId": "run-001",
    "sessionId": "session-content-task-001",
    "executionSequence": 22,
    "eventOffset": 45
  },
  "state": {
    "status": "RUNNING",
    "controlMode": "COLLABORATIVE"
  },
  "actor": {
    "type": "AGENT",
    "assistantId": "system.assistant.default-user",
    "agentId": "content-writer"
  },
  "trace": {
    "correlationId": "corr-001",
    "causationId": "evt-tool-completed"
  },
  "dataContentType": "application/json",
  "dataSchema": "urn:aaf:schema:artifact-committed:1",
  "data": {
    "artifactId": "artifact-001",
    "kind": "DOCUMENT",
    "state": "DRAFT",
    "documentId": 123,
    "documentVersionId": 456,
    "mediaType": "text/markdown",
    "operation": "CREATED"
  }
}
```

内部正文事件按 `MESSAGE_STARTED → MESSAGE_DELTA* → MESSAGE_COMPLETED` 形成完整序列，并映射为公共 `output.started → output.delta* → output.completed`；正文不在 artifact 事件中重复。断线重连或提交成功后，前端根据文档版本接口取得权威正文。自动保存由授权执行体经 ToolGateway 落库；`RETURN_ONLY` 模式才由用户后续点击保存并发起独立命令。

### 协调式复杂任务与工作流

复杂度由协调者判断，但下列规则可强制选择工作流：已指定 workflowKey、法规或审批要求固定步骤、不可逆动作存在固定顺序、跨系统补偿协议、或产品要求可重复输出。其余开放复杂任务使用有界协调：

- 协调者创建 TaskBoard，声明子任务目标、依赖、Role/Skill、预期证据和预算；
- 子任务在白名单内使用独立 Agent Loop，并独立计算工具交集；
- 主任务授权只能向下收窄，不能被子 Agent 放大；
- 主 Assistant 聚合冲突、决定是否提交产物并承担最终完成判断；具体产物动作由当前 Agent 执行体通过 ToolGateway 实施。

TaskBoard 的 PostgreSQL 状态是当前编排状态的业务真理源；`ai_task_event` 追加记录其执行变化，AgentState 只保存各 Agent 的可恢复工作态。预定义 Workflow 是稳定骨架，节点内部仍可调用相同 Assistant/Agent 执行能力。

上述协调属于 L3 Assistant 对 L2 Agent 的有界委派。若协作者需要独立 Persona、长期责任、跨助理目标对齐或冲突仲裁，应升级为 L4 Team，由多个 Assistant 协作；不能把 Team 简化为 TaskBoard 中的普通子 Agent。

### 完成门禁

任务完成不能等同于模型停止输出。CompletionValidator 至少检查：

- `TaskAnalysis.executionPlan` 与实际 owner/process/coordination 模式、Role、Skill 是否一致；
- 输出契约是否满足，最终文本或媒体是否形成；
- required 工具和子任务是否完成；
- 默认或显式要求的草稿、素材是否已保存；
- 引用、安全检查、授权和版本一致性是否通过。

验证结果只能是完成、继续修复、需要用户、失败或转人工，并写入任务状态和恢复点。

## 事件审计与落地

通用状态和审计事件只承载安全摘要与 cursor；正文边界以本章“当前对外契约”为准。历史的多协议事件草案不构成实现合同。本章只定义任务式 Assistant 需要补充的审计节点，禁止在 Assistant Controller 或 Workflow Service 中建立平行事件语义。

### 任务专属审计节点

以下节点必须形成 `ai_task_event`，并由规范事件 mapper 产生 `DURABLE_FACT`：

| 控制节点 | 记录内容 |
|---|---|
| 请求与身份解析 | 入口、主体、租户、TaskProfile、幂等键 |
| 任务分析 | taskType、complexity、执行策略、产物意图、置信度 |
| 路由与画像冻结 | Role、SkillVersion、模型、基础工具档案、最终工具摘要 |
| 控制模式与授权 | 策略优先级命中项、任务 grant、拒绝或降级原因 |
| 规划与委派 | Agent Loop / Workflow、TaskBoard 版本、子任务与预算 |
| 工具与产物 | 工具状态、receipt、artifact 状态和版本引用 |
| 验证与终态 | 完成证据、验证结果、恢复点、最终责任主体 |

建议在现有 `ExecutionEventType` 基础上补齐 `TASK_ANALYZED`、`EXECUTION_PROFILE_FROZEN`、`PLAN_CREATED`、`PLAN_REVISED`、`ARTIFACT_RESERVED`、`ARTIFACT_CHECKPOINTED`、`ARTIFACT_COMMITTED`、`ARTIFACT_FAILED` 和 `DECISION_RECORDED`。

内部事件由统一 mapper 规范化为 `task.analyzed`、`plan.created`、`artifact.committed` 等公开类型；不得把内部枚举名直接当 AG-UI SSE event name。应用 DEBUG 日志只用于运维诊断，不能依赖日志重建任务状态。

### 决策记录

`DECISION_RECORDED` 只记录可审计摘要，不保存思维链：

```json
{
  "decisionType": "EXECUTION_STRATEGY",
  "selected": {
    "ownerMode": "DELEGATE",
    "processMode": "AUTONOMOUS",
    "coordinationMode": "TASKBOARD"
  },
  "alternatives": ["DELEGATE+AUTONOMOUS+SINGLE_AGENT", "DELEGATE+PREDEFINED_WORKFLOW+TASKBOARD"],
  "reasonSummary": "任务开放且可并行拆分",
  "confidence": 0.84,
  "policyVersion": "task-analysis.v1"
}
```

同样适用于工具授权、产物策略、角色选择、工作流选择和接管决定。独立 decision audit 表保存结构化详情时，`ai_task_event` 只记录“决策已发生”和引用，避免形成第二条执行时间线。

### 落地顺序

- 先实现安全 `AafAiTaskEvent` 与事件 Schema 注册表；
- 再用统一 mapper 取代 `AssistantExecutionEventVO` 的窄投影；
- 以同一个 `AgUiProjector` 取代 `AssistantAguiController` 的 raw event 输出和 `WorkflowAgUiService` 的独立工厂；
- 最后接入 snapshot、`Last-Event-ID` 与无正文状态/审计事件的恢复投影。

迁移期间直接替换旧语义，不保留双事件协议或兼容 shim，不为文案页创建专用运行时，也不把默认保存硬编码成页面后处理。

### 验收基线

- 固定 Role + Skill 后由协调者按本次目标判断复杂度和执行策略；
- 普通回复、复杂内容任务、子 Agent 委派和产物保存使用同一规范事件流；
- 已持久化事件按 eventOffset 进行 task replay、按 sequence 进行 execution replay，客户端以 eventId 幂等；
- AG-UI 使用标准事件或 `aaf.*` CUSTOM；任务事件、Snapshot 与查询接口不返回正文；
- terminal event 先于流结束，`[DONE]` 和心跳不承担业务终态语义；
- Agent Loop 自主决定开放过程，固定、高风险或跨系统过程进入预定义 Workflow；
- 用户能看到执行摘要、工具调用、产物、子任务和验证结果；
- 审计方能还原画像、工具、授权、委派、产物和终态，且任何视图都不泄露凭证、系统提示或思维链；
- 失败、重试、恢复和多副本切换不产生重复文档或重复外部副作用。
