---
level: Reality
layer: Model
purpose: 测绘 AAF 对 AgentScope Java v2 的实际复用边界并核验五层智能架构实现完成度
status: draft
version: 1.0.0
date: 2026-08-30
author: AaronZZH
---

# AgentScope 复用边界与五层实现测绘

## 结论摘要

本次静态审计扫描了 `tmp/agentscope-java/agentscope-core/src/main/java`、`tmp/agentscope-java/agentscope-harness/src/main/java`，并逐项对照 `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/`。统计口径是下文四张逐项表中的 59 个能力条目：**复用 10 项、自研替代 47 项、未使用 2 项**。

最重要的结论如下：

- AAF 的真实策略不是“复用 AgentScope Harness 编排”，而是“复用 AgentScope core 的模型、消息、工具、状态、事件、middleware 扩展面和 ReAct 循环，同时以 AAF 外层编排替代 Harness 大部分上层能力”。直接证据是 `AgentScopeSpecCompiler.compileDynamicNew/compileNew` 只构造叶子 `HarnessAgent`，并关闭内建记忆、Skill、子智能体、文件/Shell、压缩等能力；见 `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java:108-204`。
- “Harness 工作区全部关闭”只在**语义能力**层面成立，不在对象装配层面成立。`HarnessAgent.Builder.build()` 仍无条件解析默认 workspace、构造默认 `LocalFilesystem`、`WorkspaceManager`、`WorkspaceMessageBus` 与 `WorkspaceAsyncToolRegistry`，随后挂载 `InboxMiddleware` 并注册 `WaitAsyncResultsTool`；见 `tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java:1956-2497` 与 `HarnessAgentBuilderSupport.resolveFilesystem:136-157`。这是当前最硬的边界泄漏证据。
- 五层架构并非文档空壳。Core、Cognition、Agent、Assistant 均有生产实现；Team 也已有静态 roster、版本生命周期、统一入口和 Worker 调度。Team 当前是 **MVP 实现而非仅设计文档**；证据包括 `TeamDefinition`、`DefinitionLifecycleService`、`AssistantExecutionService.startTeam/resolveTeam/teamBoard`、`DelegatedTaskCoordinator.executeBoard`。

处置标签定义：

- `复用`：AAF 有意依赖该 AgentScope 能力，或该能力形成了模型可调用/可观察的运行时表面；非预期残留会特别标注。
- `自研替代`：该能力的语义职责和真理源由 AAF 类、端口或存储承担。Harness 内部即使仍构造辅助对象，也不改变职责归属，但必须作为边界泄漏单列风险。
- `未使用`：AAF 未直接接入，也未在本边界内实现等价能力。Provider 内部可能使用的 AgentScope 私有实现细节不算 AAF 直接复用。

## AgentScope core 逐项对照

| ID | AgentScope 能力域 | 处置 | AAF 落点与证据 | 判断 |
|---|---|---|---|---|
| C01 | model：`Model`、`ChatModelBase` 与厂商实现 | 复用 | `AgentScopeModelResolver.resolve/build` 返回 `Model`，直接构造 `AnthropicChatModel`、`DashScopeChatModel`、`OpenAIChatModel`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/model/AgentScopeModelResolver.java:18-92` | AAF 自持模型登记与路由，但 provider 调用对象直接复用 AgentScope。 |
| C02 | message：`Msg`、`ContentBlock` 及多模态块 | 复用 | `AgentScopeMessageMapper.toAgentScope` 构造 `Msg`、`TextBlock`、`ImageBlock`、`URLSource`；`.../infrastructure/agentscope/mapping/AgentScopeMessageMapper.java:17-59` | AAF 自持 `AgentMessage`，在唯一基础设施边界映射为 AgentScope 消息。 |
| C03 | tool：`Toolkit`、`@Tool`/反射工具、`ToolResponse`/`ToolResultBlock` | 复用 | `AgentScopeToolkitFactory.create` 构造 `Toolkit`；`PortBackedAgentTool` 继承 `ToolBase` 并返回 `ToolResultBlock`；`.../tool/AgentScopeToolkitFactory.java:15-50`、`.../tool/PortBackedAgentTool.java:20-88` | 复用工具 schema、调用协议与 ReAct observation；真实副作用由 AAF `ToolGatewayPort` 接管。 |
| C04 | agent：`ReActAgent`、`AgentBase`、`RuntimeContext` | 复用 | AAF 复用 `HarnessAgent`，其 delegate 是 `ReActAgent`；执行端调用 `HarnessAgent.streamEvents(..., RuntimeContext)`；`.../execution/HarnessAgentExecutionAdapter.java:236-339` | 这是 AAF 对 AgentScope 的核心复用价值。 |
| C05 | state：`AgentState`、`AgentStateStore` | 复用 | `AgentScopeInfrastructureAutoConfiguration.agentScopeAgentStateStore` 装配 `RedisAgentStateStore`，`AgentScopeSpecCompiler` 注入同一 store 并断言实例一致；`.../spring/AgentScopeInfrastructureAutoConfiguration.java:54-76`、`.../compiler/AgentScopeSpecCompiler.java:108-204` | 状态存储复用 AgentScope 协议，连接复用 Spring Redis；不是 AAF 自造另一套 ReAct state。 |
| C06 | memory：`Memory`、`LongTermMemory`、`StateBackedMemory`、hooks/tools | 自研替代 | `MemoryGovernanceService.learn/remember`、`DefaultL1ContextCollaborator.resolve`、`JpaCognitionMemoryAdapter`、`RedisSessionMemoryAdapter`；同时编译器调用 `disableMemoryTools/disableMemoryHooks`；`.../compiler/AgentScopeSpecCompiler.java:128-204` | 长短期认知归 L1；AgentScope memory 不进入主链。 |
| C07 | event：`AgentEvent`、`AgentEventType` 及类型化事件 | 复用 | `HarnessAgentExecutionAdapter.executeResolved/onSourceEvent` 消费 `Flux<AgentEvent>`；`AgentScopeEventMapper.map` 按 `AgentEventType` 收敛；`.../execution/HarnessAgentExecutionAdapter.java:236-339`、`.../mapping/AgentScopeEventMapper.java:38-147` | AAF 复用源事件，但只投影白名单语义，思维链和工具参数不出边界。 |
| C08 | middleware：`MiddlewareBase`、`MiddlewareChain` | 复用 | `PromptEnvelopeCaptureMiddleware` 是 AAF 自定义 core middleware，并由 `AgentScopeSpecCompiler.middleware(envelopeCapture)` 注册；`.../compiler/AgentScopeSpecCompiler.java:119-204` | 扩展协议复用；AAF 自定义的是策略，不是 middleware 框架。 |
| C09 | session：基于 `AgentStateStore` 的 user/session bucket 与 `SessionInfo` | 自研替代 | AAF 使用 `SessionMemoryPort`、`RedisSessionMemoryAdapter` 与 `DefaultL1ContextCollaborator.sessionMessage` 管理聊天会话；`.../infrastructure/cognition/memory/RedisSessionMemoryAdapter.java` | AgentScope 的 bucket 只承载 C05 已统计的 ReAct 工作态，不是 AAF 会话历史真理源。`disableSessionPersistence()` 在 AgentScope 2.0 已是 no-op，见 `HarnessAgent.java:1860-1869`；AAF 以 `HarnessAgentExecutionAdapter.requireNoHiddenPersistentHistory:342-366` 阻断隐藏历史。 |
| C10 | plan：任务/计划状态 | 自研替代 | `TaskBoard`、`CoordinationPlan`、`DecompositionBudget`、`DelegatedTaskCoordinator.decodeAndValidatePlan/applyCoordinationPlan`；`.../assistant/model/TaskBoard.java`、`.../assistant/model/CoordinationPlan.java`、`.../assistant/application/DelegatedTaskCoordinator.java:953-1106` | 计划、DAG、迭代和完成责任归 AAF 外层，不使用 AgentScope plan mode。 |
| C11 | rag：`Knowledge`、`GenericRAGHook`、`KnowledgeRetrievalTools` | 自研替代 | `DefaultUnifiedRetrievalPort.retrieve` + `HybridSearchService.search` + `DefaultL1ContextCollaborator.appendRetrieval`；`.../cognition/application/DefaultUnifiedRetrievalPort.java:39-304` | 知识授权、Memory/Knowledge 融合和上下文预算由 AAF L1 管理。 |
| C12 | embedding | 自研替代 | `intelligent/ai/embedding/EmbeddingService`、`infrastructure/cognition/KnowledgeEmbeddingAdapter`，知识侧另有 `engine/knowledge/embedding/EmbeddingService`；调用证据见 `DefaultUnifiedRetrievalPort.retrieve` | AgentScope core 快照未发现独立 embedding 包；AAF 使用自身 Spring AI/知识引擎链。 |
| C13 | formatter：`Formatter`、`ResponseFormat`、`JsonSchema` | 未使用 | AAF AgentScope 边界无 `io.agentscope.core.formatter` import；结构化输出在 AAF 侧由 `AssistantExecutionService.outputContractProjection/validateTerminalOutput` 校验，`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/assistant/service/AssistantExecutionService.java:321-372,944-966` | Provider 内部格式化属于 AgentScope 实现细节，不计为 AAF 直接复用。 |
| C14 | tracing / observability：`OtelTracingMiddleware`、Tracer | 自研替代 | `enableAgentTracingLog(false)`；AAF 使用 `ExecutionEventStorePort`、`JpaExecutionEventStoreAdapter`、`PromptEnvelopeCaptureMiddleware`、`AgentScopeTokenMeteringObserver`；`.../compiler/AgentScopeSpecCompiler.java:128-204` | AAF 自持任务事件、Prompt 快照、计量与审计，不接 AgentScope tracing。 |
| C15 | util：`JsonUtils`、`MessageUtils`、`TypeUtils` 等 | 未使用 | AAF 边界使用自身 `JsonUtils`/Jackson 与显式 mapper，未发现 `io.agentscope.core.util` import；证据范围 `.../infrastructure/agentscope/**` | 无必要建立第二层 util 依赖。 |

## AgentScope harness 领域逐项对照

| ID | Harness 能力域 | 处置 | AAF 落点与证据 | 判断 |
|---|---|---|---|---|
| H01 | `HarnessAgent` 本体 | 复用 | `AgentScopeSpecCompiler.compileNew/compileDynamicNew` 构造；`HarnessAgentExecutionAdapter.executeResolved` 调用 `streamEvents`；`.../compiler/AgentScopeSpecCompiler.java:108-204`、`.../execution/HarnessAgentExecutionAdapter.java:236-339` | 明确复用，但用途被收窄为 ReAct + 工具调用。 |
| H02 | workspace：`WorkspaceManager`、`WorkspaceIndex` | 自研替代 | AAF 用 `DefaultL1ContextCollaborator`、`ContextRequest.taskMaterials`、`ScopedFileSystemPort` 管理受控上下文与材料；`.../cognition/application/DefaultL1ContextCollaborator.java:28-354` | 语义职责已替代；但 `HarnessAgent.Builder.build` 仍解析 `.agentscope/workspace` 并无条件构造 `WorkspaceManager`，见 `HarnessAgent.java:1956-2497`，属于**残留基础设施风险**。 |
| H03 | sandbox：`SandboxManager`、Docker sandbox、snapshot | 自研替代 | AAF `SandboxPort`、`GovernedSandboxAdapter.execute` 与 `DefaultToolGateway`；`.../agent/port/SandboxPort.java`、`.../infrastructure/governance/GovernedSandboxAdapter.java:31-144` | 编译器不设置 `sandboxFilesystemSpec`，故 Harness `SandboxLifecycleMiddleware` 不装配。 |
| H04 | filesystem：Local/Remote/Sandbox/Overlay filesystem | 自研替代 | 业务文件能力由 AAF `ScopedFileSystemPort`、`LocalScopedFileSystemAdapter` 与 ToolGateway 承担；`.../agent/port/ScopedFileSystemPort.java`、`.../infrastructure/governance/LocalScopedFileSystemAdapter.java` | `FilesystemTool` 已关闭；但 `HarnessAgentBuilderSupport.resolveFilesystem:136-157` 仍默认创建 `LocalFilesystemSpec` 供 workspace/bus 内部使用，属于**残留基础设施风险**。 |
| H05 | skill runtime：仓储、`SkillRuntime`、`SkillLoadTool` | 自研替代 | `SkillCatalogPort`、`JpaSkillCatalogAdapter`、`DefaultEffectiveSkillResolver`、`PromptAssembler.appendSkills`；编译器同时 `disableDynamicSkills/disableDefaultWorkspaceSkills/skillsEnabled(false)` | Skill 真理源与激活在 AAF，Harness runtime 不进入主链。 |
| H06 | skill curator：候选、扫描、推广、审计 | 自研替代 | `LearningCandidateApplicationService.capture/review/publish/reject`、`JpaLearningCandidateAdapter`、`MemoryGovernanceService`；`.../cognition/application/LearningCandidateApplicationService.java` | AAF 候选发布需治理；Harness curator 默认开关也为 false。 |
| H07 | subagent：声明、manager、factory、动态扫描 | 自研替代 | `SubagentSpec`、`TaskBoard`、`DelegatedTaskCoordinator.executeBoard/executeSubTask`；编译器调用 `disableSubagents/disableDynamicSubagents` | AAF 外层创建多个叶子 Agent，禁止父 Harness 在 ReAct 内自主扩张。 |
| H08 | subagent task / `BackgroundTask` / repository | 自研替代 | `DelegatedTask`、`DelegatedTaskPort`、`JpaDelegatedTaskAdapter`、`DelegatedTaskScheduler`、`AgentTaskRuntime`；`.../assistant/model/DelegatedTask.java`、`.../infrastructure/assistant/persistence/JpaDelegatedTaskAdapter.java` | 持久任务、租约、fencing、恢复与预算全部归 AAF。 |
| H09 | memory compaction：`ConversationCompactor` | 自研替代 | `DefaultHybridContextCompressor.compress`、`DefaultSessionContextCompressor.summarize`；编译器 `disableCompaction`；`.../cognition/application/DefaultHybridContextCompressor.java` | 压缩前后画像需要冻结和审计，故由 AAF 管理。 |
| H10 | memory consolidator：`MemoryConsolidator` | 自研替代 | `MemoryGovernanceService`、`DefaultUnifiedRetrievalPort`、`LearningCandidateApplicationService` | AAF 以治理、去重、冲突和候选发布替代 Harness 文件式 consolidation。 |
| H11 | memory flush：`MemoryFlushManager` | 自研替代 | `MemoryGovernanceService.learn/remember`、`MemoryWritePort`、`JpaCognitionMemoryAdapter.append`；编译器 `disableMemoryHooks` | 执行结果不能直接旁路写长期记忆。 |
| H12 | memory session tree：`SessionTree`、freshness | 自研替代 | `SessionMemoryPort`、`RedisSessionMemoryAdapter.recentTurns/appendTurn`、`DefaultL1ContextCollaborator.sessionMessage`；`.../infrastructure/cognition/memory/RedisSessionMemoryAdapter.java` | AAF 会话上下文按 tenant/user/session 管理，不使用 workspace session tree。 |
| H13 | gateway：`HarnessGateway`、wakeup、subagent registry | 自研替代 | `AssistantAguiController.run`、`DelegatedTaskCoordinator`、`AafAiTaskEventRegistry`、`DelegatedTaskEventStreamService` | 外部交互与唤醒由 AAF API、任务运行时和事件投影承担。 |
| H14 | gateway channel：router、ChatUI channel、peer | 自研替代 | `AssistantAguiController.run/executionStream` 是统一 AG-UI SSE 入口；外部渠道由 `apps/service/aaf-api/.../module/channel` 适配 | 未启用 Harness channel；AAF channel 位于交互/业务层。 |
| H15 | gateway bus：`MessageBus`、`AsyncToolRegistry` | 自研替代 | AAF 使用 `ExecutionEventStorePort`、`TaskInputPort`、`DelegatedTaskEventStreamService`、通知 outbox 与持久任务运行时；`.../assistant/port/ExecutionEventStorePort.java`、`.../assistant/port/TaskInputPort.java` | 语义职责已替代；但 builder 仍默认创建 `WorkspaceMessageBus`/`WorkspaceAsyncToolRegistry` 并触发 Inbox/Wait，见 `HarnessAgent.java:1956-2497`，属于**残留基础设施风险**。 |

### Harness middleware 实际清单

当前源码快照存在 **16 个具体 `*Middleware` 类**，不是任务描述中的 14 个；另有 `HarnessRuntimeMiddleware` 标记接口，不作为能力条目计数。目录证据：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/`。

| ID | Harness middleware | 处置 | AAF 替代或实际状态 | 证据 |
|---|---|---|---|---|
| M01 | `AgentTraceMiddleware` | 自研替代 | AAF 关闭 tracing log，使用持久 `ExecutionEvent`、PromptEnvelope、计量 | `AgentScopeSpecCompiler.enableAgentTracingLog(false)`；`HarnessAgent.Builder.agentTracingLogEnabled=true` 默认见 `HarnessAgent.java:1008-1075` |
| M02 | `AsyncToolMiddleware` | 自研替代 | AAF 用 `DelegatedTaskCoordinator`、`AgentTaskRuntime`、outbox；Harness 因 `asyncToolTimeout == null` 不装配 | `HarnessAgent.Builder.build:1956-2497` |
| M03 | `AtPathExpansionMiddleware` | 自研替代 | AAF 以 `ContextRequest.taskMaterials`、`ScopedFileSystemPort` 显式授权材料，不做 `@path` 隐式扩展 | 编译器 `disableAtPathExpansion`；`AgentScopeSpecCompiler.java:128-204` |
| M04 | `CompactionMiddleware` | 自研替代 | `DefaultHybridContextCompressor` + 冻结 `ExecutionProfileSnapshot` | 编译器 `disableCompaction` |
| M05 | `DynamicSubagentsMiddleware` | 自研替代 | `TaskBoard` + `DelegatedTaskCoordinator` | 编译器 `disableDynamicSubagents` |
| M06 | `HarnessSkillMiddleware` | 自研替代 | `DefaultEffectiveSkillResolver` + `PromptAssembler` + AAF Skill 真理源 | 编译器 `disableDynamicSkills/disableDefaultWorkspaceSkills/skillsEnabled(false)` |
| M07 | `InboxMiddleware` | 复用 | 默认 `messageBus != null`，故仍被装配；AAF 未显式请求此 middleware | `HarnessAgent.Builder.build:1956-2497` 中 message bus 默认与 `inner.middleware(new InboxMiddleware(...))` 分支 |
| M08 | `MemoryFlushMiddleware` | 自研替代 | `MemoryGovernanceService` + `MemoryWritePort` | 编译器 `disableMemoryHooks` |
| M09 | `MemoryMaintenanceMiddleware` | 自研替代 | `MemoryGovernanceService`、记忆仓储治理、学习候选 | 编译器 `disableMemoryHooks` |
| M10 | `PlanModeMiddleware` | 自研替代 | `CoordinationPlan`、`TaskBoard`、`CompletionValidator` | `planModeEnabled=false` 默认；`HarnessAgent.java:1008-1075` |
| M11 | `SandboxLifecycleMiddleware` | 自研替代 | `GovernedSandboxAdapter` 与 ToolGateway | AAF 不设置 `sandboxFilesystemSpec`；builder 仅在该 spec 非空时装配 |
| M12 | `SkillCuratorMiddleware` | 自研替代 | `LearningCandidateApplicationService` | `skillCuratorEnabled=false` 默认 |
| M13 | `SkillUsageMiddleware` | 自研替代 | `SkillDecisionAuditPort`、`ExecutionProfileSnapshotPort`、事件存储 | `skillManageToolEnabled=false` 默认，故不装配 |
| M14 | `SubagentsMiddleware` | 自研替代 | `DelegatedTaskCoordinator.executeBoard/executeSubTask` | 编译器 `disableSubagents` |
| M15 | `ToolResultEvictionMiddleware` | 自研替代 | AAF 上下文压缩和冻结画像；工具证据另由 `ToolResultEvidenceStore` 一次性传递 | 编译器 `disableToolResultEviction` |
| M16 | `WorkspaceContextMiddleware` | 自研替代 | `DefaultL1ContextCollaborator` + `PromptAssembler` + `CompiledSystemPrompt` | 编译器 `disableWorkspaceContext` |

### Harness tool 实际清单

| ID | Harness tool | 处置 | AAF 替代或实际状态 | 证据 |
|---|---|---|---|---|
| T01 | `FilesystemTool` | 自研替代 | `ScopedFileSystemPort` + `LocalScopedFileSystemAdapter` + ToolGateway | 编译器 `disableFilesystemTools` |
| T02 | `ShellExecuteTool` | 自研替代 | `SandboxPort` + `GovernedSandboxAdapter` + ToolGateway | 编译器 `disableShellTool` |
| T03 | `SessionSearchTool` | 自研替代 | `SessionMemoryPort.recentTurns` + L1 上下文注入 | 编译器 `disableMemoryTools` |
| T04 | `WaitAsyncResultsTool` | 复用 | 因默认 `WorkspaceMessageBus` 非空，builder 无条件注册该工具；即使 subagent middleware 被关闭，仍传入 null task repository | `HarnessAgent.Builder.build:1956-2497` 中 `if (messageBus != null)` 注册分支 |
| T05 | `AgentSpawnTool` | 自研替代 | `DelegatedTaskCoordinator` + `TaskBoard` + `SubagentSpec` | 编译器关闭两类 subagent middleware |
| T06 | `TaskTool` | 自研替代 | `TaskBoard`、`DelegatedTask`、`TaskTransitionPort` | 同上 |
| T07 | `SkillManageTool` | 自研替代 | API `SkillService`、`SkillStore`、`JpaSkillCatalogAdapter`、发布治理 | `skillManageToolEnabled=false` 默认 |
| T08 | `ProposeSkillTool` | 自研替代 | `LearningCandidateApplicationService.capture` | 同上 |
| T09 | `PlanModeTools` | 自研替代 | `CoordinationPlan`、`TaskBoard` 与严格计划解析 | `planModeEnabled=false` 默认 |
| T10 | `MemorySearchTool` | 自研替代 | `MemoryRetrievalPort`、`DefaultUnifiedRetrievalPort` | 编译器 `disableMemoryTools` |
| T11 | `MemorySaveTool` | 自研替代 | `MemoryGovernanceService.remember/learn`、`MemoryWritePort` | 编译器 `disableMemoryTools` |
| T12 | `MemoryGetTool` | 自研替代 | `MemoryRecallPort`、`MemoryManagementPort` | 编译器 `disableMemoryTools` |
| T13 | `AgentGenerateTool` | 自研替代 | `AgentDefinitionPort`、`JpaAgentDefinitionAdapter`；Team/Automation 定义使用统一 `DefinitionLifecycleService` | 动态 subagent 与 Skill 自管理均关闭，工具未注册 |

`FuzzyTextMatcher` 是 harness tool 包中的辅助类而非 Tool，不计入 59 项能力统计；源码位置 `tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/tool/FuzzyTextMatcher.java`。

## 自研 Harness 编排层评价

### 质量判断

AAF 外层不是一个轻量 wrapper，而是完整的企业级、可恢复任务运行时：

- **持久任务与状态机**：`DelegatedTask`、`TaskBoard`、`TaskTransitionPort`、`JpaTaskTransitionAdapter`；创建、输入消费、授权等待、澄清、完成与失败通过原子 transition 落库。证据：`DelegatedTaskCoordinator.submit/dispatch/executeBoard/finalizeBoard`，`.../infrastructure/assistant/persistence/JpaTaskTransitionAdapter.java`。
- **租约与 fencing**：`ConversationLeasePort` + `RedisConversationLeaseAdapter`，调度和每次 Agent/Tool 调用都验证当代租约。证据：`DelegatedTaskCoordinator.dispatch/withHeartbeat`、`HarnessAgentExecutionAdapter.requireCurrent`、`DefaultToolGateway.requireCurrent`。
- **工具治理**：有效工具先冻结为精确版本 `ToolRef`，再由 `AgentScopeToolkitFactory` 包装成 `PortBackedAgentTool`，真实调用必须经过 `DefaultToolGateway` 的可见性、授权、参数、凭证、幂等 receipt 与预算检查。证据：`.../agent/application/DefaultToolGateway.java:49-429`。
- **可观察与脱敏**：AgentScope 20+ 事件仅白名单映射；任务事件顺序入库；Prompt 与 token 另行冻结/计量。证据：`AgentScopeEventMapper.map`、`HarnessAgentExecutionAdapter.executeResolved`、`PromptEnvelopeCaptureMiddleware.onModelCall`。
- **上下文与认知治理**：短期会话、长期记忆、知识和任务材料由 L1 按 scope、披露和预算组装，不让 Harness workspace/Memory 文件成为第二真理源。证据：`DefaultL1ContextCollaborator.resolve`、`DefaultUnifiedRetrievalPort.retrieve`。

因此，自研层的主要问题不是“质量低”，而是**职责很重、与 AgentScope Harness 能力面高度重叠，且当前底层类型选成 HarnessAgent 后仍带入了未显式请求的 workspace/bus 行为**。

### 重复造轮子判定

| 重叠能力 | 判定 | 原因 |
|---|---|---|
| 模型、消息、工具 schema、ReAct、类型化事件、state store | 不应自研，当前复用正确 | 这些是稳定、无 AAF 业务语义的底层机制。 |
| 持久子任务、租约、fencing、HITL、预算、幂等 receipt、任务事件 | 必要自研，不属于无意义重复 | AgentScope Harness 的 workspace task/subagent 不能表达 AAF 的 tenant、授权、审计和恢复合同。 |
| Memory、Skill、RAG、上下文压缩 | 有意替代，边界合理但成本高 | AAF 需要单一业务真理源、用户可管理性与发布治理；不能同时启用 Harness 文件式真理源。 |
| workspace/filesystem/message bus/inbox/wait tool | 当前存在非必要重复 | AAF 已有替代，而 Harness 仍在内部构造并启用部分设施；这不是设计选择，而是 `HarnessAgent.Builder` 默认装配造成的残留。 |
| plan mode 与 AAF `CoordinationPlan` | 名称相近但语义不同 | Harness plan mode 是单 Agent 只读设计阶段；AAF plan 是外层 DAG/Worker 合同。当前关闭前者是正确边界。 |
| AgentScope formatter/util | 不值得显式接入 | AAF 已有边界 mapper 和输出合同，额外暴露只会扩大耦合。 |

## HarnessAgent 编译期边界证据

### AAF 主动设置的 builder 选项

`compileDynamicNew` 与 `compileNew` 两条路径设置完全对称；证据分别为 `AgentScopeSpecCompiler.java:108-159` 与 `:161-204`。

| Builder 选项 | AAF 输入来源 | 作用 |
|---|---|---|
| `agentId` | Dynamic identifier 或 `AgentSpec.agentId` | 稳定运行标识 |
| `name` / `description` | `SubagentSpec` 或 `AgentSpec` | 运行描述 |
| `sysPrompt` | 已校验 `CompiledSystemPrompt.content` | System 唯一来源 |
| `model` | `AgentScopeModelResolver.resolve(ModelSpec)` | 精确 AAF 模型真理源 |
| `toolkit` | `AgentScopeToolkitFactory.create(effectiveTools)` | 精确版本、已治理工具集 |
| `stateStore` | Spring 装配的共享 `AgentStateStore` | 避免默认本地 JsonFile store |
| `middleware` | `PromptEnvelopeCaptureMiddleware` | 每次物理模型调用冻结 Envelope |
| `maxIters` | `ExecutionPolicy.maxIterations` | ReAct 内层预算 |
| `maxRetries` | `ExecutionPolicy.maxModelRetries` | 模型重试预算 |

编译后还断言 `agent.getStateStore() == stateStore`，不一致则立即关闭并失败；见 `AgentScopeSpecCompiler.compileDynamicNew/compileNew`。这是状态边界的有效 fail-closed 保护。

### AAF 显式关闭的能力

| 调用 | 关闭内容 |
|---|---|
| `disableMemoryTools()` | MemorySearch/Get/Save 与 SessionSearch 工具 |
| `disableMemoryHooks()` | flush 与 maintenance middleware |
| `disableWorkspaceContext()` | workspace 上下文注入 |
| `disableAtPathExpansion()` | `@path` 隐式文件展开 |
| `disableSubagents()` | 静态 subagent middleware |
| `disableDynamicSubagents()` | 动态扫描、spawn/generate/task 工具 |
| `disableDynamicSkills()` | Harness 动态 Skill middleware |
| `disableDefaultWorkspaceSkills()` | workspace 默认 Skill 仓储 |
| `disableToolsConfig()` | `workspace/tools.json`、MCP 注册和 allow/deny |
| `disableFilesystemTools()` | FilesystemTool |
| `disableShellTool()` | ShellExecuteTool |
| `disableCompaction()` | CompactionMiddleware |
| `disableToolResultEviction()` | ToolResultEvictionMiddleware |
| `skillsEnabled(false)` | Skill filter 设为空 |
| `enableAgentTracingLog(false)` | AgentTraceMiddleware |

AAF 只注册一个自定义 AgentScope middleware：`PromptEnvelopeCaptureMiddleware`。`AgentScopeTokenMeteringObserver` **不是 middleware**，它在 `HarnessAgentExecutionAdapter.executeResolved` 的 `doOnNext` 中观察事件；见 `.../execution/HarnessAgentExecutionAdapter.java:270-279`。

### Harness 默认开启项与 AAF 实际结果

| Harness 默认或无条件行为 | 默认证据 | AAF 是否关闭 | 实际结果与风险 |
|---|---|---|---|
| agent tracing log | `agentTracingLogEnabled = true`，`HarnessAgent.java:1008-1075` | 是 | 关闭。 |
| compaction | 默认 `CompactionConfig`，`disableCompaction=false` | 是 | 关闭。 |
| tool result eviction | 默认 config，disable=false | 是 | 关闭。 |
| memory tools/hooks | disable flags 默认 false | 是 | 关闭。 |
| workspace context / at-path | disable flags 默认 false | 是 | 关闭语义 middleware。 |
| subagents / dynamic subagents | disable flags 默认 false | 是 | 关闭。 |
| dynamic/default workspace skills | 默认可自动发现 | 是 | 关闭。 |
| filesystem tool / shell tool | disable flags 默认 false | 是 | 两个工具关闭。 |
| tools.json | disable flag 默认 false | 是 | 关闭。 |
| plan mode | `planModeEnabled=false` | 无需 | 默认未启用。 |
| skill manage / curator | 两个 enabled 默认 false | 无需 | 默认未启用。 |
| sandbox lifecycle | 仅设置 sandbox spec 才启用 | 未设置 | 未启用。 |
| async tool middleware | 需要 `messageBus != null && asyncToolTimeout != null` | 未设置 timeout | middleware 未启用。 |
| ReAct session persistence | `disableSessionPersistence()` 自 2.0 起是 no-op；`HarnessAgent.java:1860-1869` | 无法通过该开关关闭 | AAF 明确复用 Redis AgentState，并用 `requireNoHiddenPersistentHistory` 阻断未纳入冻结画像的隐藏历史。风险是 state 与 AAF 会话记忆并存，必须持续守住“只存执行工作态”约束。 |
| 默认 workspace / local filesystem | `build()` 总会解析 workspace 并调用 `resolveFilesystem`、构造 `WorkspaceManager` | **否，且无总开关** | 即使 workspace context 与文件工具关闭，内部 workspace/filesystem 仍存在。 |
| 默认 MessageBus / AsyncToolRegistry | 未显式提供时从 filesystem 创建 workspace-backed 实现 | **否** | 形成第二套文件型 bus 基础设施。 |
| `InboxMiddleware` | `messageBus != null` 即装配 | **否** | AAF 当前实际复用该 middleware，违背“只保留 ReAct+工具”的严格表述。 |
| `WaitAsyncResultsTool` | `messageBus != null` 即注册 | **否** | 在已审计的 Harness 源码快照中，该工具被加入原始 Toolkit，形成 AAF 未冻结的 `wait_async_results` 表面；必须以 schema 断言阻断。 |

**风险定级**：workspace/filesystem 对象存在本身是资源与边界噪声，定为 major；`WaitAsyncResultsTool` 进入 Toolkit 会破坏“有效工具集由 AAF 冻结”的不变量，应按 blocker 处理。该结论由 `HarnessAgent.Builder.build:1956-2497` 的注册顺序直接支持；仅“仓库参考源码是否与 Maven 解析到的 2.0.0 二进制逐字节一致”尚未验证。

## 五层智能架构实现完成度

### 总表

| 层 | 包位置 | 核心类 | 已落地 | 仅端口/目标态与占位 | 完成判断 |
|---|---|---|---|---|---|
| Core | `.../intelligent/core/`，provider 适配在 `.../intelligent/ai/` 与 `.../infrastructure/agentscope/model/` | `LlmClient`、`PromptInvocationGateway`、`DefaultCapabilityRouter`、`ModelManagementService`、`DefaultConfidenceGate` | 非自主 L0 调用、模型管理/能力路由、Prompt 输入分类预检、Harness 模型解析、token 事实 | 完整 `LlmRequest/LlmResponse`、物理 attempt 追踪、统一结构化输出合同尚未实现；Harness 与直连模型配置仍未完全统一 | **部分完成，主路径可用** |
| Cognition | `.../intelligent/cognition/`、`.../infrastructure/cognition/` | `DefaultL1ContextCollaborator`、`DefaultUnifiedRetrievalPort`、`MemoryGovernanceService`、`DefaultHybridContextCompressor`、`RedisSessionMemoryAdapter`、`JpaCognitionMemoryAdapter` | 短期会话、长期记忆、知识混合检索、RRF/重排、上下文预算/披露、压缩、学习候选 | 用户画像/偏好证据模型、统一四分区写入门禁仍为目标态；部分端口存在多层 facade 复杂度 | **实现较完整，治理缺口明确** |
| Agent | `.../intelligent/agent/`、`.../infrastructure/agentscope/`、`.../infrastructure/governance/` | `AgentExecutionPort`、`AgentExecutionCommand`、`SubagentSpec`、`DefaultToolGateway`、`HarnessAgentExecutionAdapter`、`AgentScopeSpecCompiler` | 四身份画像、精确工具、ReAct、事件、取消、状态、工具授权/幂等/预算 | 统一 `TaskLoopContract` 类型不存在；单节点“重复同工具+等价 observation”停止门禁未落地；`inheritParentTools` 明确拒绝 | **生产主链完成，循环合同仍分散** |
| Assistant | `.../intelligent/assistant/`、`.../infrastructure/assistant/` | `AssistantApplicationService`、`DelegatedTaskCoordinator`、`TaskBoard`、`CoordinationPlan`、`PromptAssembler`、`PersistentHitlCoordinator` | 定义/Role/Skill 解析、L1 上下文、画像冻结、任务状态、DAG、租约、恢复、HITL、聚合、输入缓冲 | 三种澄清策略的完整确定性门控仍未闭合；父 Team 完成未统一经过 `CompletionValidator`；部分模型策略存在映射降级 | **五层中最重、完成度最高，但复杂度也最高** |
| Team | `.../intelligent/team/`，生命周期在 `automation/application`，运行时复用 Assistant/TaskBoard | `TeamDefinition`、`DefinitionLifecycleService`、`AssistantExecutionService.startTeam/resolveTeam/teamBoard`、`DelegatedTaskCoordinator.executeBoard` | `LEADER_COORDINATED`、1..8 静态 Worker、精确 Assistant/Role/Skill/tool target、发布校验、统一入口、计划覆盖、并行 Worker、确定性聚合 | 动态成员、Pipeline/Peer、外部 A2A、成员级预算、自动冲突仲裁未实现；`ConflictArbitrator` 未接入 Team 主链 | **存在真实 MVP 实现，不是只有设计文档** |

本次扫描未发现五层**关键生产主链**存在“只声明 Port、完全没有生产实现或 Spring 装配”的空壳；例如 `SessionMemoryPort` 对应 `RedisSessionMemoryAdapter`，`AgentExecutionPort` 对应 `HarnessAgentExecutionAdapter`，`AssistantCommandPort` 对应 `AssistantApplicationService`。表中“目标态”主要是尚未建模或只完成部分合同的能力，不应误报为已有空 Port。证据：`.../infrastructure/cognition/spring/CognitionInfrastructureAutoConfiguration.java`、`.../infrastructure/agentscope/spring/AgentRuntimePortAutoConfiguration.java`、`.../infrastructure/assistant/spring/AssistantInfrastructureAutoConfiguration.java`。

### Core 差异

设计有、代码无：`docs/design/framework/intelligent/core/core.md` 定义的完整请求/响应、结构化输出和物理调用事实仍是目标态；当前 `LlmClient` 返回 `String/Flux<String>`，证据为 `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/llm/LlmClient.java`。

代码有、总览设计状态未及时反映：`PromptInvocationGateway` 已区分 `NON_AUTONOMOUS_L0` 输入类型并记录逻辑调用预检，`PromptEnvelopeCaptureMiddleware` 已在 Harness 每次物理调用前冻结 Envelope；总体设计对“逐物理调用 Envelope 尚未落地”的表述需要按实际 middleware 行为进一步区分直连链与 Harness 链。

未发现 Core 主路径中的 `TODO` 注释或空方法；缺口表现为目标合同未建模，而不是显式占位代码。

### Cognition 差异

设计有、代码无：用户画像/偏好候选及统一四分区模型仍未找到实现；证据和目标态由 `docs/design/framework/intelligent/cognition/cognition.md` 的实现态表明确标注。

代码有、设计总览滞后：`architecture.md` 仍写“本会话短期上下文缺失”，但当前代码已经有 `SessionMemoryPort`、`RedisSessionMemoryAdapter`，且 `DefaultL1ContextCollaborator.resolve/sessionMessage` 会在预算内注入最近会话；证据：`.../cognition/application/DefaultL1ContextCollaborator.java:28-354`、`.../infrastructure/cognition/memory/RedisSessionMemoryAdapter.java`。因此该总览结论已过时。

`GraphMemoryService`、`TrajectoryCollector`、`ProceduralDistiller`、`EffectEvaluator` 等较早类存在，但当前统一主链证据集中在 `DefaultUnifiedRetrievalPort`、Memory ports 和 LearningCandidate；未找到前述旧类被主链调用的证据。其“是否仍为有效实现”属于**未验证推断**，本报告不把它们计为完成能力。

### Agent 差异

设计与代码一致的核心边界是：外层 AAF 解析 `SubagentSpec`，内层只创建叶子 Harness；证据为 `docs/design/framework/intelligent/agent/agent.md` 与 `AgentScopeSpecCompiler` 的对称关闭列表。

设计有、代码无：统一版本化 `TaskLoopContract` 未出现；职责分散于 `InvocationPolicy`、`ExecutionPolicy`、`ExecutionContract`、`CompletionCriteria` 与 `TaskBoard`。单次工具调用重复检测也未发现实现。

代码有、设计需提高显著性：默认 workspace/bus/Inbox/Wait 工具残留不是设计中声明的能力，却由 `HarnessAgent.Builder.build` 引入。这是“代码有、设计无”的负向差异。

### Assistant 差异

已落地能力不是端口堆积：`AssistantInfrastructureAutoConfiguration.assistantCommandPort` 只有在定义、TaskBoard、AgentExecution、上下文、压缩、模型、事件和恢复端口全部存在时才构造真实 `AssistantApplicationService`；`delegatedTaskCoordinator` 同样需要完整持久基础设施。证据：`.../infrastructure/assistant/spring/AssistantInfrastructureAutoConfiguration.java`。

设计有、代码部分实现：澄清策略、自然语言运行中输入分类、Team 父任务完成门禁等仍在演进。当前已经新增 `DefaultInputClassifier`、`TaskIngress`，比 2026-08-25 的 Assistant 文档“无四分类器”描述更前；但完整端到端语义需以其调用覆盖为准，不能仅因类存在宣称全部完成。

未发现 Assistant 包中的 `TODO` 或 `UnsupportedOperationException` 占位；缺口主要是合同覆盖不足和单类过重。`AssistantApplicationService` 约 2189 行、`DelegatedTaskCoordinator` 约 1483 行，说明能力真实存在，但也构成高维护风险。

### Team 差异

Team **有实现**：

- 领域模型：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/team/model/TeamDefinition.java:8-75`，校验单 Leader、1..8 Worker、唯一 memberKey。
- 发布治理：`DefinitionLifecycleService.validateTeamDefinition/requirePublishedTeam`，位于 `.../intelligent/automation/application/DefinitionLifecycleService.java`。
- 入口与解析：`AssistantAguiController.executionStream` 的 `TEAM` 分支调用 `AssistantExecutionService.startTeam`；后者调用 `resolveTeam/teamBoard`。
- 调度：`DelegatedTaskCoordinator.executeBoard/executeSubTask` 并行执行冻结 Worker，`finalizeBoard` 聚合。

仅设计或未接入：动态成员、Team 专属项目级运行时、Pipeline/Peer、外部 A2A、成员级预算。`ConflictArbitrator` 虽有代码，但未找到 `TeamDefinition`、`TaskBoard` 或 `DelegatedTaskCoordinator` 对它的调用，故自动仲裁仍未实现；证据：`.../intelligent/team/ConflictArbitrator.java` 与 `docs/design/framework/intelligent/team/team.md` 实现态。

物理包与概念层并非一一对应：Team 的模型在 `team/`，生命周期复用 `automation/`，执行复用 Assistant/TaskBoard。此结构符合“Team 不执行具体动作”的设计，但降低了仅按目录判断完成度的可靠性。

## 真实请求调用链

### DIRECT 主助理直答

DIRECT 并不绕过持久外层协调器。CHAT 请求仍创建单节点 `TaskBoard` 并取得 conversation lease，只是在叶子画像中使用 `AgentExecutionCommand.ExecutionMode.DIRECT`，从而缓存 Dynamic Harness 实例。

```text
POST /api/agui/run
→ AssistantAguiController.run
→ AssistantAguiController.executionStream
→ AssistantExecutionService.start
→ AssistantExecutionService.execute
→ DelegatedTaskCoordinator.submitAndDispatch
→ DelegatedTaskCoordinator.submit
→ DelegatedTaskCoordinator.dispatch
→ DelegatedTaskCoordinator.executeBoard
→ DelegatedTaskCoordinator.executeSubTask
→ AssistantCommandPort.execute
→ AssistantApplicationService.invoke
→ AssistantApplicationService.executeSubTask
→ AssistantApplicationService.executionContext / resolveExecutionProfile / agentCommand
→ AgentExecutionPort.execute
→ HarnessAgentExecutionAdapter.execute
→ HarnessAgentExecutionAdapter.executeDeferred
→ HarnessAgentExecutionAdapter.resolveExecution
→ AgentScopeSpecCompiler.compileDirect
→ AgentScopeSpecCompiler.compileDynamicNew
→ HarnessAgentExecutionAdapter.executeResolved
→ HarnessAgent.streamEvents(messages, runtimeContext)
```

逐跳证据：

- REST/SSE 入口与 CHAT 分支：`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/agui/AssistantAguiController.java:63-106`。
- 请求归一化、单节点 board 与持久提交：`.../module/ai/assistant/service/AssistantExecutionService.java:78-319`；其 `execute` 调 `delegatedTasks.submitAndDispatch`。
- 持久提交与租约派发：`.../assistant/application/DelegatedTaskCoordinator.java:183-299`。
- 叶子执行：`DelegatedTaskCoordinator.executeBoard/executeSubTask:417-602` 调用注入的 `AssistantCommandPort commands.execute(childCommand)`。
- `AssistantCommandPort` 的生产 Bean 是 `new AssistantApplicationService(...)`：`.../infrastructure/assistant/spring/AssistantInfrastructureAutoConfiguration.java` 的 `assistantCommandPort` 方法。
- SUBTASK 分支：`AssistantApplicationService.invoke:182-279` → `executeSubTask:281-306` → `agentExecution.execute(...)`。
- DIRECT 缓存分支：`HarnessAgentExecutionAdapter.resolveExecution:369-414` 判断 `executionMode == DIRECT` 后调用 `compiler.compileDirect`。
- 最终调用：`HarnessAgentExecutionAdapter.executeResolved:236-339` 调用 `execution.agent().streamEvents(...)`。

### DELEGATED 委托子智能体

DELEGATED 与 DIRECT 共用入口、持久任务、租约、Assistant 画像冻结和 `AgentExecutionPort`。差异是 TaskBoard 可以包含 Coordinator/多个 Executor/Aggregator，且 Dynamic 节点走 `compileDynamic`，每次现场构造并在流结束后关闭。

```text
POST /api/agui/run（EXECUTION 或 TEAM）
→ AssistantAguiController.run / executionStream
→ AssistantExecutionService.start 或 startTeam
→ AssistantExecutionService.execute
→ analyzedBoard 或 teamBoard
→ DelegatedTaskCoordinator.submitAndDispatch
→ dispatch（acquire lease + claim）
→ executeBoard（按 maxParallelism 领取节点）
→ executeSubTask（parentCommand.forSubTask）
→ AssistantCommandPort.execute
→ AssistantApplicationService.invoke
→ AssistantApplicationService.executeSubTask
→ resolveExecutionProfile + executionContext + freezeCompressedProfile
→ AssistantApplicationService.agentCommand
→ AgentExecutionPort.execute
→ HarnessAgentExecutionAdapter.executeDeferred
→ requireCurrent（lease + delegated task execution）
→ resolveExecution
   ├─ Predefined → AgentScopeSpecCompiler.compile
   └─ Dynamic DELEGATE → AgentScopeSpecCompiler.compileDynamic
→ compileNew / compileDynamicNew
→ executeResolved
→ HarnessAgent.streamEvents
→ AgentScopeEventMapper.map
→ ExecutionEventStorePort.append
→ DelegatedTaskCoordinator 收集结果并更新 TaskBoard
```

逐跳证据：

- EXECUTION/TEAM 入口：`AssistantAguiController.executionStream:79-106`。
- control mode 决策：`AssistantExecutionService.command:374-440`，Team 或 TASK 模式设为 `DELEGATED`。
- 计划和并行执行：`DelegatedTaskCoordinator.executeBoard:417-451`、`executeSubTask:454-602`。
- 租约双重校验：`HarnessAgentExecutionAdapter.requireCurrent:416-430`，先 `leases.requireCurrent`，再 `delegatedTasks.requireAgentExecution`。
- Dynamic 委托实例策略：`HarnessAgentExecutionAdapter.resolveExecution:369-414`；非 DIRECT 调 `compileDynamic`，结果 `ephemeral=true`。
- 释放：`HarnessAgentExecutionAdapter.release:455-467` 在 finally 关闭一次性 HarnessAgent。
- AgentScope 事件映射与顺序入库：`HarnessAgentExecutionAdapter.executeResolved:270-339`。

## 能力差距与演进路线

### 立即收紧运行边界

- 在 `AgentScopeSpecCompiler` build 后增加**最终 Toolkit schema 白名单断言**：实际工具名必须与 `effectiveTools` 一一相等。这样可直接发现 `WaitAsyncResultsTool` 等 Harness 默认注入；当前只断言 state store，不断言 middleware/tool surface。
- 增加 middleware 清单断言或测试：只允许 `PromptEnvelopeCaptureMiddleware` 加 ReAct 必需 middleware，显式拒绝 `InboxMiddleware`、workspace memory、subagent、skill middleware。
- 修正文档措辞：从“工作区全部关闭”改为“工作区语义能力与工具关闭，但 Harness 2.0 builder 仍构造内部 workspace/filesystem/bus”；否则代码与文档形成错误安全保证。
- 不把 `disableSessionPersistence()` 当修复方案。AgentScope 2.0 已明确将其标为 no-op；正确做法是继续使用 Redis state，并验证其中只包含当前执行工作态，或改用更低层执行体。

### 中期收敛到 core ReAct

推荐把 `AgentExecutionPort` 的实现从 `HarnessAgent` 下沉为直接构造 `ReActAgent`，同时继续复用 AgentScope 的 `Model`、`Msg`、`Toolkit`、`ToolBase`、`RuntimeContext`、`AgentStateStore`、`AgentEvent` 与 `MiddlewareBase`：

```text
AgentScopeSpecCompiler
  HarnessAgent.builder(...大量 disable...)
        ↓
  ReActAgent.builder(...只装必需能力)
```

理由：AAF 已自持 workspace、memory、skill、subagent、plan、gateway、tracing 和任务可靠性；继续使用 `HarnessAgent` 的收益只剩对 `ReActAgent` 的包装，代价却是默认 workspace/filesystem/bus 与未来版本新增默认能力的漂移风险。`AgentExecutionPort` 已隔离上层，替换不会改变外层任务合同；证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/agent/port/AgentExecutionPort.java:11-18`。

若短期必须保留 `HarnessAgent`，次优方案是向 AgentScope 上游贡献 `minimalMode` 或独立开关：禁止 workspace/filesystem/messageBus/Inbox/Wait 工具的构造，而不是在 AAF 继续追加更多 disable 调用。

### 保留 AAF 外层的部分

以下能力不应回迁 AgentScope Harness：`TaskBoard`、持久 `DelegatedTask`、租约/fencing、HITL、授权 grant、幂等 receipt、tenant/owner、预算、事件投影、L1 受控上下文和发布治理。这些对象构成 AAF 产品语义与审计合同，AgentScope 通用 Harness 无法成为其真理源。

### 降低自研层复杂度

- 将 `AssistantApplicationService` 按“画像解析/上下文冻结/Agent 调用/终态验证”拆成内部协作者，但保持一个 `AssistantCommandPort`；目标是减少 2189 行单类认知负荷，不引入第二入口。
- 将 `DelegatedTaskCoordinator` 按“提交调度/节点执行/计划解析/终态提交”拆分，同样保持 `DelegatedTaskCoordinator` 作为唯一 facade；租约和 transition 仍需集中约束。
- 建立统一 `TaskLoopContract`，把当前分散在 `InvocationPolicy`、`ExecutionPolicy`、`ExecutionContract`、`CompletionCriteria`、`TaskBoard` 的循环版本、阶段、停止和证据合并为可冻结合同。该项是设计已识别但代码未落地的最高优先级模型缺口。
- 把架构实现态改为自动生成或由测试验证：至少覆盖“最终 Toolkit 等于冻结工具”“Harness 无内建 subagent/skill/memory middleware”“Team 全 roster 被计划覆盖”“Session history 只来自 L1”。这比继续维护手工实现态表更能防止版本漂移。

## 审计边界

本报告是静态源码审计，未启动 Spring 容器，也未执行真实 provider 请求。以下判断已明确限制：

- `InboxMiddleware` 与 `WaitAsyncResultsTool` 的注册由已审计 builder 源码确定；未验证的是仓库参考源码与 Maven 解析到的 2.0.0 jar 是否字节级一致，而不是该源码快照内的注册行为。
- 未对 `tmp/agentscope-java` 与 Maven 解析到的 `io.agentscope:agentscope-harness:2.0.0` jar 做字节级 hash 比对；报告以仓库内参考源码快照和已核实依赖版本为证据。
- 未修改 `apps/` 下任何文件；本次唯一写入是本审计文档。
