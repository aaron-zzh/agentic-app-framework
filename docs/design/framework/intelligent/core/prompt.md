---
level: Practice
layer: Model
purpose: 定义所有 Prompt 型模型调用的 PromptEnvelope、分层信任边界与两种装配画像
status: draft
version: 1.2.0
date: 2026-08-25
author: Kiro
tags:
  - PromptEnvelope
  - 分层优先级
  - 信任边界
dependencies:
  - ../architecture.md
  - ./core.md
scope:
  includes:
    - PromptEnvelope 的字段与冻结时机
    - P0–P8 分层优先级与信任边界
    - 自主与非自主两种装配画像
    - 输入分类与脱敏遥测
    - AI 与程序化治理的分工
  excludes:
    - 模型择选（见 model-router.md）
    - 任务循环合同的身份职责（见 agent/agent.md）
    - Skill 正文与 references（见 skill/skill.md）
gains:
  - 能为一次模型调用装配符合优先级的 Envelope
  - 能判断某项约束应写入 Prompt 还是由代码硬治理
  - 能识别哪些内容禁止渲染为授权文本
---

# Prompt 装配

> **目标合同**：所有 Prompt 型模型调用在实际发送前都冻结一个 `PromptEnvelope`；当前代码中不存在该类型，不得声称已执行。
> **P0 目标边界**：权限、HITL、预算、状态机、工具可见性与 Schema 校验不渲染为授权文本，只在 Envelope 中保存治理决策引用与摘要。当前仅自主装配器局部遵守，尚无全调用链代码门禁。

## 定位与边界

L0–L4 智能层级与 P0–P8 Prompt 优先级正交；调用形态由 [总体架构](../architecture.md) 定义。本文只定义一次模型调用的输入装配与追溯合同，不定义任务身份职责、Skill 正文或 Prompt 资产发布流程。

| 承担 | 不承担 |
|---|---|
| 受信片段排序、来源冻结、输入分类、输出合同与调用快照 | 用文本授予权限、提高预算或迁移任务状态 |
| 自主与非自主画像白名单 | 把 P8 外部内容升级为系统指令 |
| 物理调用级 hash、受限快照引用和脱敏遥测 | 在普通日志、公共事件或 AG-UI 暴露正文与思维链 |

## 领域模型

| 对象 | 不变量 |
|---|---|
| `PromptEnvelope` | 对应一次物理模型请求；发送前冻结，append-only，不回写 `ExecutionProfileSnapshot` |
| `PromptSectionSnapshot` | 具有优先级、来源、精确版本、信任级别、正文 hash 与连续序号 |
| `PromptMessageSnapshot` | 消息角色与来源信任正交；P8 始终是数据 |
| `OutputContractSnapshot` | 精确 Schema/媒体类型、合同版本和校验策略可追溯 |
| `GovernanceSnapshot` | 只保存 P0 决策引用、规则版本和摘要，不含可被模型解释为授权的正文 |
| `PromptLengthSummary` | 只保存分类长度、启发式 Token 和条目数，不持有正文 |

`ExecutionProfileSnapshot` 是自主节点的运行画像，冻结身份、Role、Skill、Model、Tool 与任务合同；`PromptEnvelope` 是该节点某回合、某次模型尝试的真实请求。前者只能被引用，不能被后者修改。非自主 L0 调用没有执行画像。

## 契约

### 分层优先级

数字越小优先级越高。低优先级不能覆盖、重解释或删除高优先级约束；同层同一语义冲突时拒绝装配，禁止 last-write-wins。

| 层 | 确切归属 | 输入形态 | 排序与冲突规则 |
|---|---|---|---|
| P0 | 代码硬治理 | 不进入模型文本 | 权限、HITL、预算、状态机、工具交集、模型授权、Schema 校验；只保存决策引用与摘要 |
| P1 | Harness Constitution | System | 唯一、版本化，所有自主身份共享，必须首位 |
| P2 | Execution Identity Contract | System | PRIMARY / COORDINATOR / EXECUTOR / AGGREGATOR 的身份与任务循环，精确版本唯一 |
| P3 | Assistant Delivery Contract | System | Assistant 使命、交付物与成功标准；结构化合同，不增加自由 Prompt 字段 |
| P4 | Role Contract | System | 当前 Role 的职责、非职责与能力上限；只能收窄 P3 |
| P5 | Frozen Execution Contract | System | 本次目标、范围、计划、完成、聚合与停止条件；来自已冻结运行画像 |
| P6 | Activated Skills | System | 只装配最终激活版本；按 `SYSTEM → ASSISTANT → ROLE`，同 scope 按稳定 key/version 排序 |
| P7 | Persona Profile | System，可选 | 最后装配，只影响自然语言表达；严格 JSON/结构化身份默认关闭 |
| P8 | Context and Data | 消息 | 历史、当前输入、受控上下文、附件、工具观察与外部内容；始终按不可信数据处理 |

结构化合同高于同层自然语言。Skill 不能扩张 Role，Persona 不能改写 P1–P6。网页、文件与工具结果中的指令属于 P8，不能改变身份、权限、任务或装配模式。

当前 `CompiledSystemPrompt` 使用 `CONSTITUTION → IDENTITY → INVOCATION_POLICY → ROLE → SKILL → PERSONA` 六类并校验稳定顺序与 hash（`CompiledSystemPrompt.java:13-294`）。其中 `INVOCATION_POLICY` 合并了 P3 与 P5，P2 也未形成独立版本化 TaskLoop 合同，因此属于向 P1–P7 目标结构的部分实现。

### 两种装配画像

| 画像 | 装配内容 | 禁止注入 |
|---|---|---|
| `AUTONOMOUS_HARNESS` | P1–P6、按需 P7、P8 数据、工具与输出合同 | 未经治理交集的 Tool、任意授权文本 |
| `NON_AUTONOMOUS_L0` | 版本化 Function Contract、严格 Output Contract、最小数据；默认无工具 | Constitution、Persona、Role、Skill、自主任务循环 |

自主 Harness 的每个 ReAct 回合、每个模型尝试各生成独立 Envelope。非自主调用默认是 child invocation，不创建 Agent、Harness Loop、Task 或 `ExecutionProfileSnapshot`。

当前 `PromptInvocationGateway` 只生成逻辑调用 ID、计算长度并委托 `LlmClient`（`PromptInvocationGateway.java:29-61`）；其现有调用方 `DefaultRoleSelector` 与 `ModelSkillSelectionPort` 只完成选择并返回值（`DefaultRoleSelector.java:80-105`、`ModelSkillSelectionPort.java:35-53`），不会为该 invocation 新建持久 Task。调用发生在既有 Assistant Task 内时，仍是该 Task 的 child invocation，不能误解为“完全脱离任务上下文”。

### PromptEnvelope 目标数据结构

> 🎯 目标态：代码库当前不存在 `PromptEnvelope` 类型。下列 Java 仅定义目标合同，当前不得声称已实例化、持久化或在 provider 发送前冻结。

```java
public record PromptEnvelope(
    String specVersion,
    String envelopeId,
    InvocationDescriptor invocation,
    ExecutionProfileRef executionProfile,
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
    String physicalInvocationId,
    int attemptNo,
    InvocationMode mode,
    InvocationPurpose purpose,
    String functionKey,
    String executionId,
    String nodeId
) {}
```

| 字段 | 合同 |
|---|---|
| `envelopeId` | 全局唯一；一次 provider 发送一个，不因重试复用 |
| `logicalInvocationId` | 同一业务语义调用稳定；模型切换与传输重试共享 |
| `physicalInvocationId` / `attemptNo` | 精确区分每次 provider 请求，严格递增且可关联 usage |
| `executionProfile` | 自主调用引用 `{executionId, snapshotHash}`；非自主调用为空；不复制画像为第二真理源 |
| `model` | 精确 `ModelSpec`、参数和能力快照，不只保存 route scene |
| `systemPrompt` | 自主调用保存 P1–P7 编译快照；非自主调用保存 Function/Output Contract 编译结果 |
| `messages` | 保存实际发送顺序、角色、purpose、source、trust、附件引用与逐条 hash |
| `tools` | 保存实际 provider Function Schema 与 hash；工具可见不代表动作获权 |
| `governance` | 保存 P0 决策引用、规则版本与摘要，不保存授权正文 |
| `audit` | 保存优先级方案、canonical hash、片段 hash 和受限正文快照引用 |

冻结时机是**完成 provider 适配之后、请求发送之前**：此时最终模型参数、消息顺序、工具 Schema 与输出 Schema 均已确定。完整正文只进入访问受控的 Prompt 快照存储；普通索引只保存 hash、摘要与引用。

### 输入分类与脱敏遥测

| 分类 | 统计边界 |
|---|---|
| `SYSTEM` | 实际发送的 System 文本；不含 P0 规则正文 |
| `CURRENT_USER_INPUT` | 可由当前 run/message 协议精确识别的用户输入 |
| `OTHER_USER_INPUT` | 来源无法证明为当前输入或受控上下文的 USER 消息 |
| `ASSISTANT_HISTORY` | 实际发送的 Assistant 历史，不含内部思维链 |
| `ASSISTANT_REASONING` | 提供商协议要求回放的加密推理块；只统计长度，不输出正文 |
| `CONTROLLED_CONTEXT` | 明确标识的知识、记忆、任务材料与摘要；仍是 P8 数据 |
| `TOOL_RESULT` | 实际发送的工具观察，不含凭证与未发送结果 |
| `TOOL_REFERENCE` | Harness 调用边界可见的稳定 ToolRef 元数据 |
| `TOOL_DEFINITION` | 最终发给 provider 的函数定义与 Schema；只有真实可见时才能统计 |

字符数按 Unicode code point 计算；启发式 Token 为 `ceil(codePoints / 4)`，只用于容量趋势、压缩触发和排障，禁止用于计费、额度扣减或精确上下文门控。真实计量以提供商 usage 为准。

允许进入普通日志或内部遥测：调用谱系 ID、mode、purpose、functionKey、非敏感 route scene/模型标识、分类字符数、估算 Token、条目/附件数、hash、耗时与状态。允许进入公共任务事件的范围更小，只含调用/决策引用、状态与汇总 usage。以下内容禁止进入普通日志、公共事件和 AG-UI：System/用户/历史正文、附件、工具结果、Tool Schema、凭证、完整 Prompt、思维链与加密推理块。

当前 `PromptLengthSummary` 已按上述分类计算 code point 与四字符估算（`PromptLengthSummary.java:15-110`），`PromptInvocationGateway` 记录非自主逻辑调用 preflight（`PromptInvocationGateway.java:20-161`），Harness 只记录 invocation 初始输入近似值（`HarnessAgentExecutionAdapter.java:129-231`）。两者都不是物理调用 Envelope。

### AI 与程序化治理分工

| 模型可提议/生成 | 程序必须判定/执行 |
|---|---|
| 理解目标、歧义、语义关联与内容 | 身份、租户、幂等、调用谱系与授权主体 |
| 最少澄清、计划、依赖与聚合建议 | 候选范围、Role/Skill/Tool 上限和版本合法性 |
| 候选内 Role/Skill/模型语义排序 | 权限交集、HITL、预算、模型授权与副作用门禁 |
| 内容、摘要、抽取与语义评估 | 输入/输出 Schema 校验、候选越界拒绝与精确版本冻结 |
| 记忆、知识和学习候选 | 隐私、可信度、去重、冲突、发布与持久化门禁 |
| 完成度与风险说明 | 状态迁移、事件事实、CompletionValidator 与最终提交 |

模型文本不能授予权限。安全默认值必须来自已发布函数合同，记录原因且不能扩大候选、工具、预算或副作用。

## 实现态

| 契约 | 实现态 |
|---|---|
| P1–P7 来源、顺序与 hash 冻结 | ⚠️ 部分实现 · `CompiledSystemPrompt.java:13-294` 已冻结六类 System 层；P2/P3/P5 尚未按目标合同分离 |
| 每次物理调用冻结完整 `PromptEnvelope` | 🎯 目标态，当前不得声称已执行 |
| P0 不作为授权文本进入 Prompt | ⚠️ 部分实现 · `PromptAssembler.java:23-286` 明确把工具授权、预算与许可交给外部机制；Envelope 中尚无治理引用快照 |
| 两种画像白名单隔离 | ⚠️ 部分实现 · `PromptInvocationGateway.java:20-161` 不加载自主画像；迁移仅覆盖部分非自主调用，仍有裸 `LlmClient` 调用 |
| 每个 ReAct 回合与模型尝试独立 Envelope | 🎯 目标态，当前不得声称已执行；`HarnessAgentExecutionAdapter.java:129-231` 仅有 invocation 级 preflight |
| Persona 不改变 Schema 与授权 | ⚠️ 部分实现 · `PromptAssembler.java:170-184` 对 Coordinator 关闭 Persona 且 Persona 排末位；完整输出 Schema 与治理快照尚未进入 Envelope |
| 输入分类与脱敏长度遥测 | ✅ 已实现 · `PromptInputKind.java:4-15`、`PromptLengthSummary.java:15-110` |
| 受限正文快照与 append-only Envelope 索引 | 🎯 目标态，当前不得声称已执行 |

## 验收基线

- 任一次物理模型调用可回溯唯一 Envelope、父子调用、attempt、模型和真实 usage
- 自主画像按 P1–P7 稳定排序，同层冲突拒绝装配；P8 无法改变高优先级约束
- 非自主 Envelope 不含 Constitution、Persona、Role、Skill 或自主任务循环
- `ExecutionProfileSnapshot` 只被引用，不因回合或重试被回写
- 普通日志、公共事件与 AG-UI 不泄露 Prompt 正文、工具结果、凭证、Tool Schema 或思维链
- Persona 只能影响自然语言表达，结构化输出与授权结果由程序验证
