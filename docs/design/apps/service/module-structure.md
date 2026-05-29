---
level: Practice
layer: Model
purpose: AAF 后端 Maven 模块结构、依赖关系与职责分工
status: published
version: 1.1.0
date: 2026-05-20
author: AaronZZH
gains:
  - 能理解后端模块整体结构和依赖方向
  - 能快速判断新代码应放在哪个模块
---

# 后端模块结构（service）

> 完整包结构和组件关系见 [项目结构](../../../project-structure.md)，本文档聚焦"新代码放哪里"的判断指南。

## 设计原则

- **框架层与业务层分离**：`aaf-framework` 是引擎，`aaf-api` 是业务外壳
- **单一启动入口**：`aaf-api` 同时承担业务模块和启动入口职责，内部按包隔离
- **按需激活示例**：示例代码通过 `@Profile("examples")` 控制，不影响生产运行

## 目录树

```text
agentic-app-framework/
├── pom.xml                                    # 父 POM（5 个主模块）
│
├── aaf-dependencies/                          # 依赖版本管理（BOM）
│   └── pom.xml
│
├── aaf-common/                                # 公共模块
│   ├── pom.xml
│   └── src/main/java/com/xuejiai/aaf/common/
│       ├── constant/                          # 常量
│       ├── exception/                         # 异常体系
│       ├── util/                              # 工具类
│       ├── model/                             # Result、PageInfo
│       ├── enums/                             # 通用枚举
│       └── annotation/                        # 通用注解定义（@OperationLog 等）
│
├── aaf-framework/                             # 核心框架（Layer 2 引擎层 + Layer 3 智能层）
│   ├── pom.xml
│   └── src/main/java/com/xuejiai/aaf/framework/
│       ├── intelligent/                       # Layer 3 智能层
│       │   ├── core/                          # LLM 基础设施（LlmClient/ModelRouter/Token 计量）
│       │   ├── ai/                            # 弹性调用/路由（ResilientChatService）
│       │   ├── cognition/                     # 认知基础层（记忆/检索/管道）
│       │   ├── agent/                         # Agent 层（AgentPool/AgentSandbox/agentscope/）
│       │   ├── assistant/                     # Assistant 层（会话/情感/调度）
│       │   └── team/                          # Team 层（多 Assistant 编排）
│       ├── engine/                            # Layer 2 专项引擎（v1.0 全量）
│       │   ├── knowledge/                     # 知识库引擎（向量/图谱/RAG）
│       │   ├── memory/                        # 记忆引擎（AtomMemory）
│       │   ├── tool/                          # 工具引擎（ToolRegistry/MCP）
│       │   ├── skill/                         # 技能引擎
│       │   ├── workflow/                      # 工作流引擎（Flowable 封装）
│       │   ├── document/                      # 文档引擎
│       │   ├── orchestration/                 # 编排引擎
│       │   ├── scheduler/                     # 调度引擎
│       │   ├── message/                       # 消息引擎
│       │   ├── monitor/                       # 监控引擎
│       │   ├── budget/                        # 预算控制
│       │   ├── credit/                        # 积分引擎
│       │   ├── settlement/                    # 结算引擎
│       │   └── datasource/                    # 外部数据源
│       ├── security/                          # 认证授权（Spring Security）
│       ├── storage/                           # 文件存储（FileStorage 接口）
│       ├── messaging/                         # 消息推送（WebSocket/SSE）
│       ├── logging/                           # 操作日志（AOP）
│       ├── protection/                        # 限流/幂等/分布式锁
│       ├── task/                              # 异步任务队列
│       ├── crud/                              # 通用 CRUD 基类
│       ├── data/                              # 数据权限（@DataScope）
│       └── config/                            # 框架自动配置
│
├── aaf-auto-dev/                              # AI 自动开发（代码生成与自进化）
│   ├── pom.xml
│   └── src/main/java/com/xuejiai/aaf/autodev/
│       ├── agent/                             # PlanningAgent / CodingAgent / ReviewAgent
│       ├── service/
│       ├── controller/
│       └── monitor/                           # kiro-cli 监控接口
│
└── aaf-api/                                   # ⭐ Layer 4 服务层 + Layer 5 接口层（启动入口）
    ├── pom.xml
    └── src/main/
        ├── java/com/xuejiai/aaf/
        │   ├── AafApplication.java
        │   ├── config/                        # 应用级配置（CORS/WebSocket/异常处理/多租户）
        │   ├── module/                        # 业务模块（按功能域隔离）
        │   │   ├── system/                    # 框架基础服务（按子域分层）
        │   │   │   ├── user/                  # 用户子域
        │   │   │   ├── auth/                  # 认证子域
        │   │   │   ├── role/                  # 角色权限子域
        │   │   │   ├── org/                   # 组织架构子域
        │   │   │   ├── notify/                # 通知消息子域
        │   │   │   ├── log/                   # 日志审计子域
        │   │   │   ├── chat/                  # 聊天子域
        │   │   │   ├── entity/                # 元数据实体子域
        │   │   │   ├── workflow/              # 工作流子域
        │   │   │   ├── task/                  # 任务调度子域
        │   │   │   ├── dashboard/             # 仪表盘子域
        │   │   │   └── api/                   # 跨模块暴露接口
        │   │   ├── agent/                     # Agent 管理（引擎管理服务）
        │   │   ├── assistant/                 # 助手管理
        │   │   ├── knowledge/                 # 知识库管理
        │   │   ├── memory/                    # 记忆管理
        │   │   ├── skill/                     # 技能管理
        │   │   ├── model/                     # 模型管理
        │   │   ├── budget/                    # 预算管理
        │   │   ├── credit/                    # 积分管理
        │   │   ├── settlement/                # 结算管理
        │   │   ├── monitor/                   # 监控看板
        │   │   ├── document/                  # 文档模块（业务服务）
        │   │   └── [自定义业务模块]/            # 用户按需扩展
        │   └── security/                      # 应用级安全（MockToken 等）
        └── resources/
            ├── application.yml
            ├── application-dev.yml
            └── application-prod.yml
```

## 模块依赖关系

```text
aaf-api（Layer 4 服务层 + Layer 5 接口层）
    ↓
aaf-framework（Layer 2 引擎层 + Layer 3 智能层）  ←── aaf-auto-dev（可选）
    ↓
aaf-common（横切工具）
    ↓
aaf-dependencies（BOM）
```

## framework 与 api 的分工

| | aaf-framework（引擎） | aaf-api/module（业务） |
|---|---|---|
| agent | Agent 执行、AgentScope 适配、工具调用、记忆管道 | Agent 定义管理、会话记录、任务历史 |
| workflow | 流程执行引擎（Flowable）、节点调度 | 流程定义 CRUD、实例监控、审批操作 |
| knowledge | 向量检索、Embedding、文档解析、知识图谱 | 知识库管理、文档上传、检索 API |
| memory | AtomMemory 存储、RetrievalPipeline、MemoryWritePipeline | 记忆查看、清理 |
| system | 无 | 用户/角色/权限/组织（按子域分层） |

> 判断标准：换一个业务系统还能复用 → 放 framework；与本系统强绑定 → 放 module

## 业务模块组织原则

`aaf-api/module/` 分三类：

**框架基础服务**（`system/`）：按子域分层，每个子域内含 controller/service/domain/repository/vo。

**引擎管理服务**（`agent/`、`knowledge/`、`monitor/` 等）：每个引擎在 Layer 4 的管理入口，提供配置/监控/CRUD API。

**业务服务**（`document/` 等）：用户在 AAF 上构建的具体业务，可由元引擎自动生成。

```text
module/
├── system/          # 框架基础服务（user/auth/role/org/notify/log/chat/entity/workflow/task/dashboard）
├── agent/           # Agent 管理
├── assistant/       # 助手管理
├── knowledge/       # 知识库管理
├── memory/          # 记忆管理
├── skill/           # 技能管理
├── model/           # 模型管理
├── budget/          # 预算管理
├── credit/          # 积分管理
├── settlement/      # 结算管理
├── monitor/         # 监控看板
├── document/        # 文档模块（业务服务示例）
└── [自定义业务模块]/ # 用户按需扩展，零侵入
```

跨模块通过 `api/` 子包交互，禁止直接访问对方 service/repository/entity。

## 各模块职责

**aaf-dependencies**：统一管理所有第三方依赖版本（BOM），子模块引入依赖不写版本号。

**aaf-common**：无业务逻辑的纯工具层，常量、异常体系、工具类、通用响应模型。

**aaf-framework**：框架核心引擎，提供可复用的 AI 原生能力。依赖 Spring AI、JPA、Flowable。

**aaf-auto-dev**：AI 驱动的运行时代码生成与分析能力，可选引入。支持在线代码生成、热加载和自进化。"可选"指部署灵活性，不代表仅用于开发阶段。

**aaf-api**：业务层与启动入口，是用户开发业务功能的主要工作区。

> 技术选型与决策记录见 [tech-stack.md](tech-stack.md)
