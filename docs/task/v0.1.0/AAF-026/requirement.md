---
level: Practice
layer: Model
purpose: 对外文档站点需求规格
status: active
version: 1.0.0
date: 2026-05-06
author: AaronZZH
---

<!-- ⚠️ 早期需求，未经过六问分析。进入开发前由 product agent 补充需求分析章节 -->
<!-- scope_mode: hold -->

# 对外文档站点

任务编号：AAF-026

## 背景

AAF 项目文档（`docs/`）需要对外展示，让外部用户/贡献者能在线浏览框架文档、设计文档、API 参考等。使用 Nextra（基于 Next.js）构建静态文档站点，直接读取项目 `docs/` 目录。

## 用户故事

### US-1：在线浏览项目文档

**作为** 外部开发者，**我希望** 通过浏览器访问 AAF 文档站点，**以便** 了解框架能力、设计理念和使用方法。

#### 验收标准

```gherkin
Feature: 在线文档浏览

  Scenario: 访问文档首页
    Given 文档站点已部署
    When 访问站点首页
    Then 展示文档导航和快速入门指引

  Scenario: 浏览文档目录
    Given 项目 docs/ 下有多级目录
    When 打开文档站点
    Then 左侧展示文档目录树（自动从 docs/ 结构生成）

  Scenario: 文档内容渲染
    Given 点击某篇 Markdown 文档
    Then 以格式化方式渲染（标题、代码块、表格、链接等）
```

### US-2：全文搜索

**作为** 外部开发者，**我希望** 在文档站点中搜索关键词，**以便** 快速定位相关内容。

#### 验收标准

```gherkin
Feature: 文档搜索

  Scenario: 关键词搜索
    Given 文档站点已加载
    When 在搜索框输入 "架构约束"
    Then 返回匹配的文档列表，高亮匹配片段
```

### US-3：自动同步更新

**作为** 框架维护者，**我希望** 文档站点内容与 `docs/` 目录自动同步，**以便** 文档更新后无需手动操作即可上线。

#### 验收标准

```gherkin
Feature: 自动同步

  Scenario: 推送后自动部署
    Given docs/ 目录有新提交推送到 main
    When CI/CD 触发
    Then 文档站点自动重新构建并部署
```

## 技术方案

- **框架**：Nextra 4（基于 Next.js App Router）
- **内容源**：通过 symlink `apps/docs/content` → `../../docs/` 读取项目文档
- **部署**：Vercel 静态部署（或 GitHub Pages）
- **搜索**：Nextra 内置搜索引擎

## 约束

- 只展示 `docs/` 下适合对外的内容（排除 `task/`、`tmp/` 等内部目录）
- 静态站点，无后端依赖
- 支持 Front Matter 中的 metadata 渲染

## 相关设计

- 文档体系：[docs/Readme.md](../../../Readme.md)
- 内容体系规范：[content-system](../../../reference/content-system/Readme.md)
