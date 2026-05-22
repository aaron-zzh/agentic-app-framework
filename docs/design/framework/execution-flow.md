---
level: Practice
layer: Model
purpose: 五层智能架构完整执行逻辑——组件调用链、AgentScope 结合点、各层能力涌现
status: draft
version: 0.3.0
date: 2026-05-21
author: AaronZZH & Kiro
---

# 五层智能架构执行逻辑全景

> 1. 一次请求从入口到 LLM 的完整调用链是什么？
> 2. 各层分别涌现哪些能力，走 Agent 还是直接 API？

## 组件逻辑图

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Layer 5  对话与交互层（aaf-api）  多模态聊天 + UI操作 + 组件生成 + 语义组件         │
│                                                                                  │
│  HTTP/REST ──┐                                                                   │
│  GraphQL ────┤──→ 安全网关（JWT + RBAC + 限流）──→ Controller                    │
│  WebSocket ──┤                                        │                          │
│  AG-UI SSE ──┘                                        │                          │
└───────────────────────────────────────────────────────┼──────────────────────────┘
                                                        │ 调用
┌───────────────────────────────────────────────────────▼──────────────────────────┐
│  Layer 4  服务层（aaf-api/module）                                                │
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │  ChatService / AssistantManagementService / AgentManagementService       │    │
│  │  KnowledgeManagementService / ModelManagementService（AiModelService）   │    │
│  └──────────────────────────────────┬──────────────────────────────────────┘    │
│                                     │ 调用 framework 接口                        │
└─────────────────────────────────────┼────────────────────────────────────────────┘
                                      │
┌─────────────────────────────────────▼────────────────────────────────────────────┐
│  Layer 3  智能层（aaf-framework/intelligent）                                     │
│                                                                                  │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │  Team 层（team/）                                                         │   │
│  │  TeamOrchestrator ──→ TaskDistributor ──→ AgentDispatcher × N            │   │
│  │  ConflictArbitrator ←── ProgressSyncService                              │   │
│  │  A2AProtocolService（跨系统 Agent 互联）                                  │   │
│  │                                                                           │   │
│  │  【AgentScope 结合点】Pipeline / MsgHub / Supervisor 模式                 │   │
│  └──────────────────────────────┬───────────────────────────────────────────┘   │
│                                 │ 调度                                            │
│  ┌──────────────────────────────▼───────────────────────────────────────────┐   │
│  │  Assistant 层（assistant/）                                               │   │
│  │  IntentUnderstandingService ──→ EmotionPerceptionService                 │   │
│  │  SessionManager ──→ SkillMatchService ──→ AgentDispatcher                │   │
│  │  ResultAggregator ←── LearningFeedbackService                            │   │
│  │                                                                           │   │
│  │  【AgentScope 结合点】主 ReActAgent 作为 Assistant 执行体                 │   │
│  │  Hook: PreReasoningEvent 注入记忆/知识/用户画像                           │   │
│  └──────────────────────────────┬───────────────────────────────────────────┘   │
│                                 │ 派发任务                                        │
│  ┌──────────────────────────────▼───────────────────────────────────────────┐   │
│  │  Agent 层（agent/）                                                       │   │
│  │  AgentPool.borrow() ──→ AgentSandbox.execute()                           │   │
│  │       │                        │                                          │   │
│  │       ▼                        ▼                                          │   │
│  │  AgentFactory.create()   CognitiveCycleExecutor（感知→规划→执行→评估）    │   │
│  │       │                        │                                          │   │
│  │       ▼                        ├──→ ToolCallDispatcher（工具调用）        │   │
│  │  AgentScopeExecutor            ├──→ SkillMatchEngine（技能匹配）          │   │
│  │  └─ ReActAgent（AgentScope）   └──→ LlmClient.call()（LLM 推理）         │   │
│  │       └─ OpenAIChatModel                                                  │   │
│  │           └─ apiKey/baseUrl 来自 ai_model 表                              │   │
│  │                                                                           │   │
│  │  AgentCheckpointService（断点续跑）                                       │   │
│  │  AgentPool.release()（归还，reset 清空历史）                              │   │
│  │                                                                           │   │
│  │  【AgentScope 结合点】ReActAgent 是 Agent 执行体                          │   │
│  │  AgentScope 负责：ReAct 循环 / 工具调用 / Session / Tracing               │   │
│  └──────────────────────────────┬───────────────────────────────────────────┘   │
│          执行前拉取 ◄────────────┤────────────► 执行后写回                       │
│  ┌──────────────────────────────▼───────────────────────────────────────────┐   │
│  │  Cognition 层（cognition/）                                               │   │
│  │  Memory + Knowledge + Value 三核心组件 + Retrieval 服务组件               │   │
│  │                                                                           │   │
│  │  MemoryPipeline（查询理解→路由→并行检索→RRF融合→重排→MemoryContext）      │   │
│  │       ├── ShortTermMemoryService ──→ engine/memory（AtomMemoryEngine）   │   │
│  │       ├── LongTermMemoryService  ──→ engine/memory（AtomMemoryEngine）   │   │
│  │       └── UnifiedRetrievalService ─→ engine/knowledge（HybridSearch）    │   │
│  │                                                                           │   │
│  │  ValueService（价值观系统）──→ engine/value-rule（ValueRuleEngine）       │   │
│  │       伦理边界 / 优先级规则 / 交互规范 / 降级边界 / 合规约束              │   │
│  │       → Agent 决策前校验 / Retrieval 出库过滤 / Knowledge 写入拦截        │   │
│  │                                                                           │   │
│  │  【AgentScope 结合点】AafLongTermMemory 实现 AgentScope LongTermMemory   │   │
│  │  AgentScope STATIC_CONTROL 模式自动触发 retrieve/record                  │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │  Core 层（ai/）                                                           │   │
│  │                                                                           │   │
│  │  ModelRouter（六层决策链）                                                │   │
│  │    1. 显式指定 explicitModelId                                            │   │
│  │    2. 编排引擎配置 orchestrationModelId（工作流节点/AgentDefinition）     │   │
│  │    3. AI 辅助决策 AiModelSelector（任务特征：图片/推理/长文本/成本）      │   │
│  │    4. 用户偏好 ModelPreference（USER scope）                              │   │
│  │    5. 系统默认 ModelPreference（SYSTEM scope）                            │   │
│  │    6. yaml 兜底 AiProperties.defaultModel                                │   │
│  │         │                                                                 │   │
│  │         ▼                                                                 │   │
│  │  DynamicChatClientFactory.get(modelId)                                   │   │
│  │    ├── OPENAI_COMPAT → OpenAiChatModel（Spring AI M6，OpenAiSetup）      │   │
│  │    ├── ANTHROPIC     → AnthropicChatModel（Spring AI，从容器取 Bean）    │   │
│  │    └── OLLAMA        → OllamaChatModel（Spring AI，从容器取 Bean）       │   │
│  │         │                                                                 │   │
│  │         ▼                                                                 │   │
│  │  ResilientChatService（降级 + Token 计量）                               │   │
│  │    主模型失败 → fallback_model_id（ai_model 表配置）                     │   │
│  │    每次调用后 → TokenUsageEvent → TokenMeteringService                   │   │
│  │                                                                           │   │
│  │  TokenMeteringService → 积分扣减（CreditService，待实现）                │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────────────┘
                                      │
┌─────────────────────────────────────▼────────────────────────────────────────────┐
│  Layer 2  引擎层（aaf-framework/engine）                                          │
│                                                                                  │
│  knowledge/  ──→ PgVector（向量检索）+ Neo4j（图谱检索）+ PG FTS（关键词）       │
│  memory/     ──→ AtomMemoryEngine → Redis（短期）+ PostgreSQL（长期）            │
│  value-rule/ ──→ ValueRuleEngine（规则解析 + 优先级仲裁 + 行为校验）             │
│  tool/       ──→ ToolRegistry（Spring Bean + MCP）+ ScriptSandbox               │
│  skill/      ──→ SkillMatchEngine + BuiltinSkills                               │
│  workflow/   ──→ Flowable（BPMN 工作流执行，节点可嵌入 Agent 任务）              │
│  data-process/ → DataProcessEngine（批/流处理 + 统计分析 + 聚合计算）            │
│  scheduler/  ──→ 异步任务队列 + 定时触发                                         │
└──────────────────────────────────────────────────────────────────────────────────┘
                                      │
┌─────────────────────────────────────▼────────────────────────────────────────────┐
│  Layer 1  基础设施层                                                              │
│  PostgreSQL + PgVector   Redis   Neo4j   JVM Sandbox                             │
└──────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────────┐
│  横切关注点（贯穿所有层）                                                         │
│  ai_model 表 ──→ 所有 LLM 调用的 apiKey/baseUrl/capabilities 唯一来源           │
│  ModelPreference 表 ──→ 用户/系统级模型偏好                                      │
│  TokenUsageEvent ──→ 两条路径（Spring AI + AgentScope）统一计量                  │
│  AG-UI 协议 ──→ 流式输出标准（RUN_STARTED/TEXT_MESSAGE/TOOL_CALL/RUN_FINISHED）  │
│  Learning 反哺通道 ──→ Agent 执行结果→评估→更新 Memory/Knowledge/Value（异步）   │
│  Value 价值观 ──→ Agent 决策校验 + Retrieval 出库过滤 + Knowledge 写入拦截       │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 两条 LLM 调用路径

```
路径 A：Spring AI 路径（对话/RAG/记忆提取等非 Agent 场景）
  ResilientChatService
    → ModelRouter（六层决策链）
    → DynamicChatClientFactory.get(modelId)
    → Spring AI ChatClient → 各厂商 API

路径 B：AgentScope 路径（Agent 执行场景）
  AgentFactory.create(definition)
    → AgentScopeExecutor → ReActAgent
    → OpenAIChatModel.builder()
        .apiKey(ai_model.apiKey)
        .baseUrl(ai_model.baseUrl)
        .modelName(ai_model.modelName)
    → 各厂商 API（OpenAI 兼容 / Anthropic / DashScope / Gemini / Ollama）

两条路径共享：
  - ai_model 表（apiKey/baseUrl 唯一来源）
  - TokenUsageEvent（统一计量）
  - ModelPreference 表（用户偏好，路径 A 通过 ModelRouter 使用，路径 B 待接入）
```

---

## 各层涌现能力

### Layer 0 Core — LLM 基础设施

| 能力 | 实现 | 状态 |
|------|------|------|
| 多厂商模型动态切换 | DynamicChatClientFactory + ai_model 表 | ✅ |
| 六层模型路由决策 | ModelRouter（显式→编排→AI辅助→用户偏好→系统默认→yaml） | ✅ |
| 主模型降级 | ResilientChatService fallback | ✅ |
| Token 计量 | TokenMeteringService + TokenUsageEvent | ✅ |
| 积分结算 | TokenMeteringService → CreditService | ⚠️ 待实现 |

### Layer 1 Cognition — 认知基础

| 能力 | 实现 | 状态 |
|------|------|------|
| 短期记忆 | AtomMemoryEngine → Redis | ✅ |
| 长期记忆 | AtomMemoryEngine → PostgreSQL | ✅ |
| 知识库检索 | HybridSearchService（向量+图谱+关键词+RRF） | ✅ |
| 记忆管道 | MemoryPipeline（可编排步骤） | ✅ |
| 价值观校验 | ValueService → ValueRuleEngine（伦理边界+优先级+合规） | ⚠️ 待实现 |
| AgentScope 记忆适配 | AafLongTermMemory implements LongTermMemory | ⚠️ 待实现 |

### Layer 2 Agent — 无状态任务执行

| 能力 | 实现 | 状态 |
|------|------|------|
| ReAct 认知循环 | AgentScopeExecutor → ReActAgent | ✅ |
| 工具调用 | ToolCallDispatcher + McpToolService | ✅ |
| 技能匹配 | SkillMatchEngine | ✅ |
| Agent 池化复用 | AgentPool（借出重置/归还清空） | ✅ |
| 断点续跑 | AgentCheckpointService | ✅ |
| 沙箱隔离 | AgentSandbox（虚拟线程） | ✅ |
| 注意力预算 | AttentionBudget（Token 分配上限，超限压缩/丢弃低优先级上下文） | ⚠️ 待实现 |
| 多模态输入 | AgentScope ImageBlock/AudioBlock（直接使用） | ✅ AgentScope 原生 |

### Layer 3 Assistant — 有状态会话

| 能力 | 实现 | 状态 |
|------|------|------|
| 意图理解 | IntentUnderstandingService | ✅ 骨架 |
| 情感感知 | EmotionPerceptionService | ✅ 骨架 |
| 会话管理 | SessionManager | ✅ 骨架 |
| Agent 调度 | AgentDispatcher | ✅ |
| 结果聚合 | ResultAggregator | ✅ 骨架 |
| 技能路由 | SkillMatchService | ✅ |
| 能力护栏 | 根据任务类型动态限定 Agent 操作范围（工具白名单+权限边界） | ⚠️ 待实现 |
| 内置技能（4个） | self-awareness / user-understanding / self-learning / skill-creation | ✅ |

### Layer 4 Team — 多 Assistant 协作

| 能力 | 实现 | 状态 |
|------|------|------|
| 任务分发 | TaskDistributor | ✅ 骨架 |
| 进度同步 | ProgressSyncService | ✅ 骨架 |
| 冲突仲裁 | ConflictArbitrator | ✅ 骨架 |
| A2A 跨系统协作 | A2AProtocolService | ✅ AgentScope starter |
| Pipeline/Supervisor 编排 | AgentScope Pipeline/MsgHub | ✅ AgentScope 原生 |

---

## 走 Agent 还是直接 API？

```
请求到达
    │
    ├─ 固定流程（审批/发布/CI）→ 工作流引擎（Flowable）
    │     工作流节点可嵌入 Agent 任务（确定性流程 + 不确定性任务混合编排）
    │
    ├─ 简单对话/RAG/知识检索 → 路径 A（ResilientChatService 直接调用）
    │
    ├─ 复杂任务（需要规划+工具调用+迭代）→ 路径 B（AgentScope ReActAgent）
    │     判断标准：
    │     - 任务复杂度高（多步骤、需要工具）→ Agent
    │     - 任务价值高（值得 LLM 多轮推理）→ Agent
    │     - 步骤不确定（需要动态规划）→ Agent
    │     - 简单问答/检索 → 直接 API
    │
    └─ 多 Assistant 协作（项目级目标）→ Team 层编排
```

---

## AgentScope 结合点汇总

| 层 | AgentScope 组件 | AAF 接入方式 | 状态 |
|----|----------------|-------------|------|
| Core | OpenAIChatModel / AnthropicChatModel / DashScopeChatModel | AgentFactory 从 ai_model 表读配置构建 | ✅ |
| Agent | ReActAgent（ReAct 循环） | AgentScopeExecutor 适配 AgentExecutor 接口 | ✅ |
| Agent | Session（Redis/MySQL 后端） | AgentScopeSessionAdapter | ⚠️ 待引入 starter |
| Agent | autocontext-memory（Token 预算截断） | AgentScopeMemoryAdapter | ⚠️ 待引入扩展 |
| Agent | Toolkit / ToolRegistry | AgentScopeToolAdapter | ⚠️ 待迁移 |
| Cognition | LongTermMemory 接口 | AafLongTermMemory 对接 AtomMemoryEngine | ⚠️ 待实现 |
| Assistant | Hook（PreReasoningEvent） | 注入记忆/知识/用户画像到 LLM 上下文 | ⚠️ 待实现 |
| Team | Pipeline / MsgHub / Supervisor | AgentScope 编排模式直接使用 | ✅ AgentScope 原生 |
| Team | A2A 协议 | agentscope-a2a-spring-boot-starter | ✅ 已引入 |
| Layer 5 | AG-UI 协议（SSE 事件流） | agentscope-agui-spring-boot-starter | ✅ 已引入 |

---

## 关键设计约束

```
1. 依赖方向：intelligent/ 不直接访问数据库，必须通过 engine/ 接口
2. 引擎层不调用智能层（方向只能向下）
3. ai_model 表是所有 LLM 配置的唯一来源，不在 yaml 硬编码 apiKey
4. 两条 LLM 路径（Spring AI / AgentScope）共享 ai_model 表和 TokenUsageEvent
5. AgentScope 是 Agent 层的执行骨架，AAF 五层是薄门面（每层 ~50-100 行）
6. 多模态能力：Agent 内部走 AgentScope 原生；非 Agent 场景走 ai/image/ 封装
7. ModelRouter 六层决策链目前只服务 Spring AI 路径，AgentScope 路径待接入
8. 认知循环分层原则：每层有且只有一个认知循环，粒度从项目级到请求级逐层细化；
   上层循环通过调度触发下层循环，下层结果通过回调返回上层，禁止跨层直接触发
9. 决策权跨层流动：低置信度决策上报上层处理，高置信度决策本层直接执行，
   决策权随置信度在层间动态流动，不固定归属某层
10. 能力护栏：Assistant 根据任务类型动态限定 Agent 操作范围（工具白名单+权限边界），
    限定范围换取信任空间，减少人工审查成本
11. 瓶颈迁移意识：执行近乎免费，规划与审查是新瓶颈——Agent 核心价值是帮用户规划和审查
```

---

## 接口与实现来源

> 图例：`[接口]` = AAF 自定义接口，`[AS]` = AgentScope 实现，`[SAI]` = Spring AI 实现，`[AAF]` = AAF 自研实现

### Core 层

| 组件 | 类型 | 来源 | 说明 |
|------|------|------|------|
| `ModelRouter` | `[接口]` AAF | `DefaultModelRouter` `[AAF]` | 六层决策链，AAF 自研 |
| `ModelRoutingContext` | Record | `[AAF]` | 路由上下文，AAF 自研 |
| `AiModelSelector` | `[AAF]` | `[AAF]` | 任务特征→模型选择，AAF 自研 |
| `DynamicChatClientFactory` | `[AAF]` | `[AAF]` + `[SAI]` | 工厂 AAF 自研，构建 Spring AI ChatClient |
| `ChatClient` | `[SAI]` | `[SAI]` | Spring AI 核心，AAF 不自研 |
| `OpenAiChatModel` | `[SAI]` | `[SAI]` | Spring AI 2.0 M6，用 OpenAiSetup 构建 |
| `AnthropicChatModel` | `[SAI]` | `[SAI]` | Spring AI，从容器取 Bean |
| `OllamaChatModel` | `[SAI]` | `[SAI]` | Spring AI，从容器取 Bean |
| `ResilientChatService` | `[AAF]` | `[AAF]` | 降级+计量，AAF 自研，内部用 Spring AI |
| `TokenMeteringService` | `[AAF]` | `[AAF]` | Token 计量，AAF 自研 |
| `TokenUsageEvent` | Record | `[AAF]` | 两条路径统一计量事件 |
| `ModelPreferenceRepository` | `[AAF]` | `[AAF]` + JPA | 用户/系统偏好，AAF 自研 |

### Agent 层

| 组件 | 类型 | 来源 | 说明 |
|------|------|------|------|
| `AgentExecutor` | `[接口]` AAF | — | AAF 定义，屏蔽 AgentScope 细节 |
| `AgentScopeExecutor` | `[AAF]` | `[AAF]` 适配 `[AS]` | 实现 AgentExecutor，包装 ReActAgent |
| `ReActAgent` | `[AS]` | `[AS]` | AgentScope 核心，ReAct 循环 |
| `OpenAIChatModel`（AS） | `[AS]` | `[AS]` | AgentScope 内置，支持多厂商 |
| `AgentFactory` | `[AAF]` | `[AAF]` | 从 ai_model 表读配置，构建 AgentExecutor |
| `AgentPool` | `[AAF]` | `[AAF]` | 池化复用，AAF 自研 |
| `AgentSandbox` | `[AAF]` | `[AAF]` | 虚拟线程隔离，AAF 自研 |
| `AgentCheckpointService` | `[AAF]` | `[AAF]` | 断点续跑，AAF 自研 |
| `CognitiveCycleExecutor` | `[AAF]` | `[AAF]` | 感知→规划→执行→评估，AAF 自研（薄门面） |
| `WorkingMemory` | `[接口]` AAF | `WorkingMemoryImpl` `[AAF]` | 执行期临时状态 |
| `McpToolService` | `[AAF]` | `[AAF]` + `[AS]` MCP | MCP 工具发现，AAF 封装 AgentScope MCP |
| `ToolRegistry` | `[AAF]` | `[AAF]` | 工具注册表，AAF 自研 |
| `ToolCallDispatcher` | `[AAF]` | `[AAF]` | 工具调用分发，AAF 自研 |
| `SessionManager`（待） | `[接口]` AAF | `AgentScopeSessionAdapter` `[AAF]` 适配 `[AS]` | 待引入 agentscope-extensions-session-redis |

### Cognition 层

| 组件 | 类型 | 来源 | 说明 |
|------|------|------|------|
| `MemoryPipeline` | `[接口]` AAF | `DefaultMemoryPipeline` `[AAF]` | 记忆管道，AAF 自研 |
| `AtomMemoryEngine` | `[接口]` AAF | `AtomMemoryEngineImpl` `[AAF]` | 原子记忆，AAF 自研（参考 Mem0/Graphiti） |
| `HybridSearchService` | `[AAF]` | `[AAF]` | 向量+图谱+关键词+RRF，AAF 自研 |
| `UnifiedRetrievalService` | `[AAF]` | `[AAF]` | 统一检索入口，AAF 自研 |
| `ValueService` | `[接口]` AAF | `DefaultValueService` `[AAF]` | 价值观校验/冲突仲裁，AAF 自研 |
| `ValueRuleEngine` | `[接口]` AAF | `ValueRuleEngineImpl` `[AAF]` | 规则解析+优先级仲裁+行为校验，引擎层 |
| `EmbeddingModel` | `[SAI]` | `[SAI]` | Spring AI，向量化 |
| `VectorStore`（PgVector） | `[SAI]` | `[SAI]` | Spring AI PgVector |
| `AafLongTermMemory`（待） | `[AAF]` 实现 `[AS]` 接口 | `[AAF]` | 对接 AtomMemoryEngine，待实现 |

### Assistant 层

| 组件 | 类型 | 来源 | 说明 |
|------|------|------|------|
| `AssistantExecutor` | `[接口]` AAF | `DefaultAssistantExecutor` `[AAF]` | AAF 定义接口 |
| `IntentUnderstandingService` | `[AAF]` | `[AAF]` | 意图理解，AAF 自研（内部调 LLM） |
| `EmotionPerceptionService` | `[AAF]` | `[AAF]` | 情感感知，AAF 自研 |
| `SessionManager` | `[接口]` AAF | `[AAF]` / `[AS]` 可替换 | 会话管理 |
| `AgentDispatcher` | `[AAF]` | `[AAF]` | Agent 调度，AAF 自研 |
| `SkillMatchService` | `[AAF]` | `[AAF]` | 技能匹配，AAF 自研 |
| `ResultAggregator` | `[AAF]` | `[AAF]` | 结果聚合，AAF 自研 |

### Team 层

| 组件 | 类型 | 来源 | 说明 |
|------|------|------|------|
| `TeamOrchestrator` | `[接口]` AAF | `DefaultTeamOrchestrator` `[AAF]` | 团队编排，AAF 薄门面 |
| `TaskDistributor` | `[AAF]` | `[AAF]` | 任务分发，AAF 自研 |
| `ConflictArbitrator` | `[AAF]` | `[AAF]` | 冲突仲裁，AAF 自研 |
| `ProgressSyncService` | `[AAF]` | `[AAF]` | 进度同步，AAF 自研 |
| `Pipeline` / `MsgHub` | `[AS]` | `[AS]` | AgentScope 编排原语，直接使用 |
| `A2AProtocolService` | `[AAF]` 适配 `[AS]` | `[AAF]` + agentscope-a2a-starter | A2A 协议，AAF 封装 AgentScope |
| `AgUiStreamHandler` | `[AAF]` 适配 `[AS]` | `[AAF]` + agentscope-agui-starter | AG-UI 协议，AAF 封装 AgentScope |

---

### 一句话总结

```
AgentScope 负责：ReAct 执行循环 / 多模态消息格式 / Session 持久化 / Pipeline 编排 / A2A + AG-UI 协议
Spring AI 负责：ChatClient 抽象 / 各厂商 ChatModel / EmbeddingModel / VectorStore
AAF 自研：五层接口定义 / 模型路由决策链 / 记忆管道 / 知识引擎 / 工具引擎 / 技能引擎 / Token 计量 / 积分结算
```

| 文档 | 内容 |
|------|------|
| [architecture-detail.md](architecture-detail.md) | 组件关系全景图、Maven 模块映射、AgentScope 整合策略 |
| [intelligent/agent.md](intelligent/agent.md) | 五层智能架构详细设计 |
| [intelligent/cognition.md](intelligent/cognition.md) | Cognition 层详细设计 |
| [personalization.md](personalization.md) | 用户感知与个性化（记忆管道、上下文、状态管理协同） |
| [meta-engine.md](meta-engine.md) | 元引擎核心设计（调度/状态/门控/自进化） |
| [code-structure.md](code-structure.md) | 元引擎包结构详解 |
| [core/execution-dispatcher.md](core/execution-dispatcher.md) | 执行调度器详细设计 |
| [core/state-manager.md](core/state-manager.md) | 状态管理器四层状态 |
| [core/confidence-gate.md](core/confidence-gate.md) | 置信度门控器二维模型 |
| [core/metadata-manager.md](core/metadata-manager.md) | 元数据管理器与语义漂移检测 |
| [core/evolution.md](core/evolution.md) | 自进化机制（引擎自进化 + 业务系统自进化） |
| [core/runtime-capability.md](core/runtime-capability.md) | 运行时能力（工作流/智能体/降级/沙箱） |
| [core/dev-capability.md](core/dev-capability.md) | 开发时能力（自开发/四层无代码运行时） |
| [core/complexity-encapsulation.md](core/complexity-encapsulation.md) | 复杂性封装策略与五度空间约束 |
| [core/human-computation.md](core/human-computation.md) | 人类计算支撑（贡献行为/积分/结算） |
| [engine/budget-control.md](engine/budget-control.md) | 预算控制引擎（预估/监控/超限介入） |
| [engine/credit-settlement.md](engine/credit-settlement.md) | 积分与结算引擎 |
| [engine/monitor.md](engine/monitor.md) | 监控引擎（AI 可观测性/Token 统计/审计日志） |
| [engine/orchestration.md](engine/orchestration.md) | 编排引擎（执行路径决策/引擎协同） |
| [engine/scheduler.md](engine/scheduler.md) | 调度引擎（异步任务队列/定时触发） |
| [engine/message.md](engine/message.md) | 消息引擎（多渠道通知） |
| [engine/prompt.md](engine/prompt.md) | Prompt 引擎（提示词库/链式组装/评估优化） |
| [engine/semantic-compute.md](engine/semantic-compute.md) | SemanticCalc 语义计算引擎 |
| [engine/nexus-knowledge.md](engine/nexus-knowledge.md) | NexusKB 知识引擎（ECL 管道/混合检索） |
| [engine/atom-memory.md](engine/atom-memory.md) | AtomMemory 记忆引擎 |
| [engine/document-engine.md](engine/document-engine.md) | 文档引擎（七类文档/版本控制/协同） |
| [engine/external-datasource.md](engine/external-datasource.md) | 外部数据源引擎（ETL/联邦查询） |
| [engine/physics-spacetime.md](engine/physics-spacetime.md) | 物理时空引擎（世界模型/语义引力/聚类） |
| [engine/sense-ui.md](engine/sense-ui.md) | 语义组件引擎（DSL 驱动动态 UI） |
| [engine/recommendation.md](engine/recommendation.md) | 推荐引擎 |
| [engine/plugin.md](engine/plugin.md) | 插件引擎（生态市场运行时底座） |
| [engine/search.md](engine/search.md) | 搜索引擎（跨资源统一搜索） |
| [security/security.md](security/security.md) | 安全架构（加密/脱敏/审计/AI 安全） |
| [security/access-control.md](security/access-control.md) | 访问控制（认证/授权/四层权限模型） |
| [apps/service/module-structure.md](../apps/service/module-structure.md) | Maven 模块结构 |


---

## 与业务系统的交互

```
业务系统（aaf-api/module/）
    │
    ├── 读取 AI 能力（单向依赖 framework 接口）
    │     module/chat → AssistantExecutor.chat()
    │     module/knowledge → KnowledgePipelineService.ingest()
    │     module/agent → AgentFactory.create() + AgentPool
    │
    ├── 发布领域事件（解耦，AI 被动感知）
    │     业务操作完成 → ApplicationEvent → AI 感知层订阅
    │     例：文档上传完成 → DocumentUploadedEvent → 知识库自动入库
    │         用户行为变化 → UserBehaviorEvent → 记忆管道写回
    │
    └── 接收 AI 产出（回调/SSE）
          AG-UI SSE 流 → 前端实时展示
          WebSocket 推送 → 系统通知
          REST 响应 → 同步调用结果
```

---

## 场景时序视图

### 场景一：用户 AI 对话（文字输入，Spring AI 路径）

```
用户       前端          Layer5 API    Layer4 Service  Layer3 智能层      Layer2 引擎    LLM
 │          │               │               │               │               │             │
 │─输入────→│               │               │               │               │             │
 │          │─POST /chat───→│               │               │               │             │
 │          │               │─JWT+限流──────→│               │               │             │
 │          │               │               │─ChatService───→│              │             │
 │          │               │               │               │─SessionManager│             │
 │          │               │               │               │─MemoryPipeline→│            │
 │          │               │               │               │               │─AtomMemory  │
 │          │               │               │               │               │─HybridSearch│
 │          │               │               │               │←─MemoryContext─│            │
 │          │               │               │               │─ModelRouter    │             │
 │          │               │               │               │  六层决策→modelId            │
 │          │               │               │               │─DynamicChatClientFactory    │
 │          │               │               │               │─────────────────────────────→│
 │          │←─AG-UI SSE───│←──────────────│←──────────────│←──流式 token────────────────│
 │←─实时文字─│ TEXT_MESSAGE  │               │               │─TokenUsageEvent             │
 │          │               │               │               │─MemoryPipeline 写回→AtomMemory
 │          │←─RUN_FINISHED─│               │               │               │             │
```

### 场景二：用户 AI 对话（含工具调用，AgentScope 路径）

```
用户       前端          Layer5 API    Layer4 Service  Layer3 Agent层     Layer2 引擎    LLM
 │          │               │               │               │               │             │
 │─"查订单"→│               │               │               │               │             │
 │          │─POST /chat───→│               │               │               │             │
 │          │               │               │─AssistantSvc──→│              │             │
 │          │               │               │               │─SkillMatch     │             │
 │          │               │               │               │─AgentPool.borrow()          │
 │          │               │               │               │─AgentSandbox   │             │
 │          │               │               │               │  └─ReActAgent  │             │
 │          │←─TOOL_CALL────│←──────────────│←──────────────│─────────────────────────────→│
 │          │               │               │               │               │  LLM决策工具 │
 │          │               │               │               │←─────────────────────────────│
 │          │               │               │               │─ToolCallDispatcher           │
 │          │               │               │               │───────────────→ToolRegistry  │
 │          │               │               │               │               │─业务API      │
 │          │               │               │               │←──工具结果─────│             │
 │          │               │               │               │─────────────────────────────→│
 │          │←─TEXT_MESSAGE─│←──────────────│←──流式 token──│←─────────────────────────────│
 │←─实时展示─│               │               │               │─AgentPool.release()         │
 │          │←─RUN_FINISHED─│               │               │               │             │
```

### 场景三：操作界面时 AI 自动感知（事件驱动，无需用户主动输入）

```
用户       前端          Layer5 API    Layer4 Service  AI 感知层          Layer2 引擎
 │          │               │               │               │               │
 │─上传文档─→│               │               │               │               │
 │          │─POST /docs───→│               │               │               │
 │          │               │               │─DocumentService│              │
 │          │               │               │  存储文件       │               │
 │          │               │               │               │               │
 │          │               │               │  DocumentUploadedEvent（Spring ApplicationEvent）
 │          │               │               │───────────────→│              │
 │          │               │               │               │─KnowledgePipelineService
 │          │               │               │               │  分块→向量化→图谱抽取
 │          │               │               │               │───────────────→PgVector
 │          │               │               │               │───────────────→Neo4j
 │          │               │               │               │               │
 │          │               │               │  入库完成通知   │               │
 │          │←─WebSocket推送─│←──────────────│←──────────────│               │
 │←─"已入库"─│  系统通知      │               │               │               │
 │          │               │               │               │               │
 │─"总结文档"→│               │               │               │               │
 │          │─POST /chat───→│               │               │               │
 │          │               │               │  ← 知识库已有该文档，RAG 直接命中 →
```

### 场景四：多 Assistant 协作（Team 层，AgentScope Pipeline）

```
用户       前端          Layer5 API    Layer4 Service  Team 层            Assistant×N
 │          │               │               │               │               │
 │─"完成需求"→│              │               │               │               │
 │          │─POST /team───→│               │               │               │
 │          │               │               │─TeamService───→│              │
 │          │               │               │               │─TaskDistributor│
 │          │               │               │               │  拆分子任务    │
 │          │               │               │               │─AgentDispatcher→│ product
 │          │               │               │               │─AgentDispatcher→│ architect
 │          │               │               │               │─AgentDispatcher→│ developer
 │          │               │               │               │  （并发执行）   │
 │          │←─进度推送──────│←──────────────│←─ProgressSync─│←──────────────│
 │←─实时进度─│               │               │               │               │
 │          │               │               │               │─ConflictArbitrator
 │          │               │               │               │  结果仲裁聚合  │
 │          │←─最终结果──────│←──────────────│←──────────────│               │
```

### 场景五：业务操作触发 AI 辅助决策（置信度门控）

```
用户       前端          业务系统        AI 决策层       置信度门控         人工审核
 │          │               │               │               │               │
 │─业务操作─→│               │               │               │               │
 │          │─POST /action─→│               │               │               │
 │          │               │─AssistantSvc──→│              │               │
 │          │               │               │─LLM 推理       │               │
 │          │               │               │─置信度评估──────→│             │
 │          │               │               │               │               │
 │          │               │               │  置信度 > 0.9  │               │
 │          │               │               │               │─自动执行，暂存  │
 │          │               │               │               │─异步通知用户    │
 │          │               │               │               │               │
 │          │               │               │  置信度 0.7-0.9│               │
 │          │←─展示执行计划──│←──────────────│←──────────────│               │
 │─确认/拒绝→│               │               │               │               │
 │          │               │               │               │               │
 │          │               │               │  置信度 < 0.7  │               │
 │          │               │               │               │─转人工──────────→│
 │          │               │               │               │  附状态快照+建议  │
 │←─等待审核─│               │               │               │               │─审核决策
```


---

## 高层能力涌现层次

> 每种能力在哪一层涌现，依赖底层哪些功能。

```
能力                    涌现层          依赖的底层功能
─────────────────────────────────────────────────────────────────────────────
任务拆解               Agent（L2）      LLM 推理（Core L0）+ PlanNotebook（AgentScope）
                                        规划模块将模糊目标降维为可验证子任务

状态管理               多层分工
  ├─ 执行期临时状态     Agent（L2）      WorkingMemory（AAF）+ AgentScope Session
  ├─ 会话状态          Assistant（L3）  SessionManager（AAF/AgentScope）
  ├─ 持久记忆          Cognition（L1）  AtomMemoryEngine → Redis + PostgreSQL
  └─ 团队任务状态      Team（L4）       TaskDistributor + ProgressSyncService

工具调用路由           Agent（L2）      ToolRegistry（Spring Bean + MCP 发现）
                                        ToolCallDispatcher（参数校验→执行→结果封装）
                                        Assistant 配置工具白名单，Agent 执行时继承

错误重试               多层分工
  ├─ LLM 调用重试      Core（L0）       ResilientChatService（Resilience4j 熔断/重试）
  ├─ Agent 执行重试    Agent（L2）      AgentCheckpointService（从检查点恢复）
  └─ 工作流节点重试    引擎层（L2）     Flowable 内置重试 + RetryStrategy

成本监控               Core（L0）       TokenMeteringService（每次 LLM 调用后触发）
                                        BudgetEstimator（执行前预估）
                                        BudgetMonitor（执行中实时监控）
                                        → 超预算 → 暂停 + 通知用户

多模态能力             多路径
  ├─ Agent 内多模态    Agent（L2）      AgentScope 原生 ImageBlock/AudioBlock/VideoBlock
  │                                    各厂商 MediaConverter（Anthropic/DashScope/Gemini）
  ├─ 非 Agent 文生图   Core（L0）       ImageGenerationService（Spring AI ImageModel）
  ├─ 图像处理          Core（L0）       ImageProcessService（阿里云 imageenhan SDK）
  └─ 语音 ASR/TTS      Core（L0）       SpeechService（接口，待实现）

渐进决策               多层分工
  ├─ 步骤级决策        Agent（L2）      CognitiveCycleExecutor 每步评估置信度
  ├─ 意图级决策        Assistant（L3）  IntentUnderstandingService 澄清后再执行
  └─ 目标级决策        Team（L4）       目标假设性分解，动态调整
  置信度门控贯穿所有层：>0.9 自动 / 0.7-0.9 确认 / <0.7 转人工

学习反哺               横切（L1-L3）   LearningPipeline（AAF 自研，异步不阻塞执行链路）
  ├─ 轨迹采集          Agent（L2）      TrajectoryCollector（执行日志+工具调用链）
  ├─ 效果评估          Agent（L2）      EffectEvaluator（结果质量评分+用户反馈）
  ├─ 程序化记忆蒸馏    横切             ProceduralDistiller（从轨迹提取"如何做"经验）
  │   流水线：TrajectoryPreprocess → Segmentation → 分流判断
  │           ├─ SuccessExtraction（成功模式：when_to_use + experience）
  │           ├─ FailureExtraction（失败教训：when_to_avoid + lesson）
  │           └─ ComparativeExtraction（同任务不同策略优劣对比）
  │           → MemoryValidation → MemoryDeduplication → MemoryAddition
  ├─ 记忆更新          Cognition（L1）  AtomMemoryEngine 写回（经验→程序化记忆）
  ├─ 知识增长          Cognition（L1）  KnowledgeGrowthService → PgVector + Neo4j
  ├─ 语义漂移检测      横切             工具行为 vs 知识描述不一致 → 暂停+告警
  ├─ 技能生成          Assistant（L3）  SelfImprovementService → SkillDefinition 新增
  └─ 价值观更新建议    横切             ValueUpdateProposer（必须人工审核，不走自动通道）

技能匹配路由           Assistant（L3）  SkillMatchEngine（意图+关键词匹配）
                                        SkillDefinition（触发条件+绑定 Agent+系统 Prompt）
                                        内置技能（自我认知/用户理解/自学习/技能创建）

情感感知               Assistant（L3）  EmotionPerceptionService（语气+节奏分析）
                                        情感记忆 → AtomMemoryEngine（用户私有区，加密）
                                        → 动态调整回应风格/信息密度
```

---

## Agent 池化 × 模型选择 × 积分预算 整体关系

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  一次 Agent 执行的完整生命周期                                               │
│                                                                             │
│  1. 预算检查（执行前）                                                       │
│     BudgetEstimator.estimate(task)                                          │
│       → 预估 Token 数（基于任务复杂度 + 历史数据）                           │
│       → 查 ModelPreference → 确定候选模型                                   │
│       → 查 ai_model.inputPricePerK / outputPricePerK → 预估费用             │
│       → 查用户积分余额（CreditService）                                      │
│       → 余额不足 → 拒绝执行 / 降级到便宜模型                                │
│                                                                             │
│  2. 模型选择（ModelRouter 六层决策）                                         │
│     显式指定 → 编排配置 → AI辅助（任务特征）→ 用户偏好 → 系统默认 → yaml兜底 │
│       ↓                                                                     │
│     modelId → ai_model 表 → apiKey + baseUrl + providerType                │
│                                                                             │
│  3. Agent 借出（AgentPool）                                                  │
│     AgentPool.borrow(agentId)                                               │
│       → 池中有空闲实例 → 直接借出（reset 清空历史）                          │
│       → 池中无空闲 → AgentFactory.create(definition)                        │
│           → AgentScopeExecutor → ReActAgent                                 │
│               → OpenAIChatModel.builder()                                   │
│                   .apiKey(ai_model.apiKey)                                  │
│                   .baseUrl(ai_model.baseUrl)                                │
│                   .modelName(ai_model.modelName)                            │
│                                                                             │
│  4. 执行中计量（TokenMeteringHook）                                          │
│     AgentScope Hook: PostCallEvent                                          │
│       → getChatUsage() → inputTokens + outputTokens                        │
│       → TokenMeteringService.record()                                       │
│           → ai_token_usage 表写入                                           │
│           → BudgetMonitor 实时累计                                          │
│           → 超预算阈值 → 暂停 Agent + 通知用户                              │
│                                                                             │
│  5. 积分扣减（执行后）                                                       │
│     TokenUsageEvent → TokenMeteringService.onTokenUsage()                  │
│       → 查 ai_model.inputPricePerK / outputPricePerK                       │
│       → 计算费用 = (promptTokens/1000 × inputPrice)                        │
│                  + (completionTokens/1000 × outputPrice)                   │
│       → CreditService.deduct(userId, cost, "LLM调用:" + modelId)           │
│           → 积分账户扣减                                                    │
│           → 积分明细记录                                                    │
│                                                                             │
│  6. Agent 归还（AgentPool）                                                  │
│     AgentPool.release(executor)                                             │
│       → executor.reset()（清空 ReActAgent 对话历史）                        │
│       → 放回池中等待下次借用                                                 │
└─────────────────────────────────────────────────────────────────────────────┘

关键关系：
  Agent 池化  ──→  复用 ReActAgent 实例，减少初始化开销
                   每次借出前 reset，对上层透明为无状态
                   池大小 = 并发 Agent 数上限（受预算控制）

  模型选择    ──→  六层决策链最终输出 modelId
                   modelId 决定：apiKey/baseUrl（调哪个厂商）
                              + inputPricePerK/outputPricePerK（计费单价）
                   成本敏感任务 → AiModelSelector 自动选最便宜模型

  积分预算    ──→  执行前：预估费用，余额不足则拒绝/降级
                   执行中：实时累计，超阈值暂停
                   执行后：按实际 Token 数 × 单价扣积分
                   单价来源：ai_model 表（管理员配置，对应厂商实际定价）

三者联动示例：
  用户积分不足 100 积分
    → BudgetEstimator 预估 gpt-4o 需要 200 积分
    → AiModelSelector 切换到 deepseek:chat（预估 50 积分）
    → AgentPool 借出绑定 deepseek:chat 的 Agent 实例
    → 执行完成，实际消耗 45 积分
    → CreditService.deduct(userId, 45)
    → AgentPool.release()，实例归还复用
```


---

## 记忆系统、知识库与混合检索全貌

### 记忆分类与存储

```
记忆类型          生命周期    存储位置                    访问权限          实现
─────────────────────────────────────────────────────────────────────────────
工作记忆          执行期      WorkingMemory（内存）        当前 Agent 私有   AAF WorkingMemoryImpl
短期记忆          会话级      Redis（TTL 自动过期）         用户私有          AtomMemoryEngine
长期记忆          持久        PostgreSQL（ai_long_term_memory）用户私有       AtomMemoryEngine
情景记忆          持久        PostgreSQL（含时间戳）        用户私有          AtomMemoryEngine
程序性记忆        持久        PostgreSQL（ai_procedural_memory）用户/全局     AtomMemoryEngine
情感记忆          持久        PostgreSQL（AES 加密）        用户私有，不外传  AtomMemoryEngine
决策日志          持久        PostgreSQL（审计区）          管理员可审计      AtomMemoryEngine
知识库            持久        PgVector + Neo4j             全局共享          NexusKB 引擎
```

### 记忆管道（MemoryPipeline）

```
Agent 执行前：拉取上下文
─────────────────────────────────────────────────────────────────────────────
用户输入
    │
    ▼
Step 1  查询理解
        IntentUnderstandingService
        → 提取关键实体、时间范围、意图类型

    │
    ▼
Step 2  路由决策（MemoryStrategy 配置）
        MEMORY_ONLY        → 只查记忆
        KNOWLEDGE_ONLY     → 只查知识库
        HYBRID             → 记忆 + 知识库（默认）
        PROCEDURAL_FIRST   → 程序性记忆优先
        FULL               → 全部来源

    │
    ▼
Step 3  并行检索（UnifiedRetrievalService）
        ┌─────────────────────────────────────────────────────┐
        │  向量检索（PgVector）  图谱检索（Neo4j）  关键词（PG FTS）│
        │  短期记忆（Redis）     长期记忆（PG）     程序性记忆（PG）│
        └─────────────────────────────────────────────────────┘
        （并行执行，各自返回候选集）

    │
    ▼
Step 4  RRF 融合（Reciprocal Rank Fusion）
        多路结果按排名倒数加权合并，消除来源偏差

    │
    ▼
Step 5  重排序（可选，RerankService）
        按与查询的语义相关性精排 Top-K

    │
    ▼
Step 5.5  Value 校验过滤（ValueService.filter）
        所有检索结果出库前经过价值观过滤（敏感/违规/越权内容拦截）

    │
    ▼
Step 6  组装 MemoryContext
        → 注入 Prompt（Agent 执行前）
        → 或直接返回给 ResilientChatService（RAG 场景）

─────────────────────────────────────────────────────────────────────────────
Agent 执行后：写回记忆（写管道 MemoryWritePipeline，固定四步不可跳过）
─────────────────────────────────────────────────────────────────────────────
执行结果 + 对话轮次
    │
    ▼
Step 1  提取（MemoryExtractionService，LLM 抽取值得记忆的片段）
    │
    ▼
Step 2  去重（MemoryDeduplicationService，语义相似度比对，合并/更新已有记忆）
    │
    ▼
Step 3  写入（AtomMemoryEngine.write()，双时态索引）
    ├── 短期记忆 → Redis（TTL）
    ├── 长期记忆 → PostgreSQL
    └── 情感记忆 → PostgreSQL（AES 加密）
    │
    ▼
Step 4  遗忘（TimeDecayStrategy，异步，低权重旧记忆降权）

设计约束：步骤固定不可跳过/不可重排，保障数据一致性。
唯一可配置点是提取策略（提取什么），不影响步骤顺序。
```

### 知识库引擎（NexusKB）

```
知识入库流程（KnowledgePipelineService）
─────────────────────────────────────────────────────────────────────────────
文档上传（PDF/Word/MD/HTML/Web）
    │
    ▼
ImporterFactory → DocumentImporter（格式解析）
    │
    ▼
ChunkerFactory → DocumentChunker
    ├── FixedSizeChunker（固定大小）
    ├── RecursiveCharacterChunker（递归字符）
    └── AutoChunkStrategySelector（自动选策略）
    │
    ▼
EmbeddingService（向量化）→ PgVector 写入
    │
    ▼
EntityExtractionService（实体抽取，LLM 驱动）
    → GraphService → Neo4j 写入（实体 + 关系三元组）
    │
    ▼
IncrementalUpdateService（增量更新，避免重复入库）

─────────────────────────────────────────────────────────────────────────────
知识检索流程（HybridSearchService）
─────────────────────────────────────────────────────────────────────────────
查询
    │
    ├── 向量检索（EmbeddingModel → PgVector cosine similarity）
    ├── 图谱检索（Neo4j Cypher，多跳关系推理）
    └── 全文检索（PostgreSQL FTS，关键词匹配）
    │
    ▼
RRF 融合 → Top-K 候选
    │
    ▼
CitationService（溯源，每条结果携带来源文档 + 段落）
    │
    ▼
RagGenerationService（RAG 生成，注入 LLM 上下文）
```

### 混合检索的三层递进策略

```
精确匹配（关键词/实体）
    ↓ 未命中
全文检索（PostgreSQL FTS）
    ↓ 未命中或相关性低
语义检索（PgVector 向量相似度）
    ↓ 需要多跳关系
图谱推理（Neo4j 多跳 Cypher）

最终：RRF 融合多路结果，按综合相关性排序
```

### 记忆系统与其他组件的关系

```
记忆系统（Cognition L1）
    │
    ├── 被 Agent（L2）读写
    │     执行前：MemoryPipeline 拉取 → 注入 Prompt
    │     执行后：MemoryExtractionService 提取 → AtomMemoryEngine 写回
    │
    ├── 被 Assistant（L3）读写
    │     会话开始：加载用户画像 + 情感偏好
    │     会话结束：更新长期记忆 + 情感记忆
    │
    ├── 被 AgentScope 适配（待实现）
    │     AafLongTermMemory implements AgentScope LongTermMemory
    │     STATIC_CONTROL 模式自动触发 retrieve/record
    │
    ├── 被知识库引擎（L2）扩展
    │     KnowledgeGrowthService：执行结果 → 评估 → 写入知识库
    │     形成知识生长闭环
    │
    └── 状态分区隔离
          用户私有区：个人记忆、情感偏好（仅该用户 Agent 可访问）
          全局共享区：领域知识、规范文档（所有 Agent 可访问）
          Agent 工作区：执行期临时数据（任务结束后清理）
          决策审计区：决策日志（管理员可审计，不可删除）
```

### 分层 Agentic 策略（Cognition 层架构决策）

引擎层纯算法，认知层写入/检索时 Agentic，Learning 通道异步 Agentic：

| 层次 | Agentic 程度 | 触发时机 | 说明 |
|------|-------------|---------|------|
| AtomMemoryEngine（引擎层） | ❌ 纯算法 | - | 存储/索引/检索是确定性操作，不调用 LLM |
| Memory 模块写入（认知层） | ✅ Agentic | 被 Agent 调用时 | LLM 判断记忆价值、实体抽取、结构化 |
| Retrieval 融合检索（认知层） | ✅ Agentic | 被 Agent 调用时 | LLM 理解查询意图、路由策略、重排重写 |
| Learning 程序化蒸馏（横切） | ✅✅ Agentic | 异步触发 | 从执行轨迹蒸馏经验 |

**关键约束**：Cognition 仍然是**被动响应**的——"被动响应"≠"不用 LLM"，而是"不主动触发，但被调用时可以用 Agent 级能力处理"。Agentic 的触发方在上层（Agent/Assistant）或横切通道（Learning），不在 Cognition 内部。

各环节 LLM 使用决策：

```
❌ 不用 LLM：向量存储/索引、时序索引/双时态查询、Bundle Search 图遍历
✅ 用 LLM：记忆价值判断、实体/关系抽取、查询意图理解、检索结果重排/重写、
           程序化记忆蒸馏、记忆去重/合并
```

---

## 元引擎执行机制

> 元引擎是 Layer 2 的编排中枢，不是五层中的某一层，而是跨层的编排基础设施。

### 两种共存模式

元引擎支持两种模式长期共存，不互斥：

| 维度 | 传统架构模式（v1.0） | 文档驱动模式（v2.0） |
|---|---|---|
| 交互入口 | 结构化视图（AG-UI + REST） | 对话式 + DSL 指令 |
| 界面生成 | EntityDef 配置 → 自动派生视图 | DSL 动态组装语义组件 |
| 业务定义 | Java 代码 + 配置文件 | 文档即程序（DSL 直接执行） |
| 元引擎角色 | 调度器 + 状态管理 + 置信度门控 | 全套核心基础设施 + DSL 引擎 |
| AI 介入点 | 对话辅助、代码生成建议 | 意图→DSL→执行全链路 |

### DSL 统一中间表示

DSL 是元引擎的核心语言，贯穿开发时和运行时，具备四大设计维度：

- **三重身份**：规范文档（人读）/ 生成目标（AI 产出）/ 执行程序（系统跑）
- **多范式**：声明式 / 命令式 / 函数式 / 自然语言混合表达（解决"怎么写"）
- **分层**：L1 宽松给用户 / L2 结构化给 AI / L3 严格给系统执行（解决精确度）
- **分域**：dev（开发时）/ runtime（运行时）/ doc（文档）区分职责归属和生命周期

### 元引擎在五层中的定位

```
Layer 4  Team        ← 元引擎提供项目级任务分发和冲突仲裁能力
Layer 3  Assistant   ← 对话式交互运行在此层，元引擎为其提供执行上下文
Layer 2  Agent       ← 元引擎调度无状态 Agent，管理其生命周期
Layer 1  Cognition   ← 元引擎的状态管理器与此层对齐，不跨层直接读写
Layer 0  Core        ← 元引擎通过 Agent 间接调用，不直接操作 LLM
```

### 元引擎 = 调度机制 + 专项引擎编排

```
元引擎
  ├── 调度机制（大脑：决定怎么跑）
  │     执行调度器（DSL 路由 / Agent 启用判断 / 生命周期管理）
  │     状态管理器（四层状态：Session / Workspace / System / Metadata）
  │     置信度门控器（自动 / 确认 / 人工 三档）
  │     元数据管理器（四类元数据 / 语义漂移检测 / 规范变更触发链）
  │
  └── 专项引擎编排（手脚：负责执行）
        工作流引擎（Flowable，固定流程骨架）
        智能体编排（AgentScope Pipeline/Supervisor/Subagent）
        知识记忆集成（NexusKB + AtomMemory）
        预算控制（BudgetEstimator + BudgetMonitor）
        沙箱执行（GraalVM Polyglot）
```

### 执行调度器内部处理步骤

```
输入（DSL / 意图 / API 调用 / 事件触发）
  ↓ filter    权限校验、预算检查，不满足则拒绝
  ↓ transform DSL 解析、意图结构化、L1→L2→L3 转化
  ↓ route     域识别（dev/runtime/doc）+ 任务类型识别，分发到对应引擎
  ↓ parallel  多子任务并发执行（Virtual Threads + StructuredTaskScope）
  ↓ reduce    汇总子任务结果，写入会话状态
输出（执行结果 → 对话区 / 工作区）
```

**Agent 启用判断**（route 步骤内）：

| 维度 | 判断 | 结论 |
|------|------|------|
| 任务复杂度 | 低 | 走工作流，不启用 Agent |
| 任务价值 | 低（< 阈值） | 走工作流，不启用 Agent |
| 所有步骤可执行 | 否 | 缩小范围或加人工节点 |
| 错误成本 | 高 | 启用 Agent 但加人工审核节点 |

### 状态管理器四层状态

| 状态层 | 生命周期 | 存储 | 说明 |
|--------|---------|------|------|
| 会话状态（Session） | 会话结束销毁 | Redis（TTL） | 当前对话上下文、执行中任务、暂存结果 |
| 工作区状态（Workspace） | 持久，多用户共享 | PostgreSQL + Redis | 文档/代码/工作流（OT/CRDT 合并）、在线用户 |
| 系统状态（System） | 持久，全局共享 | PostgreSQL | DSL 版本库、规范文档、知识库索引 |
| 元数据状态（Metadata） | 持久，规范驱动更新 | PostgreSQL + Redis | 模块/插件/工具/UI 组件元数据 |

**渐进提交原则**：执行结果先写入会话状态（暂存），用户确认后提升到工作区/系统状态，未确认结果会话结束自动销毁。

### 置信度门控器（二维模型）

```
每个执行步骤携带两个评分：置信度 + 可验证性

置信度维度：
  > 0.9   → 自动执行，结果暂存，异步通知
  0.7–0.9 → 生成执行计划，等待用户确认
  < 0.7   → 暂停，说明原因，转人工处理

可验证性维度（与置信度正交）：
  可验证 + 高置信   → 自动执行 + 自动验证（系统闭环）
  可验证 + 低置信   → 执行 + 自动验证 + 失败自动回滚（安全试错）
  不可验证 + 高置信 → 执行 + 决策日志 + 异步人工审查
  不可验证 + 低置信 → 暂停 + 转人工决策（最保守）
```

**不可逆操作强制人工确认**（无论置信度）：删除、发布、提交、权限变更。

**防退化约束**：人类未响应不自动超时执行；不允许静默降低阈值；主导权切换必须记录审计日志。

### 元数据管理器

四类元数据统一管理，规范变更自动触发同步：

```
规范变更（docs/ 写入）
  ↓ 元引擎检测
元数据管理器更新对应元数据
  ├─ 模块元数据：边界、依赖、能力接口
  ├─ 插件元数据：注册点、契约、版本约束
  ├─ 工具元数据：参数 schema、权限要求、调用方式
  └─ UI 组件元数据：类型、行为、约束、适用角色
  ↓ 同步刷新
工作区界面 + 对话区可用能力 + Agent 可调用工具
```

**语义漂移检测**：定期对比工具实际行为与文档描述的一致性，发现不一致则暂停该工具并告警，不依赖开发者自觉。

**知识能力绑定**：每个工具注册时必须关联使用规范文档、示例文档、领域知识文档；知识库更新时，关联工具的调用规范同步校验；Agent 调用工具后，执行结果自动归档到对应知识库条目，形成「工具执行 → 知识生长」正向闭环。

### 元引擎降级策略

| 故障场景 | 降级行为 |
|---------|---------|
| AI 服务不可用 | 降级到规则引擎（预定义工作流） |
| Agent 超时 | 切换简化流程（直接 LLM 调用） |
| 沙箱执行失败 | 暂停并转人工处理 |
| 所有 AI 不可用 | 纯工作流模式，保留核心业务功能 |

降级不静默发生，对话区明确告知用户当前处于降级模式及原因。

---

## 自进化机制

> 自进化是元引擎区别于传统引擎的核心特征，支持两类对象：引擎自身和业务系统。

### 两类自进化对比

| | 引擎自进化 | 业务系统自进化 |
|---|---|---|
| 触发方 | 性能异常 / 规范冲突 / 重复模式 | 用户负反馈 / 重复需求 / 行为偏差 |
| 影响范围 | 所有用户 | 当前业务 |
| 审核级别 | 全程强制人工 | 按置信度分级 |
| 产物 | 规范更新 + 重生成代码 | DSL 更新 / 重生成代码 |
| 执行者 | `aaf-auto-dev` | `aaf-auto-dev` |

### 引擎自进化闭环

```
引擎运行数据（性能指标、错误日志、用户反馈）
  ↓ 效果评估（自动分析 + 人工标注）
模式识别（哪些模块性能差、哪些规范需要更新）
  ↓ AI 生成优化方案 + 影响范围分析
  ↓ 强制人工审核（无论置信度）
规范更新（docs/）→ 人工确认规范变更
  ↓ auto-dev 重生成受影响模块代码
  ↓ 沙箱编译验证 + 自动化测试
人工审核代码变更 → 确认后热部署
  ↓ 新一轮数据采集
```

**规范一致性扫描**：元引擎定期扫描 `docs/` 与 `aaf-modules/` 的语义一致性，发现偏离时触发告警并生成修正任务，以文档为准，不依赖人工检查。

### 业务系统自进化闭环

```
用户行为数据（操作日志、满意度、修正操作）
  ↓ 效果评估 → 模式识别 → 写入历史决策记录
AI 生成优化方案（基于当前系统状态 + 历史决策）
  ↓ 审核分级
  ├── 自动执行（>0.9，低风险）  → DSL 直接更新，立即生效
  ├── 用户确认（0.7-0.9，中风险）→ 对话区展示变更方案，用户确认后生效
  └── 人工审核（<0.7 或高风险） → AI 生成代码 → 沙箱验证 → 人工确认 → 热加载
  ↓ 新一轮数据采集
```

### 运行时沙箱

所有不可信代码在隔离沙箱中执行：

| 隔离对象 | 说明 |
|---------|------|
| 用户自定义工具 / MCP 插件 | 防止影响引擎核心 |
| 工作流自定义脚本节点 | 防止越权访问 |
| Agent 工具调用结果处理逻辑 | 防止注入攻击 |
| auto-dev 生成的代码 | 沙箱验证后再热加载 |

**首选**：GraalVM Polyglot Sandbox（`allowAllAccess(false)` / `allowIO(IOAccess.NONE)` / `allowNet(false)` / `statementLimit(N)`）

**降级**：子进程隔离 + 超时控制（无 GraalVM 时）

---

## 用户感知与个性化

> 记忆、知识库、上下文、状态管理如何协同实现用户感知与个性化。

### 四层数据职责分工

| 层 | 生命周期 | 存储 | 说明 |
|----|---------|------|------|
| 上下文（Context） | 请求级，LLM 窗口内 | 内存 | 当前消息 + 从记忆/知识检索到的相关片段，Token 预算控制 |
| 状态（State） | 会话级/工作区级，跨请求 | Redis + PostgreSQL | 当前任务列表、暂存结果、工作区文档（OT/CRDT 合并） |
| 记忆（Memory） | 持久级，跨会话，用户私有 | PostgreSQL + Redis | 短期/长期/情景/情感/程序化/决策日志 |
| 知识库（Knowledge） | 持久级，全局共享 | PgVector + Neo4j | 结构化文档 + 知识图谱，RAG 检索 |

**记忆 vs 知识库核心区别**：记忆是用户私有的交互产生的个人经历（自动写入），知识库是全局共享的领域知识（人工上传/维护）。

### 上下文组装优先级

| 优先级 | 内容 | 说明 |
|--------|------|------|
| P0（必选） | 系统 Prompt + 当前消息 | 不可压缩 |
| P1 | 工作记忆（当前任务焦点） | Agent 执行期临时状态 |
| P2 | 短期记忆（近期会话摘要） | 最近 N 轮压缩摘要 |
| P3 | 知识库检索结果 | 按相关度截取 Top-K |
| P4 | 用户画像摘要 | 长期偏好的压缩表示 |
| P5 | 情景记忆片段 | 相关历史场景 |

超出 Token 预算时从 P5 开始丢弃。使用 `agentscope-extensions-autocontext-memory`（`AutoContextMemory`）自动按优先级截断。

### 用户画像系统

```
UserProfileService（规划中）
  ├── 画像存储（PostgreSQL，结构化）
  │     偏好维度：回应风格、信息密度、领域专业度
  │     行为维度：常用功能、活跃时段、交互模式
  │     情感维度：情绪偏好、高压场景模式（来自情感记忆）
  │
  ├── 画像更新
  │     实时更新：每次对话结束后，写管道触发异步更新
  │     定期更新：UserUnderstandingSkill（内置技能）定期扫描近期记忆 → LLM 提炼 → 更新画像
  │
  └── 画像消费
        Assistant 会话开始时读取 → 注入 System Prompt
        上下文组装时作为 P4（用户画像摘要）
```

> 待实现：`UserProfileService`、`UserUnderstandingSkill`，当前通过 `UnifiedRetrievalService` 检索长期记忆临时替代。

### 个性化实现路径

| 个性化维度 | 数据来源 | 实现机制 |
|-----------|---------|---------|
| 回应风格（正式/轻松/简洁） | 情感记忆 + 用户画像 | Assistant 读取后注入 System Prompt |
| 信息密度（详细/摘要） | 长期记忆（用户偏好） | 上下文组装时调整知识片段数量 |
| 主动提醒（待办/截止日期） | 情景记忆 + 状态管理 | Assistant 会话开始时检查 |
| 领域专业度（新手/专家） | 用户画像 | Prompt 模板选择不同难度版本 |
| 历史延续（记住上次说的） | 短期/长期记忆 | 上下文组装时注入相关历史 |

---

## 专项引擎能力全景

> 各专项引擎在执行流中的定位与能力边界。

### 编排引擎与调度引擎的分工

```
编排引擎（单次请求执行路径）        调度引擎（异步任务队列）
────────────────────────────────────────────────────────
动态路由，按 DSL 决定               任务入队/出队，优先级三档
引擎协同、置信度门控                定时/周期触发（Cron）
响应式执行管道                      重试策略（指数退避）+ 死信队列
                                    分布式锁防重复执行
```

工作流引擎和调度引擎都是被编排引擎驱动的专项引擎，不直接调度 Agent。

### 消息引擎

多渠道消息通知横切能力，业务层只调用消息接口，不感知渠道细节：

| 渠道 | 场景 | 实现 |
|------|------|------|
| 站内消息 | 系统通知、任务提醒 | Redis Pub/Sub + SSE 推送 |
| 邮件 | 注册验证、重要通知 | Spring Mail（SMTP） |
| 短信 | 验证码、紧急告警 | 阿里云 / 腾讯云 SMS |
| 微信/钉钉/飞书 | 企业内部通知 | Webhook / 开放 API |
| WebSocket/SSE | 实时流式输出、进度推送 | Spring WebFlux SSE |

### 监控引擎

纯观测层，只采集、分析、告警，不干预执行（执行控制由元引擎负责）：

```
LLM 调用链路追踪（每次调用采集）：
  输入 Prompt / 输出结果 / 工具调用 / Token 消耗 / 耗时 / 模型信息 / 关联上下文

Agent 执行轨迹（Span 树）：
  AgentExecution (root)
    ├── Perception → Planning（SubTask × N）→ Execution（ToolCall × N + LlmCall）→ Evaluation

可重复执行：执行轨迹记录完整入参，支持一键重新执行或修改参数后重新执行

技术实现：OpenTelemetry + AgentScope Observability Studio + Prometheus/Grafana
```

### 预算控制引擎

```
执行前预估（BudgetEstimator）
  → 预估 Token/时间/工具调用次数/费用
  → 保守估算（上限 × 1.2），基于历史数据持续校准
  → 简单任务（置信度 > 0.9）可跳过预估

执行中监控（BudgetMonitor）
  消耗达到 70% → 告警提示（继续执行）
  消耗达到 90% → 警告，询问是否扩大预算
  消耗超出预算 → 暂停执行，保存检查点，等待用户决策

预算配置三层级（下层不能超过上层）：
  系统级（管理员）→ Assistant 级（配置）→ 任务级（用户/Agent）
```

### 积分与结算引擎

```
积分引擎（内部虚拟货币）            结算引擎（真实资金）
────────────────────────────────────────────────────────
虚拟积分的赚与花                    真实资金的进与出
不涉及真实资金                      对接支付接口（微信/支付宝/Stripe）
规则由 DSL 定义，热生效              流程由工作流引擎驱动
实时记账                            异步结算 + 对账

积分来源：贡献行为 / 生态贡献 / 游戏化 / 社交裂变 / 订单返积分 / 充值兑换
积分消费：API 调用 / 功能解锁 / 市场购买 / 治理投票 / 提现申请

两引擎通过业务服务层协作，不直接互调：
  充值 → 结算引擎.charge() → 回调积分引擎.earn()
  提现 → 积分引擎.freeze() → 结算引擎.withdraw() → 积分引擎.spend()
```

### 语义计算引擎（SemanticCalc）

横切支撑多个认知与业务组件的通用语义能力，引擎只关心"怎么算"：

| 能力 | 主要使用方 |
|------|-----------|
| Embedding 生成 | Memory 索引、Knowledge ECL、Retrieval 查询 |
| 语义相似度 | Retrieval 排序、Memory 去重 |
| 实体抽取（NER） | Knowledge Cognify、Agent 感知 |
| 关系抽取 | Knowledge 图谱构建 |
| 语义分类/聚类/去重 | Memory 归档、Knowledge 分类 |
| 语义漂移检测 | Learning 反哺、元数据管理器 |
| 意图识别 | Agent 感知、Assistant 路由 |
| 摘要生成 | 文档服务、对话历史压缩 |
| Reranker 重排 | Retrieval 结果重排 |

### 数据处理分析引擎（DataProcess）

结构化/半结构化数据的批/流处理与统计分析，与 SemanticCalc（语义计算）互补：

| 维度 | SemanticCalcEngine | DataProcessEngine |
|------|-------------------|-------------------|
| 处理对象 | 自然语言/非结构化语义 | 结构化/半结构化数据 |
| 核心输入 | 文本 | 表格/JSON/时序/事件流 |
| 核心输出 | 向量/实体/意图 | 统计/聚合/转换结果 |

| 能力 | 主要使用方 |
|------|-----------|
| 批处理（读取/清洗/转换/聚合/关联/导出） | 报表服务、仪表盘、BI |
| 流处理（事件流/窗口聚合/实时告警） | 日志分析、行为分析、指标计算 |
| 多维统计（分组/透视/累计） | 用户行为分析、Learning 效果评估 |
| 数据查询与转换 | Agent 数据工具、工作流节点 |

长期规划：v2.0 迁移到 actormesh（C++ 级高并发性能）。

### 文档引擎

一切皆文档——文档是结构化的知识容器，有语义、有关系、有历史、有空间位置：

**七类文档**：

| 类型 | 所属域 | 生产者 | 元引擎如何使用 |
|------|--------|--------|--------------|
| 规范文档 | dev | AI / 用户 | 触发元数据更新和代码重生成 |
| DSL 文档 | dev / runtime | AI / 用户 | 直接解析执行 |
| 组件文档 | doc | 系统 | 前端引擎加载渲染 |
| 插件文档 | doc | 系统 | 前端引擎动态加载 |
| 业务文档 | doc | 用户 | 工作区渲染展示 |
| 执行文档 | runtime | Agent | 自进化评估输入 |
| 日志文档 | runtime | 系统 | 自进化评估输入 |

**版本控制**：协同层（Yjs CRDT，实时多人编辑）+ 版本层（快照，PostgreSQL）+ 归档层（DAG 结构，版本关系）。

### Prompt 引擎

```
Layer 4  提示词管理服务（CRUD / 评估报告 / 版本对比）
              ↓
Layer 2  Prompt 引擎（提示词库管理 / 链式组装 / Few-shot 管理 / 评估优化）
              ↓ 被调用
Layer 3  Core / Agent / Assistant（构建 LLM 输入）
```

`intelligent/core/prompt/PromptTemplateService` 现有实现迁移至本引擎，`core/prompt/` 保留为调用门面。

### 外部数据源引擎

两种对接场景：

| 场景 | 说明 | 适用 |
|------|------|------|
| ETL 导入 | 定时/事件触发从外部拉数据，写入 AAF PostgreSQL | CRM 同步客户、ERP 同步订单 |
| 联邦查询 | 不落库，运行时实时查外部数据源，结果统一格式返回 | 报表聚合多源、大屏实时展示 |

统一抽象：`DataSourceAdapter`（JdbcAdapter / HttpAdapter / FileAdapter）→ `DataSet`（headers + rows + pagination + metadata）。DSL 驱动，参数化查询防注入，连接凭证加密存储。

### 物理时空引擎（PhysicsSpaceTime）

为虚拟空间、知识图谱布局、记忆时间线、多 Agent 空间化协作提供统一的时空坐标与物理规则：

```
核心概念：
  World（坐标系 + 维度 + 时间轴 + 物理规则配置）
  Matter（文档/知识节点/记忆原子/Agent 实例，有坐标/质量/语义向量/生命周期）

物理规则：
  语义引力  → 语义相似度产生"引力"，相近物质聚合（知识图谱聚类）
  时间流    → 物质随时间演化（新鲜度衰减，过时知识自动下沉）
  碰撞      → 相似内容合并提示、文档冲突检测
  排斥      → 冲突/矛盾内容相互排斥

v0.x：Java 基础实现（JTS + KD-Tree + Virtual Threads）
v2.0：迁移到 actormesh（C++ 原生，零 GC，SIMD 向量化）
```

### 推荐引擎

| 场景 | 策略 |
|------|------|
| 市场推荐（Agent/工具/知识库） | 协同过滤 + 语义相似 + 热度排序 |
| 技能推荐（当前对话上下文） | 上下文感知匹配 |
| 工具推荐（Agent 执行时） | 任务类型匹配 |

依赖语义计算引擎做向量相似度计算，依赖积分引擎获取资产质量评分。

### 插件引擎

生态市场的运行时底座：

| 插件类型 | 注册目标 |
|---------|---------|
| Agent 插件 | 可复用的 Agent 定义 |
| 工具插件 | 注册到工具引擎 |
| 技能插件 | 注册到技能引擎 |
| 知识库插件 | 预构建的领域知识库 |
| 提示词插件 | 注册到提示词库 |
| UI 组件插件 | 注册到组件注册表 |

核心能力：动态加载（热插拔）、沙箱隔离执行、语义化版本管理、权限声明与授权。

### 搜索引擎

跨资源统一搜索入口，屏蔽各引擎检索差异：

```
一个查询 → 并行检索多个资源（知识库/文档/Agent/工具/技能/用户/市场资产）
         → 权限过滤（不泄露无权访问的内容）
         → 跨引擎结果融合排序（RRF）
         → 搜索建议（实时补全）
```

### 语义组件引擎（Sense-UI）

后端输出 DSL，引擎动态组装组件树，前端渲染，同一套组件多端适配：

```
业务意图
  → DSL（描述"展示什么"，不描述"怎么展示"）
    → 语义组件引擎（组件匹配 + 内容注入 + 布局组装）
      → 组件树
        → 多端渲染（Web / 移动 / 微信 / CLI）
```

组件类型：展示组件 / 交互组件 / 容器组件 / 智能组件（内嵌 AI 能力）/ 执行组件（触发 Agent/工作流）。

---

## 安全执行链路

> 安全是贯穿全栈的横切关注点，不是某一层的职责。

### 安全分层模型

```
传输层安全（TLS 1.3，内部服务 mTLS）
  ↓
应用层安全（认证 / 授权 / 输入校验）
  JWT 验签 → RBAC（功能权限）→ ReBAC（资源关系权限）→ 记录规则（行级过滤）→ ABAC（动态条件）
  ↓
数据层安全（加密 / 脱敏 / 审计）
  列加密（JPA AttributeConverter）/ 密钥管理（KMS/Vault）/ 数据脱敏（@Sensitive）
  ↓
AI 安全（Agent / LLM / 沙箱）
  Prompt 注入防御 / 工具白名单 / 输出溯源 / 沙箱隔离
  ↓
基础设施安全（网络隔离 / 密钥轮换）
```

### 元引擎安全执行链路

```
输入净化
  → 动态 UUID 分隔符包裹用户输入（Prompt 注入防御）
  → 意图一致性校验
  ↓
DSL 解析
  → DSL 白名单校验（不允许任意代码注入）
  ↓
执行前检查
  → 工具白名单（ToolRegistry 强制校验）
  → 记忆隔离（用户私有区 / 全局共享区 / 决策审计区）
  → 知识分级（按权限过滤检索结果）
  → 模型配额（BudgetEstimator 预检）
  ↓
执行中
  → 沙箱隔离（不可信代码 GraalVM Polyglot）
  → 输出溯源（TraceId = agentId + modelId + toolChain + 知识来源）
  ↓
输出审查
  → 敏感词过滤（内容安全 API）
  → 脱敏（@Sensitive 注解，响应/日志中敏感字段自动脱敏）
  → 审计归档（决策日志，管理员可审计，不可删除）
```

### Actor 统一权限抽象

权限系统的"主体"不是 User，而是 **Actor**——Human 和 AI 的多态抽象：

- `UserPrincipal`（Human）和 `AgentPrincipal`（AI）都实现 `ActorAware` 接口
- 业务代码通过 `ActorContext.current()` 获取当前 Actor，不关心底层认证方式
- 权限规则对 Actor 统一生效，不为 Agent 单独建一套权限体系
- Agent 运行时请求额外权限 → WebSocket 推送 → 会话级临时授权

**数据库约定**：所有需要记录操作者的表统一使用 `actor_type + actor_id` 二元组（不建独立 `t_actor` 表）：

```sql
actor_type  VARCHAR(16) NOT NULL,  -- 'HUMAN' | 'AI'
actor_id    BIGINT      NOT NULL   -- 指向 t_user 或 t_agent 的 ID
```

列名约定：`creator_type/creator_id`、`modifier_type/modifier_id`、`executor_type/executor_id`、`approver_type/approver_id`、`assignee_type/assignee_id`。

**审计事件**：所有状态变更事件携带 Actor 信息：

```java
public record AuditEvent(
    Actor actor,           // 操作者（type + id + name）
    String action,         // CREATE / UPDATE / DELETE / APPROVE / REJECT
    String entityType,     // 操作的实体类型
    Long entityId,         // 操作的实体 ID
    Instant timestamp,
    Map<String, Object> changes  // 变更字段快照
) {}
```

### 四层权限模型

| 层 | 职责 | 技术实现 | 存储 |
|----|------|----------|------|
| RBAC | 功能权限（系统角色） | Spring Security 原生 | PostgreSQL |
| ReBAC | 资源关系权限（owner/viewer/editor） | 自定义 PermissionEvaluator + 图查询 | Neo4j |
| 记录规则 | 行级/字段级数据过滤 | JPA 拦截器（@DataScope） | PostgreSQL |
| ABAC | 动态条件策略（Agent 低置信度需人工确认） | 轻量策略引擎（内置） | 配置/代码 |

---

## 人类计算支撑

> 通过设计任务和激励机制，让用户在完成自身目标的过程中，宏观上解决高价值问题。

### 贡献行为类型

| 类型 | 场景 |
|------|------|
| 知识贡献 | 知识标注、规范评审、专家知识输入、知识拆解 |
| 计算贡献 | 向量计算、小模型训练、知识索引分片 |
| 协作贡献 | 众包任务完成、反馈与评分、游戏化任务 |

### 元引擎职责边界

元引擎负责触发和编排，不实现业务逻辑：贡献行为事件采集 → 调用积分引擎 → 触发结算引擎 → 众包任务分发 → 知识质量评估后写入知识库。

---

## 开发时能力（自开发）

> AAF 用自己开发自己——元引擎不只是构建业务系统的工具，它本身也是被自己驱动开发和演化的系统。

### 核心引擎开发流程

```
用户描述需求（对话）
  ↓ AI 生成用户故事 → 规范文档（docs/requirements/）
  ↓ AI 生成设计方案 → 设计文档（docs/design/）
  ↓ AI 生成 DSL 定义（dev/schema + dev/api + dev/flow）
  ↓ 元引擎 + auto-dev 生成 Java 代码（FreeMarker 模板 + JavaParser 精确修改）
  ↓ 沙箱编译验证 → 热部署（URLClassLoader，无需重启）
  ↓ 运行时生效，数据采集 → 自进化评估（下一轮迭代输入）
```

### 业务系统四层无代码运行时

| 层次 | 开发方式 | 场景占比 | 变更生效 |
|------|---------|---------|---------|
| 实体运行时（dev/schema DSL → 动态建表 + 自动 CRUD API） | 无代码 | ~50% | 立即 |
| 工作流运行时（dev/flow DSL → Flowable 解释执行） | 无代码 | ~20% | 立即 |
| 权限运行时（runtime/policy DSL → 动态鉴权） | 无代码 | ~10% | 立即 |
| 自定义逻辑挂载（AI 生成代码 → 沙箱验证 → URLClassLoader 热加载） | AI 生成代码 | ~20% | 热部署 |

### 复杂性封装策略

默认隐藏，按需展开，查看与操作权限分离：

| 展开层次 | 权限要求 | 可做什么 |
|---------|---------|---------|
| 默认态 | 无 | 对话框 + 工作区，复杂性完全隐藏 |
| 展开态 | 无（只读） | 查看 DSL / 执行计划 / 工作流 / 推理过程 / 积分明细 |
| 开发态 | 开发者权限 | 编辑业务 DSL / 工作流 / 自定义工具和插件 |
| 框架维护态 | 框架维护者权限 | 修改引擎配置 / 路由规则 / 系统级规范 |

**五度空间约束**：每个子模块复杂度不超过 5，超出则强制递归分解。后端核心和引擎编排层均已达上限 5，不允许再添加新模块而不分解。
