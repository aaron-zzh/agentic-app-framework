---
level: Practice
layer: Model
purpose: AAF-026 技术任务拆分
status: active
version: 1.0.0
date: 2026-05-07
author: architect
---

# AAF-026 技术任务

## 任务列表

| # | 任务 | 依赖 | 预估 | 说明 |
|---|------|------|------|------|
| #1 | 初始化 apps/docs 项目骨架 | 无 | S | Nx 项目 + Next.js + Fumadocs 依赖 + project.json |
| #2 | 配置内容源（source.config.ts + lib/source.ts） | #1 | M | 指向 docs/ 目录，实现内容过滤，验证上级目录引用 |
| #3 | 实现文档页面路由和渲染 | #2 | S | layout.tsx + page.tsx + MDX 组件 |
| #4 | 实现首页 | #3 | S | 项目介绍 + 快速入口导航 |
| #5 | 配置搜索功能 | #2 | S | Orama 搜索 API Route + 搜索 UI |
| #6 | 处理 Readme.md → index 映射 | #2 | M | 验证并实现目录索引页映射 |
| ✅ #7 | 配置 meta.json 导航排序 | #6 | S | 为对外目录添加 meta.json，配置 Root Folder tabs |
| #8 | 实现 draft 文档过滤 | #2 | S | frontmatter schema + loader 过滤 status:draft |
| ✅ #9 | Vercel 部署配置 | #3 | S | vercel.json + ignored build step 配置 |
| #10 | 验收测试 + 构建验证 | #1-#9 | M | 确认所有 AC 通过，check 全绿 |

## 任务详情

### #1 初始化 apps/docs 项目骨架

**产出**：
- `apps/docs/package.json`（依赖：fumadocs-core、fumadocs-mdx、fumadocs-ui、next、react）
- `apps/docs/project.json`（Nx targets：build、dev、start、typecheck、check）
- `apps/docs/tsconfig.json`（含 `collections/*` 路径别名）
- `apps/docs/next.config.mjs`（fumadocs-mdx 插件）
- `apps/docs/app/global.css`（Tailwind CSS 4 + Fumadocs 样式）
- `apps/docs/app/layout.tsx`（RootProvider 根布局）

**验收**：`pnpm nx dev docs` 能启动，访问 localhost:3001 不报错

### #2 配置内容源

**产出**：
- `apps/docs/source.config.ts`（defineDocs 指向 `../../docs`，过滤内部目录）
- `apps/docs/lib/source.ts`（loader 配置）

**验收**：`pnpm nx build docs` 能识别 docs/ 下的 md 文件，排除 task/prd/tmp

**风险**：需验证 `dir: '../../docs'` 是否被 fumadocs-mdx 支持。不支持则改用 symlink。

### #3 实现文档页面路由和渲染

**产出**：
- `apps/docs/app/docs/layout.tsx`（DocsLayout + sidebar）
- `apps/docs/app/docs/[[...slug]]/page.tsx`（动态路由 + MDX 渲染）
- `apps/docs/components/mdx.tsx`（组件映射）
- `apps/docs/lib/layout.shared.tsx`（导航配置）

**验收**：能浏览文档页面，左侧目录树、右侧 TOC、代码高亮正常

### #4 实现首页

**产出**：
- `apps/docs/app/page.tsx`（项目名称 + 导航入口 + 快速入门链接）

**验收**：访问 `/` 展示项目介绍，包含指南/设计/参考/解释入口

### #5 配置搜索功能

**产出**：
- `apps/docs/app/api/search/route.ts`（Orama 搜索 API）

**验收**：搜索框输入关键词能返回匹配结果，排除目录内容不出现在搜索结果中

### #6 处理 Readme.md → index 映射

**产出**：构建配置或 loader plugin，使 `Readme.md` 被识别为目录索引页

**验收**：点击目录名能展示对应 `Readme.md` 内容

### #7 配置 meta.json 导航排序

**产出**：
- 各对外目录下的 `meta.json`（title、pages 排序、root folder 配置）

**验收**：顶级目录显示为 Layout Tabs，子目录按指定顺序排列

### #8 实现 draft 文档过滤

**产出**：自定义 frontmatter schema + 过滤逻辑

**验收**：`status: draft` 的文档不出现在站点中，不可通过 URL 直接访问

### #9 Vercel 部署配置 ✅

**产出**：
- `apps/docs/vercel.json`（monorepo 根目录部署配置：buildCommand / outputDirectory / ignoreCommand）
- `scripts/vercel-ignore-docs.sh`（Ignored Build Step：docs/ 或 apps/docs/ 有变更时触发构建）

**验收**：推送 docs/ 变更触发部署，推送非 docs 变更不触发

### #10 验收测试 + 构建验证

**产出**：
- `pnpm nx check docs` 全绿
- 所有 US-1 ~ US-4 的 AC 逐项验证通过

**验收**：构建成功 + 首页可访问 + 内部目录 404 + 搜索可用 + 移动端正常
