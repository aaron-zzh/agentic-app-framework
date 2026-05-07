# AAF 文档站点

基于 [Fumadocs](https://fumadocs.dev) + Next.js 16 构建的 AAF 对外文档站点，内容源自项目根目录 `docs/`。

## 开发

```bash
pnpm nx dev docs
```

访问 http://localhost:3001

## 构建

```bash
pnpm nx build docs
```

## 项目结构

| 路径 | 说明 |
|------|------|
| `app/(home)` | 首页（项目介绍、特性、架构层） |
| `app/docs/[[...slug]]` | 文档页面（动态路由，读取 `docs/` 内容） |
| `app/api/search/route.ts` | Orama 搜索 API（支持中文分词） |
| `lib/source.ts` | Fumadocs 内容源适配器 |
| `lib/layout.shared.tsx` | 导航配置（顶部 nav links） |
| `source.config.ts` | 内容源配置（过滤内部目录、frontmatter schema） |
| `components/mdx.tsx` | MDX 组件映射（Mermaid、Callout、Tabs 等） |

## 内容来源

内容读取自 `../../docs/`，以下目录**不展示**：

- `task/`、`prd/`、`tmp/`、`learn/`、`reference/team/`

`status: draft` 的文档自动过滤，不对外展示。

## 部署

Vercel 自动部署，仅 `docs/` 或 `apps/docs/` 有变更时触发构建。详见 `vercel.json` 和 `scripts/vercel-ignore-docs.sh`。
