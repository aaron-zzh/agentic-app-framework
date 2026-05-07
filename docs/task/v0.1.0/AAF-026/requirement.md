---
level: Practice
layer: Product
purpose: 对外文档站点需求规格
status: active
version: 1.2.0
date: 2026-05-07
author: AaronZZH
changelog:
  - 2026-05-07 | 技术方案从 Nextra 切换为 Fumadocs；补充数据模型、BR-5；完善技术方案细节
  - 2026-05-06 | product Spec 细化：补充业务规则、约束边界、精确 AC
---

# 对外文档站点

任务编号：AAF-026

## 背景

AAF 项目文档（`docs/`）需要对外展示，让外部用户/贡献者能在线浏览框架文档、设计文档、API 参考等。使用 Fumadocs（基于 Next.js App Router）构建文档站点，直接读取项目 `docs/` 目录。

## 用户故事

### US-1：在线浏览项目文档

**作为** 外部开发者，**我希望** 通过浏览器访问 AAF 文档站点，**以便** 了解框架能力、设计理念和使用方法。

#### 验收标准

```gherkin
Feature: 在线文档浏览

  Scenario: 访问文档首页
    Given 文档站点已部署
    When 用户访问站点根路径 "/"
    Then 页面展示项目名称 "AAF"
    And 页面包含导航菜单（至少包含"指南"、"设计"、"参考"三个入口）
    And 页面包含快速入门链接

  Scenario: 浏览文档目录树
    Given 项目 docs/ 下存在 "guide/"、"design/"、"reference/" 等目录
    When 用户打开文档站点任意页面
    Then 左侧展示文档目录树
    And 目录树层级与 docs/ 下的目录结构一致（排除内部目录）
    And 当前页面在目录树中高亮

  Scenario: 文档内容渲染
    Given 用户点击目录树中的 "design/architecture.md"
    When 页面加载完成
    Then 正文区域渲染 Markdown 内容（标题、代码块、表格、链接、图片）
    And 右侧展示页面内标题目录（TOC）
    And 代码块有语法高亮

  Scenario: 目录 Readme 作为索引页
    Given 目录 "docs/guide/" 下存在 "Readme.md"
    When 用户点击目录树中的 "指南" 目录
    Then 展示该目录的 Readme.md 内容作为索引页

  Scenario: 内部目录不展示
    Given docs/ 下存在 "task/"、"tmp/"、"prd/" 目录
    When 用户浏览文档站点
    Then 目录树中不出现这些内部目录
    And 直接访问对应 URL 返回 404
```

### US-2：全文搜索

**作为** 外部开发者，**我希望** 在文档站点中搜索关键词，**以便** 快速定位相关内容。

#### 验收标准

```gherkin
Feature: 文档搜索

  Scenario: 关键词搜索
    Given 文档站点已加载
    When 用户在搜索框输入 "架构约束"
    Then 下拉展示匹配的文档列表（标题 + 匹配片段）
    And 点击结果跳转到对应文档页面

  Scenario: 无结果提示
    Given 文档站点已加载
    When 用户搜索 "xyznotexist123"
    Then 展示"未找到相关文档"提示

  Scenario: 搜索范围限定
    Given "task/" 目录已被排除
    When 用户搜索仅存在于 task/ 下的关键词
    Then 搜索结果为空
```

### US-3：自动同步更新

**作为** 框架维护者，**我希望** 文档站点内容与 `docs/` 目录自动同步，**以便** 文档更新后无需手动操作即可上线。

#### 验收标准

```gherkin
Feature: 自动同步

  Scenario: 推送后自动部署
    Given docs/ 目录有新提交推送到 main 分支
    When CI/CD 流水线触发
    Then 文档站点在 5 分钟内完成重新构建并部署
    And 新内容在站点上可访问

  Scenario: 非文档变更不触发
    Given 仅修改了 apps/service/ 下的 Java 代码
    When 推送到 main 分支
    Then 文档站点不触发重新部署（或触发但无变更跳过）
```

### US-4：响应式与多端适配

**作为** 外部开发者，**我希望** 在手机或平板上也能正常阅读文档，**以便** 随时随地查阅。

#### 验收标准

```gherkin
Feature: 响应式适配

  Scenario: 移动端浏览
    Given 用户使用 375px 宽度的移动设备
    When 访问文档站点
    Then 目录树收起为汉堡菜单
    And 正文内容自适应宽度，无水平滚动
    And 代码块可水平滚动查看
```

## 业务规则

### BR-1：内容过滤规则

文档站点只展示适合对外的内容，以下目录**排除**：

| 排除目录 | 原因 |
|---------|------|
| `docs/task/` | 内部任务管理，含开发日志和技术任务 |
| `docs/prd/` | 内部产品需求，含未发布的规划 |
| `docs/tmp/` | 临时文件 |

**保留展示**的目录：

| 目录 | 对外展示名称 | 说明 |
|------|------------|------|
| `docs/guide/` | 指南 | 使用教程、快速入门 |
| `docs/design/` | 设计 | 架构设计、模块设计 |
| `docs/reference/` | 参考 | 开发规范、API 参考 |
| `docs/explanation/` | 解释 | 设计原则、架构思想 |
| `docs/tutorial/` | 教程 | 手把手教程 |
| `docs/learn/` | 学习 | 学习资料 |

**根目录散落文件处理**：
- `docs/Readme.md` → 站点首页内容来源
- `docs/project-structure.md`、`docs/references.md`、`docs/contributors.md` → 纳入展示

### BR-2：导航生成规则

- 目录树从 `docs/` 的目录结构自动生成，不手动维护
- 每个目录的 `Readme.md`（或 `index.mdx`）作为该目录的索引页
- 文档标题优先取 Front Matter 中的 `title` 字段，其次取一级标题 `# xxx`，最后取文件名
- 目录排序：优先取目录下 `meta.json`（Fumadocs 约定），无则按字母序
- 使用 Fumadocs 的 Root Folder 特性将顶级目录渲染为 Layout Tabs

### BR-3：Front Matter 处理

- `status: draft` 的文档不展示（构建时过滤）
- Front Matter 中的 metadata 不渲染到正文，但可用于 SEO（title、description）
- 自定义 frontmatter schema 支持 AAF 文档的 `level`、`layer`、`purpose`、`status` 等字段

### BR-4：链接处理

- 文档间的相对链接（`[xxx](../yyy.md)`）自动转换为站点内路由
- 指向排除目录的链接渲染为纯文本（不可点击）或移除
- 外部链接在新标签页打开

### BR-5：文件格式支持

- 支持 `.md` 和 `.mdx` 文件
- 现有 `docs/` 下全部为 `.md` 文件，无需改为 `.mdx`（Fumadocs MDX 同时支持两者）
- 图片等静态资源通过相对路径引用，构建时复制到 public/

## 数据模型

### 概念登记表

本项目为纯静态站点，无数据库。以下为构建时的内容模型：

| 产品概念 | 数据实体 | 存储形式 | 说明 |
|---------|---------|---------|------|
| 文档页面 | DocPage | `.md`/`.mdx` 文件 | 单个文档内容，含 frontmatter 元数据 |
| 目录元数据 | MetaConfig | `meta.json` 文件 | 控制目录排序、显示名称、是否为 root folder |
| 页面树 | PageTree | 构建时生成（内存） | 由 fumadocs-core loader 从文件系统自动生成 |
| 搜索索引 | SearchIndex | 构建时生成（静态 JSON） | Orama 搜索引擎的索引数据 |

### DocPage 字段定义（Front Matter Schema）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `title` | string | 否 | 页面标题，缺省取一级标题 |
| `description` | string | 否 | 页面描述，用于 SEO 和搜索摘要 |
| `status` | enum: active/draft/deprecated | 否 | 文档状态，draft 不展示 |
| `date` | string | 否 | 最后更新日期 |
| `author` | string | 否 | 作者 |
| `level` | string | 否 | AAF 文档层级（Practice/Principle 等） |
| `layer` | string | 否 | AAF 文档所属层（Model/Process 等） |
| `purpose` | string | 否 | 文档用途一句话描述 |

### MetaConfig 字段定义

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `title` | string | 否 | 目录显示名称 |
| `root` | boolean | 否 | 是否为 root folder（顶级 tab） |
| `pages` | string[] | 否 | 控制排序和可见性 |
| `defaultOpen` | boolean | 否 | 是否默认展开 |

## 约束与边界

### 技术约束

- 纯静态站点，无后端、无数据库、无运行时服务端
- 构建产物为静态 HTML/CSS/JS，可部署到任意静态托管
- 构建时间 < 60 秒（当前 docs/ 约 200 个文件）
- 单页加载时间 < 3 秒（首屏）

### 范围边界

| 做 | 不做 |
|----|------|
| Markdown 渲染（标题、代码块、表格、链接、图片） | 在线编辑 |
| 客户端全文搜索（Fumadocs 内置 Orama） | 服务端搜索 |
| 自动从 docs/ 生成目录 | 手动维护目录配置 |
| 响应式布局 | 原生 APP |
| Vercel 自动部署 | 自建 CI/CD |
| 暗色/亮色主题切换 | 自定义主题编辑器 |

### 非功能需求

- **SEO**：每页有 title + description meta 标签
- **可访问性**：符合 WCAG 2.1 AA（Fumadocs 默认满足）
- **国际化**：v0.1.0 仅中文，预留 i18n 目录结构

## 技术方案

- **框架**：Fumadocs（fumadocs-ui + fumadocs-core + fumadocs-mdx，基于 Next.js 16 App Router）
- **应用位置**：`apps/docs/`，Nx monorepo 中独立应用
- **内容源**：通过 fumadocs-mdx 的 `source.config.ts` 配置 `defineDocs({ dir })` 指向项目 `docs/` 中的对外目录
- **部署**：Vercel 静态部署，仅 docs 变更触发构建
- **搜索**：Fumadocs 内置 Orama 搜索（客户端全文搜索，通过 API Route 提供索引）
- **主题**：fumadocs-ui（自带目录树、TOC、搜索、暗色/亮色模式切换）

### 项目结构

```text
apps/docs/
├── app/
│   ├── layout.tsx              → 根布局（RootProvider + 字体）
│   ├── page.tsx                → 首页（项目介绍 + 快速入口）
│   ├── docs/
│   │   ├── layout.tsx          → 文档布局（DocsLayout + sidebar）
│   │   └── [[...slug]]/
│   │       └── page.tsx        → 文档页面渲染（动态路由）
│   └── api/search/
│       └── route.ts            → Orama 搜索 API
├── components/
│   └── mdx.tsx                 → MDX 组件映射
├── lib/
│   ├── source.ts               → Fumadocs source loader（内容过滤逻辑）
│   └── layout.shared.tsx       → 共享布局配置（nav title、links）
├── source.config.ts            → fumadocs-mdx 内容源配置（dir + schema）
├── next.config.mjs             → Next.js 配置（含 fumadocs-mdx 插件）
├── app/global.css              → Tailwind CSS 4 + Fumadocs 样式
├── tsconfig.json
├── package.json
└── project.json                → Nx 项目配置
```

### 内容过滤策略

在 `source.config.ts` 中通过多个 `defineDocs` 或单个 `defineDocs` + 自定义 glob 实现：
- 只包含 `guide/`、`design/`、`reference/`、`explanation/`、`tutorial/`、`learn/` 目录
- 排除 `task/`、`prd/`、`tmp/` 等内部目录
- `status: draft` 的文档通过自定义 frontmatter schema + loader 过滤

### 导航生成

- Fumadocs 自动从文件系统生成 Page Tree
- 顶级目录使用 Root Folder 特性，渲染为 Layout Tabs（指南 | 设计 | 参考 | 解释）
- 目录的 `Readme.md` 映射为 `index` 页面
- 排序通过 `meta.json` 控制

## 测试要点

- **验收测试**：构建成功 + 首页可访问 + 内部目录不可访问 + 搜索可用
- **关键场景**：
  - 排除目录的文档确实不出现在站点中
  - 文档间相对链接正确跳转
  - Front Matter `status: draft` 的文档不展示
  - 移动端布局正常

## 相关设计

- 文档体系：[docs/Readme.md](../../../Readme.md)
- 内容体系规范：[content-system](../../../reference/content-system/Readme.md)
- 技术设计：[design.md](./design.md)
- 技术任务：[tasks.md](./tasks.md)
