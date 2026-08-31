---
level: Reality
layer: Model
purpose: 评估 AAF 自研编排层相对 AgentScope Java v2 Harness 的能力差距、重复建设与企业治理保留边界
status: draft
version: 1.1.0
date: 2026-08-31
author: AaronZZH
changelog:
  - 2026-08-30 初版
  - 2026-08-31 追加「后续修正」：Plan Mode 与 Subagent 由「完全缺失」改判为「可选启用」，新增 PermissionMode 重复项
---

# AAF 与 AgentScope Harness 能力差距及自研边界

## 结论

AAF 当前不是在 Harness 上增加少量企业插件，而是复用 `HarnessAgent` 与 `Toolkit` 的执行循环后，关闭了 Harness 大部分通用工程能力，再用约 452 个 Java 文件、约 4.5 万行 `intelligent` 代码建立另一套控制面。官方 `agentscope-harness/agent` 调查范围为全部 215 个 Java 文件、约 4.2 万行；结论不是基于产品印象，而是基于该目录全部子包、`tmp/agentscope-java/SKILL.md`、`docs/v2/zh` 中文文档以及 core 扩展点源码。

审计结论是：

- **Harness 通用执行卫生不应继续自研。** 工具结果卸载、历史会话检索、Plan Mode、任意工具后台化、技能安全与灰度治理、filesystem/sandbox、`@path`、Gateway/Channel、MessageBus/Inbox 等十项仍存在实质缺口。
- **AAF 的五项实现达到等价或以不同机制覆盖同一目标。** 上下文压缩、持久委托编排、技能渐进加载、受控记忆管道、执行追踪有企业增强，但其中上下文压缩、技能加载和事件观察与 Harness 原生能力重复度高。
- **AAF 真正应保留的是企业控制面，而不是第二套 ReAct/Harness。** tenant 级隔离、conversation fencing、计费结算、持久审批与恢复、审计事件库、Connector 凭据与幂等、数据库任务状态机具有独占价值；其采集和拦截入口应收敛到 `MiddlewareBase`、`Toolkit`、`AgentStateStore` 等官方扩展点。
- **质量呈两极。** AAF 在 fail-closed、租约校验、幂等收据、持久恢复和披露安全方面强于官方通用实现；但编排面复杂度高，且 `GovernedSandboxAdapter` 实际仍在宿主创建进程、`TokenQuotaService` 仍返回固定零、`ToolApprovalService` 与 `PersistentHitlCoordinator` 形成两套审批语义，说明“企业能力多”不等于“都已生产成熟”。

最终统计口径为：**缺失 10 项、等价或替代 5 项、超越 10 项**。这里的“等价或替代”表示解决同一运行目标，不表示 API、覆盖面和质量完全相同。

## 调查边界与判定口径

官方侧已检查：

- `tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/` 全部子包，重点覆盖 `memory`、`middleware`、`tool`、`subagent`、`skill`、`workspace`、`filesystem`、`sandbox`、`gateway`、`bus`。
- `tmp/agentscope-java/SKILL.md` 与 `tmp/agentscope-java/docs/v2/zh/docs/harness/` 下 architecture、compaction、subagent、skill、memory、plan-mode、sandbox、filesystem、workspace、channel。
- 官方扩展点：`MiddlewareBase`、`Toolkit`、`AgentStateStore`、`Model`、已弃用 `Hook`、`RequireUserConfirmEvent`、`RequireExternalExecutionEvent`。`Hook` 在 2.0.0 已标记 `@Deprecated(forRemoval = true)`，新设计不应再基于 Hook。

AAF 侧已检查 `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/` 下 assistant、cognition、agent、`core/skill`、team、automation 及对应 infrastructure。AAF 只依赖 `agentscope-harness 2.0.0`，`AgentScopeSpecCompiler` 两条构建路径均复用 `HarnessAgent`，但显式调用 `disableMemoryTools`、`disableMemoryHooks`、`disableWorkspaceContext`、`disableAtPathExpansion`、`disableSubagents`、`disableDynamicSubagents`、`disableDynamicSkills`、`disableFilesystemTools`、`disableShellTool`、`disableCompaction`、`disableToolResultEviction` 等开关。

证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java`（`AgentScopeSpecCompiler`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/package-info.java`（package 声明）；`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java`（`HarnessAgent`）。

“自研规模”采用直接核心类数量级：统计领域模型、端口、主应用服务和主适配器，不含测试、配置类、JPA repository/entity；该口径用于判断维护面，不追求伪精确。

## Harness 基线状态

下表覆盖任务要求核查的十五个主题。状态只有三种：完全缺失、有自研等价物、AAF 用别的机制解决。后两种进入后文“等价或替代清单”；存在明显功能子集缺口的仍进入“缺失清单”。

| Harness 主题 | AAF 状态 | 核心判断 | 证据 |
| --- | --- | --- | --- |
| Conversation compaction | 有自研等价物 | AAF 有两条结构化摘要链路，并冻结输入/输出 hash；但没有 Harness 的 overflow 自动强制压缩与 `TruncateArgsConfig` 一体化链路。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/memory/compaction/ConversationCompactor.java`（`ConversationCompactor`）、`.../middleware/CompactionMiddleware.java`（`CompactionMiddleware`）、`.../memory/compaction/TokenCounterUtil.java`（`TokenCounterUtil`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/cognition/application/DefaultHybridContextCompressor.java`（`DefaultHybridContextCompressor`）、`.../DefaultSessionContextCompressor.java`（`DefaultSessionContextCompressor`）。 |
| Tool result eviction | 完全缺失 | `ToolResultEvidenceStore` 只暂存白名单元数据，不落盘完整大结果、不替换上下文、不提供回读路径。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/ToolResultEvictionMiddleware.java`（`ToolResultEvictionMiddleware`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/ToolResultEvidenceStore.java`（`ToolResultEvidenceStore`）。 |
| Session tree/list/history/search | AAF 用别的机制解决 | AAF 只召回当前 session 最近轮次并在 Redis 失效时回退数据库；没有面向 Agent 的历史会话树、freshness 与关键词搜索工具，因此完整能力仍缺失。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/memory/session/SessionTree.java`（`SessionTree`）、`.../tool/SessionSearchTool.java`（`SessionSearchTool`）、`.../memory/session/SessionFreshnessEvaluator.java`（`SessionFreshnessEvaluator`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/cognition/memory/RedisSessionMemoryAdapter.java`（`RedisSessionMemoryAdapter`）、`.../cognition/port/SessionMemoryPort.java`（`SessionMemoryPort`）。 |
| Plan Mode | 完全缺失 | Confidence/HITL 能阻止动作，但没有计划状态、只读阶段、计划文件和 plan enter/write/exit 工具。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/workspace/plan/PlanModeManager.java`（`PlanModeManager`）、`.../middleware/PlanModeMiddleware.java`（`PlanModeMiddleware`）、`.../tool/PlanModeTools.java`（`PlanModeTools`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/confidence/DefaultConfidenceGate.java`（`DefaultConfidenceGate`）、`.../assistant/application/PersistentHitlCoordinator.java`（`PersistentHitlCoordinator`，不是 Plan Mode）。 |
| 任意异步工具 | AAF 用别的机制解决 | AAF 可把业务任务持久委托，但不能把任意普通 tool call 后台化后由 `wait_async_results` 聚合；通用能力仍缺失。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/bus/AsyncToolRegistry.java`（`AsyncToolRegistry`）、`.../middleware/AsyncToolMiddleware.java`（`AsyncToolMiddleware`）、`.../tool/WaitAsyncResultsTool.java`（`WaitAsyncResultsTool`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/model/DelegatedTask.java`（`DelegatedTask`）、`.../assistant/application/DelegatedTaskCoordinator.java`（`DelegatedTaskCoordinator`）。 |
| 子智能体编排 | 有自研等价物 | AAF 用 `DelegatedTask`、`TaskBoard` 和数据库适配器实现持久编排；缺少 Harness 声明式 workspace spec、临时本地/远程 subagent 和父子流式转发，但企业任务恢复更强。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/SubagentsMiddleware.java`（`SubagentsMiddleware`）、`.../middleware/DynamicSubagentsMiddleware.java`（`DynamicSubagentsMiddleware`）、`.../tool/AgentSpawnTool.java`（`AgentSpawnTool`）、`.../tool/TaskTool.java`（`TaskTool`）、`.../subagent/task/BackgroundTask.java`（`BackgroundTask`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/model/TaskBoard.java`（`TaskBoard`）、`.../assistant/application/DelegatedTaskCoordinator.java`（`DelegatedTaskCoordinator`）、`.../infrastructure/assistant/persistence/JpaDelegatedTaskAdapter.java`（`JpaDelegatedTaskAdapter`）。 |
| Skill 渐进加载 | 有自研等价物 | AAF 先冻结授权 Skill 摘要，再由 `context.load` 加载正文或 reference；目标与 Harness progressive disclosure 对等。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/skill/runtime/SkillRuntime.java`（`SkillRuntime`）、`.../skill/runtime/SkillCatalog.java`（`SkillCatalog`）、`.../skill/runtime/SkillLoadTool.java`（`SkillLoadTool`）、`.../middleware/HarnessSkillMiddleware.java`（`HarnessSkillMiddleware`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/application/ContextLoadTool.java`（`ContextLoadTool`）、`.../assistant/application/DefaultEffectiveSkillResolver.java`（`DefaultEffectiveSkillResolver`）、`.../infrastructure/agent/persistence/JpaSkillCatalogAdapter.java`（`JpaSkillCatalogAdapter`）。 |
| Skill 治理 | AAF 用别的机制解决 | AAF 有 Definition 生命周期和学习候选，但没有 Harness 的内容安全扫描、环境/白名单/Canary 可见性过滤、Promotion Gate 与自动 curator 的完整闭环，仍是功能缺口。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/skill/curator/SkillCurator.java`（`SkillCurator`）、`.../skill/curator/SkillSecurityScanner.java`（`SkillSecurityScanner`）、`.../skill/curator/SkillPromotionGate.java`（`SkillPromotionGate`）、`.../skill/curator/CanaryFilter.java`（`CanaryFilter`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/automation/application/DefinitionLifecycleService.java`（`DefinitionLifecycleService`）、`.../cognition/application/LearningCandidateApplicationService.java`（`LearningCandidateApplicationService`）。 |
| Workspace 与 filesystem | AAF 用别的机制解决 | AAF 只有 task namespace 下的 read/write/list 和 symlink/path traversal 防护；没有 overlay、remote KV、sandbox-backed filesystem、glob/grep/edit/shell 与 IsolationScope 的统一抽象，完整能力缺失。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/workspace/WorkspaceManager.java`（`WorkspaceManager`）、`.../filesystem/AbstractFilesystem.java`（`AbstractFilesystem`）、`.../filesystem/OverlayFilesystem.java`（`OverlayFilesystem`）、`.../filesystem/RemoteFilesystem.java`（`RemoteFilesystem`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/governance/LocalScopedFileSystemAdapter.java`（`LocalScopedFileSystemAdapter`）、`.../agent/port/ScopedFileSystemPort.java`（`ScopedFileSystemPort`）。 |
| Sandbox | AAF 用别的机制解决 | AAF 名为 sandbox，但在宿主 `ProcessBuilder` 启动 python/sh，仅靠字符串黑名单限制路径、网络与 API；它不是容器隔离，也无租约、快照和跨副本恢复，不能视为 Harness sandbox 等价物。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/sandbox/SandboxManager.java`（`SandboxManager`）、`.../sandbox/SandboxLease.java`（`SandboxLease`）、`.../sandbox/impl/docker/DockerSandbox.java`（`DockerSandbox`）、`.../middleware/SandboxLifecycleMiddleware.java`（`SandboxLifecycleMiddleware`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/governance/GovernedSandboxAdapter.java`（`GovernedSandboxAdapter`）。 |
| `@path` 展开 | 完全缺失 | 编译器显式关闭，AAF 没有对应消息展开 middleware。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/AtPathExpansionMiddleware.java`（`AtPathExpansionMiddleware`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java`（`AgentScopeSpecCompiler.disableAtPathExpansion`）。 |
| Memory 工具族 | AAF 用别的机制解决 | AAF 选择系统控制的召回、隐私评估、去重和自动学习，不向模型暴露通用 memory save/get/search；治理更强但降低了 Agent 主动回查能力。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/tool/MemorySaveTool.java`（`MemorySaveTool`）、`.../tool/MemoryGetTool.java`（`MemoryGetTool`）、`.../tool/MemorySearchTool.java`（`MemorySearchTool`）、`.../memory/MemoryConsolidator.java`（`MemoryConsolidator`）、`.../memory/MemoryFlushManager.java`（`MemoryFlushManager`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/cognition/application/DefaultMemoryContextCollaborator.java`（`DefaultMemoryContextCollaborator`）、`.../cognition/application/MemoryGovernanceService.java`（`MemoryGovernanceService`）。 |
| Gateway 与 Channel | 完全缺失 | 在本次要求调查的 `intelligent` 与 AgentScope adapter 范围内，没有 Harness Gateway/Channel 对等实现；AAF 其他应用入口不等于可直接复用的多 Agent 路由与 Channel SPI，本审计不外推为“AAF 整个平台无 API”。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/gateway/HarnessGateway.java`（`HarnessGateway`）、`.../gateway/ChannelManager.java`（`ChannelManager`）、`.../gateway/channel/Channel.java`（`Channel`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java`（`AgentScopeSpecCompiler`，无 gateway/channel 接线）。 |
| MessageBus 与 Inbox | AAF 用别的机制解决 | AAF 有执行事件与 outbox，但服务的是数据库事实发布和通知，不是 Agent 间可寻址 inbox/message bus；直接消息能力仍缺失。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/bus/MessageBus.java`（`MessageBus`）、`.../bus/WorkspaceMessageBus.java`（`WorkspaceMessageBus`）、`.../middleware/InboxMiddleware.java`（`InboxMiddleware`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/assistant/persistence/TaskTransitionOutboxEntity.java`（`TaskTransitionOutboxEntity`）、`.../assistant/persistence/TaskNotificationOutboxEntity.java`（`TaskNotificationOutboxEntity`）。 |
| Agent trace middleware | 有自研等价物 | Harness 提供运行日志/trace middleware；AAF 将 AgentScope 流映射为租户化执行事件并同步入库，审计与重放能力更强，但关闭了官方 tracing log，缺少低成本通用 OTel/trace 复用。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/AgentTraceMiddleware.java`（`AgentTraceMiddleware`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/AgentScopeEventMapper.java`（`AgentScopeEventMapper`）、`.../infrastructure/trace/persistence/JpaExecutionEventStoreAdapter.java`（`JpaExecutionEventStoreAdapter`）。 |

## 缺失清单

| 缺失项 | 影响与质量判断 | 是否重复建设 | 证据 |
| --- | --- | --- | --- |
| 大工具结果卸载 | 大型 shell/API 结果会完整进入下一轮模型上下文，既增加成本，也更容易触发硬上限；AAF 的 evidence metadata 不能恢复全文。 | 不是重复建设，是尚未建设。优先直接恢复官方 middleware，或以 `ScopedFileSystemPort` 实现兼容落盘端。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/ToolResultEvictionMiddleware.java`（`ToolResultEvictionMiddleware`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/ToolResultEvidenceStore.java`（`ToolResultEvidenceStore`）。 |
| 历史会话树与 Agent 自助搜索 | AAF 只能自动注入当前会话最近内容，长历史或跨会话事实无法由 Agent 按关键词回查；审计库存在不等于模型可查询。 | 已有 session memory 是部分重复，但未覆盖 SessionTree/Search。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/memory/session/SessionTree.java`（`SessionTree`）、`.../tool/SessionSearchTool.java`（`SessionSearchTool`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/cognition/memory/RedisSessionMemoryAdapter.java`（`RedisSessionMemoryAdapter`）。 |
| Plan Mode | 缺少可持久恢复的“只读调查—计划文件—人工确认—执行”阶段；ConfidenceGate 只判动作风险，不能替代规划状态机。 | 无需自研，官方已有完整能力。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/workspace/plan/PlanModeManager.java`（`PlanModeManager`）、`.../middleware/PlanModeMiddleware.java`（`PlanModeMiddleware`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/confidence/ConfidenceGate.java`（`ConfidenceGate`）。 |
| 任意异步工具 | AAF 只能把符合业务 `DelegatedTask` 契约的任务后台化，不能低成本并行运行多个普通读工具并聚合结果。 | 业务任务状态机应保留，通用 async tool 不应再造。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/AsyncToolMiddleware.java`（`AsyncToolMiddleware`）、`.../tool/WaitAsyncResultsTool.java`（`WaitAsyncResultsTool`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/model/DelegatedTask.java`（`DelegatedTask`）。 |
| Skill 安全、灰度与 curator 闭环 | AAF 有审批发布与学习候选，却未看到等价的 Skill 内容安全扫描、环境/Canary/AllowList 过滤和闲置归档；发布治理与运行期可见性治理之间有断层。 | Definition 生命周期属于企业资产治理；Scanner/Filter/Curator 应直接复用官方。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/skill/curator/SkillSecurityScanner.java`（`SkillSecurityScanner`）、`.../skill/curator/CanaryFilter.java`（`CanaryFilter`）、`.../skill/curator/SkillCurator.java`（`SkillCurator`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/automation/application/DefinitionLifecycleService.java`（`DefinitionLifecycleService`）。 |
| 完整 workspace/filesystem | AAF 文件端口的 tenant/user/task 路径与 fence 很有价值，但能力只有 read/write/list，不能承载 Harness 的 memory、skill staging、session log、tool eviction 和 sandbox projection。 | 基础文件抽象重复且覆盖面更窄，应把治理规则下沉为官方 `AbstractFilesystem` decorator。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/filesystem/AbstractFilesystem.java`（`AbstractFilesystem`）、`.../filesystem/CompositeFilesystem.java`（`CompositeFilesystem`）、`.../filesystem/OverlayFilesystem.java`（`OverlayFilesystem`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/governance/LocalScopedFileSystemAdapter.java`（`LocalScopedFileSystemAdapter`）。 |
| 真正隔离的 sandbox 与快照 | 宿主进程 + 字符串黑名单不能形成安全隔离边界；缺少资源限制、镜像、快照、恢复和多后端。该实现若处理不可信代码，风险高于“功能缺失”。 | 属于低质量重复建设，应替换而不是继续增强黑名单。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/sandbox/impl/docker/DockerSandbox.java`（`DockerSandbox`）、`.../sandbox/SandboxManager.java`（`SandboxManager`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/governance/GovernedSandboxAdapter.java`（`GovernedSandboxAdapter`）。 |
| `@path` 安全展开 | 用户不能在消息中以统一语义引用授权文件，附件与路径需在上层另行编排。 | 无需自研；恢复官方功能时需叠加 AAF tenant/task 路径策略。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/AtPathExpansionMiddleware.java`（`AtPathExpansionMiddleware`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java`（`AgentScopeSpecCompiler`）。 |
| Gateway/Channel 多入口路由 | AAF Harness adapter 没有复用 per-session 公平门、多 Agent 路由、暴露子 Agent 与 Channel SPI，入口层需自行承担这些一致性问题。 | 应复用官方 Gateway；AAF 只保留协议/身份转换。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/gateway/HarnessGateway.java`（`HarnessGateway`）、`.../gateway/channel/Channel.java`（`Channel`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java`（`HarnessAgentExecutionAdapter`）。 |
| Agent 间 MessageBus/Inbox | 数据库 outbox 能可靠发布领域事实，但不能让运行中的 Agent 直接收发消息或在下一轮获得 inbox reminder。 | outbox 不应删除，但不应继续扩展成另一套通用 Agent bus。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/bus/WorkspaceMessageBus.java`（`WorkspaceMessageBus`）、`.../middleware/InboxMiddleware.java`（`InboxMiddleware`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/assistant/persistence/TaskTransitionOutboxEntity.java`（`TaskTransitionOutboxEntity`）。 |

## 等价或替代清单

| 能力 | AAF 实现 ↔ Harness 实现 | 质量差异 | 证据 |
| --- | --- | --- | --- |
| 上下文压缩 | `DefaultHybridContextCompressor`、`DefaultSessionContextCompressor` ↔ `ConversationCompactor`、`CompactionMiddleware` | AAF 更重视结构化 JSON、当前用户原文、受保护状态、hash 冻结和 fail-closed；Harness 更完整地处理动态 context window、参数截断、memory flush/offload 与 overflow 重试。AAF 质量不低，但重复了通用 compaction。 | AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/cognition/application/DefaultHybridContextCompressor.java`（`DefaultHybridContextCompressor`）、`.../DefaultSessionContextCompressor.java`（`DefaultSessionContextCompressor`）。官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/memory/compaction/ConversationCompactor.java`（`ConversationCompactor`）、`.../middleware/CompactionMiddleware.java`（`CompactionMiddleware`）。 |
| 持久子任务/子智能体编排 | `DelegatedTaskCoordinator`、`TaskBoard`、`JpaDelegatedTaskAdapter` ↔ `SubagentsMiddleware`、`AgentSpawnTool`、`WorkspaceTaskRepository` | AAF 的数据库状态机、预算、owner、lease/fence、审批态和恢复更强；Harness 的声明、临时实例、远程 agent、流式父子事件、自动反向通知和工具体验更完整。最优边界是 Harness 执行子 Agent，AAF 管企业任务事实。 | AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/application/DelegatedTaskCoordinator.java`（`DelegatedTaskCoordinator`）、`.../assistant/model/TaskBoard.java`（`TaskBoard`）。官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/SubagentsMiddleware.java`（`SubagentsMiddleware`）、`.../subagent/task/WorkspaceTaskRepository.java`（`WorkspaceTaskRepository`）。 |
| Skill 渐进披露 | `DefaultEffectiveSkillResolver`、`ContextLoadTool`、`JpaSkillCatalogAdapter` ↔ `SkillRuntime`、`SkillCatalog`、`SkillLoadTool`、`HarnessSkillMiddleware` | AAF 先按 Assistant/Role 授权候选，安全边界更明确；Harness 四层 source、动态合成、filesystem/sandbox staging、usage 统计更成熟。AAF 的 `KNOWLEDGE_BINDING` 仍显式报未支持。 | AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/application/ContextLoadTool.java`（`ContextLoadTool`）、`.../assistant/application/DefaultEffectiveSkillResolver.java`（`DefaultEffectiveSkillResolver`）。官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/skill/runtime/SkillRuntime.java`（`SkillRuntime`）、`.../skill/runtime/SkillLoadTool.java`（`SkillLoadTool`）。 |
| 长短期记忆目标 | `RedisSessionMemoryAdapter`、`DefaultMemoryContextCollaborator`、`MemoryGovernanceService` ↔ `MemoryFlushManager`、`MemoryConsolidator`、`MemorySearchTool` | AAF 有 tenant subject、隐私级别、importance/confidence、冲突与 TTL，治理强；Harness 有双层文件记忆、Agent 主动 search/get、会话原文日志和后台 consolidation，Agent 自主性更强。两者并非同一数据模型，但解决同一跨轮事实召回目标。 | AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/cognition/application/MemoryGovernanceService.java`（`MemoryGovernanceService`）、`.../infrastructure/cognition/memory/RedisSessionMemoryAdapter.java`（`RedisSessionMemoryAdapter`）。官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/memory/MemoryFlushManager.java`（`MemoryFlushManager`）、`.../memory/MemoryConsolidator.java`（`MemoryConsolidator`）。 |
| 执行追踪 | `AgentScopeEventMapper`、`ExecutionEventStorePort`、`JpaExecutionEventStoreAdapter` ↔ `AgentTraceMiddleware` 和 core `AgentEvent` 流 | AAF 有 tenant/task/execution sequence、fence 校验、数据库重放和同步发布，企业审计更强；官方通用 trace/OTel 接入更轻。AAF 不应为获得持久审计而放弃官方 trace。 | AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/shared/event/ExecutionEventStorePort.java`（`ExecutionEventStorePort`）、`.../infrastructure/trace/persistence/JpaExecutionEventStoreAdapter.java`（`JpaExecutionEventStoreAdapter`）。官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/AgentTraceMiddleware.java`（`AgentTraceMiddleware`）、`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/event/AgentEvent.java`（`AgentEvent`）。 |

## 超越清单与必要性论证

“超越”在本节只表示 AAF 在企业语义上增加了官方通用 Harness 没有的能力，不表示官方完全没有相邻基础能力。尤其是：官方已有 `(userId, sessionId)` 隔离、`IsolationScope.USER`、Permission ASK、`RequireUserConfirmEvent`、外部工具暂停恢复、BackgroundTask 和 AgentStateStore。

| 超越项 | 官方是否真的不提供 | 能否走官方扩展点 | 自研规模、独占价值与判定 | 证据 |
| --- | --- | --- | --- | --- |
| tenant 级多租户隔离 | **部分提供。** `RuntimeContext` 与 `AgentStateStore` 原生按 `(userId, sessionId)` 隔离，filesystem 还有 `IsolationScope.USER`；但没有独立 `tenantId`、tenant→user→task 层级和所有领域仓储的 tenant 条件。已查 `RuntimeContext`、`AgentStateStore`、`IsolationScope`、`WorkspaceManager`。 | 可以把复合 tenant key 映射到官方 `RuntimeContext.userId`，或装饰 `AgentStateStore`；AAF 已由 `AgentScopeRuntimeContextMapper` 采用此方式，不需要自研执行循环。 | 约 4–6 个直接类，另有大量 tenant-aware persistence 查询。平台数据隔离与审计归属是刚需；**判定：刚需自研**，但只保留身份/存储边界。 | 官方：`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/state/AgentStateStore.java`（`AgentStateStore`）、`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/agent/RuntimeContext.java`（`RuntimeContext`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/agent/model/InvocationContext.java`（`InvocationContext`）、`.../infrastructure/agentscope/mapping/AgentScopeRuntimeContextMapper.java`（`AgentScopeRuntimeContextMapper`）。 |
| Conversation lease 与 fencing token | **不提供该语义。** Harness 有同 `(userId, sessionId)` 进程内 `SessionTurnGate`、sandbox `SandboxLease`/execution guard，但没有 conversation owner preempt、严格递增 fencing token，也没有把 token 校验到事件、工具、任务、文件写入。已查 `SessionTurnGate`、`SandboxLease`、`RedisSandboxExecutionGuard`、`AgentStateStore`。 | 可用 `MiddlewareBase.onAgent` 在 call 前后 acquire/release，也可装饰 `AgentStateStore` 拒绝旧 token；但 fence 必须继续进入 AAF 数据库写路径，不能只靠 middleware。 | 约 4–6 个核心类，且横切多个 adapter。它解决多副本脑裂和人工接管旧执行写回，是独占一致性价值；**判定：刚需自研**。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/sandbox/SandboxLease.java`（`SandboxLease`）、`.../sandbox/guard/RedisSandboxExecutionGuard.java`（`RedisSandboxExecutionGuard`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/port/ConversationLeasePort.java`（`ConversationLeasePort`）、`.../infrastructure/assistant/lease/RedisConversationLeaseAdapter.java`（`RedisConversationLeaseAdapter`）、`.../infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java`（`HarnessAgentExecutionAdapter.requireCurrent`）。 |
| Token 计量、预扣与信用额度 | **只提供 usage 事实，不提供计费。** 官方 `ModelCallEndEvent` 有 input/output token，`Model`/middleware 可观察请求；没有价格表、积分冻结、幂等结算和 delegated budget。已查 `MiddlewareBase`、`Model`、`ModelCallEndEvent`、`AgentTraceMiddleware`。 | 采集最适合改为 `TokenMeteringMiddleware.onModelCall`，预算拒绝可在调用前完成；`CreditService`、`AiCreditGuard`、价格与结算仍留 AAF。无需把观察器绑在 execution adapter。 | 约 6–10 个核心类。`JpaTokenMeteringAdapter` 已做 cache ratio、幂等结算与 fence；但 `TokenQuotaService.getQuota()` 当前恒为 `0`，月配额尚未落地，不能宣称完整 quota。**判定：可改造为官方扩展点**。 | 官方：`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/middleware/MiddlewareBase.java`（`MiddlewareBase`）、`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/model/Model.java`（`Model`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/middleware/AgentScopeTokenMeteringObserver.java`（`AgentScopeTokenMeteringObserver`）、`.../infrastructure/metering/persistence/JpaTokenMeteringAdapter.java`（`JpaTokenMeteringAdapter`）、`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/engine/credit/CreditService.java`（`CreditService`）、`.../engine/budget/TokenQuotaService.java`（`TokenQuotaService`）。 |
| 持久 HITL 工具审批 | **基础能力已提供，企业工作流未提供。** 官方 Permission ASK、`RequireUserConfirmEvent`、`ConfirmResult` 与 external tool suspend/resume 已能暂停和继续；但未提供审批表、跨进程审批队列、授权 grant、credential binding、task recovery job。已查 permission 文档、`RequireUserConfirmEvent`、`RequireExternalExecutionEvent`、`ToolBase.checkPermissions`。 | 应由自定义 `ToolBase.checkPermissions`/Toolkit 与 `MiddlewareBase.onActing` 触发 AAF 持久审批，并复用官方暂停恢复事件；`PortBackedAgentTool` 已把 `ApprovalRequiredException` 转为 `ToolSuspendException`，方向正确。 | 约 10–15 个核心类。持久授权和恢复是刚需，但 `ToolApprovalService` 与 `PersistentHitlCoordinator` 是两套审批模型，前者应迁移后削减。**总体判定：可改造为官方扩展点；遗留审批栈可削减**。 | 官方：`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/event/RequireUserConfirmEvent.java`（`RequireUserConfirmEvent`）、`.../RequireExternalExecutionEvent.java`（`RequireExternalExecutionEvent`）、`.../tool/ToolBase.java`（`ToolBase`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/hitl/ToolApprovalService.java`（`ToolApprovalService`）、`.../assistant/application/PersistentHitlCoordinator.java`（`PersistentHitlCoordinator`）、`.../infrastructure/agentscope/tool/PortBackedAgentTool.java`（`PortBackedAgentTool`）。 |
| Prompt 预检与 PromptEnvelope 留痕 | **不提供业务信封。** 官方 middleware 能拿到原始 model call，trace 能记录事件，但没有 tenant/task/execution 关联的请求 hash、attempt、消息/工具披露安全快照。已查 `MiddlewareBase.onModelCall`、`ModelCallInput`、`AgentTraceMiddleware`、`Model`。 | 这正是 `MiddlewareBase.onModelCall` 的标准用法；AAF 的 `PromptEnvelopeCaptureMiddleware` 已正确落在该扩展点，不应移回自研循环。初始“预检”只是 `HarnessAgentExecutionAdapter.logPromptPreflight` 的长度日志，真正的阻断检查来自 `CompiledSystemPrompt.verify`，不应夸大为完整安全扫描。 | 约 4–6 个核心类。hash 留痕、敏感字段排除、typed context 缺失时 fail-closed 有独占审计价值；但 `JpaPromptEnvelopeAdapter` 以 `findMaxSeq()+1` 分配序号，需依赖数据库唯一约束/锁保证并发，当前源码未显示该保证，属于**未验证推断风险**。**判定：可改造为官方扩展点（事实上已经完成）**。 | 官方：`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/middleware/MiddlewareBase.java`（`MiddlewareBase`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/middleware/PromptEnvelopeCaptureMiddleware.java`（`PromptEnvelopeCaptureMiddleware`）、`.../core/prompt/PromptEnvelope.java`（`PromptEnvelope`）、`.../infrastructure/assistant/persistence/JpaPromptEnvelopeAdapter.java`（`JpaPromptEnvelopeAdapter`）、`.../infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java`（`HarnessAgentExecutionAdapter.logPromptPreflight`）。 |
| 执行事件溯源入库 | **不提供持久 event store/reducer。** 官方提供可重建 Msg 的 `AgentEvent` 流、trace middleware 和 OTel，但没有 tenant/task/execution sequence、JPA store、fenced append、业务 reducer。已查 `AgentEvent` 全类型、`AgentTraceMiddleware`、`MiddlewareBase.onAgent`。 | 事件采集可由 `MiddlewareBase.onAgent` 或统一 stream subscriber 完成；事件 store、sequence、reducer 保持 AAF 领域端口。不要在每个 adapter 手工观察同一事件流。 | 约 5–8 个核心类。审计、恢复、SSE checkpoint 与状态重建是企业刚需；捕获入口可大幅瘦身。**判定：可改造为官方扩展点**。 | 官方：`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/event/AgentEvent.java`（`AgentEvent`）、`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/AgentTraceMiddleware.java`（`AgentTraceMiddleware`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/shared/event/ExecutionEventStorePort.java`（`ExecutionEventStorePort`）、`.../infrastructure/trace/persistence/JpaExecutionEventStoreAdapter.java`（`JpaExecutionEventStoreAdapter`）、`.../shared/event/ExecutionEventReducer.java`（`ExecutionEventReducer`）。 |
| 工具授权与 Connector 受信参数 | **提供基础权限与 preset parameters，不提供 AAF 业务信任链。** 官方有 allow/deny/ask、`ToolBase.checkPermissions`、rule matching、Toolkit presetParameters 与 external tool；没有目录精确版本、credential handle、provider idempotency、receipt replay 和 schema 敏感字段剔除。已查 `Toolkit`、`ToolRegistration`、`ToolBase`、permission system。 | `RegistryToolPortAdapter` 和 `PortBackedAgentTool` 应继续作为官方 Toolkit/ToolBase adapter；授权前置可进 `onActing`，credential/idempotency 继续由 `ToolGatewayPort` 控制。AAF 已基本沿正确边界实现，不需自研工具执行器。 | 约 8–12 个核心类。Connector trusted parameters、写动作稳定 action key 与 invocation receipt 是刚需；与官方 Permission 重叠的规则匹配可削减。**判定：可改造为官方扩展点**。 | 官方：`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/tool/Toolkit.java`（`Toolkit`）、`.../tool/ToolBase.java`（`ToolBase`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/tool/RegistryToolPortAdapter.java`（`RegistryToolPortAdapter`）、`.../agent/port/ToolGatewayPort.java`（`ToolGatewayPort`）、`.../agent/application/DefaultToolGateway.java`（`DefaultToolGateway`）。 |
| 任务持久化、恢复与 outbox | **提供后台 task 持久化，不提供 AAF 业务任务控制面。** Harness `BackgroundTask`/`WorkspaceTaskRepository`、AgentStateStore 和反向通知能跨节点读取结果；没有 TaskBoard DAG、owner/human takeover、budget、fenced transition、审批 recovery job 和 transactional outbox。已查 subagent task 全包、`AgentStateStore`、MessageBus。 | 可以实现官方 TaskRepository/AgentStateStore/MessageBus 的 AAF 后端，让 Harness 负责子 Agent 生命周期；AAF 数据库状态机和 outbox 继续作为 source of truth。该项不能仅靠一个 middleware 取代。 | 约 15–25 个直接核心类，是十项中最大维护面；`DelegatedTaskCoordinator`、`TaskBoard`、`JpaTaskTransitionAdapter` 等少数类已各达数百至上千行。其 exactly-once-ish transition、恢复与人工接管是刚需，但外围子 Agent 生命周期应让回 Harness。**判定：刚需自研**。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/subagent/task/BackgroundTask.java`（`BackgroundTask`）、`.../subagent/task/WorkspaceTaskRepository.java`（`WorkspaceTaskRepository`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/model/DelegatedTask.java`（`DelegatedTask`）、`.../assistant/model/TaskBoard.java`（`TaskBoard`）、`.../assistant/port/TaskRecoveryPort.java`（`TaskRecoveryPort`）、`.../infrastructure/governance/persistence/JpaTaskRecoveryAdapter.java`（`JpaTaskRecoveryAdapter`）、`.../infrastructure/assistant/persistence/TaskTransitionOutboxEntity.java`（`TaskTransitionOutboxEntity`）。 |
| Role/Skill 业务意图路由 | **不提供同一语义。** Harness 有 Skill catalog、tool group 和 subagent declaration，模型可自主选择，但没有 Assistant 发布版本内的 Role 职责/非职责、授权候选集、显式 preferred skill 和安全默认 role。已查 SkillRuntime、ToolGroupManager、SubagentDeclaration、DynamicSkills。 | 最适合在调用前计算并经 `onSystemPrompt` 注入 role appendix，Skill 列表则适配 Harness SkillRepository/Toolkit group；无需维持独立 agent loop。 | 约 7–10 个核心类。候选范围与 deterministic fallback 有治理价值；模型路由代码本身可被 middleware/官方 skill runtime 吸收。**判定：可改造为官方扩展点**。 | 官方：`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/skill/runtime/SkillRuntime.java`（`SkillRuntime`）、`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/tool/ToolGroupManager.java`（`ToolGroupManager`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/application/DefaultRoleSelector.java`（`DefaultRoleSelector`）、`.../assistant/application/SkillSelectionPort.java`（`SkillSelectionPort`）、`.../assistant/application/ModelSkillSelectionPort.java`（`ModelSkillSelectionPort`）。 |
| 置信度门控 | **不提供通用数值置信度策略。** 官方 Plan Mode、Permission ASK 和 middleware 可按工具风险确认，但没有 `0.7/0.9` 阈值、可验证性、不可逆性与暂存安全组合决策。已查 permission、PlanModeMiddleware、MiddlewareBase、ToolBase。 | 规则本身应保留为纯领域服务；Agent/Tool 边界可由 `onAgent`/`onActing` 调用，业务动作则在 `ToolGatewayPort` 或动作执行器前调用，不属于自研 Harness。 | 约 2–3 个核心类，规模小、策略清晰、fail-closed；没有理由删除领域规则，也没有理由因此保留整套编排。**判定：可改造为官方扩展点**。 | 官方：`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/middleware/MiddlewareBase.java`（`MiddlewareBase`）、`.../tool/ToolBase.java`（`ToolBase`）。AAF：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/confidence/ConfidenceGate.java`（`ConfidenceGate`）、`.../core/confidence/DefaultConfidenceGate.java`（`DefaultConfidenceGate`）、`.../action/AiBusinessActionExecutor.java`（`AiBusinessActionExecutor`）。 |

## 超越项最终判定

| 超越项 | 判定 | 保留与削减边界 | 证据 |
| --- | --- | --- | --- |
| tenant 级多租户隔离 | 刚需自研 | 保留 TenantId、tenant-aware repository 与复合 state key；继续使用官方 RuntimeContext/AgentStateStore。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeRuntimeContextMapper.java`（`AgentScopeRuntimeContextMapper`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/state/AgentStateStore.java`（`AgentStateStore`）。 |
| Conversation lease/fencing | 刚需自研 | 保留 Redis lease、preempt、fenced writes；用 `onAgent` 统一 acquire/validate/release。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/port/ConversationLeasePort.java`（`ConversationLeasePort`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/middleware/MiddlewareBase.java`（`MiddlewareBase`）。 |
| Token 计量/信用额度 | 可改造为官方扩展点 | 计量采集迁入 `TokenMeteringMiddleware`；保留价格、积分冻结和幂等结算；补齐或删除恒零 `TokenQuotaService`。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/middleware/AgentScopeTokenMeteringObserver.java`（`AgentScopeTokenMeteringObserver`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/middleware/MiddlewareBase.java`（`MiddlewareBase`）。 |
| 持久 HITL | 可改造为官方扩展点 | 复用 Permission ASK/外部暂停恢复，保留审批表、grant、recovery；合并或删除遗留 `ToolApprovalService`。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/application/PersistentHitlCoordinator.java`（`PersistentHitlCoordinator`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/event/RequireUserConfirmEvent.java`（`RequireUserConfirmEvent`）。 |
| PromptEnvelope | 可改造为官方扩展点 | 已在 `onModelCall` 正确实现；只需加固序号并发，不增加第二捕获路径。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/middleware/PromptEnvelopeCaptureMiddleware.java`（`PromptEnvelopeCaptureMiddleware`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/middleware/MiddlewareBase.java`（`MiddlewareBase`）。 |
| 执行事件溯源 | 可改造为官方扩展点 | 保留 EventStore/Reducer；以统一 middleware/stream subscriber 采集，官方 trace/OTel 并行开启。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/shared/event/ExecutionEventStorePort.java`（`ExecutionEventStorePort`）；`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/AgentTraceMiddleware.java`（`AgentTraceMiddleware`）。 |
| 工具授权/Connector 信任链 | 可改造为官方扩展点 | 保留 credential、idempotency、receipt；复用 Toolkit/ToolBase/permission，削减重复规则引擎。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/agent/application/DefaultToolGateway.java`（`DefaultToolGateway`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/tool/Toolkit.java`（`Toolkit`）。 |
| 任务持久化/恢复/outbox | 刚需自研 | 保留数据库任务事实与恢复；Harness 接管临时子 Agent 与通用后台任务。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/model/TaskBoard.java`（`TaskBoard`）；`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/subagent/task/WorkspaceTaskRepository.java`（`WorkspaceTaskRepository`）。 |
| Role/Skill 路由 | 可改造为官方扩展点 | 保留授权候选计算；用 `onSystemPrompt`、SkillRepository、Toolkit group 输出，不再属于独立编排层。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/application/DefaultRoleSelector.java`（`DefaultRoleSelector`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/middleware/MiddlewareBase.java`（`MiddlewareBase`）。 |
| ConfidenceGate | 可改造为官方扩展点 | 保留纯领域策略；由 onAgent/onActing/ToolGateway 调用，不扩张 Harness adapter。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/confidence/DefaultConfidenceGate.java`（`DefaultConfidenceGate`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/middleware/MiddlewareBase.java`（`MiddlewareBase`）。 |

按整项计没有建议直接删除的企业能力；“可削减”发生在项内的重复实现：遗留 `ToolApprovalService`、execution adapter 内 token observer、重复 permission rule、独立 Skill load/compaction 执行骨架。直接删除这些代码的前提是先把领域持久化接到官方扩展点，而不是先关能力。

## 重复造轮子评估

| 重复领域 | 结论 | 推荐边界 | 证据 |
| --- | --- | --- | --- |
| Compaction | 高重复。AAF 算法更严格，但与 Harness trigger/summary/state rewrite 重合。 | 保留 AAF 结构化摘要策略作为自定义 `CompactionConfig`/middleware 策略；删除双重触发和状态管理。 | AAF `.../cognition/application/DefaultHybridContextCompressor.java`（`DefaultHybridContextCompressor`）；官方 `.../memory/compaction/ConversationCompactor.java`（`ConversationCompactor`）。 |
| Skill runtime | 高重复。Catalog、摘要注入、详情加载两边都有。 | 以 AAF JPA catalog 实现官方 Skill repository/catalog SPI，保留发布审批，复用 runtime/staging/filter。 | AAF `.../assistant/application/ContextLoadTool.java`（`ContextLoadTool`）；官方 `.../skill/runtime/SkillRuntime.java`（`SkillRuntime`）。 |
| Subagent 生命周期 | 中高重复。AAF 重做 spawn/协调/状态，官方已有临时/后台/远程/流式。 | Harness 管实例与消息，AAF 管 TaskBoard、合同、预算、fence、outbox。 | AAF `.../assistant/application/DelegatedTaskCoordinator.java`（`DelegatedTaskCoordinator`）；官方 `.../middleware/SubagentsMiddleware.java`（`SubagentsMiddleware`）。 |
| Memory | 中度重合、数据模型不同。 | 不强行迁移 AAF 受控记忆；恢复官方 session log/search，并通过自定义 Memory tool 只读访问 AAF recall。 | AAF `.../cognition/application/MemoryGovernanceService.java`（`MemoryGovernanceService`）；官方 `.../tool/MemorySearchTool.java`（`MemorySearchTool`）。 |
| Trace/Event | 低层重复、上层独占。 | 官方 trace/OTel 负责技术可观测性，AAF EventStore 负责业务审计；一条事件流双写，不重造事件生命周期。 | AAF `.../infrastructure/trace/persistence/JpaExecutionEventStoreAdapter.java`（`JpaExecutionEventStoreAdapter`）；官方 `.../middleware/AgentTraceMiddleware.java`（`AgentTraceMiddleware`）。 |
| Filesystem/Sandbox | AAF 低能力重复，且 sandbox 安全边界不足。 | 以 Harness filesystem/sandbox 为底座，AAF 做 namespace/fencing decorator；停用宿主进程“沙箱”。 | AAF `.../infrastructure/governance/GovernedSandboxAdapter.java`（`GovernedSandboxAdapter`）；官方 `.../sandbox/impl/docker/DockerSandbox.java`（`DockerSandbox`）。 |
| StateStore | 基本没有重复。AAF 已使用官方 RedisAgentStateStore，仅适配 Spring Redis。 | 保持现状；可增加 fenced decorator，不重写 store。 | AAF `.../infrastructure/agentscope/spring/AgentScopeInfrastructureAutoConfiguration.java`（`AgentScopeInfrastructureAutoConfiguration`）、`.../infrastructure/agentscope/state/SpringRedisClientAdapter.java`（`SpringRedisClientAdapter`）；官方 `tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/state/AgentStateStore.java`（`AgentStateStore`）。 |

## 演进路线

### 恢复通用执行卫生

优先恢复不承载 AAF 业务语义、且官方已经成熟的能力：

- 开启或适配 `ToolResultEvictionMiddleware`，先解决上下文爆炸；落盘目标可用 AAF fenced filesystem decorator。
- 开启 session log/list/history/search；让 AAF 的长期记忆继续受控，不必开启模型任意写 memory。
- 开启 Plan Mode、AsyncToolMiddleware、`@path`、AgentTrace/Otel；`@path` 必须经过 tenant/task 路径授权。
- 用官方 Docker/Kubernetes/托管 sandbox 替换 `GovernedSandboxAdapter` 的宿主 `ProcessBuilder`。
- Gateway/Channel 和 MessageBus 先用于 Harness 内部路由；企业通知 outbox 继续独立存在。

### 收敛横切逻辑到扩展点

形成一条可审计 middleware 链，而不是在 `HarnessAgentExecutionAdapter` 中继续增加观察器：

```text
TenantContextMiddleware
  → ConversationLeaseMiddleware
  → PromptEnvelopeCaptureMiddleware（已实现）
  → TokenMeteringMiddleware
  → ExecutionEventPersistenceMiddleware
  → ToolAuthorizationMiddleware / PortBackedAgentTool
  → Harness 原生 compaction、async、subagent、trace
```

`MiddlewareBase` 已提供 `onAgent`、`onReasoning`、`onActing`、`onModelCall`、`onSystemPrompt` 五个切点；旧 `Hook` 已弃用，不应作为迁移目标。证据：`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/middleware/MiddlewareBase.java`（`MiddlewareBase`）、`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/hook/Hook.java`（`Hook`）。

### 分离数据控制面与执行面

目标边界应为：

- **Harness 执行面**：ReAct loop、消息与事件、tool execution、compaction、session、skill runtime、subagent runtime、filesystem、sandbox、Gateway/Channel。
- **AAF 企业控制面**：tenant、身份与授权、conversation lease/fence、价格/积分/预算、持久 HITL、PromptEnvelope、业务 EventStore、Connector credential/receipt、TaskBoard/recovery/outbox、Role 候选与 Confidence policy。
- **连接方式**：Middleware、Toolkit/ToolBase、AgentStateStore decorator、Skill/Task repository adapter、RuntimeContext typed attributes；不再以另一套 orchestration loop 连接。

### 按风险分批替换

- **第一批，低风险高收益**：官方 trace 并行开启；PromptEnvelope 保持现状；token observer 迁 middleware；补 tool result eviction 和 session search。
- **第二批，中风险**：Skill catalog 适配官方 runtime；Permission ASK 接持久审批；Plan Mode 与 AAF Confidence/HITL 联动。
- **第三批，高风险**：SubagentsMiddleware 接管临时实例与流式事件，AAF TaskBoard 仅管理业务任务；切换 filesystem/sandbox。该批涉及安全和任务恢复，必须做双模型架构审查与故障注入验证。
- **删除阶段**：确认事件、任务、审批和计量在新扩展点上可重放后，删除旧 observer、重复 skill loader、遗留审批服务和宿主进程 sandbox；不要长期双写或保留兼容分支。

## 验收标准

后续演进完成应满足：

- `AgentScopeSpecCompiler` 不再一刀切关闭全部 Harness 通用能力，而是按 AAF policy 明确启用或替换。
- 任意大工具结果不会完整进入下一轮 prompt；历史会话可被 Agent 按权限检索。
- 所有 delegated 写入在旧 fencing token 下失败；tenant/user/task 状态键不可串读。
- 模型调用都产生 PromptEnvelope、usage settlement 和 execution event，三者可按 executionId 对齐。
- ASK/external suspend 可跨进程恢复，审批、grant、credential、receipt 和 task transition 有单一事实源。
- 不可信代码只在真正隔离的 sandbox 中执行，宿主 `ProcessBuilder` 不再作为生产安全边界。
- AAF 自研代码集中于企业控制面，Harness 通用 runtime 不存在第二份平行实现。


## 后续修正（2026-08-31）

本节基于对官方 [子 Agent](https://java.agentscope.io/v2/zh/docs/harness/subagent.html) 与 [计划模式](https://java.agentscope.io/v2/zh/docs/harness/plan-mode.html) 两篇文档的精读，以及对 `tmp/agentscope-java` 源码的复核，修正初版的三处判定。初版正文保留不改，以留存判断变化的审计轨迹。

### 修正一 · Plan Mode 从「完全缺失」改判为「可选启用」

初版在「Harness 基线状态」与「缺失清单」中把 Plan Mode 记为完全缺失、无需自研。事实层面成立，但**判定口径需要修正**——它不是「AAF 该补的缺口」，而是「AAF 可按需启用的官方能力」，且启用方式与初版隐含的假设不同。

关键新事实：

| 事实 | 证据 |
|------|------|
| Plan Mode 是**执行内的阶段闸门**，不是整次执行的模式；一次 run 内可从只读切到可写 | `harness/agent/middleware/PlanModeMiddleware.java`；白名单为 `plan_enter` / `plan_write` / `plan_exit` / `todo_write`，见 `harness/agent/tool/PlanModeTools.java` |
| 有**程序化入口**且不触发 HITL：`enterPlanMode(ctx)` / `exitPlanMode(ctx)` / `isPlanModeActive(ctx)` | `harness/agent/HarnessAgent.java:303-322` |
| 阶段状态随 `AgentState.planModeContext` 持久化，跨进程/节点/副本恢复 | `harness/agent/workspace/plan/PlanModeManager.java`，操作 `io.agentscope.core.state.PlanModeContextState` |
| `enablePlanMode()` 默认 `false`，因此 `AgentScopeSpecCompiler` 未列 `disablePlanMode()` 不是遗漏 | 官方 builder 默认值 |

对 AAF 的意义：AAF 现有 `ControlMode.READ_ONLY` 是**整次执行级**模式，没有「同一次执行内先只读规划、再获批执行」的阶段能力。若要该能力，用程序化入口驱动 Plan Mode 比自研阶段闸门更省，且能绕开官方文档自己承认的不确定性——「是否进入 plan mode 由模型自主决定」会产生四种歧义终态，需要业务代码从 `ToolCallStartEvent` 捕获 `plan_enter` / `plan_write` 才能判断是否真的规划过。程序化入口没有这个问题。

### 修正二 · 子智能体编排的分野是「确定性」而非「有无」

初版「等价或替代清单」已记为「有自研等价物」，方向正确，但对 Harness 侧能力的描述偏保守。以下能力初版未记录：

| Harness 能力 | 证据 |
|-------------|------|
| `userId` 自动透传父→子，**多租户隔离链不断** | 官方 subagent 文档「一些行为细节」 |
| 父的全部 DENY 权限规则自动继承给子，可用 `inheritParentPermissions(false)` 关闭 | 同上 |
| Plan Mode 限制**确实**沿委派链传播 | `harness/agent/tool/AgentSpawnTool.java:299-301`：`parentState.getPlanModeContext().isPlanActive()` → `ha.enterPlanMode(currentUserId, sessionId)` |
| 后台任务持久化 + 跨副本恢复（配 `distributedStore`） | 官方 subagent 文档「跨重启与多副本」「异步任务的存储位置」 |
| 递归保护：子不能再 spawn 子，硬上限 3 层 | 官方 subagent 文档 |

因此初版正文中「Harness 的 subagent 不做租户隔离与逐节点授权」这类表述应视为**已被推翻**。

真正的分野是编排的驱动方式：

| 维度 | Harness subagent | AAF TaskBoard |
|------|-----------------|--------------|
| 拆解决策者 | LLM（`agent_spawn` 是工具） | 代码（`analyzedBoard` / `teamBoard` 执行前构造） |
| 可预先审核 | 否 | 是（`DefinitionLifecycleService.validateTeamDefinition` + `requirePublishedTeam`） |
| 审计真理源 | 流式事件 + workspace 文件 | `execution_event` 表，`(tenant_id, execution_id, sequence)` 唯一约束，可重放 |

结论：不是二选一，应按确定性要求分流——Team 执行（声明式成员、需审核）留在 Agent 外的 TaskBoard；探索性任务分解（模型自主判断需要子任务）可用 Harness 原生 subagent。

启用 subagent 的前置条件：Harness 用事件的 `source` 路径（如 `"main/researcher"`，父事件为 `null`）区分父子。AAF `AgentScopeEventMapper` 目前**只在 `AGENT_START` 分支**放了 `payload("source", source.getSource())`，其余分支均未携带。启用前必须补全，否则父子事件在 UI 上无法区分。

### 修正三 · 新增重复项 `PermissionMode`

初版未识别此项。官方 `PermissionMode` 三态与 AAF `ActionAuthorizationPolicy` 高度重叠：

| 官方 `PermissionMode` | AAF `ActionAuthorizationPolicy` | 语义 |
|----------------------|--------------------------------|------|
| `DEFAULT` | `REQUEST_ON_DEMAND` | 正常管控，ASK 时弹确认 |
| `BYPASS` | 无对应（AAF 无此逃生口） | 关闭全部规则评估 |
| `DONT_ASK` | `DENY_AUTHORIZED_ACTIONS` | 无人值守不弹确认，**ASK 决策变为 DENY** 而非放行 |

官方还提供 `setPermissionMode(ctx, mode)` 运行期 per-session 切换，保留已配置的 allow/deny/ask 规则与工作目录，只改 mode，下一次 call 生效；进行中的 call 沿用启动时的引擎。

判定：**可改造为官方扩展点**。同步补入 [04-reuse-correctness.md](04-reuse-correctness.md) 的重复能力清单。

### 修正四 · `todo_write` / `tasksContext` 不可替代 TaskBoard

官方 `todo_write`（core 提供）把清单存入 `AgentState.tasksContext`，`Task` 带 `subject` / `state`(PENDING/IN_PROGRESS/COMPLETED) / `owner` / `blocks` / `blockedBy`——结构上已是一个 DAG，与 `TaskBoard` 表面重叠。但两点使其不能替代：

- **模型全量替换**：官方文档明确「全量替换，必须恰好一个 `in_progress`」，无服务端校验、无租户隔离、无发布审核
- **无一等变更事件**：官方推荐的感知方式是监听 `todo_write` 的 `TOOL_RESULT_END`，再回头读 `AgentState`。AAF 的 `SUBTASK_*` / `TASK_STATUS_CHANGED` 是持久化、可重放的一等 `ExecutionEvent`，改用前者会丢失可重放性

因此 TaskBoard 向 AG-UI 投影仍应走 `ACTIVITY_SNAPSHOT`，不改用 `tasksContext`。

### 修正后的 Harness 原生能力启用建议

| 能力 | 建议 | 依据 |
|------|------|------|
| `PermissionMode` | 启用并替换自研 | 三态语义对应，per-session 运行期可切 |
| Plan Mode（程序化驱动） | 可选启用 | AAF 无阶段级只读闸门；程序入口避开模型自主决定的歧义 |
| Subagent（探索性分解） | 可选启用 | 需先补全 `source` 投影 |
| `todo_write` / `tasksContext` | 不启用 | 模型全量替换、无一等事件，与 TaskBoard 冲突 |
| workspace / filesystem / shell / sandbox | 保持关闭 | AAF 有自己的工具网关与授权链 |

### 方法论教训

官方两篇文档对同一安全边界给出**相反结论**：

- subagent 文档（Last updated 2026-08-09）：Plan Mode 期间 spawn 的子 agent **会**自动继承只读限制
- plan-mode 文档（Last updated 2026-07-16）：标注为「已知缺口」，**不会**自动继承

源码判定为「会继承」（`AgentSpawnTool.java:299-301`），即旧文档陈旧未更新。

由此得出的规则已写入 [agentscope-usage-guide.md](../../../reference/dev/agentscope-usage-guide.md)：涉及安全边界的能力判定，不得凭官方文档推断，必须落到源码。
