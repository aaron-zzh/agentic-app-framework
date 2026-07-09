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



---

## AAF 领域概念 vs AgentScope 核心概念对照

> 记录于 2026-06-10。两侧概念独立演进，不强求一一映射。

### AAF 五层智能架构核心概念

| AAF 概念 | 所在层 | 含义 | DB 表 |
|---------|--------|------|-------|
| **Core** | Layer 0 | LLM 推理单元，无状态 | ai_model |
| **Cognition** | Layer 1 | 跨会话持久认知底座：记忆 + 知识库 + 价值观 + 决策日志 | ai_memory_atom / ai_knowledge_* |
| **Agent** | Layer 2 | 任务执行单元，AAF 语义无状态（配置模板） | ai_agent_definition |
| **Assistant** | Layer 3 | 用户面向的对话主体，有人格/角色/记忆策略 | ai_assistant |
| **Team** | Layer 4 | 多 Assistant 协作完成复杂目标 | ai_team / ai_team_task |
| **Persona** | Layer 3 | 人格模板，可复用 | ai_persona |
| **Role** | Layer 3 | 能力配置：技能集 + 工具白名单 | ai_role |
| **Skill** | Layer 3 | 意图路由规则，匹配后路由到对应 Agent | ai_skill_definition |
| **Tool** | Layer 2 | Agent 可调用的原子能力 | ai_tool_catalog |
| **Conversation** | Layer 3 | 统一会话（AI/客服/IM） | conversation |
| **ChatTask** | Layer 3 | 用户提交的任务，助理按优先级执行 | ai_chat_task |
| **TaskExecution** | Layer 3 | 任务执行实例，支持主/子执行、重试 | ai_task_execution |
| **MemoryAtom** | Layer 1 | 记忆原子，含向量 + 双时态 + 图关系 | ai_memory_atom |

### AgentScope 2.x 核心概念

| AgentScope 概念 | 含义 | 实例粒度 | 有状态 |
|----------------|------|---------|--------|
| **ReActAgent** | 核心推理执行单元：感知→规划→执行→评估循环 | 共享或 per-user，`AgentState` final 绑定在实例上，同一实例不可并发调用 | ✅ AgentState |
| **HarnessAgent** | ReActAgent + Workspace + Subagents + Skills + PlanMode 完整封装 | 同 ReActAgent | ✅ |
| **AgentState** | Agent 运行时状态：对话历史（context）、规划进度、interrupt flag；build 时 final 绑定，不可 per-call 切换 | per-实例，唯一 | ✅ |
| **Session** | 状态持久化存储后端（InMemory/Json/Redis/MySQL）；**≠ 业务"会话"**，是 Repository | 应用级单例 | ❌ |
| **SessionKey** | AgentState 在 Session 中的存储键，通常为 userId 或 (userId:agentId) | per-请求传入 | ❌ |
| **Workspace** | Agent 文件系统上下文（AGENTS.md/MEMORY.md/KNOWLEDGE.md/subagents/），通过 NamespaceFactory 按 userId 路径隔离 | per-userId 路径 | ✅（文件） |
| **Middleware** | 2.x 替代 Hook，5 个 stage 拦截点（onAgent/onReasoning/onActing/onSystemPrompt/onModelCall） | per-agent 注册 | ❌ |
| **WorkspaceContextMiddleware** | 每次推理前按 userId 读取 workspace/AGENTS.md + MEMORY.md 注入 SYSTEM | — | ❌ |
| **MemoryFlushMiddleware** | 对话结束后将重要内容写回 workspace/MEMORY.md（per-userId 路径） | — | ❌ |
| **SubagentDeclaration** | 子 Agent 规格：name/description/systemPrompt/tools/model，LLM 据 description 决定何时委派 | 构建时注册 | ❌ |
| **DynamicSubagentsMiddleware** | 每次推理前从 workspace/subagents/*.md 动态重载子 Agent 声明 | — | ❌ |
| **SessionAgentManager** | 子 Agent 生命周期管理：agentCache（per-sessionKey 独立实例）+ 并发控制 | 应用级单例 | ✅（cache） |
| **HarnessGateway** | 请求路由 + per-gateKey 串行锁（SessionTurnGate），将请求分发到 HarnessAgent | 应用级单例 | ❌ |
| **SessionTurnGate** | per-gateKey 公平锁，保证同一 gateKey 请求串行，不同 gateKey 可并发 | — | ❌ |
| **PlanNotebook** | Agent 内置多步任务规划，存于 AgentState | per-实例 | ✅ |
| **TaskTool / TaskOutputTool** | 主 Agent 委派子 Agent 的工具：同步 + 异步后台 + 结果轮询 | — | ❌ |

### 关键差异与易混淆点

| 概念 | AAF | AgentScope | 差异 |
|------|-----|-----------|------|
| **Skill** | 意图路由规则（匹配后路由到某 Agent） | 技能文件（工具集 + 提示词，文件系统来源） | 命名相同，语义完全不同 |
| **Agent** | 领域语义无状态（配置模板） | 运行时有状态实例（AgentState final 绑定） | AAF"无状态"是业务语义，AS 实例是有状态的 |
| **Memory** | 跨会话持久记忆（ai_memory_atom，Cognition 层） | 当前对话上下文（AgentState.context） | 层次完全不同 |
| **Session** | 业务会话（conversation 表） | 状态存储后端（Redis/JSON） | 完全不同概念，仅名字相似 |
| **Task** | 用户视角后台任务（ai_chat_task） | Agent 内部子任务（TaskTool 异步执行单元） | 层次不同 |
| **多 Agent 协作** | Team 层外部编排 | LLM 自主 spawn 子 Agent | AAF 偏显式控制，AS 偏自主决策 |

### 实例模型：主 Agent 与子 Agent 的区别

这是最易混淆的地方，需要明确区分：

**主 Agent（MAIN session，HarnessGateway 路由）**：
```
启动时 build 1-N 个共享 HarnessAgent 实例（按 agentId 注册到 gateway.agentRegistry）
  ↓
用户A发消息 → gateKey="userA" → SessionTurnGate.acquire("userA")
              → 从 agentRegistry 取共享实例
              → runtimeContext = {userId="userA", sessionId="sk-A"}
              → 共享实例.call(msgs, runtimeContext)
                  WorkspaceContextMiddleware 按 userId 读 workspace/userA/MEMORY.md → 注入 SYSTEM
                  AgentState.context 写入对话历史（共享 context！）
                  MemoryFlushMiddleware 按 userId 写回 workspace/userA/MEMORY.md
              → SessionTurnGate.release("userA")

用户B发消息 → gateKey="userB" → 与 userA 并发执行（不同锁）
              → 同一个共享实例，但 workspace 路径是 workspace/userB/MEMORY.md
```

**关键**：主 Agent 的 `AgentState.context` 在多用户间**串行共享**（同一时刻只有一个用户在写），per-user 隔离靠 **Workspace 文件系统**（MEMORY.md 路径按 userId 隔离），不靠 AgentState。

**子 Agent（SUBAGENT session，SessionAgentManager 管理）**：
```
主 Agent LLM 决定委派 → 调用 TaskTool("researcher", "帮我查...")
  → SessionAgentManager.execute(sessionKey="userA:researcher")
  → agentCache.computeIfAbsent(sessionKey, k -> SubagentFactory.create(parentRc))
      → 创建独立 HarnessAgent 实例（有自己的 AgentState）
      → 执行完毕，实例留在 cache 或销毁
```

子 Agent 才是 per-(用户+任务) 独立实例，状态隔离。主 Agent 是共享实例靠文件系统隔离。

---

## HarnessAgent 运行时逻辑详解

> 记录于 2026-06-10。来源：官方示例（claw/builder/codingagent）源码分析。

### 整体架构

```
应用启动
  └── 1-N 个 HarnessAgent（共享实例，per-agentId）
        └── delegate: ReActAgent
              ├── AgentState（final，build 时绑定，当前轮次 working context）
              ├── Session（Redis，存储后端）
              └── Middleware 链

HarnessGateway（路由层）
  ├── agentRegistry: Map<agentId, HarnessAgent>（共享实例）
  ├── contextKeyToSessionKey: Map<gateKey, sessionKey>
  └── SessionTurnGate（per-gateKey 串行锁）

SessionAgentManager（子 Agent 管理）
  ├── agentCache: Map<sessionKey, Agent>（per-sessionKey 独立实例）
  └── 并发控制（Semaphore）
```

### 单次请求完整流程

```
用户A: "帮我分析这个数据"
  │
  ▼
HarnessGateway.run(MsgContext{userId="userA", gateKey="userA"}, msgs)
  │
  ├─ resolveOrCreateMainSession("userA") → sessionKey="sk-userA-001"
  ├─ runtimeContext = {userId="userA", sessionId="sk-userA-001"}
  ├─ SessionTurnGate.acquire("userA")   ← 同一用户串行
  │
  ▼
mainAgent.call(msgs, runtimeContext)
  │
  ├─ Middleware.onAgent():
  │   WorkspaceContextMiddleware
  │     → workspaceManager.readMemoryMd(rc)  ← 读 workspace/userA/MEMORY.md
  │     → 注入到 SYSTEM message
  │   DynamicSubagentsMiddleware
  │     → 扫描 workspace/userA/subagents/*.md → 更新可用子 Agent 列表
  │
  ├─ Middleware.onSystemPrompt():
  │   HarnessSkillMiddleware → 注入激活的技能提示词
  │
  ├─ [ReAct 循环 maxIters 次]
  │   ├─ LLM 推理 → 决定调用子 Agent "data-analyzer"
  │   ├─ TaskTool("data-analyzer", "分析用户上传的CSV")
  │   │     → SessionAgentManager.execute(sessionKey="userA:data-analyzer")
  │   │     → agentCache 里没有 → SubagentFactory.create() → 新实例
  │   │     → 子实例独立执行，有自己的 AgentState
  │   │     → 返回结果
  │   └─ 主 Agent 汇总结果继续推理
  │
  ├─ Middleware.onAgent() 结束:
  │   MemoryFlushMiddleware
  │     → 将重要内容写入 workspace/userA/MEMORY.md  ← per-userId 隔离
  │
  └─ 生成最终回复
  │
  ▼
SessionTurnGate.release("userA")
```

### Middleware 注册顺序

| 顺序 | Middleware | 触发时机 | 作用 |
|------|-----------|---------|------|
| 1 | AgentTraceMiddleware | onAgent | 执行追踪日志 |
| 2 | WorkspaceContextMiddleware | onAgent + onSystemPrompt | 按 userId 读 AGENTS.md/MEMORY.md/KNOWLEDGE.md 注入 SYSTEM |
| 3 | AtPathExpansionMiddleware | onReasoning | 展开 `@path` 引用 |
| 4 | MemoryFlushMiddleware | onAgent（结束） | 将对话重要内容 flush 到 workspace/[userId]/MEMORY.md |
| 5 | MemoryMaintenanceMiddleware | onAgent | 定期整理 MEMORY.md |
| 6 | CompactionMiddleware | onReasoning | Token 超限时 LLM 摘要压缩 AgentState.context |
| 7 | ToolResultEvictionMiddleware | onActing | 大工具结果替换为摘要 |
| 8 | DynamicSubagentsMiddleware | onAgent + onReasoning | 按 userId 重载 subagents/*.md，注入可用子 Agent 说明 |
| 9 | HarnessSkillMiddleware | onSystemPrompt | 注入激活的 Skill 文件内容 |
| 10 | PlanModeMiddleware | onActing | Plan 模式下过滤非只读工具 |

### 多用户并发模型

```
用户A请求 → gateKey="userA" → 加锁A ──────────────────→ 释放A
用户B请求 → gateKey="userB" → 加锁B ──────────────────→ 释放B
                               ↑两个锁不同，可以同时进行

同一用户A的第2个请求 → 等待第1个完成后才能执行
```

- 不同用户：不同 gateKey，不同锁，**可以并发**
- 同一用户：相同 gateKey，相同锁，**必须串行**
- 主 Agent 实例：**共享**，不是 per-user
- per-user 状态隔离：**Workspace 文件路径**（NamespaceFactory 按 userId 路由）

### Workspace 文件系统结构

```
<workspace>/
  [userId/]                     ← NamespaceFactory 按 userId 隔离
    AGENTS.md                   ← 用户的自定义系统提示（WorkspaceContextMiddleware 读取）
    MEMORY.md                   ← 用户长期记忆摘要（MemoryFlushMiddleware 写入/读取）
    KNOWLEDGE.md                ← 知识库索引
    subagents/                  ← 动态子 Agent 声明（DynamicSubagentsMiddleware 扫描）
      researcher.md
      coder.md
    skills/                     ← 技能文件
      _drafts/                  ← 待审批草稿
    tools.json                  ← MCP 服务器配置
    plans/                      ← Plan 模式设计文档
```

---

## HarnessAgent 与 AAF 领域模型的映射

### 映射总表

| AAF 概念 | AgentScope 承接方式 | 隔离机制 | 说明 |
|---------|-------------------|---------|------|
| ai_agent_definition | **共享 HarnessAgent 实例**（per-agentId） | — | 1个定义对应1个共享实例 |
| ai_assistant | onSystemPrompt Middleware 按 userId 动态注入 | per-userId workspace | 不对应实例，对应运行时注入参数 |
| ai_persona | workspace/[userId]/AGENTS.md | per-userId 文件路径 | 初始化时写入，用户可自定义修改 |
| ai_role（工具白名单） | ToolGroup 激活 + AafToolWhitelistHook | — | build 时配置或 Middleware 注入 |
| ai_skill_definition | workspace/[userId]/subagents/*.md | per-userId 文件路径 | Session 建立时写入，DynamicSubagentsMW 动态加载 |
| ai_memory_atom | AafLongTermMemory（retrieve/record） | per-userId 查询条件 | 每轮推理前注入，回复后异步写回 |
| workspace/MEMORY.md | MemoryFlushMiddleware | per-userId 文件路径 | 近期上下文摘要，与 ai_memory_atom 互补 |
| ai_knowledge_* | AafKnowledge + KNOWLEDGE.md | per-knowledgeBase 查询 | HybridSearchService 对接 |
| conversation | gateKey + sessionKey | per-gateKey 串行锁 | thread_id 作为 gateKey |
| ai_chat_task | 一次 call()/streamEvents() | — | — |
| ai_task_execution（子任务） | SessionAgentManager agentCache 里的子 Agent 实例 | per-sessionKey 独立实例 | 子 Agent 才是 per-task 独立 |
| ai_task_checkpoint | workspace/[userId]/agents/[agentId]/context/[sessionId]/ | per-userId 文件路径 | JsonSession 持久化 AgentState |
| Team 协作 | DynamicSubagentsMiddleware 自主 spawn | — | LLM 自主决策，可叠加工作流外部编排 |

### 待决策问题

1. **AAF 不使用 Workspace 文件系统做状态隔离**（AAF 用 DB），需要用 `AafLongTermMemory` + `MemoryContextHook` 替代 `WorkspaceContextMiddleware` + `MemoryFlushMiddleware`。两者逻辑等价，存储介质不同。

2. **ai_assistant 的个性化注入粒度**：同一用户的不同 ai_assistant（不同人格/角色），在共享实例模式下靠 onSystemPrompt Middleware 按 (userId + assistantId) 动态注入。如果差异很大（工具集完全不同），则需要 per-assistantId 独立实例。

3. **子 Agent 并发上限**：SessionAgentManager 通过 Semaphore 控制，需要结合 AAF 的积分/权益系统设置合理上限。
