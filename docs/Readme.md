---
level: Practice
layer: Product
purpose: AAF 项目文档统一管理入口
status: published
version: 1.1.0
date: 2026-04-30
author: AaronZZH
scope:
  includes:
    - 目录结构说明
    - 文档管理原则
    - 快速导航
gains:
  - 能快速找到所需文档
---

# 文档中心

AAF 项目文档统一管理入口。

## 目录结构

每个顶层目录内部可包含子域 + guide/reference/tutorial/explanation 等子目录。

- `design/` — 设计文档，架构设计、技术选型、方案对比
- `explanation/` — 理解设计原则、技术决策背后的 Why
- `guide/` — 指南文档，构建、配置、模块创建等 How-to
- `reference/` — 各类规范、代码片段、模板速查
- `prd/` — 需求管理、路线图、改进意见、用户反馈
- `learn/` — 学习资料
- `task/` — 任务管理，版本迭代任务跟踪、工作日志
- `tmp/` — 临时目录

## 快速导航

### 入门

- 📁 [项目结构](project-structure.md) — 详细的目录结构说明
- 🏗️ [架构设计](design/architecture.md) — 整体架构和设计理念
- 📝 [如何创建模块](guide/development/how-to-create-module.md) — 手把手教你创建业务模块
- 🔧 [构建指南](guide/development/build-guide.md) — 构建与开发流程
- ⚙️ [配置指南](guide/development/config-guide.md) — 配置管理
- ❓ [开发者常见问题](reference/dev-faqs.md) | [用户常见问题](reference/user-faqs.md)

### 设计

- 🏛️ [架构设计总览](design/Readme.md) — 设计文档索引
- 🧠 [元引擎设计](design/framework/meta-engine.md) — 核心引擎架构
- 💬 对话式交互 — 交互层设计
- 🔐 权限系统 — 权限设计方案
- 📄 文档引擎 — 文档系统设计
- 📦 模块设计 — 模块化设计
- 🤖 [智能体设计](design/framework/intelligent/agent.md) — Agent 相关设计
- 🎨 [UI 设计](design/ui/Readme.md) — 界面与设计系统
- 🛠️ Auto Dev 设计 — AI 自动开发

### 原理 (Explanation)

- 💡 [设计原则](explanation/design-principles.md) — 核心设计原则
- 🧩 [架构思考](explanation/architecture-thought.md) — 架构决策背后的思考
- 📐 [规范驱动开发](explanation/spec-driven-development.md) — 为什么先写规范再写代码
- 🎨 [为什么选 OKLCH](explanation/why-oklch.md) — 色彩方案选型

### 开发规范 (Reference)

- 💻 [编码风格规范](reference/dev/apps/service/coding-style-standard.md) — 代码风格与命名（后端）
- 🏗️ [架构约束](reference/dev/architecture-constraints.md) — 分层与依赖规则
- 🧱 [领域建模规范](reference/dev/apps/service/domain-modeling-standard.md) — DDD 建模标准（后端）
- 🌿 [Git 规范](reference/dev/git/Readme.md) — 分支、提交、发布
  - [提交规范](reference/dev/git/commit-standard.md)
  - [分支管理](reference/dev/git/branch-manage-standard.md)
  - [发布规范](reference/dev/git/release-standard.md)
  - [协作指南](reference/dev/git/collaboration-guide.md)
  - [工具指南](reference/dev/git/tool-guide.md)
- 🧪 [测试规范](reference/dev/test/unit-test-standard.md)
- 🔧 [开发环境](reference/dev/dev-environment.md) — 环境搭建
- 📘 [Spring Boot 参考](reference/dev/apps/service/springboot-help.md)
- 🐛 已知问题
- 📋 代码片段：[编码](reference/dev/snippets/coding-snippets.md) · [领域](reference/dev/snippets/domain-snippets.md) · [模块](reference/dev/snippets/module-snippets.md) · [测试](reference/dev/snippets/testing-snippets.md)

### 文档体系规范

- 📚 [内容体系总览](reference/content-system/Readme.md)
  - [文档路由规范](reference/content-system/doc-route-standard.md) — 文档放哪里
  - [文档元数据规范](reference/content-system/doc-meta-standard.md) — YAML Front Matter
  - [文件命名规范](reference/content-system/file-name-standard.md) — kebab-case 命名
  - [内容标准](reference/content-system/content-standard/Readme.md) — 各类型文档写作规范
  - [管理标准](reference/content-system/management-standard/Readme.md) — 验证、演进、术语表

### AI 协作

- 🤝 [AI 协作开发指南](guide/ai-development-guide.md) — AI 多智能体协作体系
- 👥 [团队规范总览](reference/team/Readme.md)
  - [协作规范](reference/team/collaboration-standard.md)
  - [流程规范](reference/team/process-standard.md)
  - [上下文管理](reference/team/context-management-standard.md)
  - [角色定义](reference/team/roles/) — 协调者、架构师、产品、开发、测试等
  - [技能库](reference/team/skills/Readme.md)
- 🔧 [Kiro CLI 指南](guide/kiro/kiro-cli-guide.md) — Kiro CLI 使用详解
- ✨ [Kiro CLI 最佳实践](guide/kiro/kiro-cli-best-practices.md)

### 需求管理

- 📋 [需求管理](prd/Readme.md) — 需求管理入口
  - [需求管理规范](reference/dev/requirement-standard.md)
  - [路线图](prd/roadmap.md) — 版本里程碑计划
  - [改进意见](prd/improvements.md)
  - [用户反馈](prd/user-feedback.md)

### 任务管理

- 📌 [任务看板](task/backlog.md) — 所有待办的唯一来源
- 📅 [定时任务与沟通机制](task/scheduler.md) — 日报/周报/迭代会
- 📖 [任务管理规范](task/Readme.md)

### 其他

- 🤝 [贡献指南](guide/contributing-guide.md)
- 👥 [贡献者列表](contributors.md)
- 📖 [学习资料](learn/Readme.md)

## 文档管理原则

1. **知识框架定位**：每篇文档通过 `level`（认知维度）、`layer`（抽象层级）明确定位，结合 `type`（文档类型）决定呈现方式，详见 [文档元数据规范](reference/content-system/doc-meta-standard.md)
2. **目录即分类**：目录按文档类型划分，文件放入对应目录，不混放
3. **命名规范**：文件名使用 kebab-case，详见 [文件命名规范](reference/content-system/file-name-standard.md)
4. **元数据必填**：正式文档须包含 YAML Front Matter 元数据头
5. **单一职责**：一篇文档解决一个问题，跨主题内容拆分并交叉引用
6. **及时归档**：`tmp/` 中的内容应尽快整理归入正式目录或清理
7. **适度篇幅**：根据文档类型控制长度，超过 300 行考虑拆分

### 文档长度参考

| 类型 | 建议行数 | 说明 |
| --- | --- | --- |
| Reference（手册） | 50-150 行 | 速查为主，信息密度高 |
| Guide（指南） | 80-200 行 | 步骤清晰即可，超长应拆分 |
| Explanation（原理） | 100-300 行 | 需要深度但不宜成论文 |
| Tutorial（教程） | 150-400 行 | 需完整上下文，可分章节 |
| Map（地图） | 50-100 行 | 以链接和结构为主，本身要精简 |

### 元数据速查

每篇文档的 YAML Front Matter 中需填写 `level` 和 `layer`，参考以下选值：

**level**（认知维度）：Reality · Thought · Theory · Practice · Meaning

**layer**（抽象层级）：Principle · Paradigm · Pattern · Model · Product

详见 [文档元数据规范](reference/content-system/doc-meta-standard.md)
