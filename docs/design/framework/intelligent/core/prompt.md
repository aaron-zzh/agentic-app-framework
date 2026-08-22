---
level: Practice
layer: Model
purpose: 定义所有 Prompt 型模型调用的 PromptEnvelope、分层信任边界、任务循环与非自主 L0 调用画像
status: draft
version: 1.0.0
date: 2026-08-22
author: AaronZZH & Kiro
related:
  - ../architecture.md
  - ../assistant/task-oriented-assistant-execution-design.md
  - ../../engine/content/prompt.md
scope:
  includes:
    - 自主 Harness Agent 的分层 System Prompt
    - 非自主 L0 模型调用的最小函数合同
    - PromptEnvelope 数据结构、冻结时点和审计边界
    - PRIMARY、COORDINATOR、EXECUTOR、AGGREGATOR 的任务循环
  excludes:
    - Prompt 资产 CRUD、评估页面与版本发布接口
    - 权限、HITL、预算和状态机的具体实现
    - Embedding 与原生 Rerank API 的请求协议
---

# PromptEnvelope 与模型调用装配设计

> Prompt 不是一个可任意追加的字符串。AAF 在每次 Prompt 型模型调用前生成带来源、优先级、信任边界、版本和摘要的 `PromptEnvelope`；权限、HITL、预算、工具可见性和状态转换仍由 Prompt 外部的确定性代码保证。

## 术语边界

AAF 智能架构只有 **L0–L4 五层**。本文的 **P0–P8 是 Prompt 治理优先级**，不是新的智能层级：

| 术语 | 含义 |
|---|---|
| L0–L4 | Core、Cognition、Agent、Assistant、Team 五层智能架构 |
| P0–P8 | 一次自主 Harness 调用中的治理和输入优先级 |
| 自主 Harness 调用 | PRIMARY、COORDINATOR、EXECUTOR、AGGREGATOR 等持有有界任务循环的 Agent 调用 |
| 非自主 L0 调用 | 选择、分类、抽取、摘要、重排、Judge 等单一函数式模型调用；不持有任务责任和循环状态 |
| 逻辑调用 | 一次业务语义上的模型请求，可包含传输重试或模型路由尝试 |
| 物理调用 | 实际发送给某一精确模型的一次请求；每次都生成独立 Envelope |

`PromptEnvelope` 适用于 Chat/Completion 等 Prompt 型调用。Embedding 和提供商原生 Rerank API 不应伪装成消息 Prompt；它们使用同一调用谱系与审计原则，但采用各自的模型请求信封。

## 分层优先级

### 自主 Harness 画像

| 优先级 | 内容 | 是否进入模型输入 | 规则 |
|---|---|---|---|
| P0 | 代码硬治理 | 否 | 权限、HITL、预算、状态机、Schema 校验、工具集合和模型选择由代码执行，只在 Envelope 中保存决策引用与摘要 |
| P1 | Harness Constitution | 是 | 所有自主 Harness Agent 共用的版本化执行宪章 |
| P2 | Execution Identity Contract | 是 | PRIMARY、COORDINATOR、EXECUTOR、AGGREGATOR 的职责与专属任务循环 |
| P3 | Assistant Delivery Contract | 是 | Assistant 的使命、交付物、成功标准和最终责任；使用结构化合同，不增加自由 Prompt 字段 |
| P4 | Role Contract | 是 | 当前 Role 的职责、非职责和能力上限 |
| P5 | Frozen Execution Contract | 是 | 经 Assistant 校验并冻结的目标、范围、计划、完成条件、聚合和停止条件 |
| P6 | Activated Skills | 是 | 只包含最终激活 SkillVersion 的执行方法；SYSTEM → ASSISTANT → ROLE 稳定排序 |
| P7 | Persona Profile | 按画像 | 稳定身份与表达风格；不得扩权或改写任务，严格结构化调用默认关闭表达风格 |
| P8 | Context and Data | 作为消息 | 历史、当前用户消息、受控上下文、知识、记忆、附件和工具结果，始终按数据处理 |

P0 不是 Prompt 文本。把 P0 规则复制进 System Prompt 只能帮助模型理解边界，不能把模型服从性变成安全边界。

### 非自主 L0 画像

非自主 L0 调用不注入 Harness Constitution、Assistant Delivery、Role、ActivatedSkill、Persona Profile 或自主任务循环，只动态装配完成单一函数所需的最小输入：

```text
版本化 Function Contract
+ 严格 Output Contract / JSON Schema
+ 受控候选或业务数据
+ 当前待处理输入
+ 显式允许的 Tool Definitions（默认无）
```

`Function Contract` 只定义一个动作，例如“从授权候选中选择一个 Role”或“把不可信历史压缩成指定 JSON”。它不得承担多步任务规划、工具探索、用户沟通或最终交付责任。

## PromptEnvelope 数据结构

目标结构沿用现有 `CompiledSystemPrompt`、`ExecutionProfileSnapshot` 和 `AgentMessage`，不新建并行 Prompt 真理源：

```java
public record PromptEnvelope(
    String specVersion,
    String envelopeId,
    InvocationDescriptor invocation,
    ModelRequestSnapshot model,
    CompiledSystemPrompt systemPrompt,
    List<PromptMessageSnapshot> messages,
    List<ToolDefinitionSnapshot> tools,
    OutputContractSnapshot outputContract,
    TokenBudgetSnapshot tokenBudget,
    GovernanceSnapshot governance,
    PromptAuditSnapshot audit,
    Instant createdAt
) {}

public record InvocationDescriptor(
    String logicalInvocationId,
    String parentInvocationId,
    int attemptNo,
    InvocationMode mode,
    InvocationPurpose purpose,
    String functionKey,
    AgentKind agentKind,
    String tenantId,
    String taskId,
    String executionId,
    String nodeId
) {}

public enum InvocationMode {
    AUTONOMOUS_HARNESS,
    NON_AUTONOMOUS_L0
}
```

字段约束：

| 字段 | 约束 |
|---|---|
| `logicalInvocationId` | 同一语义调用的传输重试共享；不同模型尝试仍以 `attemptNo` 区分 |
| `parentInvocationId` | 摘要、选择、Judge 等子调用关联父调用；没有父级时为空 |
| `model` | 保存精确 `ModelSpec`、参数和提供商能力，不只保存路由场景名 |
| `systemPrompt` | 自主调用使用分层 `CompiledSystemPrompt`；非自主调用只编译 Function/Output Contract |
| `messages` | 保存真实发送顺序、角色、来源、信任级别、附件引用和每条摘要 |
| `tools` | 保存实际发送给模型的函数定义及 Schema hash；工具可见不等于动作获权 |
| `outputContract` | 文本、JSON Schema 或其他严格输出合同；解析和候选越界由代码校验 |
| `governance` | 只保存 P0 决策引用、规则版本和摘要，不把权限文本渲染给模型作为授权 |
| `audit` | 保存 priority scheme、canonical hash、各片段 hash 和受限快照引用 |

完整 Prompt 正文、用户数据和工具结果只能进入受限 Prompt 快照存储，不得进入公共任务事件、普通日志或 AG-UI 状态投影。

### System Prompt 片段

现有 `CompiledSystemPrompt.PromptLayerSnapshot` 目标态扩展为：

```java
public record PromptSectionSnapshot(
    int ordinal,
    PromptPriority priority,
    PromptSectionKind kind,
    PromptSourceKind sourceKind,
    String sourceKey,
    String sourceVersion,
    TrustLevel trustLevel,
    boolean required,
    String content,
    String contentSha256
) {}
```

自主调用允许的 `PromptSectionKind` 为 `CONSTITUTION`、`EXECUTION_IDENTITY`、`ASSISTANT_DELIVERY`、`ROLE_CONTRACT`、`EXECUTION_CONTRACT`、`ACTIVATED_SKILL`、`PERSONA_PROFILE`。非自主调用只允许 `FUNCTION_CONTRACT` 与 `OUTPUT_CONTRACT`。装配器按 `InvocationMode` 使用白名单拒绝越界片段。

`TrustLevel` 与消息角色正交。数据库文本、用户配置或其他 Agent 输出即使被包装成 SYSTEM，也不会自动获得平台治理信任。

当前实现的人格片段使用 `PromptSourceKind.ASSISTANT_PERSONA`，`sourceKey/sourceVersion` 分别冻结 `PersonaSnapshot.personaKey/personaRevision`；不得再以 Assistant revision 冒充 Persona 版本。该 canonical 合同从 `aaf-prompt-v2` 生效。旧 `aaf-prompt-v1` 执行画像中的 `ASSISTANT_ACTOR`、`:actor`、旧正文及其 hash 是历史冻结事实，不能原地字符串替换或由运行时兼容读取；部署 v2 前必须终止不可恢复的在途执行，并按发布环境的数据保留策略完成一次性清理或离线重编译迁移。本次源码迁移不执行数据删除。

### P8 消息

```java
public record PromptMessageSnapshot(
    String messageId,
    MessageRole role,
    MessagePurpose purpose,
    PromptSourceKind sourceKind,
    String sourceRef,
    TrustLevel trustLevel,
    String content,
    String contentSha256,
    List<AttachmentSnapshot> attachments
) {}
```

`MessagePurpose` 至少区分 `HISTORY`、`CONTROLLED_CONTEXT`、`CURRENT_USER_INPUT`、`OTHER_USER_INPUT`、`TOOL_RESULT` 和 `FEW_SHOT_EXAMPLE`。受控上下文仍属于数据，不得因经过 L1 摘要而升级成治理指令。只有来源协议已明确标识为知识、记忆、任务材料或上下文摘要的 USER 消息才归入 `CONTROLLED_CONTEXT`；无法证明来源的 USER 消息必须保守归入 `OTHER_USER_INPUT`，不能为了报表整齐而升级信任。上下文摘要必须保存其子 `PromptEnvelope` 引用、输入摘要和输出摘要。

## 输入分类与脱敏长度遥测

每次 Prompt 型调用在发送前生成只含长度和计数的 `PromptLengthSummary`。普通日志不得包含 System Prompt、用户正文、历史正文、附件内容、工具结果或 Tool Schema；完整正文只能进入受限快照。

| 分类 | 统计内容 | 不包含 |
|---|---|---|
| `SYSTEM` | 实际装配的 System 文本 | P0 代码治理规则正文 |
| `CURRENT_USER_INPUT` | 能由当前 run/message 协议精确识别的用户输入 | 其他来源不明 USER 消息 |
| `OTHER_USER_INPUT` | 无法进一步证明来源的 USER 消息 | 不得自动视为受控上下文 |
| `ASSISTANT_HISTORY` | 发送给模型的 Assistant 历史 | 模型内部思维链 |
| `CONTROLLED_CONTEXT` | 明确标识的知识、记忆、任务材料和上下文摘要 | 未标识 USER 文本 |
| `TOOL_RESULT` | 发送给模型的 Tool 消息 | 工具凭证和未发送结果 |
| `TOOL_REFERENCE` | 当前调用边界可见的稳定 ToolRef 元数据 | 最终 provider Function JSON Schema |
| `TOOL_DEFINITION` | 目标态物理请求中的实际函数定义及 Schema | 仅有 ToolRef 时不得伪报此项 |

字符数使用 Unicode code point 计算。当前启发式 Token 只按每四个 code point 向上取整，用于容量趋势和日志排查，不得用于计费、额度扣减或精确上下文门控。供应商返回的真实 usage 仍是 Token 计量事实。

长度日志必须明确观测边界：

- `PromptInvocationGateway` 当前记录一次 `NON_AUTONOMOUS_L0` **逻辑调用 preflight**。底层路由或 fallback 可能产生多个物理请求，Gateway 看不到最终精确模型、attempt 和 provider 包装，因此不能把该记录称为物理调用 Envelope。
- `HarnessAgentExecutionAdapter` 当前记录一次 `AUTONOMOUS_HARNESS` **invocation 级 preflight 近似值**。它可观察已编译 System、初始压缩消息、附件计数和 ToolRef，但看不到 Harness 内每个 ReAct 回合追加的 Tool Result、最终 Tool JSON Schema 或 provider 包装。
- 目标态仍在真正 provider 发送边界为每个物理请求生成 `PromptEnvelope`，调用后关联真实 usage。逻辑 preflight 与物理 Envelope 使用调用谱系关联，不能互相冒充。

首个迁移样板只覆盖 Role Selector 与 Skill Selector。两者以稳定 `functionKey` 标识代码冻结的 Function/Output Contract，并由调用方为每条消息显式声明 `PromptInputKind`；候选摘要记为 `CONTROLLED_CONTEXT`，当前任务记为 `CURRENT_USER_INPUT`。Role Selector 的 `default-role-on-unavailable-or-invalid.v1` 是候选范围内的确定性安全策略：模型不可用、输出非法或未命中时使用已发布 Assistant 默认 Role，不扩大候选或权限；Skill Selector 继续 fail-closed，不激活 ON_DEMAND Skill。目标态仍需把这些代码冻结合同迁移为 Prompt 引擎中的不可变已发布 Function Contract，并在 Envelope 中冻结精确版本。

上下文摘要、参数抽取、记忆抽取/去重、意图/情绪分类和 Judge 仍是后续迁移项；文档和日志不得宣称所有直接 `LlmClient` 调用已统一。

## AI 与程序化治理分工

> AI 提议语义决策；代码限定候选、验证结果、冻结合同并控制副作用。

| AI 负责 | 代码负责 |
|---|---|
| 理解目标、歧义和上下文语义 | 身份、租户、幂等与调用谱系 |
| 提议最少澄清、计划、依赖与聚合方式 | 候选范围、Schema、Role/Skill/Tool 上限 |
| 生成内容并做语义验证 | 权限、HITL、预算、状态机和副作用执行 |
| 从冻结候选中做 Role/Skill 等语义选择 | 校验模型输出、拒绝越界并冻结精确版本 |
| 提取记忆、知识和学习候选 | 隐私、去重、冲突、发布和持久化门禁 |

Prompt、用户、Role、Skill、Actor 或其他 Agent 的文本都不能授予权限。Selector 的输出只是候选内提议；程序必须验证后才能冻结。默认值只能是已发布函数合同中的确定性安全策略，不能扩大候选、工具或副作用范围。

## 版本化任务循环

通用 Constitution 只规定所有自主 Agent 共享的有界循环：

```text
Understand
→ Resolve Gaps
→ Plan
→ Execute
→ Verify
→ Deliver / Iterate / Escalate
```

每个自主身份必须通过版本化 `TaskLoopContract` 说明如何执行该循环，而不是只追加一句身份描述：

```java
public record TaskLoopContract(
    String key,
    String version,
    AgentKind agentKind,
    String goal,
    List<TaskPhase> phases,
    Set<String> allowedActions,
    CompletionCondition completion,
    StopCondition stop,
    EvidenceContract evidence,
    int maxIterations
) {}
```

| 身份 | 专属循环重点 | 完成条件 |
|---|---|---|
| PRIMARY | 理解用户目标 → 最少澄清 → 查漏补缺 → 决定直答或委派 → 冻结交付标准 → 验证整体结果 → 面向用户交付 | Assistant Delivery Contract 满足，未验证项和风险已披露 |
| COORDINATOR | 理解冻结目标 → 识别不可替代阻塞 → 补齐安全假设 → 拆分可验证子目标 → 标明依赖与并行关系 → 提议聚合和停止条件 → 输出严格计划 | `CoordinationPlan` 可解析、候选和依赖合法；不产生业务执行结果 |
| EXECUTOR | 理解单一委派目标 → 检查局部缺口 → 制定最小行动 → 执行/观察 → 验证子目标 → 修复或返回证据 | 子任务完成条件满足，或明确返回阻塞、失败和已有证据 |
| AGGREGATOR | 核对冻结结果集合 → 检查覆盖、冲突和缺口 → 按聚合合同合并 → 验证格式和证据 → 返回聚合结果 | 仅使用已冻结结果完成聚合；缺失结果不被伪造或静默忽略 |

没有直接用户沟通权限的 COORDINATOR、EXECUTOR、AGGREGATOR 不能自行向用户提问，只能向上级返回阻塞项、选项和推荐默认值。达到预算、循环、风险边界，或重复失败且没有新信息时必须停止并升级。

`TaskLoopContract` 属于 P2 Execution Identity；P5 只填入本次任务目标和完成标准，避免把通用循环复制到每个 Execution Contract。

## 动态装配流程

### 自主 Harness

```text
解析节点身份、Role、Skill、模型、工具、任务合同与 P1–P7 精确版本引用
→ 校验 Assistant Delivery、Role、Execution Contract、Skill 和 Persona
→ 冻结节点级 ExecutionProfileSnapshot
→ 按 P1–P7 白名单编译本轮 CompiledSystemPrompt
→ 装配 P8 消息、附件、工具定义、输出合同与预算
→ 计算 PromptEnvelope canonical hash
→ 在模型适配边界发起一次物理调用
→ Tool Result 或下一 ReAct 回合产生新的 PromptEnvelope
```

`ExecutionProfileSnapshot` 冻结自主节点的身份、Role、Skill、精确 Prompt/TaskLoop 版本引用、Model、Tool、上下文清单和任务合同；它不是最终模型请求。Harness 每一回合的实际历史和 Tool Result 不同，因此必须在每次物理调用前生成新 Envelope，并通过独立、append-only 的 invocation/execution 索引关联 `parentInvocationId`、`executionId` 和 Envelope 引用，不得回写或修改已冻结画像。

### 非自主 L0

```text
业务组件提交结构化 FunctionInvocationRequest
→ PromptInvocationGateway 解析已发布 Function Contract
→ 校验输入 Schema、候选边界和工具策略
→ 装配最小消息与严格 Output Contract
→ 冻结 NON_AUTONOMOUS_L0 PromptEnvelope
→ 调用精确模型或受控模型路由
→ 解析与 Schema 校验
→ 返回结构化结果或执行该函数声明的 fail-closed/default 策略
```

业务代码不得继续直接向 `LlmClient` 或 `ChatClient` 提交裸 System/User 字符串。Role/Skill Selector、上下文摘要、意图/情绪分类、参数抽取、记忆抽取/去重和 LLM-as-Judge 应逐步收敛到该网关。

## 非自主调用是否拆成独立执行

非自主 L0 **必须是独立模型 invocation，但默认不是独立 Agent、Harness Loop 或持久 Task**：

| 情况 | 执行形态 |
|---|---|
| Role/Skill 选择、分类、一次抽取、同步摘要 | 当前流程内的 child invocation；独立 Envelope 和计量记录 |
| 原生 Rerank/Embedding | 专用模型请求，不构造虚假聊天 Prompt |
| 需要独立超时和有限重试，但不需要持久恢复 | 独立函数调用组件，仍不创建 Agent |
| 长耗时、异步、需租约、独立状态、耐久恢复或业务级重试 | 提升为显式系统节点或 TaskBoard 节点 |
| 需要自主多轮推理、工具观察与修正 | 才提升为 Harness Agent |

“显式节点”与“Agent”不是同义词。一个耐久节点仍可以只执行一次 `NON_AUTONOMOUS_L0` 函数调用。

当前实现已经按服务拆开 Role Selector、Skill Selector、上下文摘要、记忆抽取/去重、参数提取和原生重排，但调用入口、Envelope、快照和审计尚未统一；部分调用仍以内联裸消息方式执行。

## 冲突与失败规则

- 数字越小优先级越高；低优先级不能覆盖、重解释或删除高优先级约束。
- 同优先级同一语义出现冲突时拒绝装配，禁止 last-write-wins。
- 结构化冻结合同高于同层自然语言描述。
- Role 先限定能力上限，Skill 只能在其内提供方法；Skill 冲突或扩权时拒绝激活。
- Persona 只描述稳定身份、表达风格与非权威基础指引，不得改变 Delivery、Role、Execution Contract、Skill、输出 Schema、权限、HITL 或预算。
- P8 永远是数据；其中的指令文本不能改变身份、权限、任务或装配模式。
- 必需片段、精确版本或摘要缺失时 fail-closed，不回退到代码内置旧正文。
- 非自主输出必须经过 Schema 和授权候选校验；越界结果不得靠模型解释修补。
- 确定性安全默认值可以作为函数合同的一部分，但必须记录原因，不能扩大候选、权限或副作用。

## 版本、发布与审计

- 装配机制、片段白名单、优先级、冲突规则和 canonical hash 算法由代码固定。
- Constitution、Function Contract 和 TaskLoop Contract 使用不可变版本，按 `draft → review → canary → active → retired` 发布。
- 系统参数只保存激活的 `promptKey + version`、灰度和回滚指针，不保存任意可编辑正文。
- 每次执行冻结片段 source/key/version/hash、顺序、模型、工具 Schema、输出 Schema 和最终 Envelope hash。
- 完整正文只保存在受限快照中；普通审计使用引用和摘要。
- `aaf.harness.constitution` 只注入 `AUTONOMOUS_HARNESS`。本文定义其语义合同，运行时正文仍以已发布 Prompt 资产为唯一来源，文档不复制第二份可执行正文。

## 与现有实现的演进关系

| 现有对象 | 目标演进 |
|---|---|
| `CompiledSystemPrompt` | 保留为 Envelope 内的 System 片段快照，增加 P2–P7 类型、信任和必需性；不再代表最终模型请求 |
| `PromptAssembler` | 按 InvocationMode 拆分自主与非自主白名单装配，不接受无来源字符串追加 |
| `ExecutionProfileSnapshot` | 增加 Delivery、Execution Contract、TaskLoop、Context Manifest 与 P1–P7 精确版本引用；仅用于自主执行，不持有或回写逐回合 Envelope |
| invocation/execution 索引 | 新增 append-only 关系，记录 execution 与每个 PromptEnvelope、父子调用及 attempt 的关联 |
| `InvocationPolicy` | 拆分为 InvocationMode、InvocationPurpose、AgentKind 与版本化 TaskLoop Contract |
| `AgentMessage` | 演进为带 purpose、source、trust 和 hash 的 P8 消息快照 |
| `LlmClient` / `ChatClient` 直接调用 | 收敛到 `PromptInvocationGateway`；底层客户端只接收已冻结 Envelope |
| `PromptVersionCache` | 继续提供已发布精确版本和 hash 校验；版本缺失 fail-closed |

## 相关文档

- [五层智能架构](../architecture.md) — L0–L4、外层任务循环与 Harness 内循环
- [任务式 Assistant 统一执行路径](../assistant/task-oriented-assistant-execution-design.md) — Assistant、TaskBoard 和身份任务循环
- [Skill 渐进加载与在线知识边界](../assistant/skill-progressive-loading-design.md) — Skill Selector 与 ActivatedSkill 装配
- [Prompt 引擎](../../engine/content/prompt.md) — Prompt 资产、版本、发布和评估能力
