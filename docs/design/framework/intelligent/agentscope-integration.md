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

## 相关文档

- [五层智能架构](architecture.md)
- [Agent 技术方案](agent/agent-tech.md)
- [记忆管道](cognition/memory-pipeline.md)
- [ADR-005 AgentScope 整合策略](../../adr/ADR-005-agentscope-integration-strategy.md)
