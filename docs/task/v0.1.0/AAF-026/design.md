---
level: Practice
layer: Product
purpose: AAF-026 对外文档站点技术设计
status: active
version: 1.0.0
date: 2026-05-07
author: architect
---

# AAF-026 技术设计：对外文档站点（Fumadocs）

## 概述

基于 Fumadocs 构建 `apps/docs` 应用，读取项目 `docs/` 目录中的对外内容，生成静态文档站点。

## 技术决策

### ADR-1：选择 Fumadocs 而非 Nextra

- **决策**：使用 Fumadocs（fumadocs-ui + fumadocs-core + fumadocs-mdx）
- **原因**：
  - 基于 Next.js App Router，与 AAF 前端技术栈一致（Next.js 16）
  - 内置 Orama 搜索，无需额外配置
  - 支持 Root Folder 特性，适合多分类文档（指南/设计/参考/解释）
  - TypeScript 优先，类型安全的 frontmatter schema
  - 活跃维护，社区生态好
- **替代方案**：Nextra 4（也基于 Next.js，但 Fumadocs 功能更丰富、配置更灵活）

### ADR-2：独立应用而非 webui 子路由

- **决策**：`apps/docs` 作为独立 Nx 应用
- **原因**：
  - 独立部署，docs 变更不影响主应用
  - 独立构建缓存，加速 CI
  - 可单独配置 Vercel 项目，按需触发
  - 文档站点无需认证、无需后端 API
- **替代方案**：作为 webui 的 `/docs` 路由（耦合度高，部署不独立）

### ADR-3：内容目录引用策略

- **决策**：在 `source.config.ts` 中将 `dir` 指向 `../../docs`（相对于 apps/docs），并通过 glob 模式只包含对外目录
- **原因**：
  - 无需复制文件，直接读取源文件
  - 构建时过滤，排除内部目录
  - 文档修改后无需额外同步步骤
- **风险**：Fumadocs MDX 的 `dir` 配置需要验证是否支持 monorepo 中的上级目录引用

### ADR-4：搜索方案

- **决策**：使用 Fumadocs 默认的 Orama 搜索
- **原因**：
  - 零配置，开箱即用
  - 客户端搜索，无需服务端
  - 通过 API Route 提供搜索索引，支持静态部署
  - 搜索范围自动与内容源一致（排除的目录不会进入索引）

## 模块结构

```text
apps/docs/
├── app/
│   ├── layout.tsx              → 根布局
│   ├── global.css             → 样式入口
│   ├── page.tsx                → 首页
│   ├── docs/
│   │   ├── layout.tsx          → 文档区域布局
│   │   └── [[...slug]]/
│   │       └── page.tsx        → 文档页面
│   └── api/search/
│       └── route.ts            → 搜索索引 API
├── components/
│   └── mdx.tsx                 → MDX 组件覆盖
├── lib/
│   ├── source.ts               → 内容加载 + 过滤
│   └── layout.shared.tsx       → 导航配置
├── source.config.ts            → 内容源定义
├── next.config.mjs             → Next.js + fumadocs-mdx 插件
├── tsconfig.json
├── package.json
└── project.json                → Nx targets
```

## 关键文件设计

### source.config.ts

```typescript
import { defineDocs, defineConfig } from 'fumadocs-mdx/config';

export const docs = defineDocs({
  dir: '../../docs',
  // 通过 glob 只包含对外目录
  // 具体过滤方式需验证 fumadocs-mdx 是否支持 glob include/exclude
});

export default defineConfig();
```

**内容过滤实现方案**（按优先级尝试）：

1. **方案 A**：`defineDocs` 的 `dir` 支持 glob → 直接配置 `dir: '../../docs/{guide,design,reference,explanation,tutorial,learn}/**'`
2. **方案 B**：使用多个 `defineCollections` 分别指向各目录
3. **方案 C**：在 `lib/source.ts` 的 `loader()` 中通过 filter 过滤

### lib/source.ts

```typescript
import { docs } from 'collections/server';
import { loader } from 'fumadocs-core/source';

export const source = loader({
  baseUrl: '/docs',
  source: docs.toFumadocsSource(),
  // 过滤 status: draft 的文档
  // pageTree 自定义（如需要）
});
```

### app/layout.tsx

```typescript
import { RootProvider } from 'fumadocs-ui/provider/next';
import type { ReactNode } from 'react';
import './global.css';

export default function Layout({ children }: { children: ReactNode }) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body className="flex flex-col min-h-screen">
        <RootProvider>{children}</RootProvider>
      </body>
    </html>
  );
}
```

### app/docs/layout.tsx

```typescript
import { DocsLayout } from 'fumadocs-ui/layouts/docs';
import { source } from '@/lib/source';
import { baseOptions } from '@/lib/layout.shared';
import type { ReactNode } from 'react';

export default function Layout({ children }: { children: ReactNode }) {
  return (
    <DocsLayout tree={source.pageTree} {...baseOptions()}>
      {children}
    </DocsLayout>
  );
}
```

### app/docs/[[...slug]]/page.tsx

```typescript
import { source } from '@/lib/source';
import { notFound } from 'next/navigation';
import defaultMdxComponents from 'fumadocs-ui/mdx';

export default async function Page(props: { params: Promise<{ slug?: string[] }> }) {
  const params = await props.params;
  const page = source.getPage(params.slug);
  if (!page) notFound();

  const MDX = page.data.body;

  return (
    <article>
      <h1>{page.data.title}</h1>
      <MDX components={{ ...defaultMdxComponents }} />
    </article>
  );
}

export function generateStaticParams() {
  return source.generateParams();
}

export async function generateMetadata(props: { params: Promise<{ slug?: string[] }> }) {
  const params = await props.params;
  const page = source.getPage(params.slug);
  if (!page) notFound();

  return {
    title: page.data.title,
    description: page.data.description,
  };
}
```

### app/api/search/route.ts

```typescript
import { source } from '@/lib/source';
import { createFromSource } from 'fumadocs-core/search/server';

export const { GET } = createFromSource(source);
```

### project.json（Nx 配置）

```json
{
  "name": "docs",
  "projectType": "application",
  "sourceRoot": "apps/docs",
  "targets": {
    "build": {
      "command": "next build",
      "options": { "cwd": "apps/docs" },
      "cache": true,
      "inputs": [
        "{projectRoot}/**/*",
        "{workspaceRoot}/docs/**/*"
      ],
      "outputs": ["{projectRoot}/.next"]
    },
    "dev": {
      "command": "next dev --port 3001",
      "options": { "cwd": "apps/docs" },
      "continuous": true
    },
    "start": {
      "command": "next start",
      "options": { "cwd": "apps/docs" },
      "continuous": true
    },
    "typecheck": {
      "command": "tsc --noEmit -p tsconfig.json",
      "options": { "cwd": "apps/docs" },
      "cache": true,
      "inputs": ["{projectRoot}/**/*.ts", "{projectRoot}/**/*.tsx", "{projectRoot}/tsconfig.json"]
    },
    "check": {
      "dependsOn": ["typecheck", "build"],
      "executor": "nx:noop"
    }
  }
}
```

### package.json（关键依赖）

```json
{
  "name": "docs",
  "private": true,
  "dependencies": {
    "fumadocs-core": "^15.0.0",
    "fumadocs-mdx": "^11.0.0",
    "fumadocs-ui": "^15.0.0",
    "next": "^16.0.0",
    "react": "^19.0.0",
    "react-dom": "^19.0.0"
  },
  "devDependencies": {
    "@types/mdx": "^2.0.0",
    "@types/react": "^19.0.0",
    "tailwindcss": "^4.0.0",
    "typescript": "^5.8.0"
  }
}
```

## Vercel 部署配置

在 Vercel 项目设置中：

- **Root Directory**：`apps/docs`
- **Build Command**：`next build`（或通过 Nx：`pnpm nx build docs`）
- **Output Directory**：`.next`
- **Install Command**：`pnpm install`（monorepo 根目录）
- **Ignored Build Step**：配置 `git diff --quiet HEAD^ HEAD -- docs/ apps/docs/`（仅 docs 相关变更触发）

## Readme.md 作为 index 页面的处理

Fumadocs 默认将 `index.mdx` 作为目录索引页。AAF 项目使用 `Readme.md`，需要处理映射：

**方案**：在 fumadocs-mdx 配置中，通过文件名映射或 loader plugin 将 `Readme.md` 视为 `index`。

具体实现需验证 Fumadocs 是否原生支持 `Readme.md` → index 映射。如不支持，可通过：
1. 构建前脚本创建 symlink（`index.md` → `Readme.md`）
2. 自定义 loader plugin 处理文件名

## 风险与待验证项

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| `dir` 指向 monorepo 上级目录可能不被支持 | 无法读取内容 | 验证后备选 symlink 方案 |
| 现有 `.md` 文件的 frontmatter 格式不兼容 | 构建失败 | 自定义 schema 设为全部可选 |
| `Readme.md` 不被识别为 index | 目录无索引页 | loader plugin 或构建脚本 |
| 相对链接指向排除目录 | 死链接 | 构建时 lint 检查 + 降级为纯文本 |
| docs/ 中含非 markdown 文件（图片等） | 构建警告 | 配置忽略非 md 文件 |

## 不在本次范围

- 自定义主题/品牌色（使用 Fumadocs 默认主题）
- i18n 多语言（v0.1.0 仅中文）
- 版本化文档（docs 不分版本）
- 评论/反馈功能
- API 文档自动生成（OpenAPI）
