---
level: Practice
layer: Principle
purpose: L0 非自主模型调用改为直接复用零工具 ReActAgent，退出 Spring AI LlmClient 双栈
status: proposed
version: 2.0.0
date: 2026-09-01
author: AaronZZH & Kiro
changelog:
  - 2026-09-01 | 2.0.0 | 推翻 v1.0.0 的"新建 NonAutonomousModelInvoker 端口"方案，改为直接复用零工具 ReActAgent（人类拍板）
---

---
status: proposed
date: 2026-09-01
deciders: [AaronZZH]
consulted: []
informed: []
related-tasks: [AAF-105 / #10501]
---

# ADR-007: L0 非自主模型调用单栈化（复用零工具 ReActAgent）

## Context and Problem Statement

AAF 智能层当前同时维护两套 L0（非自主、请求级、无人格无记忆）模型调用抽象：

- **Spring AI 栈**：`LlmClient`/`SpringAiLlmClient`，经 `ResilientChatService` 提供多层 fallback/重试路由
- **AgentScope 栈**：core `Model.stream(...)` / `ReActAgent`，已用于 L1/L2 自主 Agent 循环

五个智能层调用点（`ParameterExtractionNode`、`IntentUnderstandingService`、`EmotionPerceptionService`、`DefaultSessionContextCompressor`、`DefaultHybridContextCompressor`）仍直接依赖 `LlmClient`；另有四个已通过 `PromptInvocationGateway` 间接依赖（`DefaultRoleSelector`、`DefaultInputClassifier`、`ModelDrivenTaskComplexityAnalyzer`、`ModelSkillSelectionPort`）。双栈并存导致：消息模型、流式收敛、结构化输出、模型路由四类逻辑各有一份实现，且 `ResilientChatService` 的多层 fallback 语义与 `CapabilityRouter` 的确定性路由语义互相竞争。

需要决定：L0 收敛到 AgentScope 栈时，是新建一个轻量端口直调 core `Model`，还是直接复用 `ReActAgent`（配置为零工具、无状态）。

## Decision Drivers

- 消除双栈并行抽象带来的维护成本（同一类问题两份实现）
- 复用已验证的官方实现优于自建等价逻辑（结构化输出的原生/降级路径选择、流式收敛、消息映射）
- ADR-003 已定"全量同步 + 虚拟线程，仅 SSE 保留 Flux"，调用方签名不能违反
- `CapabilityRouter` 已承载显式模型、编排配置、AI 选择、用户偏好、系统默认、YAML 兜底六层决策，不应被 provider 层的静默 fallback 绕过或复制
- 禁兼容层（AAF 未 v1.0）：不允许 `LlmClient` 与新调用路径同时作为 Spring Bean 存在

## Considered Options

- **选项 A**：保留双栈，`ResilientChatService` 包一层新端口，逐步迁移
- **选项 B**：新建轻量端口 `NonAutonomousModelInvoker` 直调 core `Model.stream(...)`，自行实现消息映射、流式收敛、结构化输出路径选择
- **选项 C**：直接复用零工具、无状态配置的 `ReActAgent`（不设 `toolkit`/`stateStore`/`fallbackModel`），`call(msgs, RuntimeContext)` 作为 L0 唯一调用入口

## Decision Outcome

**Chosen option**："选项 C — 复用零工具 `ReActAgent`"（推翻本 ADR v1.0.0 采纳的选项 B，人类拍板依据：读 `tmp/agentscope-java` 官方 `Agent`/`ReActAgent` 源码与文档后确认，零工具无状态配置下 `ReActAgent` 的行为与自建端口高度重合，复用能省掉一整套自建的消息映射、流式收敛、结构化输出路径选择逻辑）。

理由：
- 选项 A 的"包一层"本质是兼容层，违反禁兼容层硬约束，且不能消除 fallback 语义竞争（`ResilientChatService` 仍在执行路径中）
- **选项 B 被推翻**：核实 `ReActAgent.call(msgs, structuredOutputClass)` 源码后确认，其结构化输出路径选择（原生 `response_format` vs 合成 `generate_response` 工具）、流式收敛（`Model.stream()` 的 `Flux<ChatResponse>` 聚合为 `Mono<Msg>`）、消息映射（`ModelMessage`→`Msg`）三件事，自建端口需要重新实现一遍官方已经做好且经过验证的逻辑，是不必要的重复建设
- 选项 C 的代价是需要显式处理两个官方默认行为与 AAF 决策的冲突点（见下方"必须覆盖的默认行为"），但代价可控且是一次性设计成本，不是持续维护成本

### 复用方式：零工具无状态配置

```java
ReActAgent l0Agent =
        ReActAgent.builder()
                .name("l0-invoker")           // 单实例，不按 Function Contract 建多实例
                .sysPrompt("")                 // 基础 prompt 留空，实际内容由 middleware 按 RuntimeContext 动态注入
                .model(resolvedModel)           // CapabilityRouter 解析出的唯一 AiModel 对应的 core Model
                .toolkit(new Toolkit())         // 空 Toolkit：天然无工具循环，call() 一轮即返回
                // 不设 .stateStore(...) → 不持久化，AgentState 调用结束即弹出内存
                // 不设 .fallbackModel(...) → 不静默切模型
                .build();
```

**per-call Function Contract 通过 middleware 注入，不新建多个 Agent 实例**：`ReActAgent` 支持 `onSystemPrompt(Agent, RuntimeContext, String)` middleware 钩子对基础 prompt 做 per-call 改写（`PlanModeMiddleware.onSystemPrompt` 是官方参照实现——读 `RuntimeContext`/`AgentState` 后返回改写后的 prompt）。AAF 需要一个 `FunctionContractPromptMiddleware`，从 `RuntimeContext` 的类型化属性里取出本次调用的 Function Contract（`sysPrompt`/`responseSchema` 等），拼装成最终 system prompt。这样五个 L0 调用点共享同一个 `ReActAgent` 单例，不产生"每次调用 build 一个新实例"的开销。

### 必须覆盖的两个官方默认行为（真实代价，需 #10502 落地）

1. **模型容错**：`ReActAgent.builder()` 支持 `.fallbackModel(...)`，本方案**不设置该参数**即可关闭——这是 builder 级配置，零额外代码。
2. **结构化输出降级路径无法通过配置关闭，需要调用前置检查**：核实 `ReActAgent.doStructuredCall` 源码（`ReActAgent.java:1040-1074`）确认，`model.supportsNativeStructuredOutput()` 返回 `false` 时代码**直接走合成工具降级路径**，且原生路径调用失败时会 `onErrorResume` 自动降级到合成工具——**这两条降级路径都没有 builder 参数可以关闭，是硬编码行为**。这与 ADR 决策"模型不支持时直接失败，不允许静默降级为 ReAct 工具循环"冲突，因此 AAF 侧必须在调用 `ReActAgent.call(msgs, schemaClass, ctx)` **之前**，先用 `model.supportsNativeStructuredOutput()` 做前置判断，不满足直接 fail-closed，不进入 `ReActAgent` 调用——这是选项 C 相比选项 B 唯一需要的"包一层"逻辑，范围很小（一次布尔判断），不构成兼容层。

### 模型选择：不新增抽象

模型选择继续完全由 `CapabilityRouter.resolve(CapabilityRoutingContext)` 承担（显式模型 > 编排配置 > AI 选择 > 用户偏好 > 系统默认 > YAML 兜底六层）。`AgentScopeModelResolver` 新增 `resolve(AiModel)` 公共重载解析出 core `Model` 实例，传给 `ReActAgent.builder().model(...)`；`ReActAgent` 内部不复制路由逻辑，只消费解析结果。

### Fallback 语义：不静默切模型

`CapabilityRouter` 在调用前解析出唯一 `AiModel`；`ReActAgent` 因不设 `fallbackModel`，调用失败直接抛出/返回错误，不静默切换模型。这是一个**可用性与成本语义的显式变更**：`ResilientChatService` 当前提供多层 fallback（跨模型/跨 provider 重试），迁移后 L0 单次调用不再享有这层保护。可接受的依据：
- L0 场景（意图理解、参数抽取、情感感知、上下文压缩）是辅助性判断，不是用户可见的主链路最终产出，失败后按 Function Contract 有明确的调用方降级路径
- `ResilientChatService` 保留服务于四个非 L0 业务用户（`TrendingService`/`MeetingOrganizeService`/`ToolGenerator`/`AiEnricher`），fallback 能力没有被删除，只是不再覆盖智能层 L0
- 若未来发现 L0 场景确实需要跨模型 fallback，应作为 `CapabilityRouter` 路由策略的显式扩展，而不是在 provider 调用层悄悄重试

### `PromptInvocationGateway` 签名变更

`PromptInvocationGateway.call(NonAutonomousInvocation)` 返回值从 `String` 改为携带 `text`/`inputTokens`/`outputTokens`/`finishReason` 的结果类型（具体类型留给 #10502 定，直接复用 `ReActAgent.call(...)` 返回的 `Msg` 及其 usage 元数据映射，不必再照搬 v1.0.0 里自定义的 `ModelInvocationResult` record——那是为选项 B 设计的，选项 C 下 `Msg` 已经是标准载体）。gateway 内部依赖从 `LlmClient` 替换为对 `ReActAgent` 单例的调用封装。**保持同步返回**（依 ADR-003）：`ReActAgent.call(...).block()` 在虚拟线程上阻塞等待，不 pin carrier thread，符合 ADR-003 选项 B 的预期用法。

### 影响范围

| 类别 | 内容 |
|---|---|
| 新增 | L0 专用零工具 `ReActAgent` 单例（Spring Bean）+ `FunctionContractPromptMiddleware`（per-call system prompt 注入） |
| 删除 | `LlmClient`、`SpringAiLlmClient`（`MockLlmClient` 改造为测试 fixture） |
| 签名变更 | `PromptInvocationGateway.call(...)` 返回值 `String` → 结构化结果（含 usage/finishReason） |
| 前置检查（新增，范围小） | 结构化输出调用前判断 `model.supportsNativeStructuredOutput()`，不满足直接失败，不进入 `ReActAgent` |
| 迁移调用点（5 个） | `ParameterExtractionNode`、`IntentUnderstandingService`、`EmotionPerceptionService`、`DefaultSessionContextCompressor`、`DefaultHybridContextCompressor` |
| 断言调整（4 个） | `DefaultRoleSelector`、`DefaultInputClassifier`、`ModelDrivenTaskComplexityAnalyzer`、`ModelSkillSelectionPort`（已走 gateway，契约不变） |
| 边界外保留 | `ResilientChatService` 继续服务 `TrendingService`/`MeetingOrganizeService`/`ToolGenerator`/`AiEnricher`，标记为已退出智能层 L0，本批不重构 |

## Consequences

**正面**：
- 消除双栈重复实现，L0 只有一条调用路径可维护
- 复用官方已验证的结构化输出路径选择、流式收敛、消息映射逻辑，比自建端口（选项 B）少一整块需要长期维护的代码
- 模型路由决策集中在 `CapabilityRouter`，不再有 provider 层隐藏的第二套路由

**负面 / 需要承担的成本**：
- `ReActAgent` 的结构化输出降级路径无法通过配置关闭，AAF 必须在调用前做前置能力检查——这是选项 C 相比"完全零改动直接复用"的真实例外，需要在 #10502 编码时明确落地并写测试覆盖
- `ReActAgent` 未来版本若新增其它默认开启的能力（例如新的自动重试策略），AAF 需要持续审查该能力是否与"确定性失败、无状态"的 L0 语义冲突——这是复用官方实现必然伴随的适配追踪成本，选项 B 因为只依赖 `Model.stream()` 这一层不会有这个问题，是选项 C 的权衡代价
- L0 调用失去 `ResilientChatService` 的跨模型自动重试，需要在 #10504 迁移调用点时逐一确认调用方对失败的处理是否合理
- `PromptInvocationGateway` 返回值类型变更是破坏性签名变更，五个直接调用点 + 四个既有调用点全部需要同步修改调用代码

## More Information

- 技术真理源：[Harness 落地计划 · 非自主 L0 改为 AgentScope 直调](../audit/2026-09-01-harness-landing-plan.md#非自主-l0-改为-agentscope-直调)（该文档描述的是选项 B 路线，本 ADR 选项 C 落地后需回写更新该文档的对应章节）
- 并发模型约束：[ADR-003](ADR-003-virtual-threads-over-webflux.md)
- 官方参照：`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:1040-1074`（结构化输出路径选择，硬编码降级）、`agentscope-harness/.../PlanModeMiddleware.java`（`onSystemPrompt` 用法参照）
- 后续任务：`docs/task/v0.12/AAF-105/tasks.md` #10502～#10505（需同步改写以反映选项 C）
