---
level: Practice
layer: Model
purpose: AAF 后端 Maven 模块结构、依赖关系与职责分工
status: published
version: 1.0.0
date: 2026-05-05
author: AaronZZH
gains:
  - 能理解后端模块整体结构和依赖方向
  - 能快速判断新代码应放在哪个模块
---

# 后端模块结构（service）

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
│       └── annotation/                        # 通用注解定义（@OperationLog、@Trans 等）
│
├── aaf-framework/                             # 核心框架（引擎层 + 智能层）
│   ├── pom.xml
│   └── src/main/java/com/xuejiai/aaf/framework/
│       ├── engine/                            # 引擎层
│       │   ├── doc/                           # 文档引擎
│       │   ├── monitor/                       # 监控引擎
│       │   ├── permission/                    # 权限引擎
│       │   ├── license/                       # 授权引擎
│       │   ├── chat/                          # 聊天引擎
│       │   ├── workflow/                      # Flowable 8 封装
│       │   ├── knowledge/                     # 向量检索 + 文档解析
│       │   ├── dsl/                           # ANTLR4 + Magic-DSL
│       │   ├── rule/                          # Easy Rules 封装
│       │   ├── codegen/                       # FreeMarker 代码生成
│       │   └── datasource/                    # 外部数据源适配（v0.2+，JDBC/HTTP/File 统一数据集）
│       ├── intelligent/                       # 智能层
│       │   ├── core/                          # Spring AI ChatClient 封装
│       │   ├── agent/                         # Agent 接口 + SequentialAgentExecutor
│       │   ├── cognition/                     # 记忆 + 知识 + 价值观
│       │   ├── assistant/                     # 会话 + 情感
│       │   └── team/                          # 多智能体协作
│       ├── protection/                        # 防护（限流/幂等/分布式锁）
│       ├── security/                          # 认证授权（Spring Security 配置）
│       ├── storage/                           # 文件存储（FileStorage 接口 + 多后端）
│       ├── message/                           # 消息推送（WebSocket/SSE/短信/邮件）
│       ├── log/                               # 操作日志 + 登录日志（AOP 实现）
│       ├── cache/                             # 缓存封装（Caffeine + Redis 两级）
│       ├── data/                              # 数据权限（@DataScope + JPA 拦截器）
│       └── config/                            # 框架自动配置
│
├── aaf-auto-dev/                              # AI 自动开发（运行时在线代码生成与自进化）
│   ├── pom.xml
│   └── src/main/java/com/xuejiai/aaf/autodev/
│       ├── agent/                             # PlanningAgent / CodingAgent / ReviewAgent
│       ├── service/
│       ├── controller/
│       └── monitor/                           # kiro-cli 监控接口
│
└── aaf-api/                                   # ⭐ 业务层 + 启动入口
    ├── pom.xml
    └── src/main/
        ├── java/com/xuejiai/aaf/
        │   ├── AafApplication.java
        │   ├── config/                        # Security、CORS、WebSocket、SSE
        │   ├── module/                        # 业务模块（分包隔离）
        │   │   ├── system/                    # 用户管理（注册/登录/RBAC）
        │   │   │   ├── controller/
        │   │   │   ├── service/
        │   │   │   ├── domain/
        │   │   │   ├── repository/
        │   │   │   └── vo/                    # 入参 XxxReqVO + 出参 XxxRespVO，统一放此目录
        │   │   ├── document/                  # 文档管理
        │   │   └── chat/                      # 聊天协作
        │   └── examples/                      # 示例代码（@Profile("examples")）
        └── resources/
            ├── application.yml
            ├── application-dev.yml
            └── application-prod.yml
```

## 模块依赖关系

```text
aaf-api（业务层 + 启动）
    ↓
aaf-framework（引擎层）  ←── aaf-auto-dev（可选）
    ↓
aaf-common（公共能力）
    ↓
aaf-dependencies（BOM）
```

## framework 与 api 的分工

| | aaf-framework（引擎） | aaf-api/module（业务） |
|---|---|---|
| agent | Agent 调度、Tool 调用、多智能体编排 | Agent 配置管理、会话记录、任务历史 |
| workflow | 流程执行引擎、DSL 解析、节点调度 | 流程定义 CRUD、实例监控、审批操作 |
| knowledge | 向量检索、Embedding、文档解析 | 知识库管理、文档上传、检索 API |
| system | 无 | 用户/角色/权限 |

> 判断标准：换一个业务系统还能复用 → 放 framework；与本系统强绑定 → 放 module

## 业务模块组织原则

`aaf-api/module/` 下按**功能组件**独立拆分，每个模块自包含、可独立开发，不导致系统臃肿：

- **按功能域划分**：`system/`、`document/`、`chat/` 各自独立，互不耦合
- **按需引入**：未来新增模块（如 `mall/`、`crm/`）只需新建目录，不影响已有模块
- **跨模块通过 `api/` 包交互**：每个模块可设 `api/` 子包暴露接口 + DTO，其他模块只依赖接口，不直接访问对方的 service/repository/entity
- **ArchUnit 守护边界**：禁止跨模块直接访问非 `api/` 包内容
- **领域建模**：模块内 `domain/` 按聚合划分子包，详见 [领域建模规范](../../../reference/dev/apps/service/domain-modeling-standard.md)

```text
module/
├── system/          # 用户/角色/权限/部门（基础必备）
├── document/        # 文档管理（按需）
├── chat/            # 聊天协作（按需）
├── knowledge/       # 知识库管理（按需）
└── [新模块]/        # 新增功能域，独立目录，零侵入
```

## 各模块职责

**aaf-dependencies**：统一管理所有第三方依赖版本（BOM），子模块引入依赖不写版本号。

**aaf-common**：无业务逻辑的纯工具层，常量、异常体系、工具类、通用响应模型。

**aaf-framework**：框架核心引擎，提供可复用的 AI 原生能力。依赖 Spring AI、JPA、Flowable。

**aaf-auto-dev**：AI 驱动的运行时代码生成与分析能力，可选引入。支持在线代码生成、热加载和自进化。"可选"指部署灵活性，不代表仅用于开发阶段。

**aaf-api**：业务层与启动入口，是用户开发业务功能的主要工作区。

> 技术选型与决策记录见 [tech-stack.md](tech-stack.md)
