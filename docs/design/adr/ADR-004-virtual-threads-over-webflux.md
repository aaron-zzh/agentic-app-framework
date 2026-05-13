---
level: Practice
layer: Principle
purpose: 后端并发模型决策：Virtual Threads + JDBC 替代 WebFlux + R2DBC
status: accepted
version: 1.0.0
date: 2026-05-08
author: AaronZZH
---

---
status: accepted
date: 2026-05-08
deciders: [AaronZZH]
consulted: []
informed: []
related-tasks: [AAF-023 / #17]
---

# ADR-004: 全量采用 Virtual Threads + JDBC，放弃 WebFlux + R2DBC 全栈响应式

## Context and Problem Statement

AAF 后端需要支撑高并发场景（多用户同时对话、Agent 并行调用 LLM、知识库批量检索）。Spring Boot 4 同时支持两种并发模型：

1. WebFlux + R2DBC（响应式全栈）
2. Spring MVC + Virtual Threads + JDBC（同步代码 + 虚拟线程）

项目当前 pom.xml 同时引入了 WebFlux、WebMVC、R2DBC、JPA，存在架构模糊。需要明确选择一条路径作为主线。

## Decision Drivers

- **代码复杂度**：AI agent 生成同步代码的正确率远高于响应式代码
- **调试体验**：生产排查问题的效率直接影响迭代速度
- **生态兼容性**：JPA/Hibernate、Spring Security、事务管理对响应式支持不完整
- **性能**：高并发 I/O 场景的吞吐量不能有明显退步
- **团队认知负担**：AAF 是 AI 原生框架，开发者（含 AI）应聚焦业务而非并发模型

## Considered Options

- **选项 A**：WebFlux + R2DBC 全栈响应式
- **选项 B**：Virtual Threads + JDBC 全量同步（仅 SSE 流式输出保留 Flux）
- **选项 C**：混合模式（部分接口 WebFlux，部分 MVC）

## Decision Outcome

**Chosen option**: "选项 B — Virtual Threads + JDBC"，理由：Java 25 虚拟线程已正式稳定，Spring Boot 4 一个配置项即可全量启用，同步代码获得等同响应式的吞吐量，同时保持代码简单、调试友好、生态完整。

### 落地方式

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

- Tomcat 自动用虚拟线程处理所有请求，业务代码无需任何标记或改造
- Service 层串行调用（DB、外部 API）自动在虚拟线程上阻塞，不占 OS 线程
- 需要降低单请求延迟时，用 `StructuredTaskScope` 主动并行
- CPU 密集任务隔离到固定大小平台线程池（`@Async("cpuIntensiveExecutor")`）
- SSE 流式输出（Spring AI `Flux<ChatResponse>`）保留响应式

### Positive Consequences

- 代码 100% 同步命令式，AI agent 生成正确率高
- 完整调用栈，断点调试、日志追踪无断裂
- JPA + @Transactional 直接可用，无需 R2DBC 的事务 workaround
- Spring Security 全功能可用（响应式下部分特性缺失）
- 减少依赖：移除 R2DBC、r2dbc-postgresql、spring-data-r2dbc

### Negative Consequences

- 纯流式处理场景（如大文件流式传输）仍需引入少量响应式代码
- 需要正确配置连接池大小（虚拟线程并发量远超传统模型）
- `synchronized` 块内阻塞会 pin carrier thread，需用 `ReentrantLock` 替代

### Reversal Triggers（反向选择触发条件）

仅当出现以下之一时考虑回切：

1. 发现 JDBC 驱动在虚拟线程下存在严重 bug（pin thread 无法修复）
2. 业务场景大量需要背压控制（当前 AAF 不存在此场景）
3. Spring AI 未来版本强制要求 WebFlux 运行时（当前不要求）

## Pros and Cons of the Options

### 选项 A：WebFlux + R2DBC 全栈响应式

- Good: 极端低延迟场景（纳秒级）略优
- Good: 背压控制精确
- Bad: 代码复杂度高（Mono/Flux 链式、调度器切换）
- Bad: 调试困难（异步堆栈断裂）
- Bad: JPA/Hibernate 不支持，只能用 Spring Data R2DBC（功能弱）
- Bad: @Transactional 行为不同，容易踩坑
- Bad: AI agent 生成响应式代码错误率高

### 选项 B：Virtual Threads + JDBC（✅ 选定）

- Good: 同步代码，认知负担低
- Good: 完整生态支持（JPA、Security、事务）
- Good: 调试体验好，完整调用栈
- Good: 吞吐量与响应式方案相当（benchmark 差距 <5%）
- Bad: 需要注意连接池配置
- Bad: synchronized 需替换为 ReentrantLock

### 选项 C：混合模式

- Good: 各取所长
- Bad: 两套编程模型共存，认知负担最高
- Bad: 测试需覆盖两种模式
- Bad: 新人（含 AI）不知道何时用哪种

## More Information

- 讨论记录：AAF-023 技术选型对话（2026-05-08）
- 技术选型文档：[tech-stack.md](../apps/service/tech-stack.md)
- 后续动作：
  - pom.xml 移除 R2DBC 相关依赖（spring-boot-starter-data-r2dbc、r2dbc-postgresql、r2dbc-h2）
  - application.yml 添加 `spring.threads.virtual.enabled=true`
  - HikariCP `maximumPoolSize` 调整为 50（匹配 PostgreSQL 默认 max_connections=100）
  - 编码规范补充：禁止业务代码使用 `synchronized`，统一用 `ReentrantLock`
