---
level: Practice
layer: Product
purpose: 架构级核心组件及功能特性整理——递归拆分，从整体到最底层组件
status: draft
version: 0.4.0
date: 2026-05-27
author: AaronZZH
---

# Agentic App Framework

生产级 AI 原生多智能体应用开发框架

- 五层智能架构，渐进决策，置信度门控，内置技能
- 元引擎编排 25+ 专项引擎，DSL 调度 + 状态管理 + 复杂性封装 + 自进化
- 多智能体协作，A2A 跨系统互联，知识与记忆混合检索
- 多模态（文本/图像/语音/视频）AIGC，多端适配（Web/移动/微信/CLI）
- 双模式交互：结构化视图（AG-UI + REST）+ 对话式视图（DSL 驱动语义组件）
- AI 自动开发 + 四层无代码运行时（实体/工作流/权限/自定义逻辑热加载）+ 公司自动化运营
- 规范驱动：文档是唯一真理源，代码是文档的实现结果

## 交互层 `aaf-api`

系统对外边界，负责协议适配、安全网关、多端接入。所有请求必须经过认证鉴权限流后才能进入系统内部。

**功能特性：**

- 统一入口：所有外部请求经安全网关后进入系统，屏蔽内部架构复杂性
- 协议无关：业务逻辑不感知传输协议，同一能力可通过 REST/SSE/WebSocket 暴露
- AI 交互标准化：AG-UI 协议定义 AI 运行全生命周期事件，前后端解耦
- 多端一致性：同一后端能力适配桌面/移动/微信/CLI，渲染策略由前端决定

### 安全网关
### AG-UI 协议
### WebSocket / SSE
### OpenAPI

## 服务层 `aaf-api`

面向用户的具体业务逻辑承载层，分框架基础服务、引擎管理服务和业务服务三类。上层调用智能层和引擎层接口，不包含 AI 推理逻辑。

**功能特性：**

- 业务逻辑与 AI 能力解耦：服务层只调用框架接口，不感知 LLM/Agent 实现细节
- 统一管理面：所有引擎能力通过管理服务暴露 CRUD 和配置入口
- 可扩展：业务模块可由元引擎自动生成，支持无代码扩展新领域

### 系统基础服务 `module/system`

系统级通用能力，所有业务模块共享依赖。

**功能特性：**

- 用户与认证（user / auth）：注册登录、JWT 签发、第三方 OAuth
- 角色权限（role）：RBAC 功能权限、菜单权限
- 组织架构（org）：部门树、数据权限隔离
- 文件存储（file）：本地 / S3 双模式，图片处理
- 消息通知（notify / sms）：站内消息、短信验证码
- 日志审计（log）：操作日志、审计日志
- 任务调度（task）：定时任务、分布式锁
- 仪表盘（dashboard）：数据统计、可视化
- 元数据实体（entity）：动态字段、数据字典（dict）
- 工作流管理（workflow）：流程定义、任务审批
- 对话管理（chat）：会话列表、消息历史
- 系统配置（config）：参数管理

### 引擎管理服务 `module/intelligent` + `module/model` + `module/knowledge`

各引擎的 CRUD 管理、配置、监控入口。不包含引擎执行逻辑，只做管理面。

**功能特性：**

- Agent 管理（intelligent/agent）：Agent 定义 CRUD、运行记录
- 助手管理（intelligent/assistant）：助手定义、会话历史
- 模型管理（model）：模型配置、用量统计、偏好设置
- 知识库管理（knowledge）：知识库 CRUD、文档上传、检索 API
- 工具管理（system/tool）：工具注册、权限配置
- 技能管理（system/skill）：技能定义、匹配规则

### 业务服务 `module/*`

用户在 AAF 上构建的具体业务，可由元引擎自动生成。

**功能特性：**

- 文档模块（document）：文档 CRUD、版本管理
- AIGC 模块（aigc）：AI 内容生成、图片/语音/视频
- 自定义业务模块：按需扩展

## 智能层 `aaf-framework/intelligent`

AI 推理与协作的核心层，五层智能架构。每层有且只有一个认知循环，粒度从项目级到请求级逐层细化。上层调度下层，下层结果回调上层，禁止跨层直接触发。依赖引擎层执行，不直接访问基础设施。

**功能特性：**

- 认知循环分层：每层独立循环，上层通过调度触发下层，下层通过回调返回上层
- 决策权动态流动：低置信度上报，高置信度本层执行，不固定归属
- 薄门面厚骨架：AgentScope 为执行骨架，AAF 五层只做接口适配和特有扩展
- 双路径 LLM 调用：简单场景直接 API，复杂任务走 Agent ReAct 循环
- 横向共享底座：Cognition 被 Agent 和 Assistant 共同依赖，记忆/知识/价值观统一管理

**设计文档：**

- [五层智能架构设计](intelligent/agent.md)

### Core `core`

智能层接口定义层，被 Agent/Assistant/Cognition 共同依赖。定义框架核心抽象接口，同时包含模型管理、Token 计量、Prompt 模板等 LLM 基础能力的默认实现。

**功能特性：**

- 核心接口契约：AgentExecutor、AssistantExecutor、MemoryPipeline、MemoryStrategy、SkillDef、FunctionDefinition
- 上层通过 core 接口交互，不直接依赖具体实现类

#### 模型管理 `model`

AI 模型元数据管理与动态路由。ai_model 表是所有 LLM 调用的 apiKey/baseUrl/capabilities 唯一来源。

**功能特性：**

- AiModel 实体管理（厂商/模型名/apiKey/baseUrl/价格/能力标签/fallback 配置）
- ModelPreference（用户/系统级偏好，USER scope / SYSTEM scope）
- ModelRouter 六层决策链（显式→编排→AI辅助→用户偏好→系统默认→yaml兜底）
- DynamicChatClientFactory（按 modelId + providerType 动态构建 ChatClient）

#### Token 计量 `token`

每次 LLM 调用后统一计量，两条路径（Spring AI + AgentScope）共享计量事件，驱动积分扣减。

**功能特性：**

- Token 消耗记录（inputTokens + outputTokens → ai_token_usage 表）
- AgentScope 钩子计量（PostCallEvent → TokenMeteringHook）
- 统一计量事件发布（TokenUsageEvent，Spring ApplicationEvent）
- 积分扣减联动（Token × 单价 → CreditService.deduct）
- 预算实时累计（超阈值暂停 Agent + 通知用户）

#### Prompt 模板 `prompt`

提示词模板管理与链式组装，为 Core/Agent/Assistant 构建 LLM 输入。

**功能特性：**

- 模板变量注入 + 条件片段 + 多段拼装
- Few-shot 示例管理（按场景选择示例集）
- 版本管理（同一模板多版本，支持 A/B 评估）

**设计文档：**

- [功能设计 — Prompt 引擎](engine/prompt.md)

### AI 基础设施 `ai`

LLM 弹性调用与多模态能力封装。提供降级重试、流式输出，以及非 Agent 场景的图像/语音/向量化等 AI 服务。

**功能特性：**

- 弹性降级：主模型失败自动切换 fallback，Resilience4j 熔断/重试
- 多厂商动态切换：OpenAI 兼容 / Anthropic / DashScope / Ollama
- 非 Agent 场景的多模态服务（文生图/图像处理/语音/向量化/重排序）

#### 模型调用 `chat`

弹性 LLM 调用核心，统一同步/流式调用入口，屏蔽厂商差异。

**功能特性：**

- 弹性调用（主模型失败→fallback 自动切换，Resilience4j 熔断）
- 流式输出（Flux<ChatResponse> → AG-UI SSE 事件流）
- 上下文组装（P0-P5 优先级，超 Token 预算从低优先级丢弃）
- 多厂商支持（OpenAI 兼容 / Anthropic / Ollama，Spring AI ChatClient）

#### 图像服务 `image`

非 Agent 场景的图像生成与处理。Agent 内多模态走 AgentScope 原生 ImageBlock。

**功能特性：**

- 文生图（Spring AI ImageModel）
- 图像增强/超分/风格迁移（阿里云 imageenhan SDK）
- 图片格式转换与压缩

#### 向量化服务 `embedding`

文本向量化封装，供知识库入库和记忆索引调用。

**功能特性：**

- 多厂商 EmbeddingModel 切换（Spring AI 封装）
- 批量向量化（分片并发）
- 向量维度与模型绑定管理

#### 重排序服务 `rerank`

检索结果精排，按语义相关性重新排序 Top-K 候选。

**功能特性：**

- 调用重排序模型返回精排分数
- 多厂商 Reranker 适配
- 被 Cognition 层统一检索服务调用

#### 语音服务 `speech`

语音识别（ASR）和语音合成（TTS）能力封装。

**功能特性：**

- ASR：音频→文本（多厂商适配）
- TTS：文本→音频（多厂商适配）
- 流式语音输入/输出

#### 多媒体服务 `media`

多模态消息格式转换，适配各厂商多模态 API 差异。

**功能特性：**

- 各厂商多模态格式互转（Anthropic / DashScope / Gemini）
- 音视频元数据提取
- Agent 内多模态走 AgentScope 原生（ImageBlock/AudioBlock/VideoBlock）

### Cognition `cognition`

认知基础层，横向共享底座。Agent 和 Assistant 均依赖此层进行记忆读写、知识检索、价值观校验。被动响应——不主动触发，但被调用时可用 LLM 级能力处理。

**功能特性：**

- 记忆管道（读管道可编排，写管道固定四步不可跳过）
- 统一检索入口，屏蔽多源检索差异
- 价值观校验（Agent 决策前校验 / 检索出库过滤 / 知识写入拦截）
- Learning 反哺通道（轨迹采集→效果评估→程序化蒸馏→记忆/知识/技能更新）

**设计文档：**

- [功能设计 — Cognition 层详细设计](intelligent/cognition.md)

#### 记忆服务 `cognition/memory`

短期/长期/情景/情感/程序性记忆的读写管理，调用引擎层 AtomMemoryEngine 实现存储。

**功能特性：**

- 短期记忆（会话级，Redis TTL 自动过期）
- 长期记忆（持久，PostgreSQL，用户私有）
- 情景记忆（含时间戳，支持时间线回溯）
- 情感记忆（AES 加密，用户私有不外传）
- 程序性记忆（"如何做"经验，用户/全局共享）
- 记忆提取（LLM 抽取值得记忆的片段）
- 记忆去重（语义相似度比对，合并/更新已有记忆）

#### 统一检索 `cognition/retrieval`

多源并行检索 + RRF 融合 + 重排序，提供统一检索入口。

**功能特性：**

- 并行检索（向量/图谱/关键词/短期记忆/长期记忆/程序性记忆）
- RRF 融合（多路结果按排名倒数加权合并）
- 重排序（可选，调用 AI 基础设施 rerank 服务）
- Value 校验过滤（出库前经过价值观过滤）
- 组装 MemoryContext 注入 Prompt

#### 记忆管道 `cognition/pipeline`

可编排的记忆处理流水线，读管道步骤可配置，写管道固定四步。

**功能特性：**

- 读管道：查询理解→路由决策→并行检索→RRF融合→重排→Value过滤→组装
- 写管道（固定四步）：提取→去重→写入→遗忘
- MemoryStrategy 路由（MEMORY_ONLY / KNOWLEDGE_ONLY / HYBRID / FULL）
- 步骤可插拔替换（读管道），保障数据一致性（写管道）

#### 学习反哺 `cognition/learning`

Agent 执行结果异步反哺到记忆/知识/技能，形成系统越用越强的闭环。

**功能特性：**

- 轨迹采集（执行日志 + 工具调用链）
- 效果评估（结果质量评分 + 用户反馈）
- 程序化记忆蒸馏（成功模式/失败教训/对比分析）
- 知识生长（评估后写入知识库）
- 技能生成（高频模式→新 SkillDefinition）
- 价值观更新建议（必须人工审核）

### Agent `agent`

无状态任务执行层。接收 Assistant 派发的原子任务，通过认知循环（感知→规划→执行→评估）完成，执行完归还池中。AgentScope ReActAgent 是执行骨架。

**功能特性：**

- 认知循环（感知→规划→执行→评估），薄门面委托 AgentScope ReActAgent
- 池化复用（借出 reset / 归还清空），对上层透明为无状态
- 断点续跑（从检查点恢复执行）
- 沙箱隔离（虚拟线程，防止跨任务污染）
- 工具权限守卫（执行前校验工具白名单）
- 执行轨迹记录（完整入参，支持回放和重执行）

**设计文档：**

- [功能设计 — 五层智能架构 #Agent](intelligent/agent.md)
- [技术方案 — 执行轨迹](intelligent/execution-trace.md)
- [技术方案 — 工具权限](engine/tool-permission.md)

#### 认知循环与 AgentScope 适配 `agent/agentscope`

Agent 执行核心，CognitiveCycleExecutor 作为薄门面，委托 AgentScope ReActAgent 执行 ReAct 循环。

**功能特性：**

- 感知→规划→执行→评估主循环
- AgentScopeExecutor 适配 AgentExecutor 接口
- Agent 工厂（从 ai_model 表读配置构建 ReActAgent）
- Agent 定义（AgentDefinition 配置元数据）

#### 运行时 `agent/runtime`

Agent 生命周期管理：池化、沙箱、事件总线、断点续跑。

**功能特性：**

- AgentPool（借出重置/归还清空，池大小=并发上限）
- AgentSandbox（虚拟线程隔离执行）
- AgentEventBus（执行事件发布/订阅）
- AgentCheckpointService（状态快照 + 恢复）
- AgentScheduler（Agent 调度策略）

#### 执行轨迹 `agent/trace`

Agent 执行全过程记录，支持审计、回放、重执行。

**功能特性：**

- 完整调用链记录（Span 树：感知→规划→执行→评估）
- 工具调用链路追踪
- 支持一键重新执行或修改参数后重执行

**设计文档：**

- [技术方案 — 执行轨迹](intelligent/execution-trace.md)

#### 工作记忆与注意力 `WorkingMemory`

Agent 执行期临时状态，任务结束后清理。注意力预算控制 Token 分配上限。

**功能特性：**

- 执行期临时状态存储（当前 Agent 私有）
- 注意力预算（Token 分配上限，超限压缩/丢弃低优先级上下文）
- 任务结束自动清理

### Assistant `assistant`

有状态会话层。持有用户会话、情感状态、技能路由，面向人或面向 Team Leader。收到任务后通过 AgentDispatcher 向下调度 Agent 并发执行，聚合结果后返回。

**功能特性：**

- 意图理解 + 情感感知：澄清意图优先于执行，动态调整回应风格
- 技能匹配路由：根据意图+关键词匹配最合适的执行路径
- 置信度门控：>0.9 自动 / 0.7-0.9 确认 / <0.7 转人工
- 人工审批：不可逆操作强制人工确认，WebSocket 推送审批请求
- A2A 协议：跨系统 Agent 互联（Task / Artifact / Message）

**设计文档：**

- [功能设计 — 五层智能架构 #Assistant](intelligent/agent.md)

#### 会话与感知 `assistant`

意图理解、情感感知、会话生命周期管理。

**功能特性：**

- 意图理解（IntentUnderstandingService，LLM 驱动意图解析）
- 情感感知（EmotionPerceptionService，语气+节奏分析→动态调整回应风格）
- 会话管理（SessionManager，会话创建/恢复/超时/销毁）
- 学习反馈（LearningFeedbackService，用户反馈收集→Learning 通道）

#### 调度与聚合 `assistant`

Agent 调度、技能匹配、结果聚合。

**功能特性：**

- Agent 调度（AgentDispatcher，选择 Agent + 并发派发）
- 技能匹配（SkillMatchService，意图→技能→Agent 路由）
- 结果聚合（ResultAggregator，多 Agent 结果汇总）
- 内置技能（自我认知 / 用户理解 / 自学习 / 技能创建）

#### 置信度与审批 `assistant`

渐进决策门控，不可逆操作强制人工确认。

**功能特性：**

- 置信度门控（ConfidenceGate，>0.9自动 / 0.7-0.9确认 / <0.7转人工）
- 人工审批（HumanApprovalService，WebSocket 推送 + 会话级临时授权）
- 不可逆操作拦截（删除/发布/提交/权限变更，无论置信度）

**设计文档：**

- [技术方案 — 置信度门控器](core/confidence-gate.md)

#### 角色与 Actor `assistant/role` + `assistant/actor`

角色定义与执行者抽象，Human 和 AI 统一为 Actor。

**功能特性：**

- Role（角色定义：系统 Prompt + 能力边界 + 工具白名单）
- Operator 统一抽象（UserPrincipal / AgentPrincipal 实现 OperatorAware）
- OperatorContext.current() 获取当前执行者

**设计文档：**

- [技术方案 — Actor 模型](intelligent/actor.md)

#### A2A 协议 `assistant/a2a`

跨系统 Agent 互联协议，基于 Google A2A 规范。

**功能特性：**

- Task（跨 Agent 任务请求，含 id/状态/输入/输出）
- Artifact（任务产出物：文件/结构化数据/代码）
- Message（Agent 间通信消息）
- 基于 agentscope-a2a-spring-boot-starter 实现

### Team `team`

多 Assistant 协作层。由 Leader Assistant 主控，负责目标对齐、任务分发、进度同步、冲突仲裁。只调度 Assistant，不直接操作 Agent。

**功能特性：**

- 两级调度：Team→Assistant→Agent，层级清晰不跨层
- Pipeline/Supervisor/MsgHub 编排模式（委托 AgentScope）
- 跨系统协作：通过 A2A 协议委托外部 Agent 系统

#### 团队编排与分发 `team`

团队编排、任务分发、进度同步、冲突仲裁。

**功能特性：**

- TeamOrchestrator（目标对齐 + 编排策略选择）
- TaskDistributor（任务分解 + 并发派发多个 Assistant）
- ProgressSyncService（进度同步 + 实时推送）
- ConflictArbitrator（结果冲突仲裁 + 聚合）

## 引擎层 `aaf-framework/engine`

通用执行能力层，无具体业务语义，被智能层和服务层调用。包含元引擎（跨层编排中枢）和 25 个专项引擎（按职责域分为 5 组）。引擎层不调用智能层，方向只能向下。

**功能特性：**

- 接口驱动：所有引擎通过接口暴露能力，实现可替换，上层不感知具体实现
- 编排协同：引擎间不直接互调，通过编排引擎统一协调执行路径
- 确定性与不确定性混合：工作流处理固定流程，Agent 处理开放任务，两者可嵌套
- 横切支撑：语义计算、监控、预算控制等能力贯穿多个引擎，不归属单一调用方

**设计文档：**

- [元引擎设计](meta-engine.md)
- [架构细化](architecture-detail.md)

### 元引擎 `meta`

跨层编排基础设施，不是五层中的某一层。负责将意图转化为执行，将执行转化为知识。编排各专项引擎协同工作，提供统一执行上下文、DSL 调度和生命周期管理。

**功能特性：**

- 意图→执行→知识的完整闭环，执行结果反哺系统进化
- 渐进提交：结果先暂存，确认后持久化，未确认自动销毁
- 防退化：人类未响应不超时执行，不允许静默降低标准
- 知识能力绑定：工具与知识库强关联，执行结果自动归档形成正向闭环

**设计文档：**

- [功能设计 — 元引擎核心设计](meta-engine.md)
- [技术方案 — 执行调度器](core/execution-dispatcher.md)
- [技术方案 — 状态管理器](core/state-manager.md)
- [技术方案 — 置信度门控器](core/confidence-gate.md)
- [技术方案 — 元数据管理器](core/metadata-manager.md)
- [技术方案 — 自进化机制](core/evolution.md)

### 数据与知识引擎组 `data-knowledge`

负责数据存储、检索、语义计算、外部数据对接。为 Cognition 层提供底层存储和计算能力。

**功能特性：**

- 混合检索：向量+图谱+关键词多路并行，RRF 融合消除来源偏差
- 记忆与知识分离：记忆是用户私有交互经历（自动写入），知识是全局共享领域知识（人工维护）
- 引擎层纯算法：存储/索引/检索是确定性操作，不调用 LLM（Agentic 能力在 Cognition 层）

#### 知识库引擎 `knowledge`

领域知识存储与混合检索引擎（NexusKB），支持文档全流程入库和多路检索。

**功能特性：**

- 文档导入（PDF/Word/MD/HTML/Web，ImporterFactory 自动识别格式）
- 文档分块（固定大小/递归字符/自动策略选择）
- 向量化入库（EmbeddingService → PgVector）
- 知识图谱构建（实体抽取 + 关系三元组 → Neo4j）
- 混合检索（向量相似度 + 图谱多跳 + 全文检索 + RRF 融合）
- RAG 生成（检索结果注入 LLM 上下文 + 溯源引用）
- 增量更新（避免重复入库）

**设计文档：**

- [功能设计 — NexusKB 知识引擎](engine/nexus-knowledge.md)

#### 记忆引擎 `memory`

原子记忆存储引擎（AtomMemory），纯算法层，不调用 LLM。提供时序+语义双索引、时间衰减。

**功能特性：**

- 原子记忆存储（PG + Redis 双写，双时态索引）
- 记忆束检索（BundleSearch，关联记忆聚合返回）
- 时间衰减策略（异步，低权重旧记忆降权）
- 记忆关系管理（记忆间关联关系）

**设计文档：**

- [功能设计 — AtomMemory 记忆引擎](engine/atom-memory.md)

#### 语义计算引擎 `semanticcalc`

横切支撑多个认知与业务组件的通用语义能力，引擎只关心"怎么算"。

**功能特性：**

- Embedding 生成（供 Memory 索引、Knowledge ECL、Retrieval 查询）
- 语义相似度计算（供 Retrieval 排序、Memory 去重）
- 实体抽取 NER（供 Knowledge 图谱构建、Agent 感知）
- 关系抽取（供 Knowledge 图谱构建）
- 语义分类/聚类/去重（供 Memory 归档、Knowledge 分类）
- 语义漂移检测（供 Learning 反哺、元数据管理器）
- 摘要生成（供文档服务、对话历史压缩）

**设计文档：**

- [功能设计 — SemanticCalc 语义计算引擎](engine/semantic-compute.md)

#### 数据处理引擎 `dataprocess`

结构化/半结构化数据的批流处理与统计分析，与语义计算引擎互补。

**功能特性：**

- 处理管道（清洗→字段映射→路由→AI 增强，步骤可编排）
- 批处理（读取/转换/聚合/关联/导出）
- 流处理（事件流/窗口聚合/实时告警）
- 多维统计（分组/透视/累计）
- 表格处理（结构化表格数据操作）

**设计文档：**

- [功能设计 — DataProcess 数据处理引擎](engine/data-process-engine.md)

#### 外部数据源引擎 `datasource`

外部系统数据对接，ETL 导入 + 联邦查询两种模式。

**功能特性：**

- ETL 导入（定时/事件触发从外部拉数据，写入 AAF PostgreSQL）
- 联邦查询（不落库，运行时实时查外部数据源）
- 统一抽象（DataSourceAdapter：Jdbc / Http / File → DataSet）
- DSL 驱动，参数化查询防注入，连接凭证加密存储

**设计文档：**

- [功能设计 — 外部数据源引擎](engine/external-datasource.md)

### 执行与编排引擎组 `execution`

负责任务执行、流程编排、工具调用、技能匹配。为 Agent 层提供执行时的能力扩展。

**功能特性：**

- 确定性+不确定性混合编排：工作流节点可嵌入 Agent 任务，固定流程与动态规划共存
- 工具即能力：Agent 通过工具引擎获得外部能力，权限校验 + 沙箱隔离保障安全
- 技能路由：根据意图自动匹配最合适的执行路径，内置技能覆盖自我认知/学习/创建

#### 工具引擎 `tool`

工具注册发现、调用分发、权限校验、脚本沙箱执行。

**功能特性：**

- 工具注册表（Spring Bean 自动发现 + MCP Server 远程发现）
- 工具调用分发（参数校验→权限检查→执行→结果封装）
- 工具权限校验（白名单 + 风险等级 + 事件监听）
- 脚本沙箱执行（GraalVM Polyglot 优先，降级子进程隔离）
- 工具存储（持久化工具定义）
- 内置工具集（系统预置常用工具）
- 工具生成（AI 辅助生成工具定义）

**设计文档：**

- [技术方案 — 工具权限](engine/tool-permission.md)

#### 技能引擎 `skill`

技能定义、匹配路由、内置技能管理。技能是 Agent 能力的高层封装。

**功能特性：**

- 技能匹配路由（意图 + 关键词 → 最佳技能）
- 技能定义（触发条件 + 绑定 Agent + 系统 Prompt + 工具白名单）
- 技能存储（持久化技能配置）
- 内置技能（自我认知 / 用户理解 / 自学习 / 技能创建）

#### 工作流引擎 `workflow`

Flowable 封装，确定性流程骨架，节点可嵌入 Agent 任务实现混合编排。

**功能特性：**

- 流程定义管理（BPMN / DSL 驱动）
- 流程启动/查询/操作
- 任务节点处理器（人工节点 + Agent 节点 + 脚本节点）
- 条件分支、并行网关、子流程、Call Activity 嵌套
- 节点级重试策略

#### 调度引擎 `scheduler`

异步任务队列与定时触发，后台任务执行基础设施。

> ⚠️ 当前代码在 `framework/task/`，规划迁移至 `engine/scheduler/`

**功能特性：**

- 异步任务队列（入队/出队，优先级三档）
- 定时/周期触发（Cron 表达式）
- 重试策略（指数退避 + 死信队列）
- 分布式锁（防重复执行）
- 任务注册与监控

**设计文档：**

- [功能设计 — 调度引擎](engine/scheduler.md)

#### 编排引擎 `orchestration`

执行路径决策、引擎协同、响应式执行管道。元引擎的运行时执行手臂。

> ⚠️ 暂无独立包，v1.0 新建

**功能特性：**

- 执行路径决策（走工作流 or 直接 Agent or 混合）
- 引擎协同（多引擎串行/并行调用）
- 响应式执行管道（Virtual Threads + StructuredTaskScope）
- 置信度门控集成（复用 ConfidenceGate）

**设计文档：**

- [功能设计 — 编排引擎](engine/orchestration.md)

### 交互与内容引擎组 `content`

负责内容管理、提示词、UI 组装、DSL 解析、消息通知。

**功能特性：**

- 一切皆文档：所有制品以文档形式存储，有语义、有关系、有历史、有版本
- DSL 三重身份：规范文档（人读）/ 生成目标（AI 产出）/ 执行程序（系统跑）
- 语义组件：后端输出 DSL 描述"展示什么"，前端决定"怎么展示"，多端一套组件

#### 消息引擎 `message`

多渠道消息通知，业务层只调用消息接口，不感知渠道细节。

> ⚠️ 当前代码在 `framework/messaging/`，规划迁移至 `engine/message/`

**功能特性：**

- 多渠道发送（站内/邮件/短信/微信/钉钉/飞书）
- 消息模板引擎（变量注入 + 条件渲染）
- 渠道适配器（ChannelSender 接口，各渠道独立实现）
- 触发条件→模板渲染→渠道选择→发送

**设计文档：**

- [功能设计 — 消息引擎](engine/message.md)

#### DSL 引擎 `dsl`

多范式 DSL 解析与执行，元引擎的核心语言。

**功能特性：**

- 多范式支持（声明式/命令式/函数式/自然语言混合）
- 分层转化（L1 宽松→L2 结构化→L3 严格）
- 分域路由（dev/runtime/doc 区分职责归属和生命周期）
- DSL 版本管理与校验

**设计文档：**

- [功能设计 — Magic-DSL 领域语言](dsl/magic-dsl.md)

#### 文档引擎 `document`

七类文档全生命周期管理，一切皆文档的底层支撑。

> ⚠️ 暂无独立引擎包，v1.0 新建

**功能特性：**

- 七类文档统一管理（规范/DSL/组件/插件/业务/执行/日志）
- 版本控制（协同层 Yjs CRDT + 版本层快照 + 归档层 DAG）
- 协同编辑（实时多人编辑）
- 文档关系图谱（文档间引用/依赖关系）

**设计文档：**

- [功能设计 — 文档引擎](engine/document-engine.md)

#### Prompt 引擎 `prompt`

提示词库管理、链式组装、评估优化。

> ⚠️ 当前代码在 `intelligent/core/prompt/`，规划迁移至 `engine/prompt/`

**功能特性：**

- 提示词库管理（分类/标签/版本）
- 链式组装（多段拼接 + 条件片段 + 变量注入）
- Few-shot 管理（按场景选择示例集）
- 评估优化（A/B 测试 + 效果对比）

**设计文档：**

- [功能设计 — Prompt 引擎](engine/prompt.md)

#### 语义组件引擎 `senseui`

DSL 驱动动态 UI 组装，后端输出组件树，前端渲染，多端适配。

> ⚠️ v2.0 新建

**功能特性：**

- 组件匹配（意图→组件类型选择）
- 内容注入（数据绑定到组件）
- 布局组装（组件树生成）
- 多端渲染适配（Web/移动/微信/CLI）
- 组件类型（展示/交互/容器/智能/执行）

**设计文档：**

- [功能设计 — 语义组件引擎](engine/sense-ui.md)

### 运营与治理引擎组 `governance`

负责权限、监控、成本管控、积分结算、价值规则。

**功能特性：**

- Actor 统一抽象：Human 和 AI 共用一套权限体系，不为 Agent 单独建权限
- 成本可控：执行前预估、执行中监控、超限自动暂停，预算三层级约束
- AI 可观测：每次 LLM 调用完整追踪，Agent 执行轨迹可回放可重执行
- 贡献量化闭环：用户行为→积分→结算，激励与成本透明

#### 权限引擎 `security`

RBAC + ReBAC + 记录规则 + ABAC 四层权限模型，Actor 统一抽象。

> ⚠️ 当前代码在 `framework/security/`，规划迁移至 `engine/security/`

**功能特性：**

- RBAC（用户→角色→功能权限，Spring Security 原生）
- ReBAC（资源关系权限 owner/viewer/editor，Neo4j 图查询）
- 记录规则（行级/字段级数据过滤，JPA 拦截器 @DataScope）
- ABAC（动态条件策略，Agent 低置信度需人工确认）
- Actor 统一抽象（UserPrincipal / AgentPrincipal → ActorAware）
- API Key 认证 + OAuth 第三方登录 + 商业许可证控制

**设计文档：**

- [功能设计 — 访问控制](security/access-control.md)
- [功能设计 — 安全架构](security/security.md)

#### 监控引擎 `monitor`

AI 可观测性，纯观测层，只采集/分析/告警，不干预执行。

**功能特性：**

- LLM 调用链路追踪（Prompt/输出/工具调用/Token/耗时/模型）
- Agent 执行轨迹（Span 树，支持回放）
- Token 统计与用量分析
- 审计日志（不可删除，管理员可审计）
- 技术实现：OpenTelemetry + Prometheus/Grafana

**设计文档：**

- [功能设计 — 监控引擎](engine/monitor.md)

#### 预算控制引擎 `budget`

执行成本管控，四维预估 + 三档监控 + 用户决策。

> ⚠️ 暂无独立包，v1.0 新建

**功能特性：**

- 执行前预估（Token/时间/工具调用次数/费用，保守估算×1.2）
- 执行中监控（70%告警 / 90%警告 / 100%暂停）
- 预算三层级（系统级→Assistant级→任务级，下层不超上层）
- 超预算处理（暂停执行 + 保存检查点 + 等待用户决策）

**设计文档：**

- [功能设计 — 预算控制引擎](engine/budget-control.md)

#### 积分引擎 `credit`

虚拟积分管理，贡献量化与消费扣减。

**功能特性：**

- 积分账户管理（余额/冻结/明细）
- 积分来源（贡献行为/生态贡献/游戏化/充值兑换）
- 积分消费（API 调用/功能解锁/市场购买/提现申请）
- 规则执行（DSL 定义，热生效）
- 贡献量化（ContributionCalculator）

**设计文档：**

- [功能设计 — 积分与结算引擎](engine/credit-settlement.md)

#### 结算引擎 `settlement`

真实资金进出，对接支付接口，异步结算+对账。

**功能特性：**

- 支付接口适配（微信/支付宝/Stripe）
- 结算记录管理
- 争议仲裁
- 与积分引擎协作（充值→earn / 提现→freeze→withdraw→spend）

**设计文档：**

- [功能设计 — 积分与结算引擎](engine/credit-settlement.md)

#### 价值规则引擎 `valuerule`

规则解析 + 优先级仲裁 + 行为校验，支撑价值观系统。

**功能特性：**

- 规则定义（伦理边界/优先级规则/交互规范/降级边界/合规约束）
- 优先级仲裁（规则冲突时按优先级裁决）
- 行为校验（Agent 决策前校验 / 检索出库过滤 / 知识写入拦截）
- 规则热更新（修改后立即生效）

### 生态与扩展引擎组 `ecosystem`

负责插件生态、推荐、搜索、空间模型、系统自进化。均为 v2.0 规划，当前仅有接口骨架。

**功能特性：**

- 开放生态：插件热插拔 + 沙箱隔离，第三方可贡献 Agent/工具/知识库/组件
- 系统越用越强：执行轨迹→效果评估→规范更新→代码重生成，形成自进化闭环
- 跨资源统一发现：一个查询并行检索所有资源类型，权限过滤后融合排序

#### 自进化引擎 `evolution`

行为采集→效果评估→规范更新→代码重生成→沙箱验证→热部署。

**功能特性：**

- 引擎自进化（性能异常/规范冲突/重复模式触发，强制人工审核）
- 业务系统自进化（用户负反馈/重复需求触发，按置信度分级审核）
- 规范一致性扫描（docs/ 与代码语义一致性对比）
- 代码重生成（aaf-auto-dev 执行，沙箱验证后热部署）

**设计文档：**

- [技术方案 — 自进化机制](core/evolution.md)

#### 空间引擎 `space`

物理时空模型，为虚拟空间、知识图谱布局、记忆时间线提供统一时空坐标。

**功能特性：**

- World（坐标系 + 维度 + 时间轴 + 物理规则配置）
- Matter（文档/知识节点/记忆原子/Agent 实例，有坐标/质量/语义向量）
- 语义引力（相似度产生引力，相近物质聚合）
- 时间流（新鲜度衰减，过时知识自动下沉）

**设计文档：**

- [功能设计 — 物理时空引擎](engine/physics-spacetime.md)

#### 插件引擎 `plugin`

生态市场的运行时底座，支持第三方贡献和动态加载。

> ⚠️ v2.0 新建

**功能特性：**

- 插件类型（Agent/工具/技能/知识库/提示词/UI 组件）
- 动态加载（热插拔，无需重启）
- 沙箱隔离执行（防止影响引擎核心）
- 语义化版本管理 + 权限声明与授权

**设计文档：**

- [功能设计 — 插件引擎](engine/plugin.md)

#### 推荐引擎 `recommendation`

基于使用历史和语义相似度的个性化推荐。

> ⚠️ v2.0 新建

**功能特性：**

- 市场推荐（Agent/工具/知识库，协同过滤+语义相似+热度排序）
- 技能推荐（当前对话上下文感知匹配）
- 工具推荐（Agent 执行时任务类型匹配）

**设计文档：**

- [功能设计 — 推荐引擎](engine/recommendation.md)

#### 搜索引擎 `search`

跨资源统一搜索入口，屏蔽各引擎检索差异。

> ⚠️ v2.0 新建

**功能特性：**

- 并行检索多资源（知识库/文档/Agent/工具/技能/用户/市场资产）
- 权限过滤（不泄露无权访问的内容）
- 跨引擎结果融合排序（RRF）
- 搜索建议（实时补全）

**设计文档：**

- [功能设计 — 搜索引擎](engine/search.md)


## 基础设施层

存储、通信、计算底座，无业务语义，最稳定。所有上层通过引擎层间接访问，不直接操作。

**功能特性：**

- PostgreSQL + PgVector：关系数据 + 向量存储 + 全文检索（FTS）
- Redis：缓存、短期记忆、Session、分布式锁、消息队列
- Neo4j：知识图谱、实体关系存储、多跳推理
- JVM Sandbox：GraalVM Polyglot 隔离执行、资源限制、超时控制
- Flyway：数据库版本迁移管理

## 横切能力 `aaf-framework`

贯穿所有层的通用基础设施能力，不属于任何特定层。

**功能特性：**

- 通用 CRUD 基类（`crud`）：BaseCrudService / BaseCrudController
- 防护机制（`protection`）：限流 / 幂等 / 分布式锁
- 文件存储（`storage`）：StorageService 接口、Local / S3 实现、图片处理
- 序列号生成（`sequence`）：模板化序列号、分布式唯一 ID
- 数据权限（`data`）：@DataScope 注解、JPA 拦截器
- 操作日志（`logging`）：AOP 切面、审计拦截器、健康检查
- 框架自动配置（`config`）：Bean 注册、条件装配

## 全局流程图

### 用户对话

<!-- 待填充：完整时序图 -->

### 多 Agent 协作辅助完成业务

<!-- 待填充：完整时序图 -->

### Learning 自学习全过程

<!-- 待填充：完整时序图 -->

## 相关文档

- [整体架构概览](../architecture.md)
- [架构细化](architecture-detail.md)
- [元引擎设计](meta-engine.md)
- [执行逻辑全景](execution-flow.md)
- [执行逻辑图](execution-flow-diagram.md)
