---
level: Practice
layer: Product
purpose: 架构级核心组件总览——每个组件一句话简述 + 功能设计/技术方案双链接
status: draft
version: 0.5.0
date: 2026-05-28
author: AaronZZH
---

# Agentic App Framework

生产级 AI 原生多智能体应用开发框架。

**核心特色：**

- 动态决策权：决策权随置信度在人/AI/系统间实时流动，不固定归属
- 防退化保障：系统在无人响应/异常场景下不允许自动降低安全标准
- Agentic RAG：认知层写入/检索时用 LLM 级能力增强
- 异步审查：高置信操作先执行暂存，异步通知人类审查，不阻塞执行链路
- 用户感知横切：画像/记忆/情感跨层协同，被动响应式个性化
- 记忆与知识分离：记忆是用户私有交互经历（自动），知识是全局共享领域知识（人工）
- 渐进决策：置信度×可验证性二维门控，决策权在人/AI/系统间动态流动
- 元引擎编排：DSL 调度 + 四层状态渐进提交 + 26 个专项引擎协同
- 复杂性封装：默认隐藏，四层按需展开，五度空间约束递归分解
- 内置技能：自我认知 / 用户理解 / 自学习 / 技能创建，助手开箱即用
- DSL 驱动：三重身份（规范/生成目标/执行程序），分层分域，贯穿开发时和运行时
- 自进化：行为→评估→规范更新→代码重生成→沙箱验证→热部署，系统越用越强

## 交互层 `aaf-api`

系统对外边界。所有外部请求经安全网关认证鉴权后进入系统，屏蔽内部架构复杂性。AG-UI 协议标准化 AI 运行全生命周期事件流，A2A 协议实现跨系统 Agent 互联。协议无关——业务逻辑不感知传输协议，同一能力可通过多协议暴露。

### 安全网关 `gateway`

JWT + API Key 双模认证，RBAC 鉴权，请求限流，组织/工作区级数据隔离。所有请求的统一入口和安全屏障。

- [功能设计 — 安全网关](api/gateway.md)
- [技术方案 — 安全网关](api/gateway-tech.md)

### AG-UI 协议 `agui`

SSE 事件流标准化输出。定义 AI 运行全生命周期事件（RUN_STARTED / TEXT_MESSAGE / TOOL_CALL / RUN_FINISHED），前后端解耦，前端只消费事件流。

- [功能设计 — AG-UI 协议](api/agui.md)
- [技术方案 — AG-UI 协议](api/agui-tech.md)

### A2A 协议 `a2a`

跨系统 Agent 互联协议（Task / Artifact / Message）。交互层定义协议规范，实现在 Assistant 层。支持异步消息通信，不要求同框架/同进程。

- [功能设计 — A2A 协议](api/a2a.md)
- [技术方案 — A2A 协议](api/a2a-tech.md)

### 协议适配 `protocol`

REST / GraphQL / WebSocket / SSE / OpenAPI 多协议支撑。业务逻辑写一次，多协议自动暴露。

- [功能设计 — 协议适配](api/protocol.md)
- [技术方案 — 协议适配](api/protocol-tech.md)

## 服务层 `aaf-api/module`

面向用户的具体业务逻辑承载层。上层调用智能层和引擎层接口，不包含 AI 推理逻辑。业务逻辑与 AI 能力解耦——服务层只调用框架接口，不感知 LLM/Agent 实现细节。所有引擎能力通过管理服务暴露 CRUD 和配置入口。业务模块可由元引擎自动生成，支持无代码扩展。

### 系统基础服务

用户/认证/权限/组织/文件/消息/短信/日志/仪表盘/任务调度/用户画像/知识库/文档/AIGC/对话/客服。系统级通用能力，所有业务模块共享依赖。

### 引擎管理服务

提示词/Agent/助手/知识库/模型/工具/技能/工作流/编排/预算/积分/结算/监控管理入口。各引擎的 CRUD 管理、配置、监控面板，不包含引擎执行逻辑。

### 业务服务

OPC / 自定义模块。用户在 AAF 上构建的具体业务，可由元引擎自动生成。

## 智能层 `aaf-framework/intelligent`

五层智能架构的核心。每层有且只有一个认知循环，粒度从项目级到请求级逐层细化。决策权随置信度在层间动态流动——高置信本层执行，低置信自动上报。AgentScope 为执行骨架，AAF 五层只做薄门面适配和特有扩展。

### Core 核心接口 `core`

智能层接口定义层。AgentExecutor / AssistantExecutor / MemoryPipeline / SkillDef / FunctionDefinition 等核心抽象，被 Agent/Assistant/Cognition 共同依赖，零框架依赖。

- [功能设计 — Core 核心接口](intelligent/core/core.md)
- [技术方案 — Core 接口与包结构](intelligent/core/core-tech.md)

### 模型管理与路由 `core/model`

ai_model 表统一管理所有 LLM 的 apiKey/baseUrl/capabilities。ModelRouter 六层决策链（显式→编排→AI辅助→用户偏好→系统默认→yaml兜底），DynamicChatClientFactory 按 modelId 动态构建 ChatClient。

- [功能设计 — 模型管理与路由](intelligent/core/model-router.md)
- [技术方案 — 模型路由](intelligent/core/model-router-tech.md)

### 置信度门控 `core/confidence`

置信度×可验证性二维矩阵，三档决策（>0.9自动 / 0.7-0.9确认 / <0.7转人工）。防退化约束——人类未响应不超时执行。不可逆操作无论置信度强制人工确认。异步审查——高置信操作先执行暂存，异步通知人类审查。

- [功能设计 — 置信度门控器](intelligent/core/confidence-gate.md)
- [技术方案 — 置信度门控器](intelligent/core/confidence-gate.md)

### AI 服务调用 `ai`

模型调用（弹性降级 + 流式输出 + 上下文组装）+ 多模态能力封装（图像生成/处理 + 语音 ASR/TTS + 视频生成 + Embedding + Rerank）。非 Agent 场景的 LLM 和多模态统一入口。

- [功能设计 — AI 服务](intelligent/ai/ai-service.md)
- [技术方案 — AI 服务](intelligent/ai/ai-service-tech.md)

## 认知层 `cognition`

横向共享底座，被 Agent 和 Assistant 被动调用。写入/检索时用 LLM 级能力增强（Agentic RAG）。记忆与知识严格分离——记忆是用户私有交互经历（自动写入），知识是全局共享领域知识（人工维护）。学习反哺形成"越用越强"闭环。

### 记忆管道 `pipeline`

可编排的记忆处理流水线。读管道按 MemoryStrategy 从多源检索并组装上下文（步骤可配置）；写管道固定四步（提取→去重→写入→遗忘），保障数据一致性不可跳过。

- [功能设计 — 记忆管道](intelligent/cognition/memory-pipeline.md)
- [技术方案 — 记忆管道](intelligent/cognition/memory-pipeline-tech.md)

### 混合检索 `retrieval`

记忆+知识库统一检索门面。多源并行检索（向量+图谱+关键词+时序），RRF 跨源融合，LLM 重排，Value 过滤后输出 MemoryContext 注入 Prompt。

- [功能设计 — 混合检索](intelligent/cognition/retrieval.md)
- [技术方案 — 混合检索](intelligent/cognition/retrieval-tech.md)

### 学习反哺 `learning`

Agent 执行结果异步反哺认知基础。轨迹采集→效果评估→程序化蒸馏→知识生长→技能生成，失败教训同样入库，系统从错误中学习。

- [功能设计 — 学习反哺](intelligent/cognition/learning.md)
- [技术方案 — 学习反哺](intelligent/cognition/learning-tech.md)

### 用户理解 `personalization`

全面用户理解组件。实时感知（意图理解+情感感知，同步）+ 长期画像（被动接收各层事件→异步提炼）→ 统一输出当次意图、当前情绪、用户画像、偏好参数、端适配参数。

- [功能设计 — 用户感知与个性化](intelligent/cognition/personalization.md)
- [技术方案 — 用户理解](intelligent/cognition/personalization-tech.md)

## 智能体层 `agent`

无状态任务执行层。接收 Assistant 派发的原子任务，通过认知循环完成后归还池中。AgentScope ReActAgent 是执行骨架，AAF 通过 CognitiveCycleExecutor 薄门面适配。池化复用、沙箱隔离、断点续跑保障执行安全与效率。

### Agent 执行 `agent`

认知循环（感知→规划→执行→评估）+ 子任务步骤规划（PlanNotebook）+ AgentScope 适配 + 池化 + 沙箱 + 断点续跑 + 事件总线 + 工作记忆 + 注意力预算 + 执行轨迹。

- [功能设计 — 智能体层 Agent](intelligent/agent/agent-design.md)
- [技术方案 — Agent 执行](intelligent/agent/agent-tech.md)

## 助理层 `assistant`

有状态会话层，面向人的交互入口。前注意分流（<50ms）快速路由简单请求，复杂任务走完整 Agent 流程。多实例 fork 并行加速，InputBuffer 接收执行期追加输入。意图理解和情感感知由 cognition/personalization 提供。

### 助手执行 `assistant`

前注意分流（规则+小模型快速路由+缓存命中）+ 会话管理 + 任务拆解与规划 + Agent 调度 + 技能匹配 + 结果聚合与验证 + 记忆管道编排 + 学习反馈。

- [功能设计 — 助理层 Assistant](intelligent/assistant/assistant-design.md)
- [技术方案 — 助手执行](intelligent/assistant/assistant-tech.md)

### 角色与技能 `assistant/role`

Role 定义（系统 Prompt + 能力边界 + 工具白名单）+ 内置技能（自我认知 / 用户理解 / 自学习 / 技能创建）。Actor 统一抽象 Human 和 AI。

- [功能设计 — Actor 模型](operator.md)
- [技术方案 — Actor 模型](operator.md)

## 协作层 `team`

多 Assistant 协作层（v0.6+）。Leader Assistant 主控，负责目标对齐、任务分发、进度同步、冲突仲裁。通过 A2A 协议支持跨系统协作，不要求同框架/同进程。

### 团队协作 `team`

编排模式（Pipeline/Supervisor/MsgHub）+ 任务分发 + 进度同步 + 冲突仲裁。委托 AgentScope 多 Agent 编排能力。

- [功能设计 — 协作层 Team](intelligent/team/team.md)
- [技术方案 — 团队协作](intelligent/team/team-tech.md)

## 引擎层 `aaf-framework/engine`

通用执行能力层，无具体业务语义。元引擎编排 26 个专项引擎协同工作，DSL 调度 + 四层状态渐进提交。引擎间不直接互调，通过编排引擎统一协调。确定性与不确定性混合——工作流处理固定流程，Agent 处理开放任务，两者可嵌套。

### 元引擎 `meta`

跨层编排中枢，将意图转化为执行，将执行转化为知识。渐进提交——结果先暂存，确认后持久化，未确认自动销毁。防退化——人类未响应不超时执行。

#### 执行调度器 `dispatcher`

DSL 路由 + Agent 启用判断 + 生命周期管理 + 降级策略。决定走工作流还是 Agent 还是混合路径。

- [功能设计 — 执行调度器](engine/meta/execution-dispatcher.md)
- [技术方案 — 执行调度器](engine/meta/execution-dispatcher.md)

#### 编排运行时 `runtime`

编排图遍历 + 节点执行环境 + 变量池 + 暂停/恢复。响应式执行管道（Virtual Threads + StructuredTaskScope）。

- [功能设计 — 编排引擎](engine/meta/orchestration.md)
- [技术方案 — 响应式管道](engine/meta/runtime.md)

#### 编排状态 `state`

执行记录 + Checkpoint + 渐进提交（暂存→确认→持久化）。四层状态严格隔离，支持工作区多维并发。

- [功能设计 — 编排状态](engine/meta/state-manager.md)
- [技术方案 — 状态管理器](engine/meta/state-manager.md)

#### 知识库引擎 `knowledge`

文档集合的语义化聚合——分块 + 向量化入库 + 图谱构建 + 混合检索 + RAG + 增量更新。输入来自文档引擎，管"一组文档的可发现性"。

- [功能设计 — NexusKB 知识引擎](engine/data-knowledge/nexus-knowledge.md)
- [技术方案 — 知识库引擎](engine/data-knowledge/nexus-knowledge-tech.md)

#### 记忆引擎 `memory`

原子记忆存储 + 双时态索引 + Bundle 检索 + 时间衰减 + 短期/长期/情景/情感/程序性记忆读写。纯存储层，不含 LLM 逻辑。

- [功能设计 — AtomMemory 记忆引擎](engine/data-knowledge/atom-memory.md)
- [技术方案 — 记忆引擎](engine/data-knowledge/atom-memory-tech.md)

#### 语义计算引擎 `semanticcalc`

Embedding + 相似度 + NER + 关系抽取 + 聚类 + 漂移检测 + 摘要。横切支撑多个认知与业务组件的通用语义能力。

- [功能设计 — 语义计算引擎](engine/data-knowledge/semantic-compute.md)
- [技术方案 — 语义计算引擎](engine/data-knowledge/semantic-compute-tech.md)

#### 数据处理引擎 `dataprocess`

批流处理管道 + 统计分析 + 表格处理。清洗→字段映射→路由→AI 增强，步骤可编排。

- [功能设计 — 数据处理引擎](engine/data-knowledge/data-process-engine.md)
- [技术方案 — 数据处理引擎](engine/data-knowledge/data-process-tech.md)

#### 外部数据源引擎 `datasource`

ETL 导入 + 联邦查询 + DataSourceAdapter。统一抽象外部系统数据对接（Jdbc / Http / File → DataSet）。

- [功能设计 — 外部数据源引擎](engine/data-knowledge/external-datasource.md)
- [技术方案 — 外部数据源引擎](engine/data-knowledge/external-datasource-tech.md)

### 执行与编排引擎组

任务执行、流程编排、工具调用、技能匹配。确定性+不确定性混合编排——工作流节点可嵌入 Agent 任务，固定流程与动态规划共存。工具即能力，权限校验+沙箱隔离保障安全。

#### 工作流引擎 `workflow`

Flowable 封装 + BPMN + Agent 节点嵌入。确定性流程骨架，节点内 Agent 自主执行。

- [功能设计 — 工作流引擎](engine/execution/workflow.md)
- [技术方案 — 工作流引擎](engine/execution/workflow-tech.md)

#### 工具引擎 `tool`

注册发现（Spring Bean + MCP 协议）+ 调用分发 + 权限校验 + 脚本沙箱。Agent 通过工具引擎获得外部能力。

- [功能设计 — 工具引擎](engine/execution/tools.md)
- [技术方案 — 工具权限](engine/execution/tool-permission.md)

#### 技能引擎 `skill`

技能定义 + 匹配路由 + 内置技能（自我认知/用户理解/自学习/技能创建）。技能是 Agent 能力的高层封装。

- [功能设计 — 技能引擎](engine/execution/skills.md)
- [技术方案 — 技能引擎](engine/execution/skill-tech.md)

### 交互与内容引擎组

内容管理、提示词、UI 组装、DSL 解析。一切皆文档——所有制品以文档形式存储。DSL 三重身份（规范/生成目标/执行程序），贯穿开发时和运行时。语义组件——后端输出 DSL 描述"展示什么"，前端决定"怎么展示"。

#### Prompt 引擎 `prompt`

提示词库管理 + 版本控制 + 链式组装 + Few-shot 管理 + 评估优化。`core/prompt/` 保留调用门面，引擎层承载完整生命周期。

- [功能设计 — Prompt 引擎](engine/content/prompt.md)
- [技术方案 — Prompt 引擎](intelligent/core/prompt.md)

#### 文档引擎 `document`

"一切皆文档"的结构化容器——七类文档统一管理 + 版本控制 + 协同编辑。管单个文档的生命周期，是知识库引擎的上游数据源。

- [功能设计 — 文档引擎](engine/content/document-engine.md)
- [技术方案 — 文档引擎](engine/content/document-tech.md)

#### 语义组件引擎 `senseui`

DSL 驱动 UI 组装 + 组件注册 + 多端适配。后端输出组件树，前端渲染，实现对话式生成界面。

- [功能设计 — 语义组件引擎](engine/content/sense-ui.md)
- [技术方案 — 语义组件引擎](engine/content/sense-ui-tech.md)

#### DSL 引擎 `dsl`

多范式解析（声明式/命令式/函数式/自然语言混合）+ 分层转化（L1→L2→L3）+ 分域路由（dev/runtime/doc）。

- [功能设计 — Magic-DSL 领域语言](dsl/magic-dsl.md)
- [技术方案 — DSL 引擎](dsl/dsl-engine.md)

### 运营与治理引擎组

权限、监控、成本管控、积分结算、价值规则。Actor 统一抽象——Human 和 AI 共用一套权限体系。成本可控——执行前预估、执行中监控、超限自动暂停。AI 可观测——每次 LLM 调用完整追踪。

#### 监控引擎 `monitor`

LLM 链路追踪 + Agent 轨迹 + Token 统计 + 审计日志。纯观测层，只采集/分析/告警，不干预执行。

- [功能设计 — 监控引擎](engine/governance/monitor.md)
- [技术方案 — 监控引擎](engine/governance/monitor-tech.md)

#### 预算控制引擎 `budget`

预估 + 监控 + 暂停 + Token 计量。四维预算（Token/时间/工具调用/费用），三层级约束（系统→Assistant→任务）。

- [功能设计 — 预算控制引擎](engine/governance/budget-control.md)
- [技术方案 — 预算控制引擎](engine/governance/budget-control.md)

#### 积分引擎 `credit`

积分账户 + 贡献量化 + 规则执行。虚拟积分体系，DSL 定义规则，热生效。

- [功能设计 — 积分与结算引擎](engine/governance/credit-settlement.md)
- [技术方案 — 积分引擎](engine/governance/credit-tech.md)

#### 结算引擎 `settlement`

支付接口 + 商品订单 + 结算记录 + 争议仲裁。真实资金进出，与积分引擎通过业务服务层协作。

- [功能设计 — 积分与结算引擎](engine/governance/credit-settlement.md)
- [技术方案 — 结算引擎](engine/governance/settlement-tech.md)

#### 价值规则引擎 `valuerule`

规则定义 + 优先级仲裁 + 行为校验。伦理边界/优先级规则/交互规范/降级边界/合规约束，支撑价值观系统。

- [功能设计 — 价值规则引擎](engine/governance/value-rule.md)
- [技术方案 — 价值规则引擎](engine/governance/value-rule-tech.md)

### 生态与扩展引擎组

插件生态、推荐、搜索、空间模型、系统自进化。开放生态——插件热插拔+沙箱隔离。系统越用越强——执行轨迹→效果评估→规范更新→代码重生成，形成自进化闭环。

#### 元数据引擎 `metadata`

四类元数据（模块/插件/工具/组件）统一注册 + 语义漂移检测 + 规范变更触发链。

- [功能设计 — 元数据管理器](engine/meta/metadata-manager.md)
- [技术方案 — 元数据管理器](engine/meta/metadata-manager.md)

#### 自进化引擎 `evolution`

行为采集 + 效果评估 + 规范更新 + 代码重生成。沙箱验证后热部署，强制人工审核。

- [功能设计 — 自进化机制](engine/meta/evolution.md)
- [技术方案 — 自进化机制](engine/meta/evolution.md)

#### 空间引擎 `space`

物理时空模型 + 语义引力 + 时间流。为虚拟空间、知识图谱布局、记忆时间线提供统一时空坐标。

- [功能设计 — 物理时空引擎](engine/ecosystem/physics-spacetime.md)
- [技术方案 — 空间引擎](engine/ecosystem/space-tech.md)

#### 插件引擎 `plugin`

动态加载 + 沙箱隔离 + 版本管理。支持第三方贡献 Agent/工具/知识库/组件，热插拔无需重启。

- [功能设计 — 插件引擎](engine/ecosystem/plugin.md)
- [技术方案 — 插件引擎](engine/ecosystem/plugin-tech.md)

#### 推荐引擎 `recommendation`

协同过滤 + 语义相似 + 上下文感知。市场推荐、技能推荐、工具推荐。

- [功能设计 — 推荐引擎](engine/ecosystem/recommendation.md)
- [技术方案 — 推荐引擎](engine/ecosystem/recommendation-tech.md)

#### 搜索引擎 `search`

跨资源统一搜索 + 权限过滤 + RRF。一个查询并行检索所有资源类型，融合排序后返回。

- [功能设计 — 搜索引擎](engine/ecosystem/search.md)
- [技术方案 — 搜索引擎](engine/ecosystem/search-tech.md)

## Auto-Dev `aaf-auto-dev`

AI 自动开发模块。对话式开发体验，项目文档语义检索辅助，监控对接。基于 AgentScope 实现开发 Agent，AAF 提供会话管理和文档智能能力。

### AI 开发 Agent `agent`

Kiro Agent 对话式开发——会话管理 + 消息持久化 + 流式交互。开发者通过对话驱动代码生成、分析、优化。

- [功能设计 — Auto-Dev](auto-dev/auto-dev.md)
- [技术方案 — Auto-Dev Agent](auto-dev/auto-dev-tech.md)

### 文档智能 `doc`

项目文档导入 + 语义检索 + 关系图谱 + 树形结构管理。为开发 Agent 提供项目上下文。

- [功能设计 — Auto-Dev](auto-dev/auto-dev.md)
- [技术方案 — 文档智能](auto-dev/auto-dev-tech.md)

### 监控接口 `monitor`

kiro-cli 监控对接（规划）。开发过程可观测性。

- [功能设计 — Auto-Dev](auto-dev/auto-dev.md)
- [技术方案 — Auto-Dev 监控](auto-dev/auto-dev-tech.md)

## 横切能力 `aaf-framework`

贯穿所有层的通用基础设施。不属于任何特定层，被所有层共享依赖。安全与权限体系实现 Actor 统一抽象（Human 和 AI 共用），AI 安全包含 Prompt 注入防御和输出审查。

- CRUD 基类 `crud`：BaseCrudService / BaseCrudController
- 防护机制 `protection`：限流 / 幂等 / 分布式锁
- 文件存储 `storage`：StorageService + Local / S3
- 序列号生成 `sequence`：模板化序列号 + 分布式唯一 ID
- 数据权限 `data`：@DataScope + JPA 拦截器
- 操作日志 `logging`：AOP 切面 + 审计拦截器
- 消息通知 `messaging`：多渠道发送（站内/邮件/短信/微信）+ 模板渲染 + 渠道适配
- 异步调度 `task`：异步队列（Redis Stream）+ 定时触发 + 重试策略 + 分布式锁
- 安全与权限 `security`：认证（JWT/OAuth/API Key）+ 授权（RBAC/ReBAC/ABAC）+ 记录规则 + Actor 统一抽象 + 数据脱敏 + 加密 + AI 安全（Prompt 注入防御/输出审查）
- 框架自动配置 `config`：Bean 注册 + 条件装配

**设计文档：**

- [功能设计 — 访问控制](security/access-control.md)
- [功能设计 — 安全架构](security/security.md)

## 相关文档

- [整体架构概览](../architecture.md)
- [元引擎设计](engine/meta/meta-engine.md)
- [执行逻辑全景](execution-flow.md)
