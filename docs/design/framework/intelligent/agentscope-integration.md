---
level: Practice
layer: Model
purpose: AgentScope 整合策略——薄门面适配、编排模式映射、记忆/知识库对接
status: published
version: 1.0.0
date: 2026-05-28
author: AaronZZH
---

# AgentScope 整合策略

> 决策记录见 [ADR-005](../../adr/ADR-005-agentscope-integration-strategy.md)。
> AgentScope 为骨架，AAF 五层架构作为薄门面。

## 整合原则

```text
AAF 五层（薄门面，只保留 AAF 特有扩展，~50-100 行/层）
    └── AgentScope（厚实现：运行时/编排/状态/工具/Hook）
```

每层只做两件事：持有 AgentScope 组件（委托执行）+ 添加 AAF 特有逻辑。

**判断标准**：一个类里超过 50% 的代码是在调用 AgentScope → 这层太厚，需削减。

| AAF 层 | 委托给 AgentScope | AAF 特有扩展 |
|--------|-----------------|-------------|
| Team | coordinator Assistant + A2A | 协作规范容器、冲突仲裁 |
| Assistant | `DefaultAssistantExecutor` + `AgentPool` | 情感感知、用户画像注入、Skill 匹配 |
| Agent | `AgentRuntime` → `AgentScopeRuntime` → `ReActAgent` | `AgentExecutor` 接口抽象、`ToolPermissionGuard` |
| Cognition | `GenericRAGHook` + `LongTermMemory`（待接入） | 记忆管道、用户私有隔离 |
| Core | AgentScope `Model` | Token 计量（`TokenMeteringHook`）、模型路由 |

## AgentScope 运行时能力

| 能力 | 实现 | 说明 |
|------|------|------|
| 状态持久化 | `StateModule` + `Session` | Agent/Memory/PlanNotebook 均可 saveTo/loadFrom |
| Session 后端 | `JsonSession` / `RedisSession` / `MySQLSession` | 开发用 JSON，生产用 Redis/MySQL |
| 优雅关闭 | `GracefulShutdownManager` | 正在执行的 Agent 完成当前轮次后再停止 |
| Tracing | `Tracer` / `JsonlTraceExporter` | 完整调用链记录，支持回放和审计 |

## 编排模式映射

| AgentScope 模式 | AAF 五层对应 | 结合点 |
|----------------|-------------|--------|
| Pipeline（顺序/并行/循环） | Team → 多 Assistant 协作 | 替换 `TeamOrchestrator` + `TaskDistributor` |
| Supervisor（监督者调度专家） | Team → Leader → Member | 替换 `AgentDispatcher`，Leader 通过工具调用 Member |
| Routing（分类路由） | Assistant → 意图理解 → Agent 路由 | 替换 `SkillMatchService` 路由逻辑 |
| Skills（按需加载技能） | Agent → 技能引擎 | 替换 `SkillMatchEngine`，技能按需注入上下文 |
| Subagents（编排委托） | Agent → 子 Agent 工具调用 | Agent-as-Tool 模式 |
| Handoffs（状态驱动切换） | Assistant 会话中切换角色 | 替换角色切换逻辑 |
| Custom Workflow（自定义图） | 编排引擎 + 智能层 | 工作流节点可嵌入 Agent |
| MsgHub（消息广播） | Team 层多 Assistant 共享消息 | 多 Assistant 辩论/协商 |

## 与 AAF 系统的结合点

```text
AgentScopeRuntime → ReActAgent
    │
    ├── Hook（TokenMeteringHook）
    │     → 每次 LLM 调用后记录 Token 用量
    │
    ├── Toolkit（McpToolService.buildToolkit）
    │     → AAF 工具引擎的工具注册为 AgentScope Toolkit
    │     → MCP 协议工具通过 McpToolService 接入
    │
    ├── ToolPermissionGuard
    │     → Agent 执行前校验工具调用权限
    │
    ├── Cognition（待接入 Hook 体系）
    │     → AafLongTermMemory（待实现）对接 AAF AtomMemory
    │     → AafKnowledge（待实现）对接 HybridSearchService
    │     → 当前由 DefaultAssistantExecutor 在调用前手动注入上下文
    │
    └── AafAutoContextMemoryAdapter
          → AutoContextMemory 自动按 Token 预算截断上下文
```

**当前实现路径**（v0.1.0）：
```text
AssistantService → CognitiveCycleExecutor → AgentFactory → AgentRuntime
  → AgentScopeRuntime → ReActAgent.builder().hook(tokenMeteringHook).build()
  → AgentScopeAgentAdapter（实现 AgentExecutor）
```

**目标路径**（v0.2+，Hook 体系完整接入后）：
```text
AgentScopeRuntime 构建时注入全部 Hook：
  → TokenMeteringHook（已实现）
  → AafToolWhitelistHook（待实现）
  → AafToolPermissionHook（已实现，工具级权限门控）
  → AafTraceHook（待实现）
  → StaticLongTermMemoryHook + AafLongTermMemory（待实现）
  → GenericRAGHook + AafKnowledge（待实现）
```

## 记忆/知识库整合

实现 AgentScope `LongTermMemory` 接口，对接 AAF 记忆管道：

```java
public class AafLongTermMemory implements LongTermMemory {
    private final MemoryWritePipeline writePipeline;
    private final RetrievalPipeline retrievalPipeline;

    @Override
    public Mono<Void> record(List<Msg> msgs) {
        return Mono.fromRunnable(() -> writePipeline.execute(...));
    }

    @Override
    public Mono<String> retrieve(Msg msg) {
        return Mono.fromCallable(() -> retrievalPipeline.execute(...).toPromptSection());
    }
}
```

AgentScope `STATIC_CONTROL` 模式自动在推理前调用 `retrieve()`、回复后调用 `record()`。

> 知识库/记忆如何按领域模型经适配器接入物化后的助理 Agent，及示例出处与单检索路径决策，见 [对话入口统一重构方案 # 知识库与记忆系统接入](assistant-agent-runtime-refactor.md)。

## 适配器清单

| 适配器 | AgentScope 能力 | 替换 AAF 组件 | 状态 |
|--------|----------------|--------------|------|
| `AgentScopeAgentAdapter` | `ReActAgent` 执行 | `AgentExecutor` 接口实现 | ✅ 已实现 |
| `AgentScopeRuntime` | `ReActAgent.Builder` 构建 | `AgentRuntime` 接口实现 | ✅ 已实现 |
| `AafSessionAdapter` | `RedisSession` 工厂 | Agent 状态持久化 | ✅ 已实现（静态工厂） |
| `AgentScopeSessionAdapter` | `SessionManager` 流式 API | 会话 load/save/delete | ✅ 已实现 |
| `AafAutoContextMemoryAdapter` | `AutoContextMemory` 工厂 | Token 预算截断 | ✅ 已实现（静态工厂） |
| `AgentScopeMemoryAdapter` | `Memory` 委托 | `WorkingMemoryImpl` | ✅ 已实现（薄包装） |
| `AgentScopeToolAdapter` | `Toolkit` 委托 | `ToolRegistry` 部分替代 | ✅ 已实现（薄包装） |
| `AgentScopeA2AEngine` | A2A 协议 | `A2AProtocolService` | ✅ 骨架已实现（远程调用待补全） |
| `AafLongTermMemory` | `LongTermMemory`（record/retrieve） | 记忆管道接入 Agent | ✅ 已实现，**未接线**（builder 未调 `.longTermMemory`） |
| `AafKnowledge` | `Knowledge`（retrieve/addDocuments） | `HybridSearchService` 暴露给 Agent | ✅ 已实现（`cognition/`），**未接线** |

## 待引入依赖

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-agui-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-a2a-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-session-redis</artifactId>
</dependency>
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-autocontext-memory</artifactId>
</dependency>
```

## Agent 池化 vs LLM 池化

AAF 采用 Agent 池化，不做 LLM 池化：

| | Agent 池化 | LLM 池化 |
|---|---|---|
| 池化对象 | `AgentExecutor` 实例 | `ChatClient` 连接 |
| 原因 | Agent 创建有开销（工具注册、配置加载） | LLM 是无状态 HTTP 调用，无需池化 |
| 归还时 | `reset()` 清空对话历史 | — |

## PlanNotebook vs AAF 任务系统

| | AgentScope PlanNotebook | AAF 任务系统 |
|---|---|---|
| 是什么 | Agent 执行期的子任务规划 | 系统级后台任务调度 |
| 谁用 | ReActAgent 内部 | 运维/管理员 |
| 对应层 | Agent 规划模块 | 调度引擎 |

两者并存，不互相替代。

## AgentScope 1.1.0 新特性（AAF 升级到 1.1.0-RC2）

### 已启用

| 特性 | API | 说明 | 应用位置 |
|------|-----|------|---------|
| 工具中断恢复 | `enablePendingToolRecovery(true)` | HITL stopAgent 后自动恢复挂起的 ToolUseBlock | `AssistantScopeRuntime` / `AgentScopeRuntime` |
| 工具执行上下文 | `toolExecutionContext(ctx)` | 工具方法声明 `AgentRunContext` 参数自动注入，替代 ThreadLocal | `AssistantScopeRuntime` |

### 待启用

| 特性 | API | 应用场景 | 优先级 |
|------|-----|---------|--------|
| 模型重试/超时 | `modelExecutionConfig(ExecutionConfig)` | LLM 调用失败自动重试、超时控制 | 🔴 应立即启用 |
| 结构化输出 | `structuredOutputReminder(reminder)` | 语义组件生成界面（JSON Schema 强制输出） | 🔴 应立即启用 |
| 模型请求压缩 | `model-request-compression` 示例模式 | 长对话降低 Token 消耗 | 🟡 已有 AutoContextMemory 覆盖，可选增强 |
| 长期记忆异步写入 | `longTermMemoryAsyncRecord(true)` | record 不阻塞 Agent 执行，异步写回 | 🟡 Phase 2 接线时启用 |
| 多知识库 | `knowledges(List<Knowledge>)` | 一个 Agent 接多个知识库 | 🟡 多知识库场景时启用 |
| 计划笔记本 | `enablePlan()` + `PlanNotebook` | Agent 自主拆分子任务并追踪进度 | 🟡 替代/增强 AAF TaskBoard |
| 生成选项 | `generateOptions(GenerateOptions)` | 控制 temperature/top_p 等生成参数 | 🟢 按需 |

### 多 Agent 编排模式（multiagent-patterns 示例参考）

AgentScope 1.1.0 提供 6 种编排模式，AAF 可按需选用：

| 模式 | 示例 | 核心机制 | AAF 应用场景 |
|------|------|---------|-------------|
| **SubAgent（任务委派）** | `multiagent-patterns/subagent` | `TaskTool` + `TaskOutputTool`：协调者通过工具委派子 Agent 执行，支持同步/后台模式 | 助理协调者委派专精 Agent（代码分析、知识库查询等） |
| **Supervisor（监督者）** | `multiagent-patterns/supervisor` | 一个 Agent 管理多个子 Agent，每个子 Agent 注册为 SubAgentTool | 简单的多 Agent 分工（日程+邮件） |
| **Handoffs（交接）** | `multiagent-patterns/handoffs` | Graph 状态机 + TransferTo 工具，Agent 间转接对话上下文 | 客服场景：销售→技术支持→售后交接 |
| **Routing（路由）** | `multiagent-patterns/routing` | 分类→路由到对应 Agent | AAF 前注意分流 / Skill 匹配 |
| **Pipeline（流水线）** | `multiagent-patterns/pipeline` | 顺序/并行 Pipeline | 文档处理流水线（分析→改写→校验） |
| **Skills（技能）** | `multiagent-patterns/skills` | SkillBox 渐进披露 | AAF 已使用 ✅ |

#### SubAgent 模式详解（与 AAF TaskBoard 的关系）

subagent 示例的 `TaskTool` 工作方式：

```text
协调者 Agent
  → LLM 决定需要委派 → 调用 Task(description, prompt, subagent_type)
    → TaskTool 找到对应子 Agent → agent.call(prompt) → 返回结果
    → 或 run_in_background=true → 异步执行，返回 task_id
  → 需要结果时 → 调用 TaskOutput(task_id) → 获取执行结果
  → 综合所有结果 → 给用户最终回复
```

**与 AAF TaskBoard 的映射**：
- `TaskTool` ≈ AAF `TaskBoard.addTask()` + `AgentPool.borrow()`
- `TaskOutputTool` ≈ AAF `TaskBoard.getTask(id).result()`
- `TaskRepository` ≈ AAF `TaskBoard`（任务状态存储）
- `AgentSpec` ≈ AAF `AgentDefinition`（子 Agent 蓝图）

**建议**：AAF 可参考此模式实现 `plan_and_dispatch` 工具（暴露给协调者 Agent），内部复用已有的 TaskBoard + AgentPool。

#### Handoffs 模式（客服交接场景）

适用于 AAF 的企业客服功能——用户问题从通用助理交接给专精助理：

```text
用户 → 默认助理（分类意图）
  ├─ 销售咨询 → TransferToSales 工具 → 销售助理接管
  ├─ 技术支持 → TransferToSupport 工具 → 技术助理接管
  └─ 简单问题 → 直接回复
```

与 AAF 的 Skill 匹配不同——Handoffs 是**带上下文的 Agent 切换**（整个对话交接），Skill 是**同一 Agent 切换行为**。

### modelExecutionConfig（模型重试/超时）

```java
builder.modelExecutionConfig(ExecutionConfig.builder()
        .maxRetries(3)              // 最多重试 3 次
        .retryDelay(Duration.ofSeconds(1))  // 重试间隔
        .timeout(Duration.ofSeconds(60))    // 单次调用超时
        .build());
```

### structuredOutputReminder（结构化输出）

```java
// 语义组件生成界面时，强制 LLM 输出 JSON
builder.structuredOutputReminder(StructuredOutputReminder.json(schema));
```

适用于 AAF 的语义组件引擎——生成 UI 布局、表单定义、图表配置时需要严格 JSON 格式。

## 相关文档

- [五层智能架构](architecture.md)
- [Agent 技术方案](agent/agent-tech.md)
- [记忆管道](cognition/memory-pipeline.md)
- [ADR-005 AgentScope 整合策略](../../adr/ADR-005-agentscope-integration-strategy.md)


示例                   │ 核心模式                │ 对 AAF 的价值                                 │ 优先级      │
├────────────────────────┼─────────────────────────┼───────────────────────────────────────────────┼─────────────┤
│ m                      │ Agent                   │ 正好对应 AAF "助理协调者路由到不同能力"       │ 🟡 参考     │
│ ultiagent-patterns/han │ 交接（客服→销售→技术支  │ 的场景，用 Graph + TransferTo 工具实现        │             │
│ doffs                  │ 持）                    │                                               │             │
├────────────────────────┼─────────────────────────┼───────────────────────────────────────────────┼─────────────┤
│ m                      │ 协调者 + SubAgent +     │ 最贴近 AAF 架构——OrchestratorService          │ 🔴 重要参考 │
│ ultiagent-patterns/sub │ TaskTool                │ 管理子任务，AgentSpec 定义子 Agent 规格       │             │
│ agent                  │                         │                                               │             │
├────────────────────────┼─────────────────────────┼───────────────────────────────────────────────┼─────────────┤
│ m                      │ 意图路由（GitHub/Notion │ 对应 AAF 的 Skill 匹配/前注意分流             │ 🟡 参考     │
│ ultiagent-patterns/rou │ /Slack 分发）           │                                               │             │
│ ting                   │                         │                                               │             │
├────────────────────────┼─────────────────────────┼───────────────────────────────────────────────┼─────────────┤
│ m                      │ Supervisor              │ 简单版多 Agent 委派                           │ 🟢 已覆盖   │
│ ultiagent-patterns/sup │ 模式（日程+邮件）       │                                               │             │
│ ervisor                │                         │                                               │             │
├────────────────────────┼─────────────────────────┼───────────────────────────────────────────────┼─────────────┤
│ d                      │ PlanNotebook 任务规划 + │ 对应 AAF 的                                   │ 🟡 参考     │
│ ocumentation/plan-note │ Web UI                  │ TaskBoard，展示如何可视化子任务进度           │             │
│ book                   │                         │                                               │             │
├────────────────────────┼─────────────────────────┼───────────────────────────────────────────────┼─────────────┤
│ d                      │ 优雅关机（saveTo +      │ 对应 AAF 刚加的 saveTo/loadIfExists           │ ✅ 已覆盖   │
│ ocumentation/graceful- │ 恢复）                  │                                               │             │
│ shutdown               │                         │                                               │             │
├────────────────────────┼─────────────────────────┼───────────────────────────────────────────────┼─────────────┤
│ a                      │ 完整的 Coding           │ 对应 AAF 的 Kiro/AutoDev                      │ 🟡 参考     │
│ gents/agentscope-codin │ Agent（文件读写+命令执  │                                               │             │
│ gagent                 │ 行）                    │                                               │             │
├────────────────────────┼─────────────────────────┼───────────────────────────────────────────────┼─────────────┤
│ d                      │ 模型请求压缩            │ 新特性，可能优化 Token 用量                   │ 🟢 后续     │
│ ocumentation/model-req │                         │                                               │             │
│ uest-compression       │                         │                                               │    


特性                                 │ 说明                                  │ AAF 是否用了               │
├──────────────────────────────────────┼───────────────────────────────────────┼────────────────────────────┤
│ enablePendingToolRecovery            │ HITL 恢复健壮性                       │ ✅ 已启用                  │
├──────────────────────────────────────┼───────────────────────────────────────┼────────────────────────────┤
│ toolExecutionContext                 │ 工具方法参数自动注入                  │ ✅ 已启用                  │
├──────────────────────────────────────┼───────────────────────────────────────┼────────────────────────────┤
│ PlanNotebook + enablePlan()          │ Agent 自主拆分子任务 + 追踪进度       │ ❌ 可替代 AAF 的 TaskBoard │
├──────────────────────────────────────┼───────────────────────────────────────┼────────────────────────────┤
│ Graph（来自 alibaba-cloud-ai-graph） │ 状态图编排多 Agent（handoffs 示例用） │ ❌ 新依赖，评估中          │
├──────────────────────────────────────┼───────────────────────────────────────┼────────────────────────────┤
│ modelExecutionConfig                 │ 模型调用重试/超时                     │ ❌ 后续按需                │
├──────────────────────────────────────┼───────────────────────────────────────┼────────────────────────────┤
│ StructuredOutputReminder             │ 强制结构化输出                        │ ❌ 后续按需     


---

## HarnessAgent 演进方向（待评估）

> 记录于 2026-06-08，来源：对 `agentscope-harness` 及四个官方示例的分析。

### 背景

AAF 目标是"可控可编排、自主规划、自进化的通用助理"，与 AgentScope 官方示例体系高度重叠：

| 示例 | 核心能力 | AAF 对应目标 |
|------|---------|-------------|
| **agentscope-claw** | 自进化 Agent（自写技能/孵化子 Agent）+ 多通道 | 自进化、外部生态整合 |
| **agentscope-builder** | 多用户隔离 workspace + 无代码搭 Agent + 知识共享 | 无代码开发、多租户 |
| **agentscope-codingagent** | 自主执行复杂任务 + Docker Sandbox 隔离 | auto-dev、沙箱执行 |
| **agentscope-dataagent** | per-用户进化 + Marketplace 审批共享 | 知识沉淀、技能市场 |

四个示例**全部绕开了 `AguiAgentRegistry`**，直接用 `HarnessAgent` + 自定义 `Gateway`/`Channel`——与 AAF 当前 `AafAgentResolver` + `AssistantRuntime` 的做法一致，方向正确。

### 分阶段演进建议

**v0.1（当前）**：维持 `ReActAgent` + `AssistantRuntime.materialize()`，跑通核心链路。删除无效的 `AafAguiRegistryCustomizer`。

**v0.2 智能层重构**：`AssistantRuntime.materialize()` 的产物从 `ReActAgent` 升级为 `HarnessAgent`，获得：
- 技能文件系统（自学习、自写技能）
- 动态子 Agent 孵化
- Workspace 隔离（per-用户/per-Assistant）
- 内置记忆压缩（`CompactionMiddleware`）

**v0.2+ 外部生态整合**：参考 claw 的 `Channel` 抽象直接复用钉钉/飞书/企微适配器，不重写。

**auto-dev 模块（远期）**：Agent 执行代码时参考 codingagent 的 Sandbox 生命周期管理（Docker/K8s/E2b）。

### 关键判断

`HarnessAgent` 不是替换 `ReActAgent` 的竞品，而是在 `ReActAgent` 之上的封装——加了 Filesystem、Middleware 链、Skill 管理。引入后 AAF 智能层可以更薄，把自进化/技能管理的重实现委托给 Harness。



---

## AgentScope 2.x 升级要点（待评估）

> 记录于 2026-06-08，来源：`tmp/agentscope-java/docs/v2/zh/docs/change-log.md`。
> 当前 AAF 使用的是 1.x API，v0.2 升级 AgentScope 2.x 时需对照以下内容迁移。

### 必须迁移（不改会编译失败）

| 1.x | 2.x 替代 |
|-----|---------|
| `Pipeline` / `MsgHub` 等多 Agent 编排类 | **全部删除**，改用 middleware + 子 agent（`SubagentsMiddleware`） |
| `AgentMetaState` | 改名为 `AgentState` |
| `StateModule` / `StatePersistence` | 删除，由 `Session` 接管持久化 |
| `ReActAgent.Builder.memory(Memory)` | `.session(Session).sessionKey(SessionKey)` |
| `SessionManager` | 删除，直接配置 `Session` + `SessionKey` |

### 推荐迁移（`@Deprecated(forRemoval=true)`，仍可调用）

| 1.x | 2.x 替代 |
|-----|---------|
| `Hook` / `HookEvent` 全包 | `MiddlewareBase`（5 个 stage：onAgent / onReasoning / onActing / onModelCall / onSystemPrompt） |
| `Memory` / `InMemoryMemory` | `Session` + `AgentState.getContext()` |
| `SkillBox` | `AgentSkillRepository` |
| `stream()` | `streamEvents()` → 返回 `Flux<AgentEvent>`，28 个类型化事件 |
| RAG 模块（`Knowledge` 等） | v2 重写中，暂不依赖新 API |
| `ShellCommandTool` / `ReadFileTool` 等 core 内置工具 | 迁到 Harness workspace，享受权限隔离和 HITL |

### 对 AAF 当前代码的影响

AAF 中需要关注的使用点：

- `InMemoryMemory` → 标 deprecated，v0.2 迁到 `Session`
- `TokenMeteringHook` / `AafTraceHook` / `AafToolPermissionHook` 等 Hook → 迁到 `Middleware`
- `AgentScopeExampleConfig` 中的 `.memory(new InMemoryMemory())` → 全部需要替换

### 2.x 新增能力（对 AAF 有价值）

- **权限系统**：`PermissionEngine` + `PermissionMode`，工具调用前自动过权限（允许/审批/拒绝），对应 AAF 的工具治理需求
- **模型容错**：`.maxRetries(int)` + `.fallbackModel(Model)`，主模型失败自动切备用
- **`HarnessAgent.Builder.fromAgent(ReActAgent)`**：从已有 `ReActAgent` 平滑迁移到 `HarnessAgent` 的辅助方法，v0.2 升级路径的关键入口



---

## 单实例 vs 多实例架构分析（待决策）

> 记录于 2026-06-08，来源：对 AgentScope 2.x HarnessAgent 体系的分析。

### 当前架构（多实例）

`AssistantRuntime.materialize(assistantId)` 按 assistantId 动态创建 Agent 实例，每个 Assistant 配置对应一个 Agent 对象。

### 目标架构（单实例）

```
1个 HarnessAgent 对象（Toolkit/Model/Middleware 共享）
  ├── 会话A (threadId=001, assistantId=客服)  → SessionKey="001" → Redis AgentState
  ├── 会话B (threadId=002, assistantId=开发)  → SessionKey="002" → Redis AgentState
  └── 会话C (threadId=003, assistantId=客服)  → SessionKey="003" → Redis AgentState
```

多对话不是多实例，而是多个 `SessionKey`。Agent 是无状态执行引擎，状态全部外置到 `Session` 存储。

### 个性化如何实现

| 个性化需求 | 实现方式 |
|-----------|---------|
| 不同系统提示词 | `onSystemPrompt` Middleware 按 assistantId 动态注入 |
| 不同工具集（系统内置） | `ToolGroup` 激活/禁用，按 assistantId 配置 group 列表 |
| 用户自定义工具 | MCP 协议接入，`McpMeta` 动态切换 MCP Server |
| 通用技能执行 | 单个 `execute_skill` 工具 + Skill 数据库，参考 HarnessAgent Skill 体系 |
| 用户上下文（userId/知识库等） | `RuntimeContext` 注入，Middleware 和 Tool 均可读取 |

### 工具体系分层

```
系统内置工具（有限枚举）→ ToolGroup 开关
用户自定义技能          → execute_skill 工具 + Skill 定义数据库
用户自定义外部工具      → MCP Server 接入
```

### HarnessAgent 内部子 Agent 孵化

HarnessAgent 通过 `DynamicSubagentsMiddleware` 支持运行时自主创建子 Agent：

```
主 HarnessAgent
  └── 遇到复杂任务时动态孵化子 Agent
        ├── 子Agent（搜索）
        ├── 子Agent（代码执行）
        └── 子Agent（报告生成）→ 可继续孵化下一层
```

子 Agent 规格由主 Agent 运行时生成并写入 Workspace Filesystem 持久化。这是 AAF "自主规划解决复杂问题 + 自进化"目标的核心实现路径——主 Agent 简单问题直接回答，复杂问题自动分解派给子 Agent，无需开发者预定义所有 Agent 类型。

### 迁移前提条件

- AgentScope 升级到 2.x（`Session` 体系、Middleware、HarnessAgent）
- 工具体系按上表分层改造
- `AafAgentResolver` 简化：去掉 `materialize()`，改为查 `ChatSession` 后将 assistantId/userId 注入 `RuntimeContext`
- `AgentRunContextHolder` 逻辑移入 `onAgent` Middleware



---

## 工作流编排与 HarnessAgent 整合（架构方向）

> 记录于 2026-06-08。

### 两种编排模式的区别

| 模式 | 谁决定子 Agent | 特点 |
|------|--------------|------|
| HarnessAgent 自主编排 | LLM 自主决定是否 fork、怎么分工 | 灵活自适应，但不可控 |
| AAF 工作流编排 | 开发者/用户预定义 DSL | 可控、可复现、可审计 |

HarnessAgent 不直接支持可控编排，两者需要叠加使用。

### 整合架构

```
AAF 工作流层（可控编排）
  └── 工作流定义：后端节点 → 前端节点 → 测试节点（顺序/并行/条件由 DSL 定义）
        └── 每个节点 = AgentNode
              └── AgentNode 内部 = HarnessAgent（可自主规划、自主 fork 子 Agent）
```

执行路径：

```
用户触发
  → AAF 工作流引擎：按预定义启动流程
    → AgentNode1：后端 HarnessAgent（内部可自主拆分子任务）
    → AgentNode2：前端 HarnessAgent（并行执行）
    → AgentNode3：测试 HarnessAgent（等前两个完成后启动）
  → 工作流汇总结果 → 返回用户
```

### 两层职责分工

| 层 | 负责什么 | 谁控制 |
|----|---------|-------|
| 工作流层（AAF Flowable/DSL） | 哪些 Agent、什么顺序、什么条件分支 | 开发者/用户预定义 |
| HarnessAgent 层 | 单个 Agent 怎么完成任务、要不要自主拆分子任务 | LLM 自主决定 |

### 整合关键技术点

- **适配层**：Flowable ServiceTask 是同步/回调模型，HarnessAgent 是响应式（Reactor `Flux<AgentEvent>`），需要桥接适配器
- **AgentNode**：AAF 工作流的 AI 节点类型，内部持有 HarnessAgent 引用，把节点执行委托给 Agent
- **状态传递**：工作流上下文（流程变量）与 Agent `RuntimeContext` 之间的数据映射
- **流式输出**：工作流执行过程中 AgentEvent 需要透传给前端 MessageStream

### 与 AAF 五层架构的对应

```
Layer 3 智能层（Team/Assistant/Agent）
  └── Assistant = 主 HarnessAgent（协调者，每对话一个 SessionKey）
        └── Agent = 子 HarnessAgent（DynamicSubagentsMiddleware fork）

Layer 2 引擎层（工作流引擎）
  └── AgentNode = 工作流节点包装 HarnessAgent
        └── 可控编排 + Agent 自主执行 两层叠加
```

这是 AAF v0.2 智能层重构的核心架构方向。
