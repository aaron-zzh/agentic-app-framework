---
title: webui 代码审查报告
date: 2026-05-30
author: Kiro (architect)
scope: apps/webui 全量代码
---

# webui 代码审查报告

审查范围：`apps/webui/src` 全部源码
审查时间：2026-05-30
审查人：Kiro (architect)

## 总体评价

整体架构清晰，分层合理（app / features / components / lib / sections / providers），遵循了 Next.js App Router 最佳实践。核心模块（entity-engine、chatter、agui）设计有深度，API 客户端封装完善（Token 刷新、乐观锁、分页）。主要问题集中在：**未完成的集成工作**、**废弃文件残留**、**安全配置缺失**、**测试覆盖不足**四个方向。

---

## 问题清单

### blocker（必须修复）

**B-1 中间件路由守卫不完整**

文件：`src/middleware.ts`

`PROTECTED_PATHS` 只覆盖了 7 条路径，workspace 路由组下的 `/ai`、`/aigc`、`/workflow`、`/docs`、`/knowledge`、`/admin`、`/module` 等均未受保护，未登录用户可直接访问。

```ts
// 当前（不完整）
const PROTECTED_PATHS = [
  "/dashboard", "/module", "/settings",
  "/notifications", "/todos", "/trash", "/admin"
]

// 建议：改为保护整个 workspace 路由组
const PROTECTED_PATHS = ["/dashboard", "/ai", "/aigc", "/workflow",
  "/docs", "/knowledge", "/module", "/settings",
  "/notifications", "/todos", "/trash", "/admin"]
// 或更简洁：排除 /auth、/marketing、/api 之外全部保护
```

**B-2 废弃文件残留**

文件：`src/stores/chatter-store.ts`、`src/stores/voice-config.ts`

两个文件内容仅一行 `// deprecated` 注释，真实实现已迁移到 `src/lib/store/`，但废弃文件仍存在，会造成 import 混乱。应直接删除。

---

### major（建议修复）

**M-1 next-intl 集成未完成**

文件：`src/i18n/request.ts`、`next.config.ts`、`src/app/layout.tsx`

`i18n/request.ts` 已实现服务端配置，但：
- `next.config.ts` 未添加 `withNextIntl` 插件
- `layout.tsx` 未用 `NextIntlClientProvider` 包裹

注释中已写明集成步骤但未执行，导致 i18n 功能实际不可用。

```ts
// next.config.ts 需要添加
import createNextIntlPlugin from "next-intl/plugin"
const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts")
// ...
const plugins = [withNx, withSerwist, withNextIntl]
```

**M-2 next.config.ts 缺少 Content-Security-Policy**

文件：`next.config.ts`

当前安全头只有 `X-DNS-Prefetch-Control`、`X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy`，缺少 CSP 头。项目集成了 Three.js、Monaco Editor、ECharts 等第三方脚本，CSP 配置尤为重要。

```ts
// 建议添加（根据实际 CDN 域名调整）
{ key: "Content-Security-Policy",
  value: "default-src 'self'; script-src 'self' 'unsafe-eval' 'unsafe-inline'; ..." }
```

**M-3 Mock 数据未清理**

文件：`src/lib/queries/use-entity-list.ts`、`src/lib/_mock/entities.ts`、`src/features/entity-engine/entities/index.ts`

`useEntityList` 中有 `TODO` 注释说明 mock 数据需在后端就绪后移除，但 `_mock/entities.ts` 仍被生产代码引用。`entity-engine/entities/index.ts` 也是示例数据，注释说后端就绪后删除。

这两处 mock 数据会在生产环境中屏蔽真实 API 调用，属于功能性缺陷。

**M-4 TrackingProvider 实现但未启用**

文件：`src/features/agui/tracking/TrackingProvider.tsx`

`TrackingProvider` 实现完整（事件委托、批量上报、自定义事件广播），但未在任何 layout 中使用，埋点系统处于"实现但未启用"状态。如果需要启用，应在 `WorkspaceLayoutClient` 中包裹。

**M-5 API 路由 /api/chat 是 mock 实现**

文件：`src/app/api/chat/route.ts`

该路由返回随机模板文本，是开发阶段的占位实现。生产环境需替换为真实 LLM 代理或直接删除（由后端统一处理）。文件中无任何标注说明这是 mock，存在被误认为生产代码的风险。

**M-6 aigc/store.ts 配置项未持久化**

文件：`src/features/aigc/store.ts`

`model`、`resolution`、`aspectRatio` 是用户偏好配置，当前未持久化，每次刷新页面都会重置为默认值（`GPT Image 2`、`2K`、`9:16`）。应添加 `persist` 中间件。

```ts
// 建议
export const useAigcStore = create<AigcStore>()(
  persist(
    (set) => ({ ... }),
    { name: "aaf-aigc-config",
      partialize: (s) => ({ model: s.model, resolution: s.resolution, aspectRatio: s.aspectRatio }) }
  )
)
```

**M-7 QueryProvider 配置过于简单**

文件：`src/providers/QueryProvider.tsx`

- `staleTime` 固定 60s，不同数据类型（用户信息 vs 列表数据）应有不同策略
- 无 `retry` 配置（默认 3 次，对于 401/403 等错误应设为 0）
- 无全局 error boundary 集成，查询错误无法统一处理

```ts
// 建议
new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60 * 1000,
      retry: (failureCount, error) => {
        if (error instanceof ApiError && [401, 403, 404].includes(error.code)) return false
        return failureCount < 2
      },
      refetchOnWindowFocus: false
    }
  }
})
```

---

### minor（可选优化）

**m-1 entity-engine/lib/mixins.ts 是纯转发文件**

文件：`src/features/entity-engine/lib/mixins.ts`

该文件只做 re-export，将 `lib/modules/entity-mixins` 的内容转发出去，增加了一层无意义的间接层。建议直接从 `lib/modules/entity-mixins` 导入，或将 entity-engine 内部使用的 mixin 类型直接定义在 `entity-engine/types` 中。

**m-2 agui 单例类缺少测试**

文件：`src/features/agui/generation/ComponentGenerator.ts`、`src/features/agui/semantics/SemanticRegistry.ts`、`src/features/agui/intent/IntentMapper.ts`

三个核心类均使用全局单例模式，包含复杂的解析逻辑（自然语言 → 意图、意图 → 配置），但无任何单元测试。单例状态在测试间共享会导致测试污染。

建议：
1. 导出类本身（而非单例），允许测试中实例化
2. 为 `ComponentGenerator.parseIntent`、`ComponentGenerator.inferParams` 等核心方法补充测试

**m-3 WorkspaceLayout 中全局单例注册的 SSR 风险**

文件：`src/app/(workspace)/layout.tsx`

`registerDefaultComponents()` 在 Server Component 中调用，但注册表是全局单例（`entityRegistry`）。在 Next.js 多实例/边缘运行时环境下，每个请求可能共享同一个 Node.js 进程，也可能不共享，导致注册状态不确定。

建议：将注册逻辑移到模块级 side effect（`import "@/features/entity-engine/entities"`），而非在 layout 函数体中调用。当前 `entities/index.ts` 已有 side effect 注册，`registerDefaultComponents()` 的调用位置可以统一。

**m-4 lib/store 与 features/*/store 双层结构不统一**

全局 store 在 `lib/store/`（auth、ui、chatter、voice、ai-settings、ai-awareness、org），功能模块 store 在 `features/aigc/store.ts`。规则不明确：什么情况下放 `lib/store`，什么情况下放 `features/*/store`？

建议在文档中明确：
- `lib/store/`：跨模块共享的全局 UI 状态
- `features/*/store.ts`：仅在该功能模块内使用的局部 UI 状态

**m-5 测试覆盖率低**

当前只有 4 个测试文件：
- `use-entity-list.test.ts`
- `use-entity-mutations.test.ts`
- `ContextChip.test.tsx`
- `useSemanticDraggable.test.ts`

以下核心逻辑无测试：
- `lib/api/client.ts`（Token 刷新逻辑、401 重试）
- `lib/store/auth-store.ts`（cookie 同步）
- `lib/modules/entity-registry.ts`（mixin 解析、extends 继承）
- `lib/modules/entity-resolve.ts`
- `features/agui/generation/ComponentGenerator.ts`（意图解析）
- `middleware.ts`（路由守卫逻辑）

**m-6 AppSidebar 图标映射硬编码**

文件：`src/sections/layout/AppSidebar.tsx`

`ICON_MAP` 只映射了 6 个图标，后端 API 返回的菜单图标名如果不在映射表中，会渲染为空白。建议使用 lucide 的动态导入或扩展映射表。

**m-7 vitest.config.ts 未配置覆盖率阈值**

文件：`vitest.config.ts`

当前配置无覆盖率阈值，`@vitest/coverage-v8` 已安装但未配置 `coverage` 选项。建议添加最低覆盖率要求（如 branches: 60, functions: 70）。

---

## 重构建议

**R-1 统一路由保护策略**

将 `PROTECTED_PATHS` 改为白名单模式（只列出公开路径），避免新增路由时遗漏保护：

```ts
const PUBLIC_PATHS = ["/auth", "/marketing", "/api"]
const isPublic = PUBLIC_PATHS.some((p) => pathname.startsWith(p))
const isProtected = !isPublic
```

**R-2 Mock 数据隔离**

将 `_mock/` 目录的使用限制在测试环境，通过环境变量控制：

```ts
// use-entity-list.ts
if (process.env.NODE_ENV === "development" && process.env.NEXT_PUBLIC_USE_MOCK === "true") {
  const mock = _mockEntityData[entity.slug]
  if (mock) return { list: mock, total: mock.length, page, pageSize }
}
return fetchList(entity.apiPath, { ... })
```

**R-3 agui 单例改为可注入**

将 `ComponentGenerator`、`SemanticRegistry`、`IntentMapper` 的单例导出保留，但同时导出类本身，允许测试中创建独立实例：

```ts
// 导出类（测试用）
export class ComponentGeneratorImpl { ... }
// 导出单例（生产用）
export const ComponentGenerator = new ComponentGeneratorImpl()
```

**R-4 删除废弃文件**

直接删除：
- `src/stores/chatter-store.ts`
- `src/stores/voice-config.ts`

---

## 问题汇总

| 级别 | 数量 | 说明 |
|------|------|------|
| blocker | 2 | B-1 路由守卫不完整、B-2 废弃文件残留 |
| major | 7 | M-1 ~ M-7 |
| minor | 7 | m-1 ~ m-7 |

质量门控：blocker=2，不满足发布条件（blocker=0 且 major≤2）。
