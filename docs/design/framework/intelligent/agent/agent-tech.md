---
level: Practice
layer: Model
purpose: Layer 2 智能体层 Agent——无状态任务执行、池化、运行时架构
status: draft
version: 1.0.0
date: 2026-05-28
author: AaronZZH
---

# Layer 2 智能体层 Agent 技术方案

> 任务级·无状态，感知-规划-执行-评估的认知循环。AgentScope ReActAgent 为执行骨架。

## 认知循环

```text
感知 → 规划 → 执行 → 评估 → 学习 ↔ 记忆
```

| 模块 | 职责 | 类比 |
|------|------|------|
| 感知模块 | 输入解析、意图识别 | 感觉皮层 |
| 规划模块 | 目标分解、任务排序、可验证性降维 | 前额叶 |
| 执行模块 | 工具调用、代码生成 | 小脑 |
| 评估模块 | 结果验证、置信度评估 | 评估阶段 |
| 价值模块 | 优先级判断、伦理约束 | 杏仁核 |

## 状态策略

- **完全无状态**：执行前从 Cognition 拉取记忆/知识，执行后写回
- **多实例并发**：同一 Agent 定义可并发处理多个任务实例
- **失败可重试**：无状态设计使得失败后可直接重新调度
- **Checkpoint**：步骤级检查点，失败从最近检查点恢复

## AgentPool 池化

```text
AgentPool（全局共享）
  ├── idle: [Agent-1, Agent-2, Agent-3, ...]    预创建实例
  ├── busy: [Agent-4(task-x), Agent-5(task-y)]  执行中实例
  └── 策略：预热 N 个，动态扩缩，最大 M 个，超时回收

生命周期：
  borrow() → 重置框架内部状态 → 注入任务上下文 → 执行 → release() → 清空
```

Agent 由所属的 Assistant 实例调度，不跨实例共享正在执行的 Agent。

## 何时启用 Agent

| 维度 | 判断 | 结论 |
|------|------|------|
| 任务复杂度 | 低 | 用 Workflow |
| 任务价值 | < $0.1 | 用 Workflow |
| 所有步骤可执行 | 否 | 缩小范围或加人工 |
| 错误成本 | 高 | 加人工审核节点 |

**最适合场景**：编码 Agent（从需求文档到完整 PR），复杂度高、价值高、错误可控。

核心心法：Workflow 适合可预测任务，Agent 适合动态场景。

## 单 Agent 最小闭环

```python
env = Environment()
tools = Tools(env)
system_prompt = "Goals, constraints, and how to act"

while True:
    action = llm.run(system_prompt + env.state)
    env.state = tools.run(action)
```

核心心法：**站在 Agent 视角思考，它只能看到你给的上下文**。

## AgentScope 适配

```text
AgentScopeExecutor 薄门面适配 AgentExecutor 接口
  → 委托 AgentScope ReActAgent 执行 ReAct 循环
  → AgentFactory 从 ai_model 表读配置构建 ReActAgent
  → AgentScope STATIC_CONTROL 模式自动触发记忆管道
```

## 运行时基础设施

```text
intelligent/agent/runtime/
  ├── AgentPool          池化复用（借出重置/归还清空）
  ├── AgentSandbox       虚拟线程隔离（依赖 AgentExecutor）
  ├── AgentEventBus      消息路由
  └── AgentCheckpointService 调用 engine/runtime/checkpoint
```

## 运行时架构

### 缓存层

配置数据（Actor、Role、SkillDef、AgentDef、AiModel、PromptTemplate）启动时加载，运行时缓存，DB 变更时事件刷新。

```text
┌─────────────────────────────────────────────────────────────────┐
│  缓存层（应用级）                                                │
│  ActorCache / RoleCache / SkillDefCache / AgentDefCache          │
│  ModelCache / PromptCache                                        │
│  策略：本地 Caffeine + Redis 二级缓存                            │
│  刷新：DB 变更 → Spring Event → 本地失效 → 懒加载重填            │
└─────────────────────────────────────────────────────────────────┘
```

### 各层任务管理

| 层 | 任务管理组件 | 粒度 | 生命周期 | 持久化 |
|----|-------------|------|----------|--------|
| Team | GoalTracker | 目标级 | 项目级 | DB |
| 主 Assistant | TaskBoard | 子任务级 | 会话级 | 内存 + 可选持久化 |
| 子 Assistant 实例 | SubTaskContext | 当前任务 | fork→完成→销毁 | 无 |
| Agent | WorkingMemory（PlanNotebook） | 步骤级 | 执行期 | Checkpoint |

跨层不共享任务状态——上层只知道"派发了什么、结果是什么"，不感知下层内部步骤。

### Checkpoint 分层设计

```text
engine/runtime/checkpoint/
  ├── CheckpointStore（接口）       持久化存储
  ├── CheckpointEntry              快照数据结构（scopeType/scopeId/snapshot）
  └── CheckpointPolicy             策略（每步/每N步/关键节点）
```

| 层 | 是否需要 | 保存内容 | 恢复场景 |
|----|---------|---------|----------|
| Core | ❌ | 无状态 | 失败直接重试 |
| Agent | ✅ | 步骤进度 + 中间结果 + 工作记忆 | 步骤失败从最近检查点重试 |
| Assistant 子实例 | ✅ | 任务上下文 + 关联 Agent 检查点 ID | 子实例崩溃后重新 fork |
| Assistant 主实例 | ✅ | TaskBoard + 会话上下文 + InputBuffer | 服务重启后恢复长任务 |
| Team | ✅（v0.6+） | GoalTracker + 分配表 | 项目级任务恢复 |

恢复流程：
```text
服务重启 / 主实例崩溃：
  1. SessionManager 扫描未完成的 AssistantCheckpoint
  2. 恢复主实例：加载 TaskBoard + 上下文
  3. 检查子任务状态：DONE→跳过 / RUNNING→恢复或重fork / PENDING→等待
  4. 通知用户："之前的任务已恢复，继续执行中..."
```

## 沙箱执行

```text
core/sandbox/
├── SandboxExecutor.java            核心接口
├── SandboxResult.java              执行结果
├── SandboxConfig.java              沙箱配置（超时/网络/IO 约束）
└── impl/
    ├── GraalVmSandboxExecutor.java 首选（GraalVM Polyglot）
    └── SubprocessSandboxExecutor.java 降级（子进程隔离）
```

---

## AgentScope 接口映射

### 核心类映射

| AAF 组件 | AgentScope 类 | 说明 |
|----------|--------------|------|
| `AgentExecutor`（AAF 接口） | `io.agentscope.core.ReActAgent` | ReAct 循环执行骨架 |
| `AgentFactory` | `ReActAgent.Builder` | 从 AAF 配置构建 Agent 实例 |
| 工具注册 | `io.agentscope.core.tool.Toolkit` | 工具注册/分组/执行 |
| 工具执行 | `io.agentscope.core.tool.ToolExecutor` | 并行/串行工具执行 |
| MCP 工具 | `io.agentscope.core.tool.McpClientManager` | MCP 协议工具接入 |
| 规划模块 | `io.agentscope.core.plan.PlanNotebook` | 任务分解/跟踪/Hint 注入 |
| 技能匹配 | `io.agentscope.core.skill.SkillBox` | 技能注册/按需加载 |
| 状态持久化 | `io.agentscope.core.state.StatePersistence` | 选择性组件状态管理 |
| Checkpoint | `io.agentscope.core.session.Session` + `StateModule` | 步骤级状态保存/恢复 |
| Hook 系统 | `io.agentscope.core.hook.Hook` | 统一事件拦截（Mono 响应式） |

### Hook 事件生命周期

```text
PRE_CALL → [PRE_REASONING → REASONING_CHUNK* → POST_REASONING
            → PRE_ACTING → ACTING_CHUNK* → POST_ACTING]* → POST_CALL
                                                              ↓
                                                          ERROR（任意阶段异常）
```

| 事件 | 可修改 | AAF 注入点 |
|------|--------|-----------|
| `PreCallEvent` | ✅ inputMessages | 注入记忆/知识库上下文 |
| `PreReasoningEvent` | ✅ inputMessages, generateOptions | 注入 PlanNotebook Hint |
| `PostReasoningEvent` | ✅ reasoningMessage | 置信度评估、输出溯源 |
| `PreActingEvent` | ✅ toolUse | 工具白名单校验、权限检查 |
| `PostActingEvent` | ✅ toolResult, toolResultMsg | Token 计量、结果缓存 |
| `PostCallEvent` | ✅ response | 记忆写入、审计日志 |
| `ErrorEvent` | ❌ | 异常告警、降级处理 |

## 适配器实现

### AgentRuntime 接口（框架无关抽象）

```java
package com.xuejiai.aaf.framework.intelligent.agent;

/**
 * Agent 运行时接口——屏蔽底层 Agent 框架实现细节。
 * AgentFactory 只依赖此接口，切换底层框架只需替换实现类。
 */
public interface AgentRuntime {
    AgentExecutor create(AgentDefinition definition, List<String> tools);
}
```

### AgentScopeRuntime（AgentScope 实现）

```java
package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

/**
 * AgentScope 运行时——所有 AgentScope 依赖收敛于此。
 * 将 AAF AgentDefinition 转为 AgentScope ReActAgent。
 */
@Component
@RequiredArgsConstructor
public class AgentScopeRuntime implements AgentRuntime {

    private final TokenMeteringHook tokenMeteringHook;
    private final McpToolService mcpToolService;
    private final AiModelRepository modelRepository;
    private final ModelManagementService modelManagementService;

    @Override
    public AgentExecutor create(AgentDefinition definition, List<String> tools) {
        var builder = ReActAgent.builder()
                .name(definition.getName())
                .sysPrompt(definition.getSystemPrompt())
                .hook(tokenMeteringHook);

        configureModel(builder, definition);
        builder.toolkit(mcpToolService.buildToolkit(definition));

        return new AgentScopeAgentAdapter(builder.build());
    }
}
```

### AgentScopeAgentAdapter（AgentExecutor 适配）

```java
package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

/**
 * AgentScope ReActAgent → AAF AgentExecutor 适配器。
 * 上层只依赖 AgentExecutor 接口，本类将调用委托给 ReActAgent。
 */
public class AgentScopeAgentAdapter implements AgentExecutor {

    private final ReActAgent delegate;

    @Override
    public AgentResult execute(String input) {
        var msg = Msg.builder().name("user").textContent(input).build();
        var response = delegate.call(msg).block();
        return AgentResult.success(response.getTextContent());
    }

    @Override
    public void interrupt() { delegate.interrupt(); }

    @Override
    public String getName() { return delegate.getName(); }

    @Override
    public void reset() { /* AgentPool 重建实例实现 */ }
}
```

### AgentFactory（从 AAF 配置构建）

```java
package com.xuejiai.aaf.framework.intelligent.agent;

/**
 * Agent 工厂——通过 AgentRuntime 接口创建 Agent 实例。
 * 不直接依赖任何底层 Agent 框架，具体实现由 AgentRuntime 决定。
 */
@Component
@RequiredArgsConstructor
public class AgentFactory {

    private final AgentRuntime runtime;
    private final ToolPermissionGuard toolPermissionGuard;

    public AgentExecutor create(AgentDefinition definition) {
        var tools = definition.getTools() != null
                ? List.of(definition.getTools().replaceAll("[\\[\\]\"]", "").split(","))
                : List.<String>of();
        return runtime.create(definition, tools);
    }
}
```

## 关键 Hook 注入点

| AAF 能力 | Hook 类 | 拦截事件 | 优先级 | 逻辑 |
|----------|---------|---------|--------|------|
| Token 计量 | `AafTokenUsageHook` | `PostReasoningEvent` | 900 | 从 ChatUsage 提取 token 数，发布 `TokenUsageEvent` |
| 工具白名单 | `AafToolWhitelistHook` | `PreActingEvent` | 10 | 校验 toolUse.name 是否在白名单，不在则替换为错误结果 |
| 输出溯源 | `AafTraceHook` | `PostCallEvent` | 800 | 在 response metadata 注入 traceId + agentId + modelId |
| 置信度评估 | `AafConfidenceHook` | `PostReasoningEvent` | 200 | 分析推理结果，低置信度时设置 stopAgent |
| 记忆注入 | AgentScope 内置 `StaticLongTermMemoryHook` | `PreCallEvent` / `PostCallEvent` | 50 | 自动 retrieve + record |
| RAG 注入 | AgentScope 内置 `GenericRAGHook` | `PreCallEvent` | 50 | 自动检索知识库注入上下文 |
| 技能加载 | AgentScope 内置 `SkillHook` | `PreReasoningEvent` | 100 | 按需加载技能到上下文 |

## 配置与初始化

### 从 ai_model 表构建 AgentScope Model

```java
// ModelRouter 内部方法
public Model resolveAgentScopeModel(ModelPreference preference) {
    // 六层决策链选出最优模型配置
    AiModelEntity config = decisionChain.resolve(preference);

    return switch (config.getEndpointType()) {
        case OPENAI_COMPATIBLE -> OpenAIChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .build();
        case DASHSCOPE -> DashScopeChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .build();
        case OLLAMA -> OllamaChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .build();
    };
}
```

### Session 配置（Checkpoint 持久化）

```java
// 开发环境：JsonSession（文件系统）
Session devSession = new JsonSession(Path.of("data/sessions"));

// 生产环境：RedisSession（agentscope-extensions-session-redis）
Session prodSession = new RedisSession(redisConnectionFactory);

// SessionManager 使用示例
SessionManager.forSessionId(conversationId)
    .withSession(session)
    .addComponent(agent)    // ReActAgent 实现 StateModule
    .loadIfExists();        // 恢复 Memory + Toolkit + PlanNotebook
```

### StatePersistence 配置

```java
// AAF 默认：管理 Memory 和 PlanNotebook，Toolkit 由 AAF 工具引擎管理
var persistence = StatePersistence.builder()
    .memoryManaged(true)
    .planNotebookManaged(true)
    .toolkitManaged(false)       // AAF 工具引擎独立管理
    .statefulToolsManaged(false) // AAF 不使用有状态工具
    .build();
```

## 相关文档

- [五层智能架构总览](../architecture.md)
- [执行轨迹](execution-trace.md)
- [工具权限](../../engine/execution/tool-permission.md)
- [AgentScope 整合策略](../agentscope-integration.md)
