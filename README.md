# AI 原生应用开发框架 Agentic App Framework (AAF)

[![CI](https://github.com/aaron-zzh/agentic-app-framework/workflows/CI/badge.svg)](https://github.com/aaron-zzh/agentic-app-framework/actions)
[![Commitlint](https://github.com/aaron-zzh/agentic-app-framework/workflows/Commitlint/badge.svg)](https://github.com/aaron-zzh/agentic-app-framework/actions)

> 元引擎是这套分层架构的整体，不是某层的子项。整个 AAF 系统就是元引擎——将意图转化为执行，将执行转化为知识。自我开发、自我进化、规范驱动的围栏工程

## 项目简介

AAF 是一个面向开发人员的生产级 AI 原生框架，支持：

- ✅ **多智能体协作**：智能体系统、记忆系统、对话式交互
- ✅ **工作流引擎**：可视化工作流设计、DSL 定义
- ✅ **知识库管理**：向量数据库、语义检索、知识图谱
- ✅ **规范驱动开发**：先写规范，再写代码。让规范成为人类和AI的共同真理来源
- ✅ **AI 自动开发**：代码生成、分析、优化、自我进化
- ✅ **无代码开发**：普通用户可视化搭建工作流、技能、知识库
- ✅ **外部生态整合**：微信、钉钉、飞书等平台集成

## 模块分层

| 层次 | 名称 | 包含模块 | 职责 |
|------|------|---------|------|
| Layer 5 | 对话与交互层 | 意图理解、任务路由、多层协作可视化、编辑器内联命令、多端适配、REST API、WebSocket/SSE、RPC、CLI、DSL 指令 | 人机交互入口，意图表达与结果呈现，系统对外边界 |
| Layer 4 | 服务层 | 《框架内置》Auto Dev、文档、用户、任务、知识、外部整合、众包协作、虚拟空间；《自定义》用户在 AAF 上构建的具体业务 | 面向用户的具体业务逻辑 |
| Layer 3 | 智能层 | Core、Cognition、Agent、Assistant、Team | AI 推理与协作，五层智能架构 |
| Layer 2 | 引擎层 | 调度机制（执行调度器、状态管理器、上下文管理器、置信度门控器、元数据管理器）；专项引擎（DSL、工作流、工具、调度、文档、知识库、记忆、空间、语义组件、自进化、积分、结算、监控、权限） | 通用执行能力，无具体业务语义 |
| Layer 1 | 基础设施层 | PostgreSQL、Redis、Neo4j、向量库、Agent Sandbox、沙箱运行时 | 存储、通信、计算底座 |

**核心规则：上层可调用任意下层，禁止下层调用上层。**

## 架构设计

### 核心能力

- **智能体系统**：Agent 编排、多智能体协作
- **记忆系统**：短期记忆、长期记忆、情景记忆
- **语义组件**：核心组件元数据，支持对话生成界面
- **领域语言（DSL）**：交互指令、声明式工作流定义、组件及知识定义
- **对话式交互**：多轮对话、上下文管理
- **工作流**：流程编排、任务调度
- **知识库**：向量存储、语义检索
- **工具系统**：工具注册、调用、管理
- **沙箱环境**：代码安全执行、资源隔离

### 技术栈

- **核心框架**：Spring Boot 4、Spring AI、AgentScope
- **数据库**：Postgresql、Neo4j
- **工作流**：Flowable
- **工具库**：Hutool、Lombok、MapStruct
- **代码生成**：FreeMarker、JavaParser
- **测试**：JUnit 5、Mockito、Cucumber
- Nx Monorepo（单一代码库管理）
- Kiro CLI（开发助手）

## 项目结构

Nx monorepo，三端应用 + 共享包：

```text
apps/service/    → Spring Boot 后端
apps/webui/      → Next.js 前端（App Router）
apps/uniapp/     → UniApp 小程序/APP（待开发）
packages/        → 共享库（待建设）
```

后端 Maven 模块：

| 模块 | 职责 | 说明 |
|------|------|------|
| **aaf-dependencies** | 依赖管理 | 统一依赖及版本号管理（BOM） |
| **aaf-common** | 公共能力 | 工具类、常量、异常定义等 |
| **aaf-framework** | 核心框架 | 引擎层 + 智能层（Agent、工作流、知识库等） |
| **aaf-auto-dev** | AI 开发 | 运行时代码生成、分析、自进化 |
| **aaf-api** | 业务层 + 启动入口 | 业务模块（system/document/chat 等）+ 启动类 |

- 根包：`com.xuejiai.aaf`
- 业务模块包：`com.xuejiai.aaf.module.{模块名}`

更多请参考 [后端模块结构](docs/design/apps/service/module-structure.md) | [项目结构文档](docs/project-structure.md)

## 需求管理

> **文档是唯一真理**：代码是文档的实现结果，当代码与文档不一致时，以文档为准并修正代码。

开发新功能前，先写规范再写代码：

1. 在 `docs/prd/` 中编写用户故事（业务价值 + 验收标准）
2. 涉及架构、技术方案或功能与交互设计时，先在 `docs/design/` 中编写设计文档
3. 基于设计方案，细化需求规格（数据模型、接口、约束），并在需求文档中链接设计文档
4. 评审规范通过后，在任务列表中关联规范文件
5. 根据规范实现代码（AI 生成或手写）
6. 对照验收标准验证

文档关系：**需求文档**（做什么）→ **设计文档**（怎么做）→ **代码**（实现）

详见 [需求管理](docs/prd/README.md) | [架构设计](docs/design/README.md)

## 任务管理

[docs/task/backlog.md](docs/task/backlog.md) 是所有待办的唯一来源。

查看正在进行的任务 [AAF v0.1.0 任务计划](docs/task/aaf-v0.1.0.md)。

> 开发新功能前，请先确认任务已在对应迭代的任务列表中；如果没有，可按迭代新建任务文件。详见 [任务管理规范](docs/task/Readme.md)。

## 文档导航

- 📖 [开发规范](docs/reference/dev/development-standard.md) - 模块命名、依赖管理、代码规范
- 🏗️ [架构设计](docs/design/architecture.md) - 整体架构和设计理念
- 📁 [项目结构](docs/project-structure.md) - 详细的目录结构说明
- 📝 [如何创建模块](docs/guide/development/how-to-create-module.md) - 手把手教你创建业务模块
- ✏️ [如何编写文档](docs/Readme.md) - 文档结构、分类、命名与写作规范
- 📝 [任务看板](docs/task/backlog.md) - 所有待办任务
- 📝 [路线图](docs/prd/roadmap.md) - 版本里程碑计划
- 📝 [AI 协作开发](docs/guide/ai-development-guide.md) - AI 协作开发指南

## 设计原则

化繁为简 | DRY | 自动化 | 降低信息熵 | 价值驱动 | 最小可行实现 | 规范驱动 | AI 友好

详见 [设计原则文档](docs/explanation/design-principles.md)

## 开发约束

1. **业务代码放在 aaf-modules**：所有业务逻辑在此目录下开发
2. **依赖版本统一管理**：在 `aaf-dependencies` 中定义版本号
3. **遵循命名规范**：模块命名 `aaf-module-xxx`，包名 `com.xuejiai.aaf.module.xxx`
4. **规范一致性检查**：发现源码与项目原则/规范不一致时，必须提出并分析具体问题，记录到[任务日志](docs/task/aaf-v0.1.0.md)中
5. **按需渐进查阅文档**：AI 助手仅在任务相关时加载对应规范文档，不一次性读取所有规范

详细规范请参考 [开发规范](docs/reference/dev/development-standard.md)

---

<!-- AI-STOP-BEGIN: 以下内容面向人类用户（环境搭建、构建命令、贡献指南、作者信息等），AI 编程助手无需阅读 -->

## 快速开始

### 环境要求

- JDK 25+
- Maven 3.9+
- Postgresql 16.0+
- Neo4j
- Redis

### 配置管理

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/aaf_db
    username: postgres
    password: your_password
    driver-class-name: org.postgresql.Driver

aaf:
  enabled: true
  autodev-enabled: false  # 是否启用 AI 自动开发
  agent:
    default-model: gpt-4
    max-retry: 3
```

> 更多请参考 配置管理

### 构建项目

```bash
# 构建所有模块
mvn clean install

# 跳过测试构建
mvn clean install -DskipTests
```

> 更多构建与开发流程请参考 构建与开发指南

## 技术参考

- quarkus-starter
- JeecgBoot
- 芋道
- 若依

## 贡献指南

欢迎贡献代码、提交 Issue、完善文档！更多请参考[贡献指南文档](docs/guide/contributing-guide.md)

感谢所有为 AAF 做出贡献的开发者！👉 [查看贡献者列表](docs/contributors.md)

## 开源协议

本项目采用 [Apache License 2.0](LICENSE) 开源协议。

## 关于作者

- **作者**：AaronZZH
- **博客**：<https://aaronzzh.blog.csdn.net/>
- **微信**：Aaron-ZZH

💡 一位喜欢刨根问底的 AI 攻城狮  
💡 一人公司 | 精益创业 | 开源 OPC/Human3.0  
💡 学记助理 | 知识地图 | 智能知识管理 | 开放社区  
💡 第五世界 | AI 应用框架 | 元理论 | OPC 联盟

> 从心而为，找到所爱，持续付出  
> 关注我，先人一步把握 AI 时代机遇

**⭐ 如果这个项目对你有帮助，请给个 Star！**

<!-- AI-STOP-END -->
