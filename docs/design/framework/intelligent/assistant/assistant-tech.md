---
level: Practice
layer: Model
purpose: Layer 3 助理层 Assistant——会话管理、多实例并行、输入缓冲、情感感知
status: draft
version: 1.0.0
date: 2026-05-28
author: AaronZZH
---

# Layer 3 助理层 Assistant 技术方案

> 会话级，面向人的交互入口，核心编排单元。

## 认知循环

```text
情感感知 → 意图理解 → 上下文构建 → Agent 调度 → 反馈整合 → 记忆更新
```

## 组成结构

```text
Assistant = Actor + Role + MemoryStrategy

Actor（人格载体）：name / persona / systemPrompt / avatar
  - 纯配置，无运行时状态，从缓存引用
  - 可复用·跨 Role

Role（能力配置）：Skill 集 + Tool 白名单
  - 纯配置，无运行时状态，从缓存引用
  - 可复用·跨 Actor

MemoryStrategy：决定从哪些源拉取上下文
  - MEMORY_ONLY / KNOWLEDGE_ONLY / HYBRID / PROCEDURAL_FIRST / FULL
```

## 多实例并行（fork 模式）

不需要池化（实例轻量，按需创建/销毁）。

```text
主 AssistantInstance（协调者，长驻，绑定用户会话）
  │
  ├── fork(Role-A, contextSnapshot) → 子 Instance-1（临时）
  │     └── AgentPool.borrow() → Agent 执行 → AgentPool.release()
  │
  ├── fork(Role-B, contextSnapshot) → 子 Instance-2（临时）
  │     └── AgentPool.borrow() → Agent 执行 → AgentPool.release()
  │
  ├── await all → 聚合 → 冲突检测
  └── 子实例销毁（GC 回收）
```

**为什么不池化**：实例 = Actor 引用 + Role 引用 + 会话上下文，创建成本极低；每个子实例上下文独立不可互换。

**子实例上下文策略**：fork 时拷贝主实例上下文只读快照，各子实例独立写入，主实例 merge 结果。

## 多会话支持

```text
用户
  ├── 对话 A → 主 Instance-A（协调者）
  │     ├── fork → 子 Instance-A1 (Role: 后端) → AgentPool
  │     └── fork → 子 Instance-A2 (Role: 前端) → AgentPool
  ├── 对话 B → 主 Instance-B
  └── 对话 C → 主 Instance-C（直接回复，无子实例）

数量关系：
  用户 : 对话 = 1 : N
  对话 : 主实例 = 1 : 1
  主实例 : 子实例 = 1 : 0..N
  AgentPool = 全局 1 个（所有实例共享）
```

## 输入缓冲区（InputBuffer）

Agent 执行期间，用户可继续输入。InputBuffer 在 Assistant 层接收并分类处理。

| 输入类型 | 处理方式 |
|---------|---------|
| 取消/中断 | 立即中断当前执行 |
| 修改指令 | 标记当前结果待废弃，重新规划 |
| 补充信息 | 注入当前执行上下文（下一个 Checkpoint 可见） |
| 无关/闲聊 | 排队，当前任务完成后处理 |

实现要点：基于 WebSocket/SSE 双向通道，缓冲区是 Assistant 级别（不是 Agent 级别），Agent 通过 Checkpoint 回调检查新输入。

## 状态策略

| 状态类型 | 生命周期 | 存储位置 |
|----------|----------|----------|
| 会话上下文 | 会话级 | 内存 |
| TaskBoard | 会话级 | 内存 + Checkpoint（长任务） |
| InputBuffer | 会话级 | 内存 |
| 用户画像 | 持久 | Cognition 用户私有区 |
| 长期记忆引用 | 持久 | Cognition（实体存储） |

## 情感感知

- **感知**：通过语言语气、操作节奏推断用户情绪状态
- **响应**：AI 回应风格、信息密度随情绪状态自适应
- **记忆**：情感偏好纳入用户画像
- **伦理边界**：不利用情绪弱点，不模拟情感依赖

## 与 Team 的边界

| 维度 | 单 Assistant 多实例 | Team 多 Assistant |
|------|---------------------|-------------------|
| 用户感知 | 一个助理在高效工作 | 多个助理在协作 |
| 上下文共享 | 共享同一 Cognition 用户私有区 | 各自独立（需 A2A 同步） |
| 冲突处理 | 主实例直接裁决 | 需要仲裁协议 |
| 适用场景 | 同一用户、同一目标的并行加速 | 跨系统、跨服务、真正独立的多方 |

## 包结构

```text
intelligent/assistant/
  ├── DefaultAssistantExecutor    实现 AssistantExecutor
  ├── AssistantService            统一入口（意图→Skill→Agent 认知循环→学习）
  ├── AssistantDefinition         @Entity：Actor + Role + MemoryStrategy
  ├── AssistantDefinitionRepository
  ├── AssistantPermissionEvaluator 权限评估
  ├── PermissionScope             权限范围枚举
  ├── actor/Actor                 @Entity：人格载体
  ├── actor/ActorRepository
  ├── role/Role                   @Entity：能力配置
  ├── role/AiRoleRepository
  ├── role/RoleStore              角色存储接口
  ├── SessionManager              会话管理
  ├── AgentDispatcher             Agent 调度器
  ├── SkillMatchService           技能匹配
  ├── HumanApprovalService        人工审批
  ├── ResultAggregator            结果聚合
  ├── LearningFeedbackService     学习反馈
  └── a2a/
      ├── A2AEngine               A2A 协议接口
      ├── LocalA2AEngine          本地直调实现
      ├── AgentScopeA2AEngine     AgentScope A2A 实现
      └── A2AAutoConfiguration    自动配置
```

## 相关文档

- [五层智能架构总览](../architecture.md)
- [Actor 模型](actor.md)
- [用户感知与个性化](../cognition/personalization.md)

---

## AgentScope 接口映射

### 核心类映射

| AAF 组件 | AgentScope 类 | 说明 |
|----------|--------------|------|
| 会话持久化 | `io.agentscope.core.session.Session` | 状态存储接口（save/get/delete） |
| 会话管理 | `io.agentscope.core.session.SessionManager` | 流式 API 管理组件状态 |
| 开发环境 | `io.agentscope.core.session.JsonSession` | 文件系统 JSON 持久化 |
| 生产环境 | `io.agentscope.core.session.InMemorySession` | 内存存储（生产用 Redis 扩展） |
| 状态模块 | `io.agentscope.core.state.StateModule` | 组件状态序列化接口 |
| 会话标识 | `io.agentscope.core.state.SessionKey` | 会话唯一标识 |

### Session 接口

```java
// AgentScope 定义的会话存储接口
public interface Session {
    void save(SessionKey key, String stateKey, State value);          // 保存单值
    void save(SessionKey key, String stateKey, List<? extends State> values); // 保存列表
    <T extends State> Optional<T> get(SessionKey key, String stateKey, Class<T> type);
    <T extends State> List<T> getList(SessionKey key, String stateKey, Class<T> itemType);
    boolean exists(SessionKey key);
    void delete(SessionKey key);
    Set<SessionKey> listSessionKeys();
}
```

### SessionManager 流式 API

```java
// AgentScope 提供的便捷管理器
SessionManager.forSessionId("user_123_conv_456")
    .withSession(session)           // 注入 Session 实现
    .addComponent(agent)            // ReActAgent（含 Memory + PlanNotebook）
    .loadIfExists();                // 恢复状态（如存在）

// 保存
SessionManager.forSessionId("user_123_conv_456")
    .withSession(session)
    .addComponent(agent)
    .saveSession();                 // 持久化当前状态
```

## 适配器实现

### AafSessionAdapter（Redis Session 工厂）

```java
package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

/**
 * AAF Session 适配器——封装 AgentScope RedisSession 实现状态持久化。
 * 静态工厂方法，创建基于 Lettuce 的 RedisSession 实例。
 */
public class AafSessionAdapter {

    private AafSessionAdapter() {}

    /**
     * 创建基于 Lettuce 的 RedisSession 实例。
     */
    public static Session createRedisSession(RedisClient redisClient) {
        return RedisSession.builder().lettuceClient(redisClient).build();
    }
}
```

### AgentScopeSessionAdapter（会话生命周期管理）

```java
package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

/**
 * AgentScope Session 适配器——封装 SessionManager 流式 API。
 * 提供简化的 load/save/delete 操作。
 */
@RequiredArgsConstructor
public class AgentScopeSessionAdapter {

    private final Session session;

    public void loadIfExists(String sessionId, StateModule... components) {
        var manager = SessionManager.forSessionId(sessionId).withSession(session);
        for (var component : components) manager.addComponent(component);
        manager.loadIfExists();
    }

    public void save(String sessionId, StateModule... components) {
        var manager = SessionManager.forSessionId(sessionId).withSession(session);
        for (var component : components) manager.addComponent(component);
        manager.saveSession();
    }

    public void remove(String sessionId) {
        session.delete(SimpleSessionKey.of(sessionId));
    }
}
```

### DefaultAssistantExecutor（会话处理主流程）

```java
package com.xuejiai.aaf.framework.intelligent.assistant;

/**
 * AssistantExecutor 默认实现：
 * 会话管理 → 记忆拉取（按 MemoryStrategy）→ Skill 匹配 → Agent 调度 → 记忆写回。
 */
@Service
@RequiredArgsConstructor
public class DefaultAssistantExecutor implements AssistantExecutor {

    private final AssistantDefinitionRepository assistantRepo;
    private final SessionManager sessionManager;
    private final SkillMatchEngine skillMatch;
    private final AgentRegistryService agentRegistry;
    private final AgentPool agentPool;
    private final AgentSandbox agentSandbox;
    private final ShortTermMemoryService shortTermMemory;
    private final MemoryPipelineFactory pipelineFactory;

    @Override
    public AssistantResponse chat(String sessionId, String assistantId, Long userId, String userMessage) {
        // 1. 加载 Assistant 配置
        // 2. 会话管理
        // 3. 记录用户消息到短期记忆
        // 4. 按 MemoryStrategy 拉取上下文（RetrievalPipeline）
        // 5. Skill 匹配 → 确定 Agent
        // 6. AgentPool.borrow → AgentSandbox.execute → AgentPool.release
        // 7. 记录响应到短期记忆
        // 8. 返回 AssistantResponse
    }
}
```

## 关键 Hook 注入点

Assistant 层通过自定义 Hook 注入 AAF 特有逻辑：

| Hook | 拦截事件 | 优先级 | 逻辑 |
|------|---------|--------|------|
| `EmotionPerceptionHook` | `PreCallEvent` | 80 | 分析用户消息情绪，注入情感上下文到 systemPrompt |
| `UserProfileHook` | `PreCallEvent` | 60 | 从 Cognition 加载用户画像摘要，注入到上下文 |
| `SessionAutoSaveHook` | `PostCallEvent` | 900 | Agent 回复后自动保存会话状态 |

### 会话状态保存内容

AgentScope `ReActAgent.saveTo()` 自动保存：

| 组件 | 状态 Key | 内容 |
|------|---------|------|
| Agent 元数据 | `agent_meta` | agentId, name, description, sysPrompt |
| Memory 消息 | `memory_messages` | 对话历史（增量追加） |
| Toolkit 活跃组 | `toolkit_activeGroups` | 当前激活的工具组列表 |
| PlanNotebook | `plan_notebook` | 当前计划 + 子任务状态 |

## 配置与初始化

```java
// Spring Boot 自动配置
@Configuration
public class AssistantSessionConfig {

    @Bean
    @Profile("dev")
    public Session devSession() {
        // 开发环境：文件系统 JSON
        return new JsonSession(Path.of("data/sessions"));
    }

    @Bean
    @Profile("prod")
    public Session prodSession(RedisTemplate<String, String> redis) {
        // 生产环境：Redis 适配
        return new AafSessionAdapter(redis, new ObjectMapper());
    }
}
```

## 相关文档（补充）

- [AgentScope 整合策略](../agentscope-integration.md)
