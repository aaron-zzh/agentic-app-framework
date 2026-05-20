---
level: Theory
layer: Paradigm
purpose: AAF 整体架构细化——组件关系图、代码架构全景、模块边界
status: published
version: 1.0.0
date: 2026-05-20
author: AaronZZH
gains:
  - 理解五层架构各组件之间的调用关系
  - 掌握 Maven 模块与包结构的对应关系
  - 快速定位新代码应放在哪个模块/包
---

# AAF 架构细化

> 概览见 [architecture.md](../architecture.md)，本文档聚焦三件事：**组件关系细化**、**代码架构全景**、**模块边界约束**。

## 组件关系全景图

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│  Layer 5  对话与交互层                                                         │
│                                                                               │
│   安全网关（认证 · 鉴权 · 限流 · 路由）                                         │
│       │           │           │           │                                   │
│   REST API     GraphQL    WebSocket    CLI / DSL 指令                         │
│                                                                               │
│   AG-UI 协议（SSE 事件流：RUN_STARTED / TEXT_MESSAGE / TOOL_CALL / RUN_FINISHED）│
└───────┼───────────┼───────────┼───────────┼──────────────────────────────────┘
        │           │           │           │  所有请求经安全网关
        ▼           ▼           ▼           ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  Layer 4  服务层                                                               │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  框架基础服务（aaf-api/module/system）                                 │   │
│  │  用户·认证·权限·组织·工作区   文件存储   消息通知   外部集成              │   │
│  │  日志审计   任务调度   仪表盘   工作流   元数据实体   用户画像            │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  引擎管理服务（aaf-api/module/，各引擎的配置/监控/管理入口）             │   │
│  │  Agent管理   助手管理   知识库管理   工作流管理   技能管理               │   │
│  │  编排服务    模型管理   预算管理    积分管理    结算管理    监控看板       │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  业务服务（用户在 AAF 上构建的具体业务，可由元引擎自动生成）              │   │
│  │  文档模块   聊天模块   自定义业务模块 …                                 │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────────────┐
│  Layer 3  智能层                                                               │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  Team  多 Assistant 协作                                              │   │
│  │  团队编排 → 任务分发 → Agent 派发   冲突仲裁                           │   │
│  │  A2A 协议（跨系统 Agent 互联：Task / Artifact / Message）             │   │
│  └────────────────────────────┬─────────────────────────────────────────┘   │
│                                ▼                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  Assistant  会话 · 情感 · 调度                                        │   │
│  │  意图理解   情感感知   会话管理   技能匹配   学习反馈   结果聚合        │   │
│  └──────────────────────────────┬───────────────────────────────────────┘   │
│                                  │ 派发任务                                   │
│                                  ▼                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  Agent  无状态任务执行                                                │   │
│  │  认知循环（感知→规划→执行→评估）                                      │   │
│  │  工作记忆（执行期临时）  注意力预算   断点续跑   Agent 池   沙箱隔离   │   │
│  │      │ 工具调用    │ 技能匹配    │ LLM 推理                           │   │
│  └──────┼─────────────┼─────────────┼──────────────────────────────────┘   │
│         │ 执行前拉取/执行后写回（◄►）  Assistant 读取用户画像/长期记忆（◄►）  │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  Cognition  认知基础层（横向共享底座，Agent + Assistant 均依赖）       │   │
│  │  记忆：短期/长期/情景/情感/决策日志，时序+语义双索引，用户/Agent 私有  │   │
│  │  知识：领域知识，全局共享，向量+图谱检索                               │   │
│  │  价值观：团队级伦理约束，全局一致                                     │   │
│  │  记忆管道：可编排的记忆处理流水线（提取→去重→写回→遗忘），支持自定义步骤│   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  Core  LLM 基础设施（被上层依赖，不依赖上层）                          │   │
│  │  LLM 调用   模型路由   Token 计量   弹性重试/熔断                      │   │
│  │  Prompt 引擎（调用 engine/prompt）                                    │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────────┘
         │工具调用  │技能匹配  │知识检索  │记忆读写  │工作流  │监控/权限/消息…
         ▼          ▼          ▼          ▼          ▼          ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  Layer 2  引擎层                                                             │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  元引擎（编排各专项引擎，提供统一执行上下文）                            │   │
│  │  执行调度器   状态管理器   置信度门控器   元数据管理器   上下文管理器     │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  知识库引擎   记忆引擎   工具引擎   技能引擎   工作流引擎   文档引擎           │
│  编排引擎     调度引擎   消息引擎   监控引擎   权限引擎     预算控制           │
│  积分引擎     结算引擎   外部数据源  自进化引擎  Prompt引擎                   │
└──────────────────────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  Layer 1  基础设施层                                                           │
│  PostgreSQL + PgVector   Redis（缓存/会话/队列）   Neo4j（知识图谱）            │
│  JVM Sandbox（脚本隔离）  消息队列                                              │
└──────────────────────────────────────────────────────────────────────────────┘

图例：→ 调用方向   ▼ 向下依赖（禁止反向）
```

## Maven 模块与架构层对应

```text
aaf-dependencies          → 无代码，纯 BOM（版本管理）
aaf-common                → 横切工具（常量/异常/工具类/通用模型）
aaf-framework             → Layer 2 引擎层 + Layer 3 智能层
aaf-auto-dev              → 自进化执行侧（代码生成/热部署）
aaf-api                   → Layer 4 服务层 + Layer 5 接口层（启动入口）
```

依赖方向（单向，禁止反向）：

```text
aaf-api
  └─ aaf-framework ←── aaf-auto-dev（可选）
       └─ aaf-common
            └─ aaf-dependencies（BOM）
```

## 代码架构全景

### aaf-framework 包结构

```text
com.xuejiai.aaf.framework/
│
├── intelligent/                    ← Layer 3 智能层
│   ├── core/                       ← LLM 基础设施（被 agent/assistant 依赖）
│   │   ├── llm/                    LlmClient（Spring AI ChatClient 封装）
│   │   ├── model/                  AiModel、ModelManagementService
│   │   ├── token/                  TokenMeteringService、TokenMeteringHook
│   │   ├── prompt/                 PromptTemplateService
│   │   ├── memory/                 MemoryPipeline、MemoryStrategy（接口）
│   │   ├── skill/                  SkillDef、SkillProvider（接口）
│   │   ├── agent/                  AgentExecutor（接口）
│   │   ├── assistant/              AssistantExecutor（接口）
│   │   └── function/               FunctionDefinition
│   │
│   ├── ai/                         ← AI 基础设施（弹性调用/路由）
│   │   ├── ResilientChatService    重试/降级/熔断
│   │   ├── ModelRouter             模型路由策略
│   │   └── ChatContextBuilder      上下文组装
│   │
│   ├── cognition/                  ← Cognition 层（横向共享底座）
│   │   ├── memory/                 短期/长期/图谱/程序性记忆
│   │   │   ├── ShortTermMemoryService
│   │   │   ├── LongTermMemoryService
│   │   │   ├── GraphMemoryService
│   │   │   ├── ProceduralMemoryService
│   │   │   ├── MemoryExtractionService
│   │   │   ├── MemoryRetrievalService
│   │   │   └── MemoryDeduplicationService
│   │   ├── retrieval/              UnifiedRetrievalService（统一检索入口）
│   │   └── pipeline/               MemoryPipelineFactory、DefaultMemoryPipeline
│   │
│   ├── agent/                      ← Agent 层（无状态任务执行）
│   │   ├── CognitiveCycleExecutor  感知→规划→执行→评估主循环
│   │   ├── AgentFactory            Agent 实例化
│   │   ├── AgentScopeExecutor      AgentScope 集成
│   │   ├── AgentRegistryService    Agent 注册发现
│   │   ├── WorkingMemory           工作记忆（执行期临时状态）
│   │   ├── AttentionBudget         注意力预算控制
│   │   ├── McpToolService          MCP 工具调用
│   │   └── runtime/                AgentPool、AgentSandbox、AgentEventBus、AgentCheckpointService
│   │
│   ├── assistant/                  ← Assistant 层（会话/情感/调度）
│   │   ├── AssistantService        会话主服务
│   │   ├── IntentUnderstandingService
│   │   ├── EmotionPerceptionService
│   │   ├── SessionManager          会话生命周期
│   │   ├── AgentDispatcher         向 Agent 层派发任务
│   │   ├── SkillMatchService       技能匹配
│   │   ├── LearningFeedbackService 学习反馈
│   │   ├── ResultAggregator        结果聚合
│   │   ├── role/                   Role（角色定义）
│   │   └── actor/                  Actor（执行者抽象）
│   │
│   └── team/                       ← Team 层（多 Assistant 协作）
│       ├── TeamOrchestrator        团队编排
│       ├── TaskDistributor         任务分发
│       ├── A2AProtocolService      Agent-to-Agent 协议
│       ├── ProgressSyncService     进度同步
│       └── ConflictArbitrator      冲突仲裁
│
├── engine/                         ← Layer 2 专项引擎（v1.0 全量）
│   │
│   ├── knowledge/                  知识库引擎（NexusKB）
│   │   ├── embedding/              向量化服务
│   │   ├── chunker/                文档分块（固定/递归/语义）
│   │   ├── importer/               文档导入（PDF/Word/MD/HTML/Web）
│   │   ├── search/                 相似度检索
│   │   ├── graph/                  知识图谱（实体抽取/图推理）
│   │   ├── rag/                    混合检索 + RAG 生成 + 评估
│   │   └── pipeline/               知识入库全流程编排
│   │
│   ├── memory/                     记忆引擎（AtomMemory）
│   │   ├── AtomMemoryEngine        原子记忆核心接口
│   │   ├── AtomMemoryEngineImpl    实现（PG + Redis 双写）
│   │   ├── BundleSearchService     记忆束检索
│   │   ├── MemoryAtom              原子记忆实体
│   │   └── TimeDecayStrategy       时间衰减策略
│   │
│   ├── tool/                       工具引擎
│   │   ├── ToolRegistry            工具注册表
│   │   ├── ToolCallDispatcher      工具调用分发
│   │   ├── McpToolService          MCP Server 集成
│   │   └── ScriptSandbox           脚本沙箱执行
│   │
│   ├── skill/                      技能引擎
│   │   ├── SkillMatchEngine        技能匹配路由
│   │   ├── SkillDefinition         技能定义
│   │   └── BuiltinSkillInitializer 内置技能初始化
│   │
│   ├── workflow/                   工作流引擎（Flowable 封装）
│   │   ├── WorkflowService         流程启动/查询/操作
│   │   ├── WorkflowDefinition      流程定义管理
│   │   └── TaskHandler             任务节点处理器
│   │
│   ├── document/                   文档引擎
│   │   ├── DocumentService         文档全生命周期
│   │   ├── DocumentVersion         版本管理
│   │   └── DocumentCollaboration   协同编辑
│   │
│   ├── orchestration/              编排引擎
│   │   ├── OrchestrationEngine     执行路径决策
│   │   ├── EngineCoordinator       引擎协同
│   │   └── ConfidenceGate          置信度门控（复用 core/gate）
│   │
│   ├── scheduler/                  调度引擎
│   │   ├── TaskScheduler           异步任务队列
│   │   ├── CronTrigger             定时触发
│   │   └── RetryStrategy           重试/补偿策略
│   │
│   ├── message/                    消息引擎
│   │   ├── MessageService          多渠道消息发送
│   │   ├── MessageTemplate         模板管理
│   │   └── ChannelAdapter          渠道适配（站内/邮件/短信/微信）
│   │
│   ├── monitor/                    监控引擎
│   │   ├── MetricsCollector        指标采集
│   │   ├── TokenUsageTracker       Token 统计
│   │   └── AuditLogger             审计日志
│   │
│   ├── budget/                     预算控制引擎
│   │   ├── BudgetEstimator         执行前预估（Token/时间/费用）
│   │   ├── BudgetMonitor           执行中实时监控
│   │   └── BudgetConfig            预算配置（系统/用户/单次）
│   │
│   ├── credit/                     积分引擎
│   │   ├── CreditService           积分账户管理
│   │   ├── CreditRule              积分规则执行
│   │   └── ContributionCalculator  贡献量化
│   │
│   ├── settlement/                 结算引擎
│   │   ├── SettlementService       结算记录
│   │   ├── PaymentAdapter          支付接口适配
│   │   └── DisputeArbitrator       争议仲裁
│   │
│   └── datasource/                 外部数据源引擎（v1.0 基础版）
│       ├── DataSourceAdapter       统一数据集接口
│       ├── JdbcDataSource          外部数据库对接
│       └── HttpDataSource          外部 API 对接
│
├── dsl/                            ← DSL 引擎（v2.0）
├── data/                           ← 数据权限（@DataScope）
├── security/                       ← 认证授权（Spring Security）
├── storage/                        ← 文件存储（FileStorage 接口）
├── messaging/                      ← 消息推送（WebSocket/SSE/邮件）
├── logging/                        ← 操作日志（AOP）
├── protection/                     ← 限流/幂等/分布式锁
├── task/                           ← 异步任务队列
├── crud/                           ← 通用 CRUD 基类
├── flyway/                         ← 数据库迁移配置
└── config/                         ← 框架自动配置
```

### aaf-api 包结构

```text
com.xuejiai.aaf/
│
├── AafApplication.java             ← 启动入口
│
├── config/                         ← 应用级配置
│   ├── TenantFilter / TenantContext
│   ├── GlobalExceptionHandler
│   ├── WebSocketConfig / CorsConfig / JacksonConfig / OpenApiConfig
│   └── ...
│
├── module/                         ← 业务模块（按功能域隔离）
│   ├── system/                     系统模块（按子域分层）
│   │   ├── user/                   用户子域（controller/ service/ domain/ repository/ vo/）
│   │   ├── auth/                   认证子域
│   │   ├── role/                   角色权限子域
│   │   ├── org/                    组织架构子域
│   │   ├── notify/                 通知消息子域
│   │   ├── log/                    日志审计子域
│   │   ├── chat/                   聊天子域
│   │   ├── entity/                 元数据实体子域
│   │   ├── workflow/               工作流子域
│   │   ├── task/                   任务调度子域
│   │   ├── dashboard/              仪表盘子域
│   │   └── api/                    跨模块暴露接口
│   ├── agent/                      Agent 管理（Agent 定义/配置/运行记录）
│   ├── assistant/                  助手管理（助手定义/会话历史）
│   ├── knowledge/                  知识库管理（知识库 CRUD/文档上传/检索 API）
│   ├── memory/                     记忆管理（记忆查看/清理）
│   ├── skill/                      技能管理（技能注册/配置）
│   ├── model/                      模型管理（模型配置/用量统计）
│   ├── budget/                     预算管理（预算配置/用量告警）
│   ├── credit/                     积分管理（积分账户/明细）
│   ├── settlement/                 结算管理（结算记录/对账）
│   ├── monitor/                    监控看板（Token 统计/调用链路）
│   ├── document/                   文档模块（文档管理业务）
│   └── [自定义业务模块]/             用户按需扩展
│
├── security/                       ← 应用级安全
└── util/                           ← 应用级工具
```

### aaf-auto-dev 包结构

```text
com.xuejiai.aaf.autodev/
├── agent/                          ← AI 开发 Agent
│   ├── PlanningAgent               需求→任务拆分
│   ├── CodingAgent                 代码生成
│   └── ReviewAgent                 代码审查
├── service/                        ← 代码生成服务
├── controller/                     ← 开发 API
└── monitor/                        ← kiro-cli 监控接口
```

### aaf-common 包结构

```text
com.xuejiai.aaf.common/
├── constant/                       全局常量
├── exception/                      异常体系（AafException、ErrorCode）
├── util/                           工具类（无 Spring 依赖）
├── model/                          Result<T>、PageInfo<T>
├── enums/                          通用枚举
└── annotation/                     通用注解（@OperationLog、@Trans 等）
```

## 关键调用链

### 用户发起对话请求

```text
HTTP POST /api/chat/message
  → TenantFilter（租户隔离）
  → GlobalExceptionHandler（异常兜底）
  → ChatController（aaf-api/module/chat）
  → AssistantService.chat()（aaf-framework/intelligent/assistant）
    → IntentUnderstandingService（意图解析）
    → SessionManager（会话状态）
    → AgentDispatcher.dispatch()
      → CognitiveCycleExecutor.execute()（感知→规划→执行→评估）
        → UnifiedRetrievalService（拉取记忆/知识）
          → MemoryRetrievalService → AtomMemoryEngine → Redis/PG
          → HybridSearchService → PgVector + Neo4j
        → LlmClient.call()（LLM 推理）
          → ModelRouter（选模型）
          → TokenMeteringHook（计量）
        → ToolCallDispatcher（工具调用，如有）
          → McpToolService / ScriptSandbox
        → MemoryExtractionService（写回记忆）
    → ResultAggregator（聚合结果）
  → SSE 流式返回
```

### 知识库文档入库

```text
POST /api/knowledge/documents
  → KnowledgeController（aaf-api/module/knowledge）
  → KnowledgePipelineService.ingest()（aaf-framework/engine/knowledge/pipeline）
    → ImporterFactory → DocumentImporter（PDF/Word/MD/HTML）
    → ChunkerFactory → DocumentChunker（分块策略）
    → EmbeddingService（向量化）→ PgVector
    → EntityExtractionService（实体抽取）→ GraphService → Neo4j
    → IncrementalUpdateService（增量更新）
```

### 多智能体协作

```text
TeamOrchestrator.orchestrate(goal)
  → TaskDistributor.distribute()（任务分解）
  → AgentDispatcher × N（并发派发多个 Assistant）
    → 每个 Assistant → CognitiveCycleExecutor
  → ProgressSyncService（进度同步）
  → ConflictArbitrator（结果冲突仲裁）
  → A2AProtocolService（跨系统 Agent 协作）
```

## 模块边界约束

### 依赖方向硬规则

| 规则 | 说明 |
|------|------|
| `intelligent/` 不直接访问数据库 | 必须通过 `engine/` 接口 |
| `engine/` 不调用 `intelligent/` | 方向只能向下 |
| `aaf-common` 零业务依赖 | 禁止 Spring Bean、禁止数据库访问 |
| `aaf-framework` 不依赖 `aaf-api` | 框架不知道业务存在 |
| 跨业务模块通过 `api/` 子包交互 | 禁止直接访问对方 service/repository/entity |

### 包内分层规则

```text
intelligent/
  core/       ← 接口定义层（被 agent/assistant/cognition 依赖）
  cognition/  ← 横向共享底座（agent/assistant 均可调用）
  agent/      ← 无状态执行（不持有会话状态）
  assistant/  ← 有状态会话（持有 SessionManager）
  team/       ← 多 Assistant 编排（不直接操作 Agent）

engine/
  knowledge/  ← 知识引擎（被 cognition/retrieval 调用）
  memory/     ← 记忆引擎（被 cognition/memory 调用）
  tool/       ← 工具引擎（被 agent 调用）
  skill/      ← 技能引擎（被 assistant 调用）
```

### ArchUnit 守护（待激活）

```java
// 智能层不直接访问数据库
noClasses().that().resideInPackage("..intelligent..")
    .should().accessClassesThat().resideInPackage("..repository..")

// 引擎层不调用智能层
noClasses().that().resideInPackage("..engine..")
    .should().accessClassesThat().resideInPackage("..intelligent..")

// common 无 Spring Bean
noClasses().that().resideInPackage("..common..")
    .should().beAnnotatedWith(Component.class)
```

## 技术方案

### 安全网关

AAF 不引入独立网关进程，安全网关能力内嵌在 `aaf-api` 应用内，通过 Spring Security Filter Chain 实现。

```text
请求
  → TenantFilter（租户解析，aaf-api/config）
  → RateLimitFilter（限流，protection/，Bucket4j 令牌桶）
  → SecurityFilterChain（Spring Security）
      → JwtDecoder（HMAC-SHA256 验签）
      → JwtBlacklistValidator（Redis 黑名单校验）
      → JwtClaimValidator（issuer / audience 校验）
  → @PreAuthorize（方法级权限，@EnableMethodSecurity）
  → Controller
```

| 能力 | 实现方案 |
|------|---------|
| 认证 | Spring Security OAuth2 Resource Server + 自签发 JWT（HMAC-SHA256） |
| 第三方登录 | 自研 OAuthClient（微信 / 钉钉 / 企业微信），`framework/security/oauth/` |
| Token 吊销 | Redis 黑名单（`JwtBlacklistValidator`），登出/改密即时失效 |
| 鉴权 | RBAC（用户→角色→权限）+ 字段级权限（JSONB，多角色取并集）+ 行级数据权限（`DataAccessRule` 动态生成 JPA Specification） |
| 限流 | Bucket4j 令牌桶（`protection/`，待实现），Redis 存储桶状态 |
| 熔断/重试 | Resilience4j（`protection/`，待实现），保护下游 LLM 调用 |
| 多租户隔离 | `TenantFilter` 解析请求头写入 `TenantContext`，JPA 拦截器自动过滤 |
| 开发环境 | `MockTokenFilter`（`@Profile("dev")`），跳过真实 JWT 验证 |

> 未来流量规模增大时可在 `aaf-api` 前置 Nginx / APISIX，现阶段单进程内嵌足够。

### AgentScope 运行时与智能体编排

**运行时 vs 编排的关系**：编排是"决定谁做什么、按什么顺序"，运行时是"执行时的状态持久化、会话管理、上下文恢复"。两者配合才能支撑有状态的多轮多智能体协作。

AgentScope 运行时核心能力：

| 能力 | 实现 | 说明 |
|------|------|------|
| **状态持久化** | `StateModule` 接口 + `Session` | Agent/Memory/PlanNotebook 均可 saveTo/loadFrom，支持断点续跑 |
| **Session 后端** | `JsonSession` / `RedisSession` / `MySQLSession` | 开发用 JSON，生产用 Redis/MySQL |
| **优雅关闭** | `GracefulShutdownManager` | 正在执行的 Agent 完成当前轮次后再停止，不丢失中间状态 |
| **Tracing** | `Tracer` / `JsonlTraceExporter` | 每次 Agent 执行的完整调用链记录，支持回放和审计 |

AgentScope 编排模式（均可通过适配层接入 AAF）：

| 模式 | AgentScope 实现 | AAF 五层对应 | 结合点 |
|------|----------------|-------------|--------|
| **Pipeline**（顺序/并行/循环） | `SequentialPipeline` / `FanoutPipeline` | Team → 多 Assistant 协作 | 替换 `TeamOrchestrator` + `TaskDistributor` |
| **Supervisor**（监督者调度专家） | `SubAgentTool` + 主 Agent | Team → Leader Assistant → Member | 替换 `AgentDispatcher`，Leader 通过工具调用 Member |
| **Routing**（分类路由） | `StateGraph` + 路由节点 | Assistant → 意图理解 → Agent 路由 | 替换 `SkillMatchService` 的路由逻辑 |
| **Skills**（按需加载技能） | `AgentSkill` / `SkillBox` | Agent → 技能引擎 | 替换 `SkillMatchEngine`，技能内容按需注入上下文 |
| **Subagents**（编排委托） | `SubAgentTool` + `Task` | Agent → 子 Agent 工具调用 | Agent-as-Tool 模式，子 Agent 无状态执行 |
| **Handoffs**（状态驱动切换） | `StateGraph` + 状态变量 | Assistant 会话中切换角色 | 替换 `ConflictArbitrator` 的角色切换逻辑 |
| **Custom Workflow**（自定义图） | `StateGraph`（确定性+Agent混合） | Layer 2 编排引擎 + Layer 3 智能层 | 工作流节点可嵌入 Agent，混合确定性步骤与 LLM 步骤 |
| **MsgHub**（消息广播） | `MsgHub` | Team 层多 Assistant 共享消息 | 多 Assistant 辩论/协商场景 |

**与 AAF 记忆/知识/工具/技能系统的结合点：**

```text
AgentScope ReActAgent
    │
    ├── Hook（PreReasoningEvent）
    │     → 注入 AAF Cognition 检索结果（记忆 + 知识库）到 LLM 上下文
    │     → 注入用户画像摘要（AssistantService 读取后传入）
    │
    ├── Toolkit（@Tool 注解）
    │     → AAF 工具引擎的工具注册为 AgentScope @Tool
    │     → AAF 技能引擎的技能注册为 AgentScope AgentSkill
    │     → AAF 知识库引擎暴露为 RAG 工具（KnowledgeRetrievalTools）
    │
    ├── LongTermMemory（agentscope-extensions-reme / mem0）
    │     → 对接 AAF AtomMemory 引擎（适配器模式）
    │     → 或直接用 AgentScope ReMe 扩展替换 AAF 自研长期记忆
    │
    ├── Session（agentscope-extensions-session-redis）
    │     → 替换 AAF 自研 SessionManager
    │     → Agent 状态与 Memory 状态统一持久化到 Redis
    │
    └── autocontext-memory（Token 预算截断）
          → 解决 AAF 上下文管理器缺失的 P0-P5 优先级截断
          → 自动管理 LLM 上下文窗口，防止超限
```

### AgentScope 整合策略

> 决策记录见 [ADR-005](../adr/ADR-005-agentscope-integration-strategy.md)。

**选定方案：AgentScope 为骨架，AAF 五层架构作为薄门面。**

```text
AAF 五层（薄门面，只保留 AAF 特有扩展，~50-100 行/层）
    └── AgentScope（厚实现：运行时/编排/状态/工具/Hook）
```

五层架构保留代码层次，但每层只做两件事：持有 AgentScope 组件（委托执行）+ 添加 AAF 特有逻辑。

**判断标准**：一个类里超过 50% 的代码是在调用 AgentScope → 这层太厚，需削减。

| AAF 层 | 委托给 AgentScope | AAF 特有扩展 |
|--------|-----------------|-------------|
| Team | `Pipeline` / `MsgHub` | A2A 协议、冲突仲裁 |
| Assistant | 主 `ReActAgent` | 情感感知 Hook、用户画像注入 |
| Agent | `ReActAgent`（执行循环） | `AgentExecutor` 接口适配 |
| Cognition | `GenericRAGHook` + `LongTermMemory` | 记忆管道、用户私有隔离 |
| Core | AgentScope `Model` | Token 计量、模型路由 |

现有厚实现（`CognitiveCycleExecutor`、`TeamOrchestrator`、`TaskDistributor` 等）逐步重构为薄门面。

AAF 的 Agent 能力基于 AgentScope Java SDK 构建，通过适配层屏蔽 AgentScope API 细节，上层只依赖 AAF 自身接口。

适配层位于 `aaf-framework/intelligent/agent/agentscope/`：

| 适配器 | AgentScope 能力 | 替换 AAF 自研组件 | 状态 |
|--------|----------------|-----------------|------|
| `AgentScopeAgentAdapter` | `ReActAgent` 执行 | `AgentScopeExecutor`（迁移整合） | ✅ 已实现 |
| `AgentScopeSessionAdapter` | `SessionManager`（Redis/MySQL 后端） | AAF 自研 `SessionManager` | 🔲 待引入 starter |
| `AgentScopeMemoryAdapter` | `Memory`（autocontext-memory，含 Token 预算截断） | `WorkingMemoryImpl` | 🔲 待引入扩展 |
| `AgentScopeToolAdapter` | `Toolkit` / `ToolRegistry` / `McpClientManager` | `ToolRegistry` / `ToolCallDispatcher` | 🔲 待迁移工具注册 |
| `AgentScopeAguiAdapter` | `agentscope-agui-spring-boot-starter` | `AgUiStreamHandler` / `AgUiEvent` | 🔲 待引入 starter |
| `AgentScopeA2aAdapter` | `agentscope-a2a-spring-boot-starter` | `A2AProtocolService`（占位） | 🔲 待引入 starter |

待引入的 Maven 依赖：

```xml
<!-- AG-UI 官方实现 -->
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-agui-spring-boot-starter</artifactId>
</dependency>
<!-- A2A 官方实现 -->
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-a2a-spring-boot-starter</artifactId>
</dependency>
<!-- Redis Session -->
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-session-redis</artifactId>
</dependency>
<!-- Token 预算自动截断（解决上下文 P0-P5 优先级截断缺失问题） -->
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-autocontext-memory</artifactId>
</dependency>
```

AAF 采用两级调度结构：

```text
Team
  └── Leader Assistant（主控助理）
        ├── 调度 Member Assistant 1 → Agent A / Agent B …
        ├── 调度 Member Assistant 2 → Agent C …
        └── 调度 Member Assistant N → …
```

**Team 层**：由一个 Leader Assistant 主控，负责目标对齐、任务分发、进度同步、冲突仲裁。Member Assistant 平等协作或层级协作均支持，通过 `TaskDistributor` 分发。

**Assistant 层**：每个 Assistant 持有会话状态和用户画像，面向人或面向 Leader。收到任务后通过 `AgentDispatcher` 向下调度一个或多个 Agent 并发执行，聚合结果后返回。

**Agent 层**：无状态，执行单一原子任务。一个 Assistant 可同时调度多个 Agent 并行运行，Agent 之间不直接通信，结果由 Assistant 的 `ResultAggregator` 汇总。

```text
调度规则：
  Team  → 只调度 Assistant，不直接操作 Agent
  Assistant → 只调度 Agent，不跨层调用其他 Assistant 的 Agent
  Agent → 只调用工具/技能/LLM，不调度其他 Agent
```

跨系统时，Leader Assistant 通过 A2A 协议将子任务委托给外部 Agent 系统，外部系统返回 `Artifact` 后由 Leader 汇总。

AG-UI 是 AAF 前后端之间的 AI 交互标准协议，基于 SSE 传输，定义了 AI 运行过程中的标准事件类型。

| 事件 | 含义 |
|------|------|
| `RUN_STARTED` | 一次 AI 运行开始 |
| `TEXT_MESSAGE_START/CONTENT/END` | 流式文本输出 |
| `TOOL_CALL_START/ARGS/END` | 工具调用过程 |
| `RUN_FINISHED / RUN_ERROR` | 运行结束/异常 |

后端 `AgUiStreamHandler` 将 Spring AI 的 `Flux<ChatResponse>` 转换为 AG-UI 事件流，前端通过 `EventSource` 消费。前端框架使用 `@assistant-ui/react` 原生支持 AG-UI 协议。

### A2A 协议

A2A（Agent-to-Agent）是 AAF 跨系统 Agent 互联协议，基于 Google A2A 规范，定义三种核心对象：

| 对象 | 含义 |
|------|------|
| `Task` | 跨 Agent 任务请求（含 id、状态、输入/输出） |
| `Artifact` | 任务产出物（文件、结构化数据、代码等） |
| `Message` | Agent 间通信消息 |

`A2AProtocolService` 实现协议适配，Team 层通过它与外部 Agent 系统（其他 AAF 实例或第三方 Agent）协作。

### 编排服务的实现基础

**编排服务与元引擎运行时的关系：**

元引擎不是五层中的某一层，而是跨层的编排基础设施。编排服务和运行时能力都是元引擎的子能力：

```text
元引擎
  ├── 编排服务（大脑：决定怎么跑）
  │     执行调度器 → DSL 路由、引擎选择、生命周期管理
  │     置信度门控器 → 自动/确认/人工三档
  │     状态管理器 → 四层状态持久化
  │
  └── 运行时能力（手脚：负责执行）
        工作流执行、智能体编排、知识记忆集成、降级、沙箱
```

编排服务发出指令（路由到哪个引擎、并发还是串行、置信度够不够），运行时能力负责执行（工作流节点运行、Agent 认知循环、知识检索注入）。两者是指挥与执行的关系，不是并列关系。

编排服务不是单一组件，而是分层协作：

```text
元引擎编排引擎（执行路径决策、置信度门控）
    │
    ├── 智能体编排（AgentScope Pipeline/Supervisor/Subagent）← Layer 3
    │     AssistantService → AgentDispatcher → AgentPool → AgentScopeExecutor
    │
    ├── 工作流引擎（Flowable，固定流程骨架）← Layer 2
    │     业务流程节点，可嵌入 Agent 任务
    │
    └── 调度引擎（异步任务队列，定时触发）← Layer 2
          后台任务，不直接调度 Agent
```

**编排服务最终如何调度 Agent：**

```text
请求进入
  → 编排引擎（路由决策：走工作流 or 直接 Agent）
  → AssistantService（会话管理 + 用户画像）
  → AgentDispatcher（选择哪个 Agent）
  → AgentPool.borrow()（借出 Agent 实例）
  → AgentSandbox.execute()（隔离执行）
  → AgentScopeExecutor → ReActAgent（实际执行）
  → AgentPool.release()（归还，reset 清空历史）
```

工作流引擎和调度引擎是被编排引擎驱动的专项引擎，不直接调度 Agent。

### Agent 池化 vs LLM 池化

**AAF 采用 Agent 池化，不做 LLM 池化。**

| | Agent 池化（当前实现） | LLM 池化 |
|---|---|---|
| 池化对象 | `AgentExecutor` 实例（含 AgentScope ReActAgent） | `ChatClient` / LLM 连接 |
| 为什么 | Agent 创建有开销（工具注册、配置加载），复用减少初始化成本 | LLM 是无状态 HTTP 调用，无需池化 |
| 归还时 | `executor.reset()` 清空对话历史，防止跨任务污染 | — |
| 实现 | `AgentPool`（ConcurrentHashMap + ConcurrentLinkedQueue） | `ResilientChatService`（降级+计量，非池化） |

LLM 层（`ResilientChatService`）做的是**降级+计量**：主模型失败时切换 fallback 模型，每次调用后发布 `TokenUsageEvent`。Spring AI `ChatClient` 本身是无状态的，不需要池化。

### AgentScope 与 AAF 记忆/知识库的整合

**三个问题的结论：**

**1. 智能体运行时 vs 元引擎运行时：不冲突，职责不同**

AgentScope 运行时管 Agent 执行生命周期（Session/Memory/Plan/Tracing），元引擎管整个系统的编排（DSL 调度、跨引擎协同、置信度门控、预算控制）。AgentScope 运行时是 Layer 3 的执行基础，元引擎是 Layer 2 的编排中枢，两者是上下层关系。

**2. AgentScope PlanNotebook vs AAF 任务系统：不同概念，不可复用**

| | AgentScope PlanNotebook | AAF 任务系统 |
|---|---|---|
| 是什么 | Agent 执行期的子任务规划（LLM 自主调用工具分解） | 系统级后台任务调度（定时/异步执行记录） |
| 谁用 | ReActAgent 内部，对话内 | 运维/管理员，跨会话持久 |
| 对应层 | Layer 3 Agent 规划模块 | Layer 4 调度引擎 |

PlanNotebook 应接入 AAF Agent 层（替换 `CognitiveCycleExecutor` 的规划阶段），AAF 任务系统继续作为运维工具并存。

**3. 记忆+知识库用 AAF 自己的，参考 ReMe 实现扩展**

实现 AgentScope `LongTermMemory` 接口，对接 AAF 的 AtomMemory + NexusKB：

```java
public class AafLongTermMemory implements LongTermMemory {

    private final MemoryWritePipeline writePipeline;    // AAF 写管道
    private final RetrievalPipeline retrievalPipeline;  // AAF 读管道

    @Override
    public Mono<Void> record(List<Msg> msgs) {
        // 走 AAF 写管道：提取 → 去重 → AtomMemory 写入
        return Mono.fromRunnable(() -> writePipeline.execute(...));
    }

    @Override
    public Mono<String> retrieve(Msg msg) {
        // 走 AAF 读管道：记忆 + 知识库混合检索
        return Mono.fromCallable(() -> retrievalPipeline.execute(...).toPromptSection());
    }
}
```

AgentScope 的 `STATIC_CONTROL` 模式自动在推理前调用 `retrieve()`、回复后调用 `record()`，无需在 `DefaultAssistantExecutor` 里手动触发记忆管道。知识库同理，实现 AgentScope `Knowledge` 接口对接 `HybridSearchService`。

### 可编排对象

AAF 中"编排"贯穿多个层次，以下对象均支持通过 DSL 或编排引擎进行流程编排：

| 对象 | 所在层 | 编排方式 | 说明 |
|------|--------|---------|------|
| **工作流节点** | Layer 2 工作流引擎 | Flowable BPMN / DSL | 业务流程编排，支持条件分支、并行、子流程；已定义的工作流可作为 Call Activity 嵌套调用 |
| **Agent** | Layer 3 智能层 | 编排引擎 / Team 层 | 多 Agent 串行/并行/条件执行，支持 DAG；已编排的 Agent 团队可作为子节点嵌入更大编排 |
| **工具调用链** | Layer 3 Agent | CognitiveCycleExecutor | Agent 执行期内的工具调用序列 |
| **记忆管道步骤** | Layer 3 Cognition | MemoryPipelineFactory | 提取→去重→写回→遗忘，步骤可插拔替换 |
| **知识入库管道** | Layer 2 知识库引擎 | KnowledgePipelineService | 导入→分块→向量化→图谱抽取，步骤可配置 |
| **Prompt 模板** | Layer 3 Core | PromptTemplateService | 多段 Prompt 拼装，支持条件片段和变量注入 |
| **消息通知流** | Layer 2 消息引擎 | 模板 + 渠道路由 | 触发条件→模板渲染→渠道选择→发送 |
| **自动化规则** | Layer 4 服务层 | AutomationService | 事件触发→条件判断→动作执行（无代码编排） |
| **自进化流程** | Layer 2 自进化引擎 | EvolutionEngine | 行为采集→效果评估→规范更新→代码重生成→沙箱验证 |

### 编排产物的对外服务方式

编排完成后，产物可通过以下方式对外暴露：

| 产物类型 | 对外方式 | 适用场景 |
|---------|---------|---------|
| **含 AI 流式输出的工作流 / Agent** | AG-UI 协议（SSE） | 前端实时展示推理过程、工具调用、流式文本 |
| **纯业务逻辑工作流** | REST API | 同步调用，无需流式，返回最终结果 |
| **后台异步工作流** | 调度引擎触发 + Webhook 回调 | 长时任务，完成后推送结果 |
| **跨系统 Agent 团队** | A2A 协议 | 与外部 Agent 系统互联协作 |
| **技能** | 技能引擎路由 → REST / AG-UI | 由 Assistant 内部调用，也可直接对外暴露为 API |

> 同一个编排产物可同时注册多种服务方式，例如一个 Agent 工作流既提供 AG-UI 流式接口供前端使用，也提供 REST 接口供第三方系统调用。

| 文档 | 内容 |
|------|------|
| [architecture.md](../architecture.md) | 整体架构概览（五层 + 引擎表） |
| [meta-engine.md](meta-engine.md) | 元引擎核心设计（调度/状态/门控） |
| [code-structure.md](code-structure.md) | 元引擎包结构详解 |
| [module-structure.md](../apps/service/module-structure.md) | Maven 模块结构与分工 |
| [agent.md](intelligent/agent.md) | 五层智能架构详细设计 |
| [cognition.md](intelligent/cognition.md) | Cognition 层详细设计 |
| [nexus-knowledge.md](engine/nexus-knowledge.md) | 知识引擎详细设计 |
| [atom-memory.md](engine/atom-memory.md) | 记忆引擎详细设计 |
