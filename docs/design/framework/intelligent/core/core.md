---
level: Practice
layer: Model
purpose: 定义 L0 内核的职责边界、对上层的接口契约、LLM 封装形态与资源计量
status: draft
version: 1.1.0
date: 2026-08-25
author: Kiro
tags:
  - L0 Core
  - 模型调用
  - 资源计量
dependencies:
  - ../architecture.md
related:
  - ./model-router.md
  - ./prompt.md
scope:
  includes:
    - L0 的职责边界与状态约束
    - 对上层暴露的接口契约
    - 两种 LLM 封装形态
    - Token 与资源计量归属
  excludes:
    - 模型择选与路由决策（见 model-router.md）
    - Prompt 装配与信任边界（见 prompt.md）
    - 任务状态与业务副作用（属 L2/L3）
gains:
  - 能判断某项能力应由 L0 提供还是由调用方自备
  - 能选择合适的 LLM 封装形态发起一次模型调用
  - 能定位一次调用的 Token 计量归属
---

# L0 内核

> L0 提供通用思考与生成能力：模型选择、理解、推理、生成，并控制上下文与资源消耗。
> **请求级，无人格、无记忆，不感知具体用户与业务身份**；不持有任务状态，不产生业务副作用。

## 定位与边界

模型常识是随模型版本固化的**参数化知识**，不是可独立维护、授权和审计的知识真理源。调用方按 [统一运行时](../runtime.md) 冻结任务责任，按 [Prompt 装配](prompt.md) 提交本次调用输入；L0 只返回模型事实或规范化失败。

| 承担 | 不承担 |
|---|---|
| 把已冻结调用输入转化为文本、流或结构化结果 | 决定用户目标、执行计划或完成状态 |
| 上下文窗口校验、模型调用与提供商差异隔离 | 持有会话焦点、任务进展、记忆或知识 |
| 模型能力择选（委托 [模型路由](model-router.md)） | 解释业务身份、租户策略或动作权限 |
| 每次物理调用的用量事实与错误分类 | 配额扣减、积分结算、HITL 或业务副作用 |

## 领域模型

| 对象 | 不变量 |
|---|---|
| `LlmRequest` | 引用一个已冻结 `PromptEnvelope`、一个精确 `ModelSpec` 和一个不透明计量归属；请求创建后不可变 |
| `LlmResponse` | 包含正文、完成原因、模型与物理调用标识、真实 usage；不携带任务终态 |
| `LlmChunk` | 同一物理调用内 `sequence` 严格递增；仅终止片段可携带完成原因与最终 usage |
| `StructuredOutputContract<T>` | JSON Schema、目标类型、合同版本和校验策略不可变；提供商原生约束不能替代本地校验 |
| `ModelUsageFact` | 一次物理模型请求一条事实，以 provider event/request ID 幂等；失败尝试有 usage 时同样记录 |
| `LlmFailure` | 分类、可重试性、精确模型和物理调用标识齐全；不得用空结果表示失败 |

计量归属与执行主体分离：人类触发时归属其 `owner`；AI 代执行时归属委托者 `owner`。`tenantId` 是隔离与预算聚合维度，不默认充当积分债务人；租户代付等策略由引擎层在调用前解析为不透明 `billingAccountRef`，L0 不自行推断。

## 契约

### LlmClient

目标端口以已冻结 Envelope 为唯一输入真理源；`PromptEnvelope` 字段由 [prompt.md](prompt.md) 定义，路由规则由 [model-router.md](model-router.md) 定义。

```java
public interface LlmClient {
    LlmResponse call(LlmRequest request);

    Flux<LlmChunk> stream(LlmRequest request);

    <T> StructuredLlmResponse<T> callStructured(
        LlmRequest request,
        StructuredOutputContract<T> outputContract
    );
}

public record LlmRequest(
    PromptEnvelope envelope,
    UsageAttributionRef usageAttribution
) {}

public record LlmResponse(
    String physicalInvocationId,
    ModelSpec model,
    String content,
    FinishReason finishReason,
    ModelUsage usage
) {}
```

当前端口仅提供 `call(messages, scene, userId)`、`callExact(messages, model, userId)` 与 `stream(messages, scene, userId)`，返回 `String` / `Flux<String>`（`LlmClient.java:10-46`）。参数名 `userId` 是历史适配名：`ResilientChatService` 将其仅作为计量 owner 的兜底，优先使用 `OperatorContext.currentOwnerId()`；它不是 `tenantId`，也不应被调用方解释为 L0 可感知业务用户。该端口不是上述完整合同；新增调用不得据此声称已具备 Envelope、结构化输出或物理尝试追踪。

### 流式与结构化输出

| 契约 | 规则 |
|---|---|
| 流式顺序 | 同一 `physicalInvocationId` 的片段按 `sequence` 严格递增；取消信号向提供商传播，禁止取消后继续计为成功 |
| 流式完成 | usage 以提供商最终累计值为准；只有正常终止片段可声明 `COMPLETED` |
| 结构化生成 | 优先使用模型原生 JSON Schema；无论提供商是否原生支持，返回值必须在代码侧解析并校验 |
| 校验失败 | 产生 `OUTPUT_CONTRACT_VIOLATION`；是否换模型或重试由调用策略决定，禁止启发式修补后冒充合规结果 |
| 快照 | 请求、Schema 版本、模型尝试和结果摘要关联同一调用谱系；正文只进入受限快照 |

### 失败与降级

| 分类 | 可重试 | 默认语义 |
|---|---|---|
| `INVALID_REQUEST`、`AUTHENTICATION`、`CONTENT_REJECTED` | 否 | 原样失败，不切换模型 |
| `RATE_LIMITED`、`TIMEOUT`、`PROVIDER_UNAVAILABLE` | 是 | 在预算内尝试能力兼容的下一模型 |
| `OUTPUT_CONTRACT_VIOLATION` | 由输出合同声明 | 每次尝试都重新生成 Envelope 并重新校验 |
| `CANCELLED` | 否 | 终止流，不发布成功事实 |

`EXACT` 调用禁止路由与 fallback；`ROUTED` 调用只能使用 [model-router.md](model-router.md) 产出的有序候选。降级必须暴露实际模型与尝试序号，不能静默改变结果来源。L0 不负责保存任务状态；调用方接收失败后按 [任务耐久性](../task-durability.md) 保持或迁移任务状态。

### 两种封装形态

| 形态 | 入口 | 适用场景 | 循环与工具 | 当前实现 |
|---|---|---|---|---|
| 直连链路 | `LlmClient` → `ResilientChatService` → Spring AI | 选择、分类、抽取、摘要、Judge 与无需自主循环的生成 | 无自主循环；默认无工具 | `SpringAiLlmClient.java:20-61` |
| Harness 链路 | `AgentExecutionPort` → AgentScope Harness → provider model | Coordinator、Executor、Aggregator 的多轮推理与工具观察 | AgentScope 提供 ReAct；工具仍经 AAF 治理 | `HarnessAgentExecutionAdapter.java:50-503`、`AgentScopeSpecCompiler.java:27-231` |

两条链路的**模型登记与选择**共享 `ai_model`，但当前尚未完全共享模型运行配置：Harness 按 `ModelSpec` 从 `ai_model` 构建 provider model；直连链路仅 `OPENAI_COMPAT` 动态读取模型配置，`ANTHROPIC/OLLAMA` 仍取 Spring 容器 Bean（`DynamicChatClientFactory.java:57-67,147-168`）。因此不得声称两条链路已具备唯一模型配置真理源。历史文档中的 `AgentScopeLlmClient` 未落地，也不作为必要抽象。

`MockLlmClient` 的设计用途仅限测试、评估和显式离线开发，通过 `aaf.llm.mock=true` 替换真实客户端（`MockLlmClient.java:28-88`）。当前条件注解没有生产环境阻断，故只能声明“不得用于生产”，不能声称代码已保证其无法在生产启用；它不属于 `ResilientChatService` 的 fallback 模型链。

### 调用形态

装配画像由 [prompt.md](prompt.md) 唯一定义：

| 形态 | 是否持有自主循环 | 典型用途 |
|---|---|---|
| `AUTONOMOUS_HARNESS` | 是 | Coordinator、Executor、Aggregator 的多轮推理与工具观察 |
| `NON_AUTONOMOUS_L0` | 否 | Role/Skill 选择、分类、摘要、参数提取、记忆抽取去重、LLM-as-Judge |

非自主调用默认是当前流程中的 child invocation，不创建 `ExecutionProfileSnapshot`、Harness Loop 或 TaskBoard 子任务。

### 资源计量出口

| 时点 | L0 输出 | 引擎层职责 |
|---|---|---|
| 调用前 | 精确模型、能力、计量归属引用、预算估算 | `AiCreditGuard` 做余额/配额预检；拒绝时不调用模型 |
| 调用后 | 物理调用级 `ModelUsageFact`：输入、输出、缓存 Token、模型、能力、时间与幂等键 | 记录、累计、结算、预警与任务预算门控 |
| 低余额 | 不由 L0 判定 | 引擎发布 `CreditLowEvent` 并通知；该事件不是模型调用合同 |

直连链路当前以 `TokenUsageEvent(userId, modelId, promptTokens, completionTokens, usageId, capability)` 输出（`TokenUsageEvent.java:17-49`），监听器异步结算（`TokenUsageEventListener.java:22-60`）。`ResilientChatService` 优先取 `OperatorContext.currentOwnerId()`，否则使用调用方 userId（`ResilientChatService.java:371-373`、`OperatorContext.java:12-25`）。Harness 已以模型事件 ID 记录含任务上下文的幂等 `ModelUsageFact`（`AgentScopeTokenMeteringObserver.java:23-57`）。配额扣减与预警的权威契约见 [AI 计费门控](../../engine/governance/ai-quota-gating-tech.md)，本文不重复定义。

## 实现态

| 契约 | 实现态 |
|---|---|
| 请求级无任务状态与长期记忆 | ✅ 已实现 · `LlmClient.java:10-46`、`AgentScopeSpecCompiler.java:97-159` |
| L0 端口不接收业务身份 | ⚠️ 部分实现 · `LlmClient.java:19-32` 仍直接接收 `Long userId`；不透明计量归属尚未落地 |
| 同步、精确与流式基础调用 | ✅ 已实现 · `SpringAiLlmClient.java:20-61` |
| 完整请求/响应、物理调用追踪与结构化输出合同 | 🎯 目标态，当前不得声称已执行 |
| 可重试分类与 fallback | ⚠️ 部分实现 · `ResilientChatService.java:62-300` 已区分可重试错误并做单级 fallback；缺熔断、统一 attempt 事实与响应中的降级披露 |
| 两种调用形态的画像隔离 | ⚠️ 部分实现 · `PromptInvocationGateway.java:20-161` 与 `HarnessAgentExecutionAdapter.java:50-503` 已分入口；仍有绕过 Gateway 的裸 `LlmClient` 调用，逐物理调用 Envelope 尚未落地 |
| 非自主 Gateway 不新建持久 Task | ✅ 已实现 · Gateway 只校验、记录 preflight 并委托 `LlmClient`（`PromptInvocationGateway.java:29-61`）；当前两个调用方仅作 Role/Skill 选择（`DefaultRoleSelector.java:80-105`、`ModelSkillSelectionPort.java:35-53`），不调用持久任务端口；它们可运行在既有 Task 内，但不会为该 L0 invocation 新建 Task |
| Token 计量到归属主体 | ⚠️ 部分实现 · `ResilientChatService.java:55-73,371-373` 先取当前 `ownerId`、无上下文时才使用调用参数；`TokenUsageEvent.userId` 因而实际承载结算 owner，监听器以该字段结算（`TokenUsageEventListener.java:30-60`）。直连事件仍缺 tenant/task/execution/attempt 维度且 `usageId` 为本地 UUID；Harness 使用 provider 事件 ID（`AgentScopeTokenMeteringObserver.java:34-57`） |

## 验收基线

- 目标端口不接收或返回业务身份、任务状态与记忆句柄，只接收不透明计量归属
- 同一运行中的每个物理模型尝试都有独立标识、Envelope、真实 usage 与可追溯模型
- 非自主调用不产生持久任务、Harness Loop 或执行画像
- 结构化输出必须经过代码侧 Schema 校验，解析失败不能返回伪成功
- `MockLlmClient` 不进入模型 fallback 链，且生产部署必须另有配置门禁禁止 `aaf.llm.mock=true`
- 配额不足在 provider 调用前阻断；结算、预警与扣减只由引擎层执行
