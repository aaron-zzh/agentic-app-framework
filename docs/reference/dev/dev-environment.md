---
level: Practice
layer: Model
purpose: 开发环境搭建与工具配置参考
status: published
version: 1.0.0
date: 2026-05-06
author: AaronZZH
changelog:
  - 2026-05-06 | 补充 Front Matter
---

# 开发环境参考

本文档记录 AAF 项目的标准开发环境配置，包括 IDE、JDK、构建工具、编程助手及核心技术栈选型。

---

## IDE

| 工具 | 版本 | 用途 |
|------|------|------|
| IntelliJ IDEA | 2026 | Java 后端主力 IDE |
| VS Code | 最新稳定版 | 前端（Next.js）、文档编写、配置文件 |

---

## JDK

项目采用 **JDK 25 LTS** 作为运行时，提供两个发行版供选择：

| 发行版 | 下载地址 | 说明 |
|--------|----------|------|
| Liberica JDK 25 | https://bell-sw.com/pages/downloads/#jdk-25-lts | BellSoft 出品，完整 JDK + JavaFX，推荐日常开发 |
| GraalVM JDK 25 | https://www.graalvm.org/downloads/ | 支持 Native Image 编译，用于性能敏感场景或容器镜像优化 |

> 两个发行版均基于 OpenJDK 25，API 完全兼容，可按需切换。GraalVM 在需要 AOT 编译或 Native 部署时启用。

---

## 构建工具

| 工具 | 版本 | 用途 |
|------|------|------|
| Apache Maven | 3.9.15 | Java 后端依赖管理与构建 |
| Nx | 最新稳定版 | 前端 Monorepo 构建管理（Next.js 单体项目） |
| pnpm | 9+ | 前端包管理器（Nx workspace） |

**Maven 下载**：https://maven.apache.org/download.cgi  
**Nx 文档**：https://nx.dev

---

## 编程助手

| 工具 | 用途 |
|------|------|
| kiro-cli | AI 编程助手，规范驱动开发、代码生成、需求到代码全流程 |
| Amazon Q (IDE 插件) | IDE 内联补全、对话式开发辅助 |

**kiro-cli 最佳实践**：参见 kiro-cli 使用指南

---

## 大语言模型

### 主力平台

**阿里云百炼**（https://bailian.console.aliyun.com）

- 国内合规部署，数据不出境
- 支持通义千问全系列模型及第三方模型接入
- 与阿里云基础设施（OSS、函数计算等）深度集成
- 提供 Embedding、文生图、语音等多模态能力

```yaml
spring:
  ai:
    openai:
      # 阿里云百炼兼容 OpenAI API 格式
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-max
```

### 备选平台

**阶跃星辰开放平台**（https://platform.stepfun.com）

- 强推理能力，适合复杂逻辑和长链推理任务
- 同样兼容 OpenAI API 格式，切换成本极低

```yaml
spring:
  ai:
    openai:
      base-url: https://api.stepfun.com/v1
      api-key: ${STEPFUN_API_KEY}
      chat:
        options:
          model: step-2-16k
```

> 通过 Spring AI 统一抽象，切换平台只需修改 `base-url` 和 `model`，业务代码零改动。

---

## 智能体框架

| 框架 | 定位 | 说明 |
|------|------|------|
| Spring AI | 基础抽象层 | 统一 LLM 接入、Tool Calling、RAG 管道、向量存储 |
| AgentScope | 多智能体编排 | 阿里开源，支持多 Agent 协作、消息路由、分布式执行 |
| AAF 自研框架 | 业务智能体层 | 基于上述框架封装，提供领域 DSL、记忆管理、工作流集成 |

三层关系：**Spring AI**（模型接入）→ **AgentScope**（Agent 编排）→ **AAF 自研**（业务封装）

---

## 记忆系统

| 组件 | 类型 | 说明 |
|------|------|------|
| Reme | 第三方记忆框架 | 结构化记忆存储与检索，支持短期/长期记忆 |
| AAF 自研记忆模块 | 业务记忆层 | 情景记忆、用户画像、知识图谱记忆，基于 Neo4j + PostgreSQL |

---

## 部署环境

**目标云平台**：阿里云

| 服务 | 用途 |
|------|------|
| ECS / 容器服务 ACK | 应用运行时 |
| RDS PostgreSQL | 关系型数据库 |
| OSS | 文件与多媒体存储 |
| Redis（云数据库 Tair） | 缓存、会话、消息队列 |
| 函数计算 FC | 轻量任务、Webhook 回调 |
| 阿里云百炼 | 大模型推理、Embedding、向量检索 |

---

## 环境变量清单

```bash
# 阿里云百炼（主力大模型）
DASHSCOPE_API_KEY=sk-xxx

# 阶跃星辰（备选大模型）
STEPFUN_API_KEY=sk-xxx

# 阿里云基础服务
ALIYUN_AK=xxx
ALIYUN_SK=xxx

# 数据库
DATABASE_URL=jdbc:postgresql://localhost:5432/aaf_db
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=xxx

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=xxx
```

> 本地开发使用 `.env.local` 或 IDEA 的 Run Configuration 注入环境变量，**禁止提交到版本控制**。
