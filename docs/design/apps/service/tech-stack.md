---
level: Practice
layer: Model
purpose: AAF 后端服务技术选型与决策记录
status: published
version: 1.0.0
date: 2026-05-05
author: AaronZZH
gains:
  - 了解后端各技术选型的决策依据
  - 新成员能快速理解技术栈选择原因
---

# 后端技术选型（service）

## 技术栈总览

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 语言 | Java | 25 | 虚拟线程、Record、Pattern Matching |
| 核心框架 | Spring Boot | 4.0.6 | 响应式支持、原生编译 |
| AI 集成 | Spring AI | 2.0 | 统一 LLM 接口、向量存储抽象 |
| 响应式 | Spring WebFlux | - | SSE 流式推送、非阻塞 IO |
| API | GraphQL | - | 灵活查询，减少前后端耦合 |
| 协议 | MCP | - | Model Context Protocol，工具调用标准 |
| 工作流 | Flowable | 7.x | 成熟 BPMN 引擎，支持 DSL 驱动 |
| 代码生成 | FreeMarker + JavaParser | - | 运行时在线代码生成与 AST 操作 |
| 关系数据库 | PostgreSQL | 16+ | 主存储，兼具向量扩展（PgVector） |
| 向量存储 | PgVector | - | 复用 PostgreSQL，减少运维复杂度 |
| 图数据库 | Neo4j | - | 文档关系图谱、知识图谱 |
| 缓存 | Redis | - | 短期记忆、会话状态、热点缓存 |
| 数据库迁移 | Flyway | - | 版本化 Schema 管理 |
| 安全 | Spring Security | - | JWT + RBAC |
| 构建 | Maven | 3.9+ | 多模块管理 |
| 运行时 | GraalVM | - | 生产构建原生镜像，开发用 Temurin |

## 关键决策记录

### 为什么选 PostgreSQL 而不是 MySQL

- PgVector 扩展直接支持向量存储，无需引入独立向量数据库（Milvus/Weaviate）
- JSON 支持更强，适合 DSL 文档存储
- 减少运维复杂度：一个数据库承担关系 + 向量两种职责

### 为什么同时使用 Neo4j

- PostgreSQL 不擅长多跳关系查询（文档引用链、知识图谱路径）
- Neo4j 的 Cypher 查询语言在图遍历场景远优于 SQL JOIN
- 两者职责互补，不重叠：PostgreSQL 存内容，Neo4j 存关系

### 为什么选 Flowable 而不是自研工作流

- 成熟的 BPMN 2.0 标准支持，生态完善
- 支持 DSL 驱动实例化，符合元引擎设计
- Spring Boot 集成成熟，维护成本低

### 为什么选 Spring AI 而不是 LangChain4j

- Spring 生态原生集成，与 Spring Boot 4 无缝配合
- 统一的 LLM 接口抽象，模型无关
- 向量存储抽象层支持 PgVector，与数据库选型一致

### 为什么用 WebFlux + SSE 而不是 WebSocket

- SSE 单向推送足够满足流式对话场景，实现更简单
- WebFlux 响应式管道天然适合流式处理
- WebSocket 保留用于需要双向实时通信的场景（如多人协作）

## 模块结构

```text
apps/service/
  aaf-dependencies/   依赖版本统一管理
  aaf-common/         公共工具、常量、异常
  aaf-framework/      核心框架（智能体、工作流、引擎等）
  aaf-auto-dev/       AI 驱动代码生成与自进化
  aaf-api/            对外 API 入口（聚合启动）
```

## v0.1.0 架构决策

### 五层架构 → Maven 模块映射

```
Layer 5  对话与交互层  → aaf-api（REST API + SSE/WebSocket 端点，启动入口）
Layer 4  服务层        → aaf-api/module/（system/document/chat/autodev 分包隔离）
Layer 3  智能层        → aaf-framework: intelligent/（core, agent, cognition, assistant, team）
Layer 2  引擎层        → aaf-framework: engine/（调度机制 + 专项引擎）
Layer 1  基础设施层    → aaf-common（公共能力）+ PostgreSQL + Neo4j
```

> v0.1.0 采用单启动模块（`aaf-api` 内分包隔离），后续迭代按需拆分为独立 Maven 模块。

> 详细目录树和模块分工见 [模块结构](module-structure.md)

### kiro-cli 协作接口

```
kiro-cli（本地执行）
  ├─ POST /api/monitor/events    上报执行事件
  ├─ POST /api/monitor/logs      上报执行日志
  ↓
AAF 后端 → 持久化 → SSE /api/monitor/stream → Web 前端实时展示
```

### 开源授权控制设计

```
启动时：读取 JWT 文件 → RS256 验签（公钥内置）→ 解析 is_premium + user_id
         → 存入全局只读 LICENSE 对象（单例，不可修改）

运行时：高级模块入口检查 LICENSE.is_premium（O(1) 内存读取）
        关键算法使用 LICENSE.user_id 作 seed/trace_id（分散耦合）
        配置初始化按权限设置默认参数
        插件注册阶段过滤高级插件
```

> 详细需求见 [开源授权控制需求](../../../task/v0.1.0/AAF-018/requirement.md)
