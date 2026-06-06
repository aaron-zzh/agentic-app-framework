---
level: Practice
layer: Model
purpose: AAF 后端服务技术选型与决策记录
status: published
version: 2.0.0
date: 2026-05-08
author: AaronZZH
gains:
  - 了解后端各技术选型的决策依据
  - 新成员能快速理解技术栈选择原因
  - 明确框架内置能力边界与元引擎迁移路径
---

# 后端技术选型（service）

> 本选型服务于 AAF 六大核心能力与五层架构，背景见 [架构设计](../../architecture.md)。
> **第一性原理**：减少运行时复杂度、最大化 AI 原生能力、最小化运维成本。
> **双模式并存**：传统 MVC（稳定可控，安全基础设施）与元引擎无代码（DSL 驱动，配置即运行）长期互补。业务逻辑逐步迁移到元引擎，安全基石永久硬编码。

## 一、Java 25 核心特性

| 特性 | JEP | 价值 |
|------|-----|------|
| Virtual Threads | 正式 | 百万级轻量线程，替代 WebFlux 复杂度，同步写法异步性能 |
| Scoped Values | 506 | 替代 ThreadLocal，跨方法链传递上下文，适配虚拟线程 |
| Structured Concurrency | 505 (Preview) | 多子任务并行编排（Agent 并发调用多 LLM），统一错误处理 |
| Record | 正式 | 不可变数据载体，DTO/VO/Event 首选 |
| Sealed Classes | 正式 | 有限状态建模（工作流节点、Agent 状态机） |
| Pattern Matching (switch) | 正式 | 类型安全分支逻辑，替代 if-else/visitor |
| Flexible Constructor Bodies | 513 | 构造器内 super() 前校验参数 |
| Compact Object Headers | 519 | 对象头 12→8 字节，向量 Embedding 场景节省内存 |
| AOT Class Loading | 514/515 | 启动加速 + 热点预编译 |
| Stable Values | 502 (Preview) | 线程安全懒初始化，替代 double-checked locking |

Preview 特性（Structured Concurrency、Stable Values）在 `aaf-framework` 封装，业务层不直接依赖，版本升级可平滑切换。

## 二、Spring Boot 4 / Spring AI 2.0

### 2.1 框架核心

| 特性 | 说明 |
|------|------|
| Jakarta EE 11 | Servlet 6.1 + JPA 3.2 + Bean Validation 3.1 |
| JSpecify Null Safety | 全栈 null 安全注解，编译期消除 NPE |
| Jackson 3 | 默认 JSON 序列化，更好的 Record 支持 |
| HTTP Service Clients | 声明式 HTTP 接口（替代 Feign） |
| API Versioning | 内置 API 版本管理（MVC/WebFlux） |
| Virtual Threads 自动适配 | Tomcat 零配置使用虚拟线程处理请求 |
| OpenTelemetry Starter | 内置 OTLP 导出（metrics + traces） |
| Modular Auto-Configuration | 按需加载减少启动开销 |
| GraalVM Native Image | 生产级原生编译，秒级启动 |
| SSL/TLS 自动管理 | 证书健康检查 + 自动轮换 |

### 2.2 AI 原生能力（Spring AI + AgentScope）

| 能力 | 说明 |
|------|------|
| ChatClient 统一抽象 | 模型无关（OpenAI/Ollama/Anthropic），一套代码切换模型 |
| Tool Calling | 声明式工具注册，LLM 自动调用 Java 方法 |
| MCP Client/Server | `@McpTool` / `@McpResource` / `@McpPrompt` 注解式开发 |
| A2A Protocol | Agent-to-Agent 通信标准，跨系统协作 |
| Advisor Chain | 请求/响应拦截链（RAG、日志、安全过滤） |
| Vector Store 抽象 | PgVector/Milvus 统一接口 |
| Chat Memory | JDBC 持久化对话记忆 |
| Document Reader | Tika/Markdown/Jsoup 多格式解析 |
| Structured Output | 类型安全 LLM 输出解析（直接映射 Record） |
| Evaluation Framework | AI 输出质量评估（相关性、忠实度）+ 用户反馈收集 + RAGAS 指标|
| AgentScope Java | 多智能体编排 Starter，统一提供 Token 统计、模型路由、Memory、RAG、Human-in-the-Loop、Observability Studio、Hook System、AG-UI/A2A、Pipeline/Supervisor/Handoffs |
| LLM 可观测性 | 基于 OTLP + AgentScope Observability Studio，Agent 执行可视化 + 调用链追踪 + Token 用量分析 |
| Prompt 模板管理 | 系统级 Prompt 版本化存储 + 动态加载（基于 AgentScope + 文档引擎） |
| Token 用量统计 | 每次 LLM 调用消耗记录，支持配额控制和成本分析（AgentScope getChatUsage() 内置） |


### 2.3 AAF 独有 AI 能力

| 能力 | 说明                                                                                                                                                                                              |
|------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 认知层（Cognition） | Memory + Knowledge + Value（价值观） + Retrieval（融合检索） + Learning 横切，基于 PgVector/Neo4j/Redis 自实现，借鉴 Mem0/Graphiti/Cognee/LightRAG，不集成 Python 框架。详见 [认知层设计](../../framework/intelligent/cognition/cognition.md) |
| 情感感知 | 文本情绪分类 + 操作节奏分析，驱动回应风格/信息密度自适应；情感记忆本地加密，不外传                                                                                                                                                     |
| 置信度门控 | >0.9 自动 / 0.7-0.9 确认 / <0.7 转人工；不可逆操作强制人工；结果暂存后提交                                                                                                                                               |
| 输出溯源 | 每条输出携带 TraceId（agentId + modelId + toolChain + 知识来源），审计归档                                                                                                                                       |
| Prompt 注入防御 | 输入净化（动态 UUID 分隔符包裹用户输入）+ 意图一致性校验                                                                                                                                                                |
| LLM 响应缓存 | 相同 prompt + 参数缓存响应，降低 Token 成本（Advisor + Redis）                                                                                                                                                 |
| 内容审核 | 输入/输出敏感词过滤，优先对接云服务（阿里云/腾讯云内容安全 API）                                                                                                                                                             |
| 语音处理 | ASR/TTS 对接云服务（阿里云/火山引擎/OpenAI Whisper API）                                                                                                                                                      |
| 图像处理 | OCR/多模态对接云服务（阿里云 OCR / GPT-4V / Gemini Vision）                                                                                                                                                  |

## 三、核心依赖清单

仅列需要锁定或新增引入的依赖，标准 Spring Boot Starter 默认引入不再列出。所有版本由 `aaf-dependencies` BOM 统一管理。

### 3.1 Web / API

| 用途 | 依赖 | 备注 |
|------|------|------|
| Web | Spring MVC + Virtual Threads | 同步编程模型 + 虚拟线程 |
| 流式推送 | SseEmitter | AI 流式输出天然适配 |
| 双向通信 | WebSocket | 仅多人协作场景 |
| API 文档 | SpringDoc OpenAPI 3 | REST 自动生成 Swagger UI |
| 灵活查询 | Spring GraphQL | 前端灵活查询/知识图谱，与 REST 并存 |
| 参数校验 | spring-boot-starter-validation | Bean Validation 3.1 |
| 邮件 | spring-boot-starter-mail | 通知/验证码 |

### 3.2 数据访问

| 用途 | 依赖 | 备注 |
|------|------|------|
| ORM | Spring Data JPA + Hibernate 7 | JPA 3.2 |
| 关系 + 向量 | PostgreSQL 17 + PgVector | 一库双职，减少组件 |
| 图数据库 | Neo4j 5 | 知识图谱、文档关系 |
| 图查询 DSL | neo4j-cypher-dsl | 类型安全 Cypher 构建 |
| 分布式缓存 | Redis 7 + Lettuce | 连接池 + Spring Cache 接入 |
| 本地缓存 | Caffeine | Spring Cache 两级缓存 |
| 全文检索 | PostgreSQL FTS | 中小规模够用，避免 ES |
| Schema 迁移 | Flyway | 版本化 DDL |

### 3.3 业务能力

| 用途 | 依赖 | 备注                                      |
|------|------|-----------------------------------------|
| 工作流 | Flowable 8 | BPMN 2.0，官方支持 Spring Boot 4 + Jackson 3 |
| 规则引擎 | Easy Rules | 轻量；复杂场景升级 Drools                        |
| DSL 解析 | ANTLR4 | Magic-DSL 语法解析                          |
| 代码生成模板 | FreeMarker | 代码生成器，JDK 25 兼容                         |
| AST 操作 | JavaParser | 运行时代码分析与改写（auto-dev）                    |
| 热部署诊断 | Arthas | 运行时诊断 + 类热替换                            |
| 实时协同 | Yjs (CRDT) | 多人协作冲突合并                                |
| 沙箱执行 | GraalVM Polyglot | Agent 运行时隔离                             |

### 3.4 安全 / 防护

| 用途 | 依赖 | 备注 |
|------|------|------|
| 认证授权 | Spring Security 7 + JWT | OAuth2 Resource Server + Method Security |
| 无密码认证 | Spring Security WebAuthn | 指纹/Face ID/硬件密钥 |
| 关系权限图存储 | Neo4j 5 | ReBAC 关系元组 + 权限继承路径遍历（见 3.2） |
| 权限缓存 | Redis 7 | 正向/负向结果缓存，TTL 5min，变更主动失效 |
| 容错 | Resilience4j | 熔断/重试/超时/舱壁 |
| 限流/配额 | Bucket4j | Token 桶，支持 Token 预算感知 |
| 配置加密 | Jasypt | 本地兜底，生产用 Vault/KMS |
| 密钥管理 | HashiCorp Vault / 云 KMS | 密钥托管 + 自动轮换（90 天数据密钥 / 年度主密钥） |
| XSS 过滤 | Jsoup | HTML Safelist 白名单净化 |
| 沙箱隔离 | GraalVM Polyglot | Agent 不可信代码执行隔离（见 3.3） |

### 3.5 工具 / 集成

| 用途 | 依赖 | 备注 |
|------|------|------|
| 对象映射 | MapStruct + Lombok | 编译期代码生成 |
| 文件存储 | MinIO / 阿里云 OSS / AWS S3 | 统一 FileStorage 接口 |
| 文件导出 | Apache Fesod 2.0.1-incubating | 原 EasyExcel → FastExcel → Fesod |
| PDF 导出 | iText 8 | 报表 PDF |
| Word 导出 | poi-tl | 模板驱动 |
| Markdown 解析 | commonmark-java | 知识库文档导入 |
| User-Agent 解析 | yauaa | 登录日志设备识别 |
| 系统信息 | oshi-core-java25 6.12+ | 跨平台 CPU/内存/磁盘监控 |
| IP 归属地 | ip2region | 纯本地库 |
| 汉字转拼音 | TinyPinyin | 轻量低内存 |

### 3.6 可观测性

| 用途 | 依赖 | 备注 |
|------|------|------|
| 指标 | Micrometer + Prometheus + Grafana | Spring Boot 4 内置 OpenTelemetry |
| 日志 | SLF4J + Logback + Structured JSON | 便于采集 |
| 错误告警 | Sentry | 生产异常 + 堆栈聚合 + 性能追踪 |

### 3.7 构建 / 测试

| 用途 | 依赖 | 备注                   |
|------|------|----------------------|
| 构建 | Maven 3.9+ | Nx 桥接                |
| 格式化 | Spotless + Google Java Format | 代码格式化，CI 强制          |
| 单元测试 | JUnit 5 + Mockito | -                    |
| 架构守护 | ArchUnit | 分层规则                 |
| 测试数据 | Instancio | 支持 Record 自动生成       |
| 覆盖率 | JaCoCo | 测试覆盖率度量，CI 质量门控      |
| 漏洞扫描 | OWASP dependency-check | CI 扫描 CVE            |
| Git 注入 | git-commit-id-maven-plugin | Actuator /info       |
| 热重启 | spring-boot-devtools | 代码变更自动重启，加速本地开发，生产 profile 自动排除 |

## 四、关键架构决策

| 决策 | 选择 | 放弃 | 理由 |
|------|------|------|------|
| 并发模型 | Virtual Threads + 同步 MVC | WebFlux 全栈 | JDK 25 虚拟线程正式，SSE 场景保留响应式 |
| ORM | JPA + Hibernate 7 | MyBatis / MyBatis-Plus | 避免双 ORM，JPA 3.2 能力足够 |
| 工具库 | JDK + Spring 原生 | Hutool | 标准库已覆盖，避免重复 |
| 关系 + 向量 | PostgreSQL + PgVector | 独立向量库（Milvus/Weaviate） | 一库双职，减少组件和运维 |
| 关系 + 图 | PostgreSQL + Neo4j 并存 | 纯关系库 | 多跳关系 Cypher 远优于 SQL JOIN，职责互补不重叠 |
| 工作流 | Flowable 8 | 自研 | BPMN 2.0 标准 + DSL 驱动 + Spring Boot 集成成熟 |
| LLM 抽象 | Spring AI 2.0 | LangChain4j | Spring 生态原生 + 模型无关 + 向量抽象与 PgVector 一致 |
| 流式推送 | SSE | WebSocket 全量 | SSE 单向够用；WebSocket 仅协作场景 |
| Preview 特性 | framework 层封装 | 业务层直用 | 版本升级可平滑切换 |

## 五、五层架构 → Maven 模块映射

```text
Layer 5  对话与交互层  → aaf-api（REST + SSE/WebSocket 端点，启动入口）
Layer 4  服务层        → aaf-api/module/（system/document/chat/autodev 分包隔离）
Layer 3  智能层        → aaf-framework: intelligent/（core/agent/cognition/assistant/team）
Layer 2  引擎层        → aaf-framework: engine/（调度机制 + 专项引擎）
Layer 1  基础设施层    → aaf-common + PostgreSQL + Neo4j + Redis
```

| 模块 | 职责 |
|------|------|
| aaf-dependencies | 依赖版本 BOM，纯 pom，禁 Java 代码 |
| aaf-common | 工具类、常量、异常、基础实体，零业务依赖 |
| aaf-framework | 引擎层 + 智能层 + 框架内置能力 |
| aaf-auto-dev | AI 驱动代码生成与自进化 |
| aaf-api | 业务模块 + 启动入口 |

v0.1.0 采用单启动模块 + 分包隔离，后续按需拆分为独立 Maven 模块。详细目录见 [模块结构](module-structure.md)。

## 六、框架内置能力（开箱即用）

> 本节只列**框架自研封装**的能力——即在第三节依赖之上做了额外抽象/注解/策略的部分。纯依赖引入即可用的能力（如 Caffeine 缓存、Resilience4j 熔断、Jsoup XSS 过滤、Bean Validation、JPA `@Version`）不再重复，见第三节。

### 6.1 请求与响应基础（aaf-common）

| 能力 | 封装价值 |
|------|---------|
| 统一响应封装 | `Result<T>` 标准响应体（code/message/data/timestamp） |
| 错误码体系 | 错误码注册表 + `BusinessException`，模块级区段划分，前端按 code 国际化 |
| 分页查询封装 | `PageRequest` / `PageResult<T>` 统一分页协议 |
| 基础实体 | `BaseEntity`（id/createTime/updateTime/deleted），逻辑删除 + 审计字段自动填充 + 乐观锁 |

### 6.2 Web 与 API（aaf-api/config）

| 能力 | 封装价值 |
|------|---------|
| 全局异常处理 | `@RestControllerAdvice` 统一捕获 → 错误码映射 → 标准 `Result` 响应 |
| 消息推送 | WebSocket/SSE 统一推送封装（系统通知/任务进度/Agent 状态），屏蔽协议差异 |
| 多语言国际化 | i18n 错误码/消息多语言，`MessageSource` + 错误码体系联动 |

### 6.3 安全与权限

> 四层权限模型与安全横切设计详见 [访问控制设计](../../framework/security/access-control.md) 和 [安全架构设计](../../framework/security/security.md)。

| 能力 | 封装价值 |
|------|---------|
| 四层权限检查 | RBAC（Spring Security 原生）+ ReBAC（Neo4j 图遍历）+ 记录规则（JPA 拦截器）+ ABAC（策略引擎），统一 Actor 抽象 |
| 数据权限 | `@DataScope` 按组织/部门/团队/个人多级过滤，SQL 自动注入 |
| 数据脱敏 | `@Sensitive` 注解，响应/日志中敏感字段自动脱敏 |
| 列加密 | JPA AttributeConverter 应用层加密，L3/L4 数据保护 |
| API Key 管理 | B2B 外部系统鉴权，支持配额/过期/撤销 |
| 实时交互授权 | Agent 运行时请求额外权限 → WebSocket 推送 → 会话级临时授权 |
| AI 数据安全 | LLM 发送前脱敏引擎 + 多模型分片 + Prompt 注入防护 |
| 验证码 | 图片/短信/邮件验证码，Redis 存储 + 过期失效 |
| 登录失败锁定 | Redis 计数 + 临时封禁 |

### 6.4 并发与可靠性

| 能力 | 封装价值 |
|------|---------|
| 幂等控制 | `@Idempotent` 注解 + Redis，声明式防重复提交 |
| 分布式锁 | `@DistributedLock` 注解 + Redis，声明式加锁 |
| 配额与限流 | 在 Resilience4j/Bucket4j 之上封装按用户/租户/API 维度的 Token 预算感知配额，超额告警/降级 |
| 事件总线 | Spring ApplicationEvent 封装，模块间解耦通信 |
| 轻量任务队列 | PostgreSQL 持久化 + Redis 通知 + 失败重试/死信/削峰/延迟执行，零额外中间件。详见 [任务队列设计](../../../design/apps/service/task-queue.md) |
| 定时任务 | `@Scheduled`（无状态）+ Quartz（持久化/动态管理），按场景选择 |

### 6.5 日志与可观测

| 能力 | 封装价值 |
|------|---------|
| 操作日志 | `@OperationLog` 注解切面，SpEL 模板 + bizNo 关联（借鉴 bizlog-sdk，自研实现） |
| 登录日志 | 记录登录时间/IP/设备/成功失败，支持异常登录告警 |
| 链路追踪 | TraceId 自动注入（MDC + 虚拟线程适配），全链路可追踪 |
| 健康检查扩展 | 自定义 HealthIndicator（LLM 可用性/向量库连通性/Neo4j 连通性） |

### 6.6 数据与存储

| 能力 | 封装价值 |
|------|---------|
| 文件存储 | 统一 `FileStorage` 接口，多后端配置切换（依赖见 3.5，此处是接口抽象层） |
| 系统参数 | 运行时动态业务配置（区别于字典枚举），支持类型化读取 |
| VO 数据翻译 | `@Trans` 注解序列化时自动翻译字典值/用户 ID/部门 ID 为显示名（基于 Jackson Serializer 自研） |
| 语义检索策略 | 在 PgVector + PostgreSQL FTS 之上封装精确/全文/语义三层递进检索策略 |

### 6.7 外部集成

| 能力 | 封装价值 |
|------|---------|
| 第三方集成 | 企业微信/钉钉/飞书统一接入层（消息推送/组织架构同步/审批流转） |
| 短信服务 | 统一 `SmsSender` 接口，多厂商实现（阿里云/腾讯云/Twilio），配置切换 |
| 支付对接 | 统一 `PaymentService` 接口，微信支付/支付宝配置切换渠道 |
| IoT 设备接入 | 阿里云 IoT Platform + Spring Integration MQTT 消费封装 |

### 6.8 AI 原生能力

| 能力 | 封装价值 |
|------|---------|
| AI 对话封装 | ChatClient 统一入口 + 记忆管理 + 流式 SSE（对 Spring AI ChatClient 的业务增强） |
| 工具白名单 | 工具注册表 + 白名单校验器，Agent 调用前强制校验 |
| 输出溯源 ID | traceId + agentId + modelId + toolChain，与 MDC 联动审计 |
| 情感记忆存储 | 本地加密存储（AES）+ 用户私有区隔离，不进入模型训练、不外传 |

### 6.9 引擎能力

| 能力 | 封装价值 |
|------|---------|
| DSL 解析封装 | 在 ANTLR4 之上封装 Magic-DSL 语法骨架，dev/runtime/doc 三域解析。详见 [Magic-DSL 设计](../../framework/dsl/magic-dsl.md) |
| AI 工作流编排 | DSL 定义 AI 流程 → Flowable 实例化执行，v0.3+ 自动暴露为 REST API |
| 规则引擎封装 | 在 Easy Rules 之上封装 DSL 驱动规则定义，支持促销/通知/权限规则运行时热更新 |
| 热部署能力 | 自定义 ClassLoader + Arthas API 封装，aaf-auto-dev 生成代码沙箱验证后热加载 |
| 物理时空引擎 | 世界模型 + 物质定义 + 物理规则（语义相似度驱动聚合），v2.0 对接 actormesh。详见 [物理时空引擎设计](../../../design/framework/engine/ecosystem/physics-spacetime.md) |

### 6.10 业务模块（aaf-api/module/system）

| 能力 | 封装价值 |
|------|---------|
| 菜单管理 | 树形菜单定义 + 动态路由，与权限系统联动 |
| 部门管理 | 组织架构树，支持数据权限按部门过滤 |
| 字典管理 | 系统字典定义 + 缓存，前端下拉选项统一管理 |

### 6.11 开发工具（aaf-auto-dev）

| 能力 | 封装价值 |
|------|---------|
| 代码生成器 | 表结构 → CRUD 代码（Controller/Service/Repository/Entity/VO） |

## 七、业务实现路径：双模式并存

### 7.1 迁移分类

| 分类 | 含义 |
|------|------|
| 🔴 永不迁移 | 元引擎启动依赖 / 安全基石，永久硬编码 |
| 🟡 部分迁移 | 核心骨架保留硬编码，扩展点迁移到 DSL |
| 🟢 全迁移 | 元引擎成熟后迁移到无代码 |

### 7.2 模块清单

| 模块 | 分类 | 理由 |
|------|------|------|
| 用户认证 / RBAC 核心 / 系统配置 / 操作日志 / 任务队列 / 文件存储 / 监控 | 🔴 | 安全基石 + 框架基础设施，必须硬编码可审计 |
| 权限点/角色/数据范围定义 | 🟡 | 鉴权逻辑不动，规则定义走 DSL |
| 聊天/对话（消息收发/SSE） | 🟡 | 通道不动，对话策略/路由走 DSL |
| 文档服务（存储/版本/解析） | 🟡 | 引擎不动，文档类型/元数据走 DSL |
| 计费/积分 / 支付对接 | 🟡 | 事务/SDK 不动，规则/路由走 DSL |
| 消息通知 | 🟢 | 通知规则 + 模板完全可 DSL 化 |
| 知识库管理 | 🟢 | CRUD + 检索策略配置 |
| 客户/联系人/项目/任务/商品 | 🟢 | 动态实体 + 字段变更频繁 |
| 订单流程（发货/售后）/ 审批 | 🟢 | 状态机 + 工作流运行时 |
| 促销/优惠规则 | 🟢 | 规则引擎天然场景 |
| 表单/问卷 / 报表/仪表盘 | 🟢 | 动态定义 + 数据收集/展示 |
| Agent 配置/编排 | 🟢 | DSL 定义能力/工具/策略 |

### 7.3 元引擎运行时路线（v0.2+）

| 运行时 | 版本 | 核心技术 | 说明 |
|-------|------|---------|------|
| **实体运行时** | v0.2+ | ALTER TABLE 动态加列 + JPA MetaModel + 自动生成 API | DSL 定义实体 → 动态建表 → 自动 CRUD；用户自定义字段详见 [custom-fields.md](../../framework/intelligent/core/custom-fields.md) |
| **工作流运行时** | v0.3+ | Flowable 动态部署 + Magic-DSL | DSL 描述流程 → Flowable 实例化 |
| **权限运行时** | v0.4+ | Spring Security + 规则引擎 + DSL | DSL 定义权限规则 → 动态鉴权 |
| **规则引擎运行时** | v0.4+ | Easy Rules / Drools + DSL | 促销/通知/数据权限规则 |
| **自定义逻辑挂载** | v0.5+ | Spring Cloud Function + 自定义 ClassLoader + 沙箱 | AI 生成代码 → 沙箱验证 → 热加载 |
| **自进化闭环** | v0.9+ | 行为采集 + 效果评估 + 规范更新 + 代码重生成 | Learning 层闭环，详见认知层设计 |

详见 [元引擎设计](../../framework/engine/meta/meta-engine.md) 与 [路线图](../../../prd/roadmap.md)。

### 7.4 设计约束

1. **跨模块通过接口交互**：模块间调用走 `api/` 包接口 + DTO；模块内 Service 单实现不加接口（避免样板代码）
2. **实体设计标准化**：字段命名、类型、关联关系遵循统一规范，方便 DSL 描述
3. **业务规则外置**：校验/计算集中到独立方法，不散落在 Controller
4. **状态机显式化**：枚举 + 状态转换方法，不用 if-else 隐式控制

## 八、不选 / 暂缓的技术

| 技术 | 原因 |
|------|------|
| R2DBC | 虚拟线程下 JDBC 已非阻塞瓶颈，R2DBC 增加复杂度无收益 |
| Elasticsearch | PostgreSQL FTS 中小规模够用，按需引入 |
| Kafka / RabbitMQ | v0.1.0 单体，事件用 Spring ApplicationEvent |
| Testcontainers | ADR-002：本地真实 DB + CI service container |
| EasyExcel / FastExcel | 已进化为 Apache Fesod 2.0.1 |
| bizlog-sdk (mzt-biz-log) | 自研 AOP 切面更可控，避免低频维护依赖 |
| easy-trans | 绑定 MyBatis-Plus，自研 Jackson Serializer |
| Knife4j | SpringDoc OpenAPI 原生够用 |
| Druid | HikariCP 是 Spring Boot 默认且性能更优 |
| Spring Cloud Alibaba / Nacos | v0.1.0 单体，不需微服务治理 |
| Transmittable ThreadLocal | Java 25 Scoped Values 替代 |
| FastJSON / fastjson2 | Jackson 是默认，类型更安全 |
| Kubernetes / Docker Compose | 本地直连 + CI service container |
