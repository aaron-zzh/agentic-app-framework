---
level: Practice
layer: Product
purpose: AAF 后端 Java 服务端核心技术特性选型（基于 Spring Boot 4 + Java 25）
status: draft
version: 1.0.0
date: 2026-05-08
author: AaronZZH
---

# Java 服务端技术选型（Spring Boot 4 + Java 25）

## AAF 核心定位

AAF 是 **AI 原生**框架，AI 是架构一等公民，不是附加物。判断标准：把 AI 拿掉，系统还能正常运转吗？能 → AI 增强；不能 → AI 原生。

**六大核心能力**：

| 能力 | 说明 |
|------|------|
| **AI 自动开发** | 意图 → 规范 → 代码，AI 参与架构决策、代码生成、审查、测试全生命周期 |
| **自动运维自进化** | 用户行为 → 效果评估 → 规范更新（人工确认）→ 代码重生成 → 沙箱验证 → 热部署，系统越用越强 |
| **元引擎无代码** | DSL 驱动实体/工作流/权限运行时，配置即运行，开发与运行边界消失 |
| **一切皆文档** | 所有制品（界面/工作流/组件/知识/对话）以文档形式存储，以 DSL 描述，规范是人机共同真理源 |
| **语义组件** | 后端输出 DSL，前端动态组装界面，同一套组件多端适配（Web/移动/微信/CLI） |
| **五层智能架构** | Core（LLM）→ Cognition（记忆/知识）→ Agent（任务执行）→ Assistant（会话/情感）→ Team（多智能体协作） |

技术选型服务于以上能力，不是目的本身。

**五层分层架构**：

```
Layer 5  对话与交互层   意图理解 / 任务路由 / 多端适配 / REST+SSE+WebSocket
Layer 4  服务层         Auto Dev / 文档 / 用户 / 知识 / 外部整合 / 自定义业务
Layer 3  智能层         Core → Cognition → Agent → Assistant → Team
Layer 2  引擎层         执行调度 / DSL / 工作流 / 工具 / 知识库 / 记忆 / 权限 / 自进化
Layer 1  基础设施层     PostgreSQL + PgVector / Neo4j / Redis / Agent Sandbox
```

> 核心规则：上层可调用任意下层，禁止下层调用上层。Maven 模块映射见[第四节技术栈](#四推荐核心技术栈)。

> 第一性原理：选型以「减少运行时复杂度、最大化 AI 原生能力、最小化运维成本」为核心判据。

> **双模式长期并存**：AAF 长期支持两种开发模式——**传统 MVC 硬编码**（稳定可控，适合安全基础设施）和**元引擎无代码**（DSL 驱动，配置即运行，适合业务逻辑）。两者不是替代关系而是互补：安全基石永久硬编码，业务逻辑逐步迁移到元引擎。详见[第八节：业务实现路径](#八业务实现路径现阶段-vs-未来迁移)。

## 一、核心语言特性（Java 25 LTS）

| 特性 | JEP | 价值 |
|------|-----|------|
| Virtual Threads | 正式 | 百万级轻量线程，替代 WebFlux 响应式编程的复杂度，同步写法异步性能 |
| Scoped Values | 506 | 替代 ThreadLocal，跨方法链传递上下文（用户身份、租户 ID），天然适配虚拟线程 |
| Structured Concurrency | 505 (Preview) | 多子任务并行编排（Agent 并行调用多 LLM），统一错误处理和取消 |
| Record | 正式 | 不可变数据载体，DTO/VO/Event 首选，减少样板代码 |
| Sealed Classes | 正式 | 有限状态建模（工作流节点类型、Agent 状态机） |
| Pattern Matching (switch) | 正式 | 类型安全的分支逻辑，替代 if-else/visitor 模式 |
| Flexible Constructor Bodies | 513 | 构造器内 super() 前校验参数，减少辅助方法 |
| Compact Object Headers | 519 | 对象头 12→8 字节，大量小对象场景（向量 Embedding）节省内存 |
| AOT Class Loading | 514/515 | 启动加速 + 方法热点预编译，生产冷启动优化 |
| Stable Values | 502 (Preview) | 线程安全懒初始化，替代 double-checked locking |

**策略**：正式特性直接使用；Preview 特性（Structured Concurrency、Stable Values）在 framework 层封装，业务层不直接依赖。

## 二、框架核心特性（Spring Boot 4 / Spring Framework 7）

| 特性 | 说明 |
|------|------|
| Jakarta EE 11 | Servlet 6.1 + JPA 3.2 + Bean Validation 3.1 |
| JSpecify Null Safety | 全栈 null 安全注解，编译期消除 NPE |
| Jackson 3 | 默认 JSON 序列化，性能提升 + 更好的 Record 支持 |
| HTTP Service Clients | 声明式 HTTP 接口（替代 Feign），注解驱动自动生成实现 |
| API Versioning | 内置 API 版本管理（MVC/WebFlux），无需自研 |
| Virtual Threads 自动适配 | Tomcat 自动使用虚拟线程处理请求，零配置 |
| OpenTelemetry Starter | 内置 OTLP 导出（metrics + traces），统一可观测性 |
| Modular Auto-Configuration | 自动配置 JAR 拆分为模块，按需加载减少启动开销 |
| GraalVM Native Image | 生产级原生编译支持，秒级启动 |
| SSL/TLS 自动管理 | 证书健康检查 + 自动轮换 |

## 三、AI 原生能力（Spring AI 2.0）

| 能力 | 说明 |
|------|------|
| ChatClient 统一抽象 | 模型无关（OpenAI/Ollama/Anthropic），一套代码切换模型 |
| Tool Calling / Function Calling | 声明式工具注册，LLM 自动调用 Java 方法 |
| MCP Client/Server | @McpTool/@McpResource/@McpPrompt 注解式开发 |
| A2A Protocol | Agent-to-Agent 通信标准，跨系统多智能体协作 |
| Advisor Chain | 请求/响应拦截链（RAG、日志、安全过滤） |
| Vector Store 抽象 | PgVector/Milvus 统一接口，Embedding 自动管理 |
| Chat Memory | JDBC 持久化对话记忆，支持窗口/摘要策略 |
| Document Reader | Tika/Markdown/Jsoup 多格式文档解析 |
| Structured Output | 类型安全的 LLM 输出解析（直接映射到 Java Record） |
| Evaluation Framework | 内置 AI 输出质量评估（相关性、忠实度） |
| **AgentScope Java** | 多智能体编排框架（Spring Boot starter），内置：Token 用量统计、模型路由、Memory 管理、RAG、Human-in-the-Loop、Observability Studio、Online Training、Hook System、AG-UI/A2A 协议、Pipeline/Supervisor/Handoffs |
| Prompt 模板管理 | 系统级 Prompt 版本化存储 + 动态加载（基于 AgentScope + 文档引擎） |
| 模型路由 | 按任务类型/成本/延迟自动选择模型（AgentScope Routing 内置） |
| Token 用量统计 | 每次 LLM 调用消耗记录，支持配额控制和成本分析（AgentScope getChatUsage() 内置） |
| LLM 响应缓存 | 相同 prompt + 参数缓存响应，降低 Token 成本（Advisor Chain + Redis 实现） |
| 内容审核 | 输入/输出敏感词过滤 + 违禁内容检测，优先对接云服务（阿里云/腾讯云内容安全 API） |
| Prompt 注入防御 | 输入净化 + 意图一致性校验（对比原始意图与最终 Prompt），防止恶意 prompt 劫持 |
| 语音处理 | ASR（语音转文字）+ TTS（文字转语音），优先对接云服务（阿里云/火山引擎/OpenAI Whisper API） |
| 图像处理 | OCR / 图像识别 / 多模态理解，优先对接云服务（阿里云 OCR / GPT-4V / Gemini Vision） |

## 四、推荐核心技术栈

| 层次 | 技术 | 版本 | 选型理由 |
|------|------|------|---------|
| Web | Spring MVC + Virtual Threads | - | 同步编程模型 + 虚拟线程 = 简单且高性能，放弃 WebFlux 复杂度 |
| 流式推送 | SSE (SseEmitter) | - | AI 流式输出天然适配，比 WebSocket 简单 |
| 双向通信 | WebSocket | - | 仅多人协作场景使用 |
| API 文档 | SpringDoc OpenAPI 3 | 3.x | REST 接口自动生成 Swagger UI，对外暴露/第三方集成/MCP 工具暴露 |
| 灵活查询 | Spring GraphQL | - | 前端灵活查询/复杂关联数据/知识图谱，GraphiQL 内置调试；与 REST 并存互补 |
| 数据访问 | Spring Data JPA + Hibernate 7 | - | JPA 3.2，成熟稳定 |
| 数据库 | PostgreSQL 17 + PgVector | - | 关系 + 向量一体，减少组件 |
| 图数据库 | Neo4j 5 | - | 知识图谱、文档关系 |
| 缓存 | Redis 7 + Lettuce + Caffeine | - | Redis 分布式缓存 + Caffeine 本地二级缓存，Spring Cache 注解统一切换 |
| 搜索 | PostgreSQL Full-Text Search | - | 中小规模够用，避免引入 ES |
| 工作流 | Flowable 7 | - | BPMN 2.0 标准，Spring Boot 集成成熟 |
| 安全 | Spring Security 7 + JWT | - | RBAC + OAuth2 Resource Server |
| 无密码认证 | Spring Security WebAuthn | - | 指纹/Face ID/硬件密钥，v0.1.0 暂缓 |
| 参数校验 | spring-boot-starter-validation | - | Bean Validation 3.1，需显式引入 |
| 邮件发送 | spring-boot-starter-mail | - | 邮件通知/验证码发送 |
| 实时协同 | Yjs（CRDT） | - | 多人协作编辑，OT/CRDT 冲突合并 |
| 沙箱执行 | GraalVM Polyglot Sandbox | - | Agent 代码安全隔离执行，v2.0 迁移到 actormesh |
| 敏感配置加密 | Jasypt | - | DB 密码等敏感配置加密，不明文存配置文件 |
| 容错 | Resilience4j | - | 熔断/重试/超时/舱壁，LLM 调用必备 |
| 图查询 | neo4j-cypher-dsl | - | 类型安全 Cypher 构建，替代字符串拼接 |
| IP 归属地 | ip2region | - | 用户管理/操作日志 IP 解析，纯本地库 |
| 文件导出 | EasyExcel | - | Excel 流式读写，内存友好，注解驱动，底层基于 POI |
| 文件存储 | MinIO + 阿里云 OSS + AWS S3 | - | 统一 FileStorage 接口，配置切换后端 |
| 迁移 | Flyway | - | 版本化 DDL，团队协作友好 |
| 监控 | Micrometer + Prometheus + Grafana + OTLP | - | Spring Boot 4 内置 OpenTelemetry，Grafana 可视化 |
| 错误告警 | Sentry | - | 生产异常实时告警 + 堆栈聚合 + 性能追踪 |
| 日志 | SLF4J + Logback → Structured Logging | - | JSON 结构化日志，便于采集 |
| 工具库 | MapStruct + Lombok | - | 编译期代码生成，零运行时开销 |
| 测试 | JUnit 5 + Mockito + ArchUnit | - | 分层测试 + 架构守护 |
| 格式化 | Spotless + Google Java Format | - | 统一风格，CI 强制 |
| 构建 | Maven 3.9+ | - | Nx 桥接统一命令入口，多模块管理 |

## 五、关键架构决策

### 5.1 放弃 WebFlux，拥抱 Virtual Threads

**理由**：Java 25 虚拟线程已正式稳定，Spring Boot 4 自动适配。同步代码 + 虚拟线程 = WebFlux 的吞吐量 + 命令式代码的可读性。WebFlux 的响应式编程模型增加认知负担且调试困难，在虚拟线程时代不再是高并发的必选项。

**保留 WebFlux 的场景**：仅 SSE 流式推送（Spring AI 的 Flux<ChatResponse>）使用响应式，业务代码全部同步。

### 5.2 JPA 优先，不引入 MyBatis

**理由**：Spring Data JPA 3.2 + Hibernate 7 已足够强大（Specification 动态查询、Projection、@Query 原生 SQL）。AAF 是框架项目，ORM 标准化优先于灵活 SQL。复杂报表场景用 @Query + Native SQL 解决。

### 5.3 不引入 Hutool

**理由**：Java 25 标准库 + Spring 工具类已覆盖绝大多数场景。Hutool 大而全但引入不必要的依赖体积，且部分实现与 Spring 重复。按需引入单一职责的小库（如 commons-lang3）。

### 5.4 Structured Concurrency 封装

**理由**：Preview 特性不直接暴露给业务层。在 `aaf-framework` 中封装 `AgentTaskScope`，业务层通过稳定 API 使用并行 Agent 调用能力，底层实现可随 JDK 版本升级平滑切换。

## 六、参考开源项目

| 项目 | 参考价值 | 链接 |
|------|---------|------|
| **ruoyi-vue-pro（芋道）** | Maven 多模块 BOM 管理、权限体系、代码生成、多租户 | github.com/YunaiV/ruoyi-vue-pro |
| **JeecgBoot** | 低代码引擎、Online 表单、工作流集成、模块拆分 | github.com/jeecgboot/JeecgBoot |
| **pig** | Spring Security OAuth2 最佳实践、微服务网关、RBAC | gitee.com/log4j/pig |
| **Spring AI Samples** | ChatClient/MCP/Tool Calling/RAG 官方示例 | github.com/spring-projects/spring-ai |
| **Langchain4j** | Agent 编排模式、Memory 策略、Tool 注册（设计参考，不引入） | github.com/langchain4j/langchain4j |
| **Dify** | AI 应用工作流编排、知识库管理、Agent 策略（产品参考） | github.com/langgenius/dify |
| **A2A Protocol** | Agent-to-Agent 通信标准实现 | github.com/google/A2A |

## 七、框架内置能力（开箱即用）

AAF 框架层（`aaf-common` + `aaf-framework`）为业务开发者提供以下开箱即用能力：

| 能力 | 说明 | 所在模块 |
|------|------|---------|
| 全局异常处理 | @RestControllerAdvice 统一捕获，标准错误响应格式 + 错误码体系 | aaf-api/config |
| 错误码体系 | 错误码注册表，与 `BusinessException` 绑定，前端按 code 做国际化；模块级错误码区段划分 | aaf-common |
| 统一响应封装 | `Result<T>` 标准响应体（code/message/data/timestamp） | aaf-common |
| 参数校验集成 | Bean Validation 自动绑定 + 字段级错误信息返回 | aaf-common |
| 分页查询封装 | `PageRequest` / `PageResult<T>` 统一分页协议 | aaf-common |
| 基础实体 | `BaseEntity`（id/createTime/updateTime/deleted），逻辑删除 + 自动填充开箱即用 | aaf-common |
| 连接池管理 | HikariCP（PostgreSQL）/ Neo4j Driver Pool / Lettuce Pool（Redis），虚拟线程下自动排队不占 OS 线程，需按 DB 承载力配置 `maximumPoolSize` | Spring Boot 默认 |
| 验证码 | 图片/短信/邮件验证码生成与校验，Redis 存储 + 过期自动失效 | aaf-framework |
| 文件存储 | 统一 `FileStorage` 接口，多后端实现（Local / MinIO / 阿里云 OSS / AWS S3），配置项一键切换 | aaf-framework |
| 操作日志 | `@OperationLog` 注解切面，自动记录操作人/操作类型/变更内容 | aaf-framework |
| 登录日志 | 记录登录时间/IP/设备/成功失败，支持异常登录告警 | aaf-framework |
| 多租户隔离 | 租户上下文自动注入，数据自动过滤 | aaf-framework |
| 权限注解 | `@RequiresPermission` / `@RequiresRole` 声明式鉴权，`@PreAuthorize` Spring Security 原生支持 | aaf-framework |
| 数据权限 | `@DataScope` 注解按部门/用户/自定义规则过滤查询结果，SQL 自动注入 where 条件 | aaf-framework |
| 第三方登录 | OAuth2 Client，优先支持微信/企业微信/钉钉，扩展支持飞书/GitHub/Google | aaf-framework |
| 第三方集成 | 企业微信/钉钉/飞书统一接入层，覆盖消息推送、通知、组织架构同步、审批流转 | aaf-framework |
| API Key 管理 | B2B 场景外部系统调用鉴权（区别于用户 JWT），支持配额/过期/撤销 | aaf-framework |
| 短信服务 | 统一 `SmsSender` 接口，多厂商实现（阿里云/腾讯云/Twilio），配置切换 | aaf-framework |
| 乐观锁 | JPA `@Version` 注解防并发更新冲突，BaseEntity 基础能力 | aaf-common |
| 系统参数 | 运行时可动态调整的业务配置（与字典区分：字典是枚举选项，参数是可调配置），支持类型化读取 | aaf-framework |
| 安全防护 | XSS 过滤（请求参数净化）/ SQL 注入防护（参数化查询）/ 登录失败锁定（Redis 计数 + 临时封禁） | aaf-framework |
| 数据脱敏 | `@Sensitive` 注解，日志/响应中手机号/身份证/邮箱自动脱敏 | aaf-framework |
| 幂等控制 | `@Idempotent` 注解 + Redis，防重复提交 | aaf-framework |
| 链路追踪 | TraceId 自动注入（MDC + 虚拟线程适配），请求全链路可追踪 | aaf-framework |
| 消息推送 | WebSocket/SSE 统一推送封装（系统通知/任务进度/Agent 状态） | aaf-framework |
| 健康检查扩展 | 自定义 HealthIndicator（LLM 可用性/向量库连通性/Neo4j 连通性） | aaf-framework |
| 多语言国际化 | i18n 错误码/消息多语言，`MessageSource` 统一管理 | aaf-framework |
| 分布式锁 | `@DistributedLock` 注解 + Redis 实现 | aaf-framework |
| 缓存 | `@Cacheable/@CacheEvict` Spring Cache 注解，本地 Caffeine + 远程 Redis 两级，配置切换 | aaf-framework |
| 接口限流 | Resilience4j `@RateLimiter` 注解，单体内限流；分布式场景升级为 Redis 滑动窗口 | aaf-framework |
| 定时任务 | `@Scheduled`（框架级/无状态任务）+ Quartz（业务级/持久化/动态管理），按场景选择 | aaf-framework |
| 事件总线 | Spring ApplicationEvent 封装，模块间解耦通信 | aaf-framework |
| 代码生成器 | 表结构 → CRUD 代码（Controller/Service/Repository/Entity/VO），减少重复开发 | aaf-auto-dev |
| 菜单管理 | 树形菜单定义 + 动态路由，与权限系统联动 | aaf-api/module/system |
| 部门管理 | 组织架构树，支持数据权限按部门过滤 | aaf-api/module/system |
| 字典管理 | 系统字典定义 + 缓存，前端下拉选项统一管理 | aaf-api/module/system |
| 轻量任务队列 | PostgreSQL 持久化 + Redis 实时通知 + 失败重试/死信/削峰/延迟执行，零额外中间件。详见 [任务队列设计](../../../design/apps/service/task-queue.md) | aaf-framework |
| AI 对话封装 | ChatClient 统一入口 + 记忆管理 + 流式 SSE | aaf-framework |
| 全文与语义检索 | PostgreSQL FTS（中文分词）+ PgVector 向量相似度，精确/全文/语义三层递进，零额外搜索引擎 | aaf-framework |
| 工具注册 | `@McpTool` 声明式注册，LLM 自动发现和调用 | aaf-framework |

## 八、业务实现路径：现阶段 vs 未来迁移

> 终极目标：所有业务逻辑通过元引擎 DSL 驱动，硬编码仅保留引擎自身的 bootstrap。
> 现阶段：元引擎未实现，全部走传统 MVC，但提前规划哪些模块未来迁移、哪些永久保留。

### 迁移分类

| 分类 | 含义 |
|------|------|
| 🟢 必迁移 | 元引擎成熟后迁移到无代码，当前传统实现是临时方案 |
| 🟡 部分迁移 | 核心骨架保留硬编码，扩展点迁移到 DSL |
| 🔴 永不迁移 | 元引擎自身的启动依赖 / 安全基石，永久硬编码 |

### 模块清单

| 模块 | 现阶段实现 | 未来迁移 | 理由 |
|------|-----------|---------|------|
| 用户认证（登录/注册/JWT） | 传统 MVC | 🔴 永不 | 元引擎启动前提，安全基石 |
| RBAC 核心鉴权 | 传统 MVC | 🔴 永不 | 权限判断必须硬编码可审计 |
| 系统配置 | 传统 MVC | 🔴 永不 | 框架启动依赖 |
| 操作日志 | 传统 MVC | 🔴 永不 | 审计合规，逻辑固定 |
| 任务队列 | 传统 MVC | 🔴 永不 | 框架基础设施 |
| 文件存储 | 传统 MVC | 🔴 永不 | 底层 I/O，与存储强绑定 |
| 监控/健康检查 | 传统 MVC | 🔴 永不 | 运维基础设施 |
| 权限点/角色/数据范围定义 | 传统 MVC | 🟡 扩展点迁移 | 鉴权逻辑不动，规则定义走 DSL |
| 聊天/对话（消息收发/SSE） | 传统 MVC | 🟡 扩展点迁移 | 通道不动，对话策略/路由走 DSL |
| 文档服务（存储/版本/解析） | 传统 MVC | 🟡 扩展点迁移 | 引擎不动，文档类型/元数据走 DSL |
| 计费/积分（结算事务） | 传统 MVC | 🟡 扩展点迁移 | 事务不动，规则/策略走 DSL |
| 支付对接 | 传统 MVC | 🟡 扩展点迁移 | SDK 集成不动，支付路由规则走 DSL |
| 消息通知 | 传统 MVC | 🟢 全迁移 | 通知规则 + 模板完全可 DSL 化 |
| 知识库管理 | 传统 MVC | 🟢 全迁移 | 典型 CRUD + 检索策略配置 |
| 客户/联系人管理 | 传统 MVC | 🟢 全迁移 | 字段多变，典型实体运行时场景 |
| 项目/任务管理 | 传统 MVC | 🟢 全迁移 | 实体 + 状态流转 + 看板 |
| 商品管理 | 传统 MVC | 🟢 全迁移 | 字段/分类/SKU 变更频繁 |
| 订单流程（发货/售后） | 传统 MVC | 🟢 全迁移 | 状态机 + 审批，工作流运行时 |
| 促销/优惠规则 | 传统 MVC | 🟢 全迁移 | 规则引擎天然场景 |
| 表单/问卷 | 传统 MVC | 🟢 全迁移 | 动态表单定义 + 数据收集 |
| 审批流程 | 传统 MVC | 🟢 全迁移 | DSL → Flowable 实例化 |
| 报表/仪表盘 | 传统 MVC | 🟢 全迁移 | DSL 定义数据源 + 图表 |
| Agent 配置/编排 | 传统 MVC | 🟢 全迁移 | DSL 定义能力/工具/策略 |

### 迁移时间线

```
v0.1.0  全部传统 MVC（当前）
  ↓
v0.2.0  实体运行时上线 → 🟢 新增业务模块直接走无代码
  ↓
v0.3.0  工作流运行时上线 → 🟢 审批/流程类迁移
  ↓
v0.4.0  规则引擎上线 → 🟢 促销/通知/权限规则迁移
  ↓
v1.0.0  🟡 扩展点全部开放给 DSL，🔴 模块永久保留硬编码
```

### 设计约束

为了未来平滑迁移，v0.1.0 传统实现需遵守：

1. **接口与实现分离**：Service 层定义接口，未来元引擎生成的实现可直接替换
2. **实体设计标准化**：字段命名、类型、关联关系遵循统一规范，方便 DSL 描述
3. **业务规则外置**：硬编码中的业务规则（如校验、计算）集中到独立方法，不散落在 Controller 中
4. **状态机显式化**：有状态流转的模块用枚举 + 状态转换方法，不用 if-else 隐式控制

## 九、不选/暂缓的技术

| 技术 | 原因 |
|------|------|
| WebFlux 全栈响应式 | 虚拟线程时代不再必要，增加复杂度 |
| MyBatis / MyBatis-Plus | JPA 标准化优先，避免双 ORM |
| Hutool | 与 Spring/JDK 工具重复，体积大 |
| Elasticsearch | 当前规模 PostgreSQL FTS 够用，按需引入 |
| Kafka / RabbitMQ | v0.1.0 单体架构，事件用 Spring ApplicationEvent，后续按需引入 |
| Kubernetes / Docker Compose | 开发环境本地直连，CI 用 GitHub Actions service container |
| R2DBC | 虚拟线程下 JDBC 已非阻塞瓶颈，R2DBC 增加复杂度无收益 |
| Testcontainers | ADR-002 已决策：本地真实 DB + CI service container |

## 十、下一步

1. 基于本选型更新 `pom.xml`（移除 WebFlux/R2DBC 全栈依赖，保留 SSE 部分）
2. 完善 `aaf-dependencies` BOM（参考芋道写法）
3. 创建 Maven 多模块完整目录结构（#17）
4. 落地 ArchUnit 分层规则（#21）
