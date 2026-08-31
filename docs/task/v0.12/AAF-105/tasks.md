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

- **状态**：[ ] 待开始
- **负责人**：architect
- **依赖**：无
- **范围**：
  - 记录决策：`CapabilityRouter` 在调用前解析唯一 `AiModel`，invoker 只调该模型，provider 层不静默切换。
  - 说明后果：删除 `ResilientChatService` 承担的 L0 多层 fallback 会改变可用性与成本语义，失败改为按 Function Contract 稳定失败。
  - 可作为独立 ADR 或 `core/model-router.md` 设计增补，由协调者定形式。
- **完成标准**：🔴 人类审核通过后方可启动 #10502。

### #10502 定义 NonAutonomousModelInvoker 与 AgentScope 实现

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10501、AAF-103 #10301
- **范围**：
  - 新增端口 `NonAutonomousModelInvoker`（同步 `ModelInvocationResult invoke(ModelInvocation)`，options 含 `responseSchema`/`thinkingBudget`/`reasoningEffort`）。
  - 唯一生产实现 `AgentScopeNonAutonomousModelInvoker`：`ModelMessage → Msg/ContentBlock`，options → `GenerateOptions`，内部消费 `Model.stream(...)` 并收敛（虚拟线程阻塞，不 pin carrier）。
  - 收敛规则：`TextBlock` 增量按 `ReasoningContext` 策略顺序拼接，usage 取最后非空快照，finishReason 取最后非空值。
  - `AgentScopeModelResolver` 新增 `resolve(AiModel)` 公共重载，现有 `resolve(ModelSpec)` 委托它；不新增第二套路由。
  - `PromptInvocationGateway` 改注入新端口，返回值由 `String` 改为 `ModelInvocationResult`。
- **完成标准**：`PromptInvocationGateway` 不再依赖 `LlmClient`；`compile` 通过。

### #10503 结构化输出统一入口

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10502
- **范围**：
  - 删除 `callExact` 第二方法；gateway 按 Function Contract 填 `responseSchema`，实现构造 `GenerateOptions.responseFormat`，统一走 `invoke`。
  - gateway 做严格 schema 解析；模型不支持 native structured output 时 L0 直接失败或调用前重选，不降级为 ReAct 工具循环。
  - 依据 core `supportsNativeStructuredOutput()` 判定能力。
- **完成标准**：无 `callExact` 旁路；非法模型输出 fail-closed；`compile` 通过。

### #10504 迁移全部智能层 L0 调用点

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10503
- **范围**：
  - 五个直接调用 `LlmClient` 的类改走 gateway：`ParameterExtractionNode`、`IntentUnderstandingService`、`EmotionPerceptionService`、`DefaultSessionContextCompressor`、`DefaultHybridContextCompressor`。
  - 已走 gateway 的 `DefaultRoleSelector`、`DefaultInputClassifier`、`ModelDrivenTaskComplexityAnalyzer`、`ModelSkillSelectionPort` 只需调整既有断言。
  - 业务类不得直接注入 invoker；只能经 gateway。
- **完成标准**：所有 L0 调用都有 Function Contract、preflight、logicalInvocationId；`compile` 通过。

### #10505 原子删除旧抽象

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10504
- **范围**：
  - 删除 `LlmClient`、`SpringAiLlmClient`；`MockLlmClient` 改为新端口的测试实现或直接用 fixture。
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
