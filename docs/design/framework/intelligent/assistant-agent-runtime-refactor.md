---
level: Practice
layer: Model
purpose: 对话入口统一重构——Assistant 作为唯一对外入口、Agent 作为内置 pool，与 AgentScope AG-UI 的绑定方式
status: draft
version: 1.0.0
date: 2026-06-02
author: AaronZZH
gains:
  - 看清 AgentDefinition 与 AssistantDefinition 的职责边界与对外暴露方式
  - 知道 Assistant 如何物化为单条 AG-UI 流，内置 Agent 如何作为工具被调度
  - 知道 AG-UI 按类型注册而非按用户实例注册，实例数量为何有界
  - 拿到分阶段迁移步骤与受影响文件清单
---

# 对话入口统一重构方案

> Assistant 是用户唯一交互入口，Agent 是 Assistant 内部调度的无状态能力单元。
> 本文是 [五层智能架构](architecture.md) 在「对话入口 + AgentScope 绑定」上的落地与纠偏，配套 [Assistant 技术方案](assistant/assistant-tech.md)、[Agent 技术方案](agent/agent-tech.md)、[AgentScope 整合策略](agentscope-integration.md)。

## 背景与问题

当前代码里存在两个实体、两条对外入口，且主入口绕过了 Assistant 层，与架构文档「Assistant 是唯一入口」相矛盾。

| 实体 | 表 | 本质 | 数量级 |
|------|----|------|--------|
| `AgentDefinition` | `ai_agent_definition` | AgentScope ReActAgent 的蓝图（无状态模板，可池化） | 少量、受治理 |
| `AssistantDefinition` | `ai_assistant` | 面向用户的有状态实体 = Actor + Role + MemoryStrategy + PermissionScope + 知识库 | 海量、per-user |

两条入口：

```text
路径 A（符合设计，但非前端主路）：
  AssistantService.handle → 意图/情感 → DefaultAssistantExecutor.chat
    → SkillMatch 得 agentId → AgentPool.borrow(AgentDefinition) → 执行

路径 B（前端 ChatterRuntime 实际在用）：
  /agui/runs/{agentId} → AafAgentResolver → 直接拿裸 ReActAgent（按 AgentDefinition 构建）
    ✗ 跳过 意图理解 / 情感 / Skill 路由 / 记忆策略 / 权限边界 —— 整个 Assistant 层被绕过
```

错配点：

- **入口错配**：AG-UI 注册的是 `AgentDefinition`，前端按 `agentId` 直连 Agent，Assistant 沦为旁路。
- **并行抽象**：「Assistant 调 Agent」有三套实现——`DefaultAssistantExecutor`（Skill→agentId）、`AgentDispatcher`（intent→capability）、`AgentManagementService.execute`（按 DB id 直跑），违反「禁并行抽象」。
- **膨胀风险**：现每个 Agent 全量注册。若照搬到 per-user 的 Assistant，registry 条目随用户数无限增长且启动期全量加载。

## 领域模型定位（钉死）

| 维度 | Assistant（Layer 3） | Agent（Layer 2） |
|------|----------------------|------------------|
| 面向 | 人（唯一交互入口） | Assistant（内部调度，不对外） |
| 状态 | 有状态（会话级） | 无状态（任务级，池化） |
| 组成 | Actor 人格 + Role 能力 + MemoryStrategy + PermissionScope + 知识库 | sysPrompt + model + tools + mcpServers |
| 数量 | per-user，海量 | 少量、受治理的内置能力单元 |
| AG-UI | **注册类型工厂，按上下文物化** | **不直接注册**，作为工具暴露给 Assistant |

数量关系沿用架构文档：用户:Assistant = 1:N，Assistant:Agent = 1:N（按能力共享、池化）。

## 目标架构：Assistant 物化为协调者 Agent

AG-UI 协议需要一个 AgentScope `Agent` 来产出事件流。因此 Assistant 在运行时**物化为一个「协调者」ReActAgent**，把领域模型字段映射到 AgentScope 构件，内置 Agent 通过 agent-as-tool 被它调度——全程一条流。

```text
/agui/runs/{assistantId}
   │
   ▼
AssistantRuntime.materialize(ctx)          ← 按 threadId 上下文物化
   │  Actor.systemPrompt   → ReActAgent.sysPrompt
   │  Role.tools(白名单)    → Toolkit
   │  Role.skills          → SkillBox（按需披露）
   │  MemoryStrategy       → memory adapter（AutoContext + 记忆管道）
   │  PermissionScope      → AafToolPermissionHook
   │  前注意/意图/情感      → PreCall Hook（注入 sysPrompt 段）
   │  内置 Agent pool       → 作为 Tool（agent-as-tool）注册进 Toolkit
   ▼
协调者 ReActAgent（单条 AG-UI 流）
   ├─ 简单场景：直接回复
   └─ 复杂场景：调用 call_agent("backend-coder", ...) 委派内置 Agent
                  └→ AgentPool.borrow(AgentDefinition) → 执行 → release
```

- **简单对话/闲聊**：协调者直接回复，不触达 Agent pool（对应架构文档「前注意分流，简单请求不走 Agent」）。
- **单一任务**：协调者调用一个内置 Agent 工具完成。
- **多角色并行（fork，后置 v0.x+）**：协调者内部编排多个内置 Agent，事件 merge 进同一条流——这是协调者的高级能力，不改变对外入口契约。

> 这是对 [agentscope-integration.md](agentscope-integration.md) 编排模式映射的落地：Assistant→Agent 走 **Subagents / Supervisor（agent-as-tool）**，Skill 路由走 **SkillBox**。

## AG-UI 注册：按类型，不按实例

`AguiAgentRegistry` 存的是「类型→工厂/单例」，真正的运行实例由 `ThreadSessionManager` 按 `threadId` 管理且有界。据此：

```java
// 只注册一个 "assistant" 类型工厂（或少量模板），不按用户注册
registry.registerFactory("assistant",
    () -> assistantRuntime.materialize(AgentRunContextHolder.current()));
```

具体是哪个用户/哪个 `assistantId`，由 `AafAgentResolver` 在调 `getOrCreateAgent` **之前** 已设置的 `AgentRunContextHolder`（来自 `ChatSessionResolver.resolveByThreadId(threadId)`）解析——工厂在实例化那一刻读上下文物化对应助理。

| 关注点 | 注册对象 | key | 运行实例管理 | 数量级 |
|--------|---------|-----|------------|--------|
| 现状（待改） | 每个 AgentDefinition | agentId | ThreadSessionManager 按 threadId 有界 | registry = Agent 数 |
| 目标 | 单个 `assistant` 类型工厂 | 固定类型名 | 同上，maxSessions + 超时 + LRU 淘汰 | registry ≈ 1 |

结论：registry 永不随用户膨胀；活跃实例被 `ThreadSessionManager`（maxSessions + 超时淘汰 + 删最旧）钉死上界。per-user 差异在物化那一刻按上下文注入。

## 与 AgentScope 的绑定映射

| AAF 领域概念 | AgentScope 机制 | 说明 |
|--------------|----------------|------|
| Assistant（协调者） | `ReActAgent`（一条 AG-UI 流） | 由 `AssistantRuntime` 物化 |
| Actor 人格 | `sysPrompt` | persona/systemPrompt 注入 |
| Role 工具白名单 | `Toolkit` + `AafToolWhitelistHook` | 细粒度工具边界 |
| Role 技能集 | `SkillBox` | 按需披露，激活后才暴露绑定工具 |
| MemoryStrategy | memory adapter（`AafAutoContextMemoryAdapter` + 记忆管道） | 决定拉取哪些源 |
| PermissionScope | `AafToolPermissionHook` | 工具调用 HITL 门控 |
| 前注意/意图/情感 | `PreCallEvent` Hook | 注入上下文段，简单请求短路 |
| 内置 Agent（pool） | agent-as-tool（注册进 Toolkit 的 `call_agent`） | 协调者委派的工人 |
| AgentDefinition | `ReActAgent` 蓝图 + `AgentPool` 池化 | 不再直接对外注册 |

编排模式与适配器清单复用 [agentscope-integration.md](agentscope-integration.md)，本文不重复。

## 知识库与记忆系统接入（复用 AgentScope SPI）

AgentScope 把「知识库」和「长期记忆」各抽象成一个 2 方法 SPI，AAF 用薄适配器把自有领域组件接进去，由 `AssistantRuntime.materialize(ctx)` 按 `userId/sessionId/knowledgeBaseId` 注入——这印证了示例 `agentscope-examples/boba-tea-shop/consult-sub-agent` 的「共享 Builder + 按 userId 物化时注入 LongTermMemory」模式。

### 可复用的 AgentScope 能力（示例出处）

| 能力 | AgentScope SPI / 类 | 示例 | AAF 现状 |
|------|--------------------|------|---------|
| 知识库 RAG | `rag.Knowledge` + `RAGMode.GENERIC/AGENTIC` + `RetrieveConfig` | `advanced/RAGExample`、`quickstart/PgVectorRAGExample` | **`AafKnowledge` 已实现（`cognition/`），未接线** |
| 外部知识库 | `BailianKnowledge`/`DifyKnowledge`/`RAGFlowKnowledge`/`HayStackKnowledge` | `quickstart/DifyRAGExample` 等 | 未用；对接外部 KB 时可直接复用 |
| 长期记忆 | `memory.LongTermMemory` + `LongTermMemoryMode.STATIC_CONTROL` | `advanced/Mem0Example`、`AutoMemoryExample` | **`AafLongTermMemory` 已实现，但未接线** |
| 程序化记忆 | `ReMeLongTermMemory` | `advanced/ReMeExample` | 可选复用（参考 `tmp/mem/ReMe`） |
| 上下文压缩 | `AutoContextMemory` + `AutoContextHook` | `AutoMemoryExample` | `AafAutoContextMemoryAdapter` 已用 ✅ |
| 会话持久化 | `Session`(Json/Redis) + `agent.loadIfExists/saveTo` | `AutoMemoryExample` | 见 [assistant-tech.md](assistant/assistant-tech.md) |
| per-user 物化 | 共享 `ReActAgent.Builder` + 按 userId build 注入 LTM | `consult-sub-agent/AgentScopeRunner` | 印证 `AssistantRuntime.materialize` ✅ |

### 两个 SPI 与绑定方式

```java
// io.agentscope.core.rag.Knowledge
Mono<Void> addDocuments(List<Document> docs);
Mono<List<Document>> retrieve(String query, RetrieveConfig config);

// io.agentscope.core.memory.LongTermMemory
Mono<Void> record(List<Msg> msgs);
Mono<String> retrieve(Msg msg);
```

```java
// 物化时绑定（二选一，见下「单检索路径决策」）
builder.longTermMemory(aafLongTermMemory).longTermMemoryMode(STATIC_CONTROL); // 自动 retrieve+record
builder.knowledge(aafKnowledgeAdapter).ragMode(AGENTIC);                      // 暴露 retrieve_knowledge 工具
```

### AAF 领域组件映射

- **记忆** → `AafLongTermMemory`（已实现）：`retrieve(Msg)`→`RetrievalPipeline`，`record(List<Msg>)`→`MemoryWritePipeline`，按 `userId/sessionId` 隔离。
- **知识库** → `AafKnowledge`（已实现，`cognition/`）：`retrieve` 委托 `HybridSearchService` 并把 `RagSearchResult` 映射为 `Document`；`addDocuments` 占位（入库由文档引擎独立管理，同 Dify/Bailian 适配器）。注意 `UnifiedRetrievalService` 已把 Memory+Knowledge 做 RRF 融合——是否单独启用 `AafKnowledge` 见下「单检索路径决策」。
- **上下文压缩** → 沿用 `AafAutoContextMemoryAdapter`（`AutoContextMemory`）。

### 单检索路径决策（避免并行抽象）

因 `RetrievalPipeline → UnifiedRetrievalService` 已经把知识库一并融合检索，二选一：

- **默认（推荐）**：只接 `LongTermMemory`（`AafLongTermMemory`），知识库随 `MemoryStrategy` 在 `RetrievalPipeline` 内融合。**不**再接 AgentScope `Knowledge`/`ragMode`，避免对知识库二次检索（违反「知识能力一体」「禁并行抽象」）。
- **可选（agentic KB-as-tool）**：当要让 Agent 自主决定查库时，单独接 `AafKnowledgeAdapter` + `RAGMode.AGENTIC`；此时 `RetrievalPipeline` 关闭知识源，两条路不重叠。

`MemoryStrategy` → 模式映射：

| MemoryStrategy | 检索行为 | 绑定 |
|----------------|---------|------|
| MEMORY_ONLY | 仅记忆 | LongTermMemory（KB 关） |
| KNOWLEDGE_ONLY | 仅知识库 | LongTermMemory（route 仅 KB）或 Knowledge+GENERIC |
| HYBRID（默认） | 记忆+知识融合 | LongTermMemory（route 融合） |
| PROCEDURAL_FIRST | 程序化记忆优先 | LongTermMemory（可叠加 ReMe） |
| FULL | 全量 | LongTermMemory 融合 |

### 当前缺口（接线 TODO）

- `AafLongTermMemory`（`cognition/`）和 `AafKnowledge`（`cognition/`）均已实现，但**从未挂进 `AgentScopeRuntime`/物化逻辑**（builder 无 `.longTermMemory(...)` / `.knowledge(...)` 调用）——Phase 2 物化时补接线，并按下方分包建议移入 `cognition/agentscope/`。
- `DefaultAssistantExecutor` 现用 `MemoryPipelineFactory` 手动拼 preamble 注入上下文；接线后改由 LongTermMemory SPI 自动 `retrieve/record`，删除手动注入（禁并行抽象）。

## 包结构与集成机制

### 集成机制：接口 + 薄门面 + 组合 + Hook，禁继承

| 机制 | 职责 | 例子 |
|------|------|------|
| 接口（ports） | 定义框架无关边界，AAF 领域只依赖它 | `core/` 的 `AgentRuntime`/`AgentExecutor`/`AssistantRuntime` |
| 薄门面/适配器 | 实现接口并委托 AgentScope（has-a delegate） | `AgentScopeRuntime implements AgentRuntime`、`AgentScopeAgentAdapter implements AgentExecutor` |
| 组合 | 装配 AgentScope 构件、持有 delegate | `ReActAgent.builder()...build()`；Hook/Toolkit/Memory 注入 builder |
| Hook | 把 AAF 行为塞进 ReAct 循环（不改源码、不子类化） | `AafTraceHook`、`AafToolPermissionHook`、`MemoryContextHook` |
| 继承 | 仅被迫扩 starter 端点时用，属例外 | `AafAguiRestController extends AguiRestController` |

两个适配方向都是「接口 + 组合」，零继承：

```java
// 方向一：AAF 定接口，AgentScope 适配器实现 → 委托 ReActAgent
class AgentScopeAgentAdapter implements AgentExecutor { private final ReActAgent delegate; /* 组合 */ }
// 方向二：AgentScope 定 SPI，AAF 反向实现 → 把自有引擎插进扩展点
class AafLongTermMemory implements io.agentscope.core.memory.LongTermMemory { /* → MemoryPipeline */ }
class AafKnowledge       implements io.agentscope.core.rag.Knowledge       { /* → HybridSearch  */ }
```

不用继承：`ReActAgent` 非为子类化设计，加行为用 Hook 接口即可；唯一 `extends` 是 starter 接入缝，非领域继承。`AssistantScopeRuntime` 同理——`implements AssistantRuntime` + 复用共享 `ReActAgentBuilderFactory`（组合），不继承 `AgentScopeRuntime`。

### 框架侧：单一 `intelligent/agentscope/` 适配器环

所有 AgentScope 耦合收敛到一个包（按关注点分子包），避免 worker/coordinator 两个 runtime 各写一份 `ReActAgent.builder()` 而重复：

```
intelligent/
  core/                          框架无关接口契约（零 AgentScope import）
    agent/AgentExecutor · agent/AgentRuntime(上移)
    assistant/AssistantExecutor · assistant/AssistantRuntime(新增)
    memory/ llm/ skill/ function/ model/(接口) …
  agent/                         Agent 领域（AgentDefinition/Registry/Factory · runtime/ · run/ · trace/）
  assistant/                     Assistant 领域（AssistantDefinition · actor/ · role/ · SkillMatch · SessionManager）
  cognition/                     认知领域（memory/ retrieval/ pipeline/ personalization/ learning/）
  agentscope/                    ★ 唯一 AgentScope 适配器环
    runtime/    AgentScopeRuntime · AssistantScopeRuntime · ReActAgentBuilderFactory(共享) · AgentScopeAgentAdapter
    hook/       AafTraceHook · AafToolPermissionHook · MemoryContextHook · AafToolWhitelistHook
    memory/     AafLongTermMemory · AafAutoContextMemoryAdapter · AgentScopeMemoryAdapter
    knowledge/  AafKnowledge
    tool/       AgentScopeToolAdapter · AgentScopeToolGovernanceService
    session/    AafSessionAdapter · AgentScopeSessionAdapter · ChatSessionResolverImpl
    a2a/        AgentScopeA2AEngine
```

三条约束，确保它不变 god-package：

- **领域层零 AgentScope import**：`core/` 与各层根只放框架无关逻辑。
- **适配器环不是第六层**：ports-and-adapters 的 adapter，可依赖所有内层领域；领域绝不反向依赖它。五层「下不调上」约束领域层，适配器环在最外圈不违反。
- **只搬实现不搬契约**：`AgentRunContext(Holder)`、`ExecutionCompletedEvent`、`UserMessageEvent` 及 `*Runtime`/`*Executor` 接口留在 `core/`/`agent/run/`；只搬 import 了 `io.agentscope.*` 的实现类。

### API 侧：`agui` 提为顶层

AG-UI 是统一 AI 入口（助理对话），不再只是「聊天」子能力，从 `chat/agui/` 提升为 `module/ai/agui/`：

```
module/ai/
  agui/        AafAguiRestController · AafAgentResolver · AafAguiConfirmController
               AafAguiRegistryCustomizer · AafAguiConfiguration · ChatSessionResolverImpl
  assistant/   助理 CRUD（已有）
  agent/       内置 Agent 池管理 CRUD（admin 定位）
  chat/        会话持久化/历史 · ws(用户间聊天) · listener（移走 agui 后）
  role/ skill/ team/ model/ memory/ tool/ …
```

### 搬迁清单

| 类 / 包 | 现位置 | 目标位置 | 风险 |
|---------|--------|---------|------|
| `AgentRuntime`（接口） | `intelligent/agent/` | `intelligent/core/agent/` | 🟡 改少量 import |
| `AssistantRuntime`（接口，新增） | — | `intelligent/core/assistant/` | 🟢 新增 |
| `agent/agentscope/*` | `intelligent/agent/agentscope/` | `intelligent/agentscope/{runtime,hook,memory,tool,session}` | 🟡 改包名+import |
| `AafLongTermMemory` | `intelligent/cognition/` | `intelligent/agentscope/memory/` | 🟢 未接线 |
| `AafKnowledge` | `intelligent/cognition/` | `intelligent/agentscope/knowledge/` | 🟢 未接线 |
| `AgentScopeA2AEngine` | `intelligent/assistant/a2a/` | `intelligent/agentscope/a2a/` | 🟢 引用方少 |
| `AssistantScopeRuntime` · `ReActAgentBuilderFactory`（新增） | — | `intelligent/agentscope/runtime/` | 🟢 新增（Phase 2） |
| `chat/agui/*`（9 文件） | `module/ai/chat/agui/` | `module/ai/agui/` | 🔴 改包名+import+配置扫描 |

不搬（框架无关）：`agent/run/*`、`core/*` 接口、`cognition/` 领域（pipeline/retrieval/memory services）、`assistant/a2a/{A2AEngine,LocalA2AEngine}`。

## 关键组件改造

| 组件 | 现状 | 改造 |
|------|------|------|
| `AssistantRuntime`（新增，接口+实现） | 无 | 把 `AssistantDefinition` 编译成协调者 ReActAgent；与 `AgentRuntime` 平行 |
| `AafAguiRegistryCustomizer` | 全量注册 AgentDefinition | 改注册单个 `assistant` 类型工厂 |
| `AafAgentResolver` / `ChatSessionResolver` | key=agentId | key 切到 assistantId（上下文已带 assistantId，改动小） |
| `DefaultAssistantExecutor` | controller 外的独立路由链 | 逻辑迁入协调者 Hook / 物化逻辑；非流式后备视需要保留 |
| `AgentDispatcher` | intent→capability 第二套路由 | 收敛为 agent-as-tool 的工具选择，或删除 |
| `AgentManagementService.execute` | 用户级直跑 | 降级为 admin/调试入口，明确标注非对话入口 |
| 内置 Agent → 工具 | 现作为 AG-UI 入口 | 改为 `call_agent` 工具暴露给协调者（复用 `WorkflowTool` 同款 agent-as-tool 思路） |
| 前端 `ChatterRuntime` | `/agui/runs/{agentId}` | 改 `/agui/runs/{assistantId}` |

## 迁移步骤（分阶段，全替换无兼容层）

遵循「禁兼容层」：每阶段是完整替换，不保留双路径。

- **Phase 1 入口对齐**：registry 注册 `assistant` 类型工厂；resolver/`ChatSessionResolver` key 切 assistantId；前端路由切 `/agui/runs/{assistantId}`。物化暂时仍按「Assistant 选一个默认 Agent」过渡。
  影响：`AafAguiRegistryCustomizer`、`AafAgentResolver`、`ChatSessionResolver`、`ChatterRuntime.tsx`。
- **Phase 2 Assistant 物化**：新增 `AssistantRuntime`，将 Actor/Role/MemoryStrategy/PermissionScope 映射到协调者 ReActAgent；内置 Agent 以 `call_agent` 工具暴露。
  影响：新增 `AssistantRuntime`/实现、`McpToolService`（注册 `call_agent`）、`AgentScopeRuntime`（复用 build 逻辑）。
- **Phase 3 收敛并行抽象**：`DefaultAssistantExecutor` 的意图/情感/前注意迁入 Hook；删除/收敛 `AgentDispatcher`；`AgentManagementService.execute` 降级 admin。
  影响：`DefaultAssistantExecutor`、`AssistantService`、`AgentDispatcher`、`AgentManagementService`。

## 风险与门控

- 🔴 **高风险架构调整**：改对外入口契约 + 跨 ≥5 文件 + 前后端联动，按 [协作红线](../../../../.kiro/steering/collaboration.md) 必须人类审核后再开发。
- **数据**：不新增表；需确认 `actor` / `role` / `ai_skill_definition` 的 seed 数据齐备，且每个 `AssistantDefinition` 能解析出有效 Actor+Role。
- **回滚**：按 Phase 粒度回滚；Phase 1 可独立验证（前端能按 assistantId 跑通即通过）。

## 开放问题

- **Skill 语义对齐**：架构文档把 Skill 定义在 Assistant 层（路由规则），而代码 `SkillStore.findByAgentId` 把 Skill 绑在 Agent 上做 SkillBox 披露。需统一：Assistant 层 Skill = 路由/披露规则，Agent 层不再独立持有 Skill。
- **fork 多实例 vs 单条 AG-UI 流**：协调者内部并行如何 merge 事件流，作为 v0.x+ 高级能力单独设计，不阻塞本次入口统一。
- **非流式后备入口**：`/api/chat/run` 等是否保留为后备，待定。

## AgentScope API 使用验证（对比示例 2026-06-02）

对照 `tmp/agent/agentscope-java/agentscope-examples/`（hitl-chat、agui、quickstart、advanced）逐一验证 AAF 适配器环用法。

### ✅ 已正确使用

- Hook 签名 `implements Hook` + `onEvent(T) → Mono<T>` + `priority()`
- PostReasoningEvent.stopAgent()（AafToolPermissionHook HITL 暂停）
- PostReasoningEvent.gotoReasoning(Msg)（AafToolPermissionHook 拒绝后重走推理）
- AutoContextMemory + AutoContextHook 配套注册
- AG-UI Registry: `registry.registerFactory(id, factory)` + ThreadSessionManager
- HITL Confirm/Reject: approved → `agent.stream(StreamOptions.defaults())`；rejected → `agent.stream(cancelMsg with ToolResultBlock[])`
- Toolkit.registerTool(obj) + @Tool/@ToolParam
- SkillBox + registerSkillLoadTool + registration().skill().tool().apply()
- agent.interrupt()

### 🔴 需修正（3 项）

#### AafToolWhitelistHook 拦截方式非惯例

**现状**：PreActingEvent 中替换 ToolUseBlock 为 `__blocked__` 工具名。该名不在 Toolkit 中，AgentScope 会返回错误 ToolResult（Agent 可恢复，但产生无意义的重试轮次）。

**示例惯例**：PostReasoningEvent + stopAgent()（hitl-chat/advanced/hitl ToolConfirmationHook）或直接 gotoReasoning(拒绝消息)。

**建议**：改为在 PostReasoningEvent 中遍历 ToolUseBlock，命中黑名单的用 `gotoReasoning(ToolResult 拒绝消息)` 阻止执行并告知 Agent 原因——与 AafToolPermissionHook DENIED 分支同模式。

#### LongTermMemory 未接线（record 缺失）

**现状**：`AafLongTermMemory` 已实现 `retrieve/record`，但 `AgentScopeRuntime.buildReActAgent()` 无 `.longTermMemory(...)` 调用。

**示例惯例**（AutoMemoryExample）：
```java
builder.longTermMemory(longTermMemory)
       .longTermMemoryMode(LongTermMemoryMode.STATIC_CONTROL)
```

**影响**：AgentScope 框架不会自动调用 `retrieve`（AAF 用 MemoryContextHook 变相代替了 retrieve，但非惯例）；`record` 完全缺失——对话结束后不写回长期记忆。

**建议**：Phase 2 物化时在 `AssistantScopeRuntime.materialize(ctx)` 中根据 `userId/sessionId` 构造 `AafLongTermMemory` 实例并接入 builder。接入后可考虑简化/移除 `MemoryContextHook`（让 AgentScope 内置 `StaticLongTermMemoryHook` 接管 retrieve 注入）。

#### Session saveTo 未调用（状态不持久化）

**现状**：`AafSessionAdapter` 创建了 RedisSession，`AafAgentResolver` 通过 ThreadSessionManager 做内存态 per-thread 复用，但 **doFinally 无 saveTo**——服务重启后 Agent 状态（Memory、PlanNotebook）全丢。

**示例惯例**（hitl-chat AgentService、AutoMemoryExample）：
```java
agent.stream(userMsg)
    .doFinally(signal -> { agent.saveTo(session, sessionId); })
    .subscribe();
```

**建议**：在 AG-UI 事件流的 doFinally 中（`AafAguiRestController` 或 ThreadSessionManager 的 lifecycle hook）加入 `agent.saveTo(redisSession, threadId)`。需确认 ThreadSessionManager 是否暴露了 onEvict/onComplete 回调。

### HITL 交互架构分析

AAF 有**两套并行的 HITL 机制**，需要明确职责划分：

#### 路径 A：AgentScope 原生 HITL（AG-UI 链路）

```text
Agent 执行中 → AafToolPermissionHook.stopAgent() → Agent 暂停
  → AG-UI 事件流推送 requires-action 状态
  → 前端展示确认 UI
  → POST /agui/runs/{threadId}/confirm
  → AafAguiConfirmController: approved → agent.stream(StreamOptions.defaults())
                              rejected → agent.stream(cancelMsg)
  → Agent 恢复执行
```

**关键组件**：`AafToolPermissionHook` + `AafAguiConfirmController` + `ToolPermissionChecker`（grantWithScope）
**特点**：同步阻塞 Agent（stopAgent），恢复靠 `agent.stream()` 续跑，无中间状态持久化。

#### 路径 B：AAF 通用审批（独立 REST + SSE）

```text
AI 执行中 → HumanApprovalService.request() → 存入 pending Map + 生成 requestId
  → ApprovalEventStreamService.publish() → SSE 推送 approval_request 事件
  → 前端展示审批 UI
  → POST /api/assistant/approvals/{requestId}/resolve
  → HumanApprovalService.resolve() → 发布 ApprovalResolvedEvent
  → HitlApprovalGrantListener → ToolPermissionChecker.grantWithScope()
  → AI 侧轮询 getResult(requestId) 获取决策
```

**关键组件**：`HumanApprovalService` + `HumanApprovalController` + `ApprovalEventStreamService` + `HitlApprovalGrantListener` + `AssistantSessionTrustService`
**特点**：异步轮询模式（AI 发 request 后轮询 getResult），支持多种审批类型（8 种 ApprovalType），有超时机制。

#### 两条路径的关系与问题

| 维度 | 路径 A（AG-UI） | 路径 B（通用审批） |
|------|----------------|-------------------|
| 触发方 | AgentScope Hook 自动触发 | 业务代码主动调用 |
| 阻塞方式 | Agent 同步暂停（stopAgent） | 异步轮询（getResult） |
| 恢复方式 | agent.stream() 续跑 | 授权后下次工具调用自动通过 |
| 适用链路 | AG-UI SSE 流 | 任何 AI 执行链路 |
| 状态持久化 | 无（内存态，重启丢失） | 内存 Map（同样重启丢失） |
| 前端入口 | `/agui/runs/{threadId}/confirm` | `/api/assistant/approvals/{requestId}/resolve` |

**核心问题**：路径 A 的 `AafToolPermissionHook` stopAgent 后，没有调用路径 B 的 `HumanApprovalService.request()`。两套机制各自独立，前端需要知道从哪个端点获取审批请求和提交结果。

**建议**（Phase 2 统一）：
- AG-UI 链路（路径 A）作为**执行层机制**——负责暂停/恢复 Agent 执行流。
- 通用审批（路径 B）作为**通知层机制**——负责推送审批事件、记录审批历史、管理信任关系。
- 桥接：`AafToolPermissionHook` 在 stopAgent 同时调用 `HumanApprovalService.request()` 发起审批记录 + 推送通知；`AafAguiConfirmController` 在用户确认时同步调用 `HumanApprovalService.resolve()` 归档。这样两条路不再并行，而是分层协作。

## 相关文档

- [五层智能架构](architecture.md)
- [Assistant 技术方案](assistant/assistant-tech.md)
- [Agent 技术方案](agent/agent-tech.md)
- [AgentScope 整合策略](agentscope-integration.md)
- [ADR-005 AgentScope 整合策略](../../adr/ADR-005-agentscope-integration-strategy.md)

## 架构重构意见：以认知模型驱动，AgentScope 仅为执行层（2026-06-02）

### 问题

当前 `AssistantScopeRuntime.materialize()` 的代码组织方式是**以 AgentScope API 为骨架**——每一步都是在"如何配置 ReActAgent.Builder"。这导致：

1. 认知循环（感知→理解→规划→执行→评估→学习）的语义被 AgentScope 的技术概念（Hook/Toolkit/Memory/SkillBox）掩盖
2. 如果换掉 AgentScope（用 LangChain4j / Spring AI Agent），整个物化逻辑要重写——认知模型的组织方式没有沉淀在领域层
3. 五层智能架构（Core→Cognition→Agent→Assistant→Team）的分层在代码中看不到——所有逻辑堆在一个适配器类里

### 目标架构：认知模型 → 领域编排 → 技术适配

```text
┌─ 领域层（框架无关，五层智能语义）─────────────────────────────────┐
│                                                                    │
│  AssistantCognitivePipeline（认知管道，编排顺序）                  │
│    ├─ PreAttentionFilter      → 前注意分流（规则/小模型）          │
│    ├─ EmotionPerception       → 情感感知                          │
│    ├─ ContextRetrieval        → 上下文构建（记忆+知识检索）        │
│    ├─ IntentRouting           → 意图路由 / Skill 匹配             │
│    ├─ ExecutionStrategy       → 执行策略选择（直答/工具/规划/委派）│
│    ├─ ConfidenceGate          → 置信度评估                        │
│    └─ LearningFeedback        → 学习反馈                          │
│                                                                    │
│  每个步骤是领域接口，不依赖任何 AgentScope 类                      │
└────────────────────────────────────────────────────────────────────┘
                              ↓ 编译为
┌─ 适配器层（AgentScope 技术实现）──────────────────────────────────┐
│                                                                    │
│  AgentScopeCognitiveCompiler（将认知管道编译为 AgentScope 构件）   │
│    ├─ PreAttentionFilter   → PreCallEvent Hook                    │
│    ├─ EmotionPerception    → PreReasoningEvent Hook               │
│    ├─ ContextRetrieval     → MemoryContextHook                    │
│    ├─ IntentRouting        → SkillBox                             │
│    ├─ ExecutionStrategy    → Toolkit + PlanNotebook + TaskTool    │
│    ├─ ConfidenceGate       → PostReasoningEvent Hook              │
│    └─ LearningFeedback     → PostCallEvent Hook                   │
│                                                                    │
│  最终输出：配置好的 ReActAgent                                     │
└────────────────────────────────────────────────────────────────────┘
```

### 与当前代码的差距

| 维度 | 当前 | 目标 |
|------|------|------|
| 物化入口 | `AssistantScopeRuntime.materialize()` 直接操作 Builder | 领域层 `AssistantCognitivePipeline` 定义认知步骤 → Compiler 翻译为 Builder 调用 |
| 认知步骤 | 隐式散落在各 Hook 中，看代码看不出认知循环全貌 | 显式定义为 Pipeline 步骤列表，一眼可见 |
| 可替换性 | 换框架要重写 materialize | 只需重写 Compiler（Hook→其他框架的等价物），Pipeline 不变 |
| 可配置性 | 写死的 Hook 链 | Pipeline 按 AssistantDefinition 配置动态组装步骤（某些助理不需要情感感知） |

### 建议实现路径

**当前不大改**——先跑通对话流程，验证端到端可用。但在后续迭代中逐步重构为：

1. **Phase A（当前）**：保持现状，`AssistantScopeRuntime.materialize()` 作为唯一物化入口，注释中标注每步对应的认知循环阶段（已做）
2. **Phase B（v0.2）**：抽取 `AssistantCognitivePipeline` 接口——定义认知步骤列表，`AssistantScopeRuntime` 改为读取 Pipeline 配置再编译为 Builder 调用
3. **Phase C（v0.3）**：各认知步骤提升为独立接口（`PreAttentionFilter` / `EmotionPerception` / ...），支持按 AssistantDefinition 动态组装——不同助理可有不同认知管道

### 包结构演进（目标态）

```
intelligent/
  core/
    cognitive/                     认知管道接口（框架无关）
      CognitivePipeline           管道定义（步骤列表）
      CognitiveStep               步骤接口
      PreAttentionFilter           前注意分流
      EmotionPerception            情感感知
      ContextRetrieval             上下文构建
      ExecutionStrategy            执行策略
      ConfidenceGate               置信度评估
      LearningFeedback             学习反馈
    assistant/
      AssistantRuntime             物化接口（不变）
  assistant/
    pipeline/                      认知管道默认实现
      DefaultCognitivePipeline     默认管道（全步骤）
      SimpleCognitivePipeline      简化管道（跳过情感/规划）
  agentscope/
    runtime/
      AgentScopeCognitiveCompiler  将管道编译为 ReActAgent（Hook 映射）
      AssistantScopeRuntime        保留为薄包装（调 Compiler）
```

### 当前代码的过渡注释

`AssistantScopeRuntime.materialize()` 的每个 Step 已标注对应认知阶段：
- Step 2（systemPrompt）= 人格载体
- Step 3（model）= 推理引擎
- Step 4（Memory）= 短期记忆
- Step 5（Hook 链）= 认知循环各阶段的技术实现
- Step 6（Toolkit）= 执行能力
- Step 7（SkillBox）= 意图路由 / 渐进披露
- Step 8（PlanNotebook）= 多步规划

这些注释是向目标架构过渡的桥梁——后续重构时按注释拆分即可。
