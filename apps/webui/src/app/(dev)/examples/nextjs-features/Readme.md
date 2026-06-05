# Next.js App Router 特性演示

> 路径：`/dev/examples/nextjs-features`
> 来源：迁移自 `tmp/nextjs/xueji/apps/demo/src/app/(order)/`，用 Mock 数据替代 Prisma

## 演示的特性清单

| 特性 | 文件位置 | 关键 API |
|------|---------|---------|
| Route Groups（路由组） | `layout.tsx`、`dashboard/(overview)/` | `(groupName)` 目录 |
| View Transitions API | `layout.tsx`、`page.tsx`、`view/page.tsx` | `ViewTransitions`、`Link`、`useTransitionRouter` |
| 嵌套 Layout | `layout.tsx` + `dashboard/layout.tsx` | 多层 `export default function Layout` |
| loading.tsx 路由级骨架屏 | `dashboard/(overview)/loading.tsx` | 自动包裹 Suspense |
| Streaming + Suspense 细粒度 | `dashboard/(overview)/page.tsx` | `<Suspense fallback={<Skeleton>}>` |
| async Server Component | `_components/dashboard/cards.tsx` | 组件内直接 `await fetch` |
| Server Actions | `_actions/invoice.ts` | `'use server'`、`revalidatePath`、`redirect` |
| useActionState（React 19） | `_components/invoices/create-form.tsx` | `useActionState(action, initialState)` |
| URL 搜索参数驱动 | `_components/search.tsx`、`dashboard/invoices/page.tsx` | `useSearchParams`、`router.replace` |
| useDebouncedCallback | `_components/search.tsx` | `use-debounce` |
| 动态路由 `[id]` | `dashboard/invoices/[id]/edit/page.tsx` | `params: Promise<{ id: string }>` |
| error.tsx 错误边界 | `dashboard/invoices/error.tsx` | `error` + `reset` props |
| notFound() | `dashboard/invoices/[id]/edit/page.tsx` | `import { notFound } from 'next/navigation'` |
| 并行数据获取 | `dashboard/invoices/[id]/edit/page.tsx` | `Promise.all([...])` |
| connection() 强制动态 | `_components/dashboard/revenue-chart.tsx` | `import { connection } from 'next/server'` |
| PPR（部分预渲染） | `dashboard/layout.tsx`（注释说明） | Next.js 16 默认启用，无需配置 |

## 目录结构

```
nextjs-features/
├── layout.tsx                     ← Route Group Layout + ViewTransitions
├── page.tsx                       ← 首页：View Transitions 入口 + 特性导航
├── view/
│   └── page.tsx                   ← View Transitions 文档页
├── dashboard/
│   ├── layout.tsx                 ← 嵌套布局：侧边栏 + 内容区
│   ├── (overview)/
│   │   ├── loading.tsx            ← 路由级骨架屏
│   │   └── page.tsx              ← Dashboard 概览：多级 Suspense
│   ├── invoices/
│   │   ├── page.tsx              ← URL 搜索参数 + Suspense key 重置
│   │   ├── error.tsx             ← 错误边界
│   │   ├── create/page.tsx       ← 服务端预取 + useActionState
│   │   └── [id]/edit/page.tsx    ← 动态路由 + notFound + Promise.all
│   └── customers/
│       └── page.tsx
├── _actions/
│   └── invoice.ts                ← Server Actions（'use server'）
├── _data/
│   └── mock.ts                   ← Mock 数据 + 工具函数（替代 Prisma）
└── _components/
    ├── search.tsx                 ← useSearchParams + useDebouncedCallback
    ├── skeletons.tsx              ← 骨架屏组件（shimmer 动画）
    ├── dashboard/
    │   ├── sidenav.tsx
    │   ├── nav-links.tsx          ← usePathname 活跃高亮
    │   ├── cards.tsx             ← async Server Component（延时 1s）
    │   ├── latest-invoices.tsx
    │   └── revenue-chart.tsx     ← connection() 强制动态（延时 3s）
    ├── invoices/
    │   ├── table.tsx             ← async Server Component
    │   ├── status.tsx
    │   ├── buttons.tsx           ← Server Action 表单
    │   ├── breadcrumbs.tsx
    │   ├── pagination.tsx        ← URL 参数分页
    │   ├── create-form.tsx       ← useActionState（React 19）
    │   └── edit-form.tsx         ← .bind() 预填充 action 参数
    └── customers/
        └── table.tsx
```

## 关键模式说明

### View Transitions

```tsx
// 1. layout 中启用
import { ViewTransitions } from 'next-view-transitions'
<ViewTransitions><div>{children}</div></ViewTransitions>

// 2. 使用 Link（自动触发过渡）
import { Link } from 'next-view-transitions'
<Link href="/about">跳转</Link>

// 3. 自定义动画
const router = useTransitionRouter()
router.push('/about', { onTransitionReady: myAnimation })

// 4. 共享元素过渡（CSS 属性）
<h1 style={{ viewTransitionName: 'hero-title' }}>标题</h1>
```

### Suspense 流式渲染

```tsx
// loading.tsx = 整页 Suspense（粗粒度）
// <Suspense key={query}> = 组件级（细粒度），key 变化时重置
<Suspense key={query + page} fallback={<TableSkeleton />}>
  <DataTable query={query} page={page} />  {/* async Server Component */}
</Suspense>
```

### Server Actions

```ts
'use server'
// createInvoice 可直接传给 <form action={}>
export async function createInvoice(prevState: State, formData: FormData) {
  // 验证 → 写库 → revalidatePath → redirect
}
```

### URL 即状态

```tsx
// 搜索状态存在 URL，不需要 useState
// 刷新不丢失，可分享链接，服务端可读
const handleSearch = useDebouncedCallback((term) => {
  const params = new URLSearchParams(searchParams)
  params.set('query', term)
  router.replace(`${pathname}?${params.toString()}`)
}, 300)
```

## 依赖

```json
"next-view-transitions": "0.3.2",
"use-debounce": "10.0.4"
```
