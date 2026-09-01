---
level: Practice
layer: Product
purpose: 拆分 AAF-105 非自主 L0 单栈的端口替换、调用点迁移与旧抽象退出任务
status: draft
version: 1.0.0
date: 2026-09-01
author: AaronZZH & Kiro
tags:
  - AAF-105
  - L0
  - AgentScope
  - 技术任务
related:
  - ../../../design/audit/2026-09-01-harness-landing-plan.md
  - ../../../design/adr/ADR-003-virtual-threads-over-webflux.md
gains:
  - 能消除 Spring AI 与 AgentScope 两套模型栈的并行抽象
  - 能让 L0 失败语义稳定可预期
---

# AAF-105 非自主 L0 单栈任务

## 任务约束

- 技术真理源：[Harness 落地计划 · 非自主 L0 改为 AgentScope 直调](../../../design/audit/2026-09-01-harness-landing-plan.md)。
- 并发模型约束：[ADR-003](../../../design/adr/ADR-003-virtual-threads-over-webflux.md)——**签名保持同步**，不把非流式路径响应式化，不新增 `Flux` 重载。
- 风险等级：🔴 高（接口删除 + 模型路由与成本语义变更）。
- 禁兼容层：`LlmClient` 与新端口不得同时成为 Spring Bean；不保留适配器桥接旧接口。
- **阶段约束**：断言补进既有测试，不新增测试文件；不执行 `check` / `acceptance`；准出最低要求 `pnpm nx compile service` 通过。

## 技术任务

### #10501 立 ADR：L0 模型选择与 fallback 语义

- **状态**：✅ 已完成（2026-09-01，人类已审核通过——复用零工具 ReActAgent 方案）— Kiro
- **负责人**：architect
- **依赖**：无
- **范围**：
  - ✅ 已产出 [ADR-007](../../../design/adr/ADR-007-l0-model-invoker-unification.md)（`proposed`，人类已拍板方向）：记录决策——**不新建 `NonAutonomousModelInvoker` 端口，直接复用零工具、无状态配置的 `ReActAgent`** 作为 L0 唯一调用入口。
  - ✅ 说明后果：删除 `ResilientChatService` 承担的 L0 多层 fallback 会改变可用性与成本语义，失败改为按 Function Contract 稳定失败，不静默切模型（不设 `.fallbackModel(...)`）。
  - ✅ 已核实并记录真实限制：`ReActAgent` 结构化输出的降级路径（原生失败或不支持时自动切换合成 `generate_response` 工具）**硬编码无法关闭**，AAF 必须在调用前用 `model.supportsNativeStructuredOutput()` 做前置检查，不满足直接 fail-closed，不进入 `ReActAgent` 调用。
- **完成标准**：🔴 人类审核通过后方可启动 #10502。
- **实际结果**：人类拍板复用 `ReActAgent`（推翻最初"新建端口"方案），ADR-007 已更新为 v2.0.0，#10502 解锁。

### #10502 装配 L0 专用零工具 ReActAgent 单例与 Function Contract 注入

- **状态**：✅ 已完成（2026-09-01）— developer-service
- **负责人**：developer-service
- **依赖**：#10501、AAF-103 #10301
- **范围**：
  - ✅ 新增 `FunctionContractSystemPrompt`（`core/prompt` 包，per-call system prompt 载体）+ `FunctionContractPromptMiddleware`（`onSystemPrompt` 钩子，未携带契约直接失败）。
  - ✅ `BoundedAgentCache` 从 `AgentScopeSpecCompiler` 私有嵌套类提取为独立公共类（同包 `infrastructure/agentscope/compiler`），供 L0 场景复用，不重新发明缓存逻辑。
  - ✅ 新增 `L0ReActAgentFactory`（`infrastructure/agentscope/model` 包）：按 `AiModel.getModelId()` 分桶缓存零工具 `ReActAgent` 实例（不设 `stateStore`/`fallbackModel`），`implements Function<AiModel, ReActAgent>, AutoCloseable`。
  - ✅ `AgentScopeModelResolver` 新增 `resolve(AiModel)` 公共重载；`resolve(ModelSpec)` 改为查出 `AiModel` 后委托它。
  - ✅ 重写 `PromptInvocationGateway`：构造器改为 `(Function<AiModel,ReActAgent>, CapabilityRouter)`，内部按 `routeScene` 走 `CapabilityRouter.resolve(...)` 解析模型后从工厂取（或建）对应 `ReActAgent` 实例；`call(...)` 返回值由 `String` 改为 `ModelInvocationResult`（`text`/`inputTokens`/`outputTokens`/`finishReason`）。
  - ✅ `AgentScopeInfrastructureAutoConfiguration` 新增 `l0ReActAgentFactory`/`promptInvocationGateway` 两个 Bean（`destroyMethod = "close"` 释放缓存）；`@AutoConfigureAfter` 追加 `AiAutoConfiguration`（`CapabilityRouter` 依赖）。
- **完成标准**：`PromptInvocationGateway` 不再依赖 `LlmClient`；`compile` 通过。
- **实际结果**：`pnpm nx compile service` BUILD SUCCESS。**实际改动范围超出本任务原描述**——新旧 `PromptInvocationGateway` 构造器冲突无法与调用方分割处理，本次同批完成了：`AssistantInfrastructureAutoConfiguration` 删除 3 处基于 `LlmClient` 的 Bean 定义（`promptInvocationGateway`/`modelRoleSelector`/`modelSkillSelectionPort`，改为 `@ConditionalOnBean(PromptInvocationGateway.class)`）；4 个已接入 gateway 的调用点（`DefaultRoleSelector`/`DefaultInputClassifier`/`ModelDrivenTaskComplexityAnalyzer`/`ModelSkillSelectionPort`）同步改为读 `response.text()`——这部分原属于 #10504 范围，因技术上无法拆分而提前完成，详见 #10504 更新说明。`contextCompressionPort`（依赖 `LlmClient` 构造 `DefaultHybridContextCompressor`）未改动，保留给 #10504。

### #10503 结构化输出统一入口与前置能力检查

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10502
- **范围**：
  - 删除 `callExact` 第二方法；gateway 按 Function Contract 需要结构化输出时，调用前先判断 `model.supportsNativeStructuredOutput()`（或 `supportsNativeStructuredOutputWithTools()`，取决于是否同时有工具），不满足直接 fail-closed，不调用 `ReActAgent.call(msgs, schemaClass, ctx)`——这是唯一需要 AAF 自己包一层的逻辑，因为 `ReActAgent` 内部的降级路径硬编码无法关闭。
  - 满足原生能力时统一走 `ReActAgent.call(msgs, schemaClass, ctx)`，复用官方的 schema 生成与解析，不重新实现。
- **完成标准**：无 `callExact` 旁路；模型不支持原生结构化输出时在调用前 fail-closed，不产生一次实际的降级工具调用；`compile` 通过。

### #10504 迁移剩余智能层 L0 调用点

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10503
- **范围**：
  - ⚠️ 范围已收窄——4 个原计划迁移的调用点（`DefaultRoleSelector`/`DefaultInputClassifier`/`ModelDrivenTaskComplexityAnalyzer`/`ModelSkillSelectionPort`）已随 #10502 提前完成（新旧 gateway 构造器冲突无法分割处理）。
  - 剩余五个直接调用 `LlmClient` 的类改走 gateway：`ParameterExtractionNode`、`IntentUnderstandingService`、`EmotionPerceptionService`、`DefaultSessionContextCompressor`、`DefaultHybridContextCompressor`（含 `AssistantInfrastructureAutoConfiguration.contextCompressionPort` 装配同步调整，不再依赖 `LlmClient`）。
  - 业务类不得直接注入 `L0ReActAgentFactory`/`ReActAgent`；只能经 gateway。
- **完成标准**：所有 L0 调用都有 Function Contract、preflight、logicalInvocationId；`compile` 通过。

### #10505 原子删除旧抽象

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10504
- **范围**：
  - 删除 `LlmClient`、`SpringAiLlmClient`；`MockLlmClient` 改为测试 fixture。
  - 调整 `AssistantInfrastructureAutoConfiguration` 的条件装配，确保生产 Bean 图无旧接口。
  - `ResilientChatService` 保留给四个非 L0 用户（`TrendingService`、`MeetingOrganizeService`、`ToolGenerator`、`AiEnricher`），本批不重构它们，但标注其已退出智能层 L0。
- **完成标准**：智能层生产 Bean 图无 `LlmClient`/`SpringAiLlmClient`；无 shim / 双 Bean；`compile` 通过。

## 新增任务

> 开发过程中发现需要新增的任务，由开发者提出，协调者评估后写入

## 评审状态（🔴 高风险适用）

| 阶段 | 执行次数 | 最后执行 | 状态 | 必须 |
|------|---------|---------|------|------|
| product（需求细化） | 0 | — | ⏳ PENDING | 🔴 是 |
| architect（技术设计） | 0 | — | ⏳ PENDING | 🔴 是 |
| designer（UI 审查） | — | — | — | 不涉及前端 |
| developer（编码） | 0 | — | ⏳ PENDING | 🔴 是 |
| architect（代码审查） | 0 | — | ⏳ PENDING | 🔴 是 |
| tester（验收测试） | 0 | — | ⏳ PENDING | 🔴 是 |
| qa（过程审计） | 0 | — | ⏳ PENDING | 🔴 是 |
