---
level: Practice
layer: Model
purpose: 需求管理目录入口
status: published
version: 1.1.0
date: 2026-05-02
author: AaronZZH
---

# 需求管理（Spec）

本目录管理 AAF 项目的功能需求文档，是开发的第一输入。

> 先写需求，再写代码。让需求成为人类和 AI 的共同真理来源。

## 目录索引

| 文档 | 说明 |
|------|------|
| [需求管理规范](../reference/dev/requirement-standard.md) | 核心原则、需求层级、生命周期、变更流程、开发流程 |
| [需求收集](requirements.md) | 未评估的业务需求和产品点子 |
| [路线图](roadmap.md) | 版本里程碑计划 |
| [用户反馈](user-feedback.md) | 产品使用体验反馈 |
| [改进意见](improvements.md) | 内部流程/规范/技术改进 |

### 文档定位区分

| 文档 | 定位 | 内容来源 | 流转去向 |
|------|------|---------|---------|
| **需求收集** | 业务需求池 | 人类用户、开发灵感 | 评估后转 backlog |
| **用户反馈** | 产品体验反馈 | 外部用户使用产品时的意见 | 整理后可转为需求 |
| **改进意见** | 内部改进 | 流程/规范/技术债 | 评估后转 backlog 或直接执行 |
| **路线图** | 版本规划 | 已确定的里程碑目标 | 拆分为用户故事 |

## 模块需求文档

按模块组织子目录，每个模块一个目录，功能一个文件：

```text
docs/prd/
  ├── Readme.md                  # 本文件
  └── {module-name}/             # 模块目录（kebab-case）
      └── {feature-name}.md      # 功能需求（用户故事 + 需求规格）
```

### 现有模块

| 模块 | 文档 | 说明 |
|------|------|------|
| auto-dev/ | [auto-dev-monitor.md](features/auto-dev-monitor.md) | Auto Dev 多智能体代码生成 + kiro-cli 监控 |
| document/ | [document-management.md](features/document-management.md) | 文档管理系统（块状存储 + Neo4j 关系图谱 + 全文检索） |
| chat/ | [chat-collaboration.md](features/chat-collaboration.md) | 聊天协作界面（流式对话 + AI Tool 修改文档） |
| system/ | [user-auth.md](features/user-auth.md) | 用户认证 + RBAC 角色权限 |
| framework/ | [license-control.md](features/license-control.md) | 开源框架授权控制（离线 JWT + 分散式权限耦合） |

> 随功能增长按模块持续新建子目录，结构与 `auto-dev/` 一致。
