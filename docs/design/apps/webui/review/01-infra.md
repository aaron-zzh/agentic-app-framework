# 基础设施层审查报告

审查范围：next.config / middleware / providers / i18n / lib/api / lib/store / lib/hooks / lib/utils / lib/constants / lib/modules / lib/types / lib/queries
审查者：AI/architect
审查时间：2026-05-30

---

## 模块：next.config.ts

### 问题

- [minor] 缺少 `Strict-Transport-Security` 安全头，生产环境应强制 HTTPS。`apps/webui/next.config.ts:29`
- [minor] 缺少 `Content-Security-Policy` 头，建议至少配置基础 CSP 防止 XSS。`apps/webui/next.config.ts:28-36`
- [minor] `module.exports` 应改为 `export default`，与 `.ts` 文件的 ESM 风格一致。`apps/webui/next.config.ts:40`

### 建议

- 添加 `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload`
- 添加基础 CSP（至少 `default-src 'self'`），后续按需放宽
- 使用 `export default composePlugins(...plugins)(nextConfig)` 替代 CommonJS 导出

---

## 模块：middleware.ts

### 问题

- [major] Token 存在性检查不验证 Token 有效性，攻击者可伪造任意 cookie 值绕过路由守卫。`apps/webui/src/middleware.ts:30` — `request.cookies.get("aaf-token")?.value` 仅检查非空。
- [minor] `PROTECTED_PATHS` 未包含 `/aigc` 路径（paths.ts 中定义了 aigc 路由），可能导致未登录用户访问 AIGC 功能。`apps/webui/src/middleware.ts:16-24`

### 建议

- Middleware 层无法做完整 JWT 验证（无密钥），但可检查 token 格式（如 JWT 三段结构）作为基础防护，真正的鉴权由后端完成。当前设计可接受，但需确认后端对所有 API 做了 token 校验。
- 将 `/aigc` 加入 `PROTECTED_PATHS`（如果需要登录才能访问）。

---

## 模块：app/layout.tsx

### 问题

- [minor] `suppressHydrationWarning` 仅应加在 `<html>` 标签上（用于 next-themes），当前用法正确但缺少注释说明原因。`apps/webui/src/app/layout.tsx:30`

### 建议

- 无重大问题，结构清晰。Provider 嵌套顺序合理（Theme > Query > Tooltip > Nuqs）。

---

## 模块：app/(workspace)/layout.tsx

### 问题

- [major] `registerDefaultComponents()` 在 Server Component 顶层调用，每次请求都会执行。如果注册逻辑有副作用或非幂等操作，可能导致问题。`apps/webui/src/app/(workspace)/layout.tsx:11`
- [minor] `import "@/features/entity-engine/entities"` 依赖 side-effect import 注册实体，缺少注释说明为何需要在此处导入。`apps/webui/src/app/(workspace)/layout.tsx:9`

### 建议

- 确认 `registerDefaultComponents()` 是幂等的（重复注册不会报错或覆盖）。如果是，添加注释说明；如果不是，改为模块级单次初始化（如 `let registered = false` 守卫）。

---

## 模块：providers/

### 问题

- [minor] `ServiceWorkerRegister` 静默吞掉注册失败错误，生产环境无法排查 SW 问题。`apps/webui/src/providers/ServiceWorkerRegister.tsx:14-16`
- [minor] `QueryProvider` 的 `staleTime: 60 * 1000` 对所有查询统一设置，部分高频变化数据（如通知未读数）可能需要更短的 staleTime。`apps/webui/src/providers/QueryProvider.tsx:11`

### 建议

- SW 注册失败时至少在 `process.env.NODE_ENV === 'development'` 时打印警告。
- `staleTime` 作为全局默认值是合理的，各 query 可自行覆盖（已在 `use-notifications.ts` 中通过 `refetchInterval` 实现）。当前设计可接受。

---

## 模块：i18n/

### 问题

- [minor] `zod-error-map.ts` 中 `initZodErrorMap` 依赖在客户端 Provider 中手动调用，但未在任何 Provider 中看到实际调用代码，可能是死代码或集成未完成。`apps/webui/src/i18n/zod-error-map.ts:22-25`
- [minor] `request.ts` 中 `import(\`./messages/${locale}.json\`)` 使用动态路径，Webpack/Turbopack 会将整个 messages 目录打包。当前只有 2 个语言文件影响不大，但语言增多时需注意。`apps/webui/src/i18n/request.ts:24`

### 建议

- 确认 `initZodErrorMap` 是否已在某个 Provider 中调用，若未调用则补充集成或标记 TODO。
- 当前实现可接受，语言文件体积小。



---

## 模块：lib/api/client.ts

### 问题

- ✅ 已修复 [major] `request()` 函数在服务端（SSR）调用时会崩溃——`useAuthStore.getState()` / `useUIStore.getState()` / `useOrgStore.getState()` 依赖 Zustand 客户端 store，SSR 环境下 store 为空或未初始化。`apps/webui/src/lib/api/client.ts:49-51`
> 已修复｜2026-05-30｜提交：apps/webui/src/lib/api/client.ts
- [major] Token 刷新竞态：`refreshPromise` 是模块级变量，在并发请求场景下正确（锁机制），但刷新失败后调用 `redirectToLogin()` 使用 `window.location.href` 硬跳转，会中断所有并发请求且无法被 React 错误边界捕获。`apps/webui/src/lib/api/client.ts:72-74`
- [minor] `ListParams` 使用 `[key: string]: unknown` 索引签名，削弱了类型安全。`apps/webui/src/lib/api/client.ts:27`
- [minor] `buildQuery` 中 `String(v)` 对对象/数组会产生 `[object Object]`，缺少深层参数序列化。`apps/webui/src/lib/api/client.ts:119`

### 建议

- 为 SSR 场景提供独立的 server-side fetch 函数（不依赖 Zustand store），或在 `request()` 中检测 `typeof window === 'undefined'` 时跳过 store 读取。
- Token 刷新失败后抛出错误让调用方处理，而非直接跳转。跳转逻辑应在 React 层（如全局错误边界或 QueryClient 的 `onError`）统一处理。
- `ListParams` 可改为泛型 `ListParams<T extends Record<string, unknown> = Record<string, unknown>>` 或移除索引签名。

---

## 模块：lib/api/auth.ts

### 问题

- [minor] `getDeviceId()` 将设备 ID 存储在 `localStorage`，清除浏览器数据后会生成新 ID，可能影响设备绑定逻辑的准确性。`apps/webui/src/lib/api/auth.ts:80-86`
- [minor] `getOAuthUrl` 直接拼接 `state` 参数到 URL 中未做 URL 编码。`apps/webui/src/lib/api/auth.ts:68` — 应使用 `encodeURIComponent(state)`。

### 建议

- `getOAuthUrl` 改为 `request<string>(\`/auth/oauth/${provider}/url?state=${encodeURIComponent(state)}\`)`。
- `getDeviceId` 设计可接受，但需确认后端不依赖设备 ID 做安全决策。

---

## ✅ 已修复 模块：lib/api/dashboard.ts、notification.ts、stats.ts、chat.ts

### 问题

- [major] 这些文件各自定义了独立的 `req<T>()` 函数，与 `client.ts` 的 `request<T>()` 功能重复，但缺少 Token 注入、401 自动刷新、workspace/org header 等能力。`apps/webui/src/lib/api/dashboard.ts:97-104`、`apps/webui/src/lib/api/notification.ts:28-35`、`apps/webui/src/lib/api/stats.ts:47-54`、`apps/webui/src/lib/api/chat.ts:40-47`
- [minor] 重复的 `const BASE = process.env.NEXT_PUBLIC_API_URL ?? "/api"` 在多个文件中出现（dashboard、notification、stats、chat、permission、voice-config）。`apps/webui/src/lib/api/dashboard.ts:95`

### 建议

- **统一使用 `client.ts` 的 `request()` 函数**，删除各文件的私有 `req()` 实现。这些私有实现缺少认证头注入，上线后会导致 401 错误。这是最严重的架构一致性问题。
- `BASE_URL` 常量应从 `client.ts` 导出复用。

> 已修复｜2026-05-30｜提交：apps/webui/src/lib/api/dashboard.ts, apps/webui/src/lib/api/notification.ts, apps/webui/src/lib/api/stats.ts, apps/webui/src/lib/api/chat.ts

---

## ✅ 已修复 模块：lib/api/permission.ts

### 问题

- [major] `fetchEntityAccess` 未使用 `client.ts` 的 `request()` 函数，缺少 Authorization header，上线后必定 401。`apps/webui/src/lib/api/permission.ts:22-28`
- [minor] `EntityAccess` 类型在 `permission.ts` 和 `types/entity/access.ts` 中重复定义，且 `fieldAccess` 字段类型不一致——`permission.ts` 中为必填 `Record<string, FieldAccess>`，`types/entity/access.ts` 中为可选 `fieldAccess?: Record<string, FieldAccess>`。`apps/webui/src/lib/api/permission.ts:11` vs `apps/webui/src/lib/types/entity/access.ts:7`

### 建议

- 改用 `import { request } from "./client"` 并调用 `request<EntityAccess>(\`/permissions/entity/${slug}\`)`。
- 统一 `EntityAccess` 类型定义到 `types/entity/access.ts`，`permission.ts` 只导入使用。

> 已修复｜2026-05-30｜提交：apps/webui/src/lib/api/permission.ts

---

## 模块：lib/store/

### 问题

- [minor] `auth-store.ts` 将 `accessToken` 和 `refreshToken` 存储在 localStorage（通过 Zustand persist），XSS 攻击可直接读取。`apps/webui/src/lib/store/auth-store.ts:42` — `persist(..., { name: "aaf-auth" })`。
- [minor] `chatter-store.ts` 的 `syncToRemote` 函数直接调用 `fetch` 而非 `client.ts` 的 `request()`，缺少认证头。`apps/webui/src/lib/store/chatter-store.ts:72-81`
- [minor] `voice-config.ts` 的 `serverStt` 和 `serverTtsStream` 函数直接调用 `fetch`，缺少认证头。`apps/webui/src/lib/store/voice-config.ts:30-35`、`apps/webui/src/lib/store/voice-config.ts:38-50`
- [minor] `ui-store.ts` 未使用 `persist` 中间件，刷新页面后侧边栏状态、主题色等用户偏好丢失。`apps/webui/src/lib/store/ui-store.ts:33`

### 建议

- Token 存储在 localStorage 是 SPA 常见做法（httpOnly cookie 需要 BFF），当前可接受但需确保 CSP 防 XSS。
- `syncToRemote` 和 voice 函数应使用 `request()` 或至少手动注入 Authorization header。
- `ui-store` 中 `themeColor`、`compactLayout`、`sidebarOpen` 等偏好建议加 `persist`。
- 所有 store 仅存储客户端 UI 状态，未将服务端数据存入 Zustand，**符合规范**。✅



---

## 模块：lib/hooks/

### 问题

- [major] ~~`use-websocket.ts` 无最大重连次数限制，网络永久断开时会无限重连（指数退避到 30s 后持续每 30s 一次），浪费资源。~~ ✅ 已修复（添加 maxRetries 选项，默认 10 次） `apps/webui/src/lib/hooks/use-websocket.ts:79-82`
- [major] ~~`use-record-presence.ts` 直接构造 WebSocket 连接 `new WebSocket(\`/ws/presence?userId=${currentUser.id}\`)`，userId 未做 URL 编码，且 WebSocket URL 缺少协议前缀（依赖浏览器相对路径解析，部分环境不支持）。~~ ✅ 已修复（使用 getWsBaseUrl 模式 + encodeURIComponent） `apps/webui/src/lib/hooks/use-record-presence.ts:52`
- ✅ 已修复 [major] `use-chatter-config.ts` 的 `useEffect` 依赖数组包含 `configs`（整个对象引用），但 `setConfig` 会修改 `configs`，导致**无限循环**。注释说"只在 pageId 变化时执行"但实际依赖了 `configs`。`apps/webui/src/lib/hooks/use-chatter-config.ts:24-35`
> 已修复｜2026-05-30｜提交：apps/webui/src/lib/hooks/use-chatter-config.ts
- [minor] `use-ai-awareness.ts` 的 `useCallback` 依赖 `store` 对象引用（Zustand store 每次渲染引用不变，实际无害），但 `options.fields` 作为依赖可能导致不必要的重建（如果调用方每次传新数组）。`apps/webui/src/lib/hooks/use-ai-awareness.ts:42`
- [minor] `use-batch-operation.ts` 的 `options` 参数在 `useCallback` 依赖中，如果调用方每次传新对象会导致 `execute` 和 `pollProgress` 不断重建。`apps/webui/src/lib/hooks/use-batch-operation.ts:62`、`apps/webui/src/lib/hooks/use-batch-operation.ts:95`
- [minor] `use-export-progress.ts` 的 `cancel` 函数依赖 `progress` state，每次 progress 变化都会重建 cancel 函数。`apps/webui/src/lib/hooks/use-export-progress.ts:120`
- [minor] `use-doc-events.ts` 的 `onUpdate` 回调在依赖数组中，如果调用方未 memoize 会导致 EventSource 反复重建。`apps/webui/src/lib/hooks/use-doc-events.ts:12`
- [minor] `use-page-context.ts` 的 `availableComponents` 数组在依赖数组中，调用方每次传新数组引用会导致重复上报。`apps/webui/src/lib/hooks/use-page-context.ts:32`
- [minor] `use-relationship-picker.ts` 使用 `setTimeout` 返回值类型为 `ReturnType<typeof setTimeout> | undefined`，初始值 `undefined` 传给 `clearTimeout` 无害但类型不精确。`apps/webui/src/lib/hooks/use-relationship-picker.ts:28`

### 建议

- `use-websocket.ts`：添加 `maxRetries` 选项（默认 10），超过后停止重连并通知调用方。
- `use-record-presence.ts`：使用 `getWsBaseUrl()` 模式（如 `use-notification-ws.ts` 中已有）构造完整 WebSocket URL，并对 userId 做 `encodeURIComponent`。
- `use-chatter-config.ts`：从依赖数组中移除 `configs`，改为在 effect 内部通过 `useChatterStore.getState().configs[pageId]` 读取，避免循环。
- `use-batch-operation.ts` / `use-export-progress.ts`：将 `options` 和 `progress` 改为 ref 引用，避免 callback 重建。
- `use-doc-events.ts` / `use-page-context.ts`：文档中说明调用方需 memoize 回调/数组，或内部用 ref 存储。



---

## 模块：lib/utils/

### 问题

- 无问题。`cn.ts` 和 `time.ts` 实现简洁正确，类型完整。

### 建议

- 无。

---

## 模块：lib/constants/paths.ts

### 问题

- [minor] `paths.auth.oauthCallback` 路径为 `/login/oauth-callback`，与其他 auth 路径前缀 `/auth/` 不一致。`apps/webui/src/lib/constants/paths.ts:9`

### 建议

- 确认实际路由文件路径，如果是 `/auth/oauth-callback` 则修正常量。

---

## 模块：lib/modules/

### 问题

- [minor] `entity-resolve.ts` 中 `resolveExtends` 的 `getParent` 回调如果返回 `undefined` 会抛出 Error，但调用方（`entity-registry.ts`）未做 try-catch，注册了 `extends` 指向不存在实体的 EntityDef 会导致运行时崩溃。`apps/webui/src/lib/modules/entity-resolve.ts:52-54`
- [minor] `entity-registry.ts` 的 `get()` 方法在解析失败时会抛异常（来自 resolveMixins/resolveExtends），但 `getAll()` 遍历所有实体时一个失败会中断整个列表获取。`apps/webui/src/lib/modules/entity-registry.ts:47-58`

### 建议

- `getAll()` 中对单个实体解析失败做 try-catch，跳过并 console.warn，避免一个配置错误导致整个应用侧边栏空白。
- 或在 `register` 时做前置校验（检查 mixins/extends 引用是否存在）。

---

## 模块：lib/types/

### 问题

- [minor] `types/entity/access.ts` 中 `fieldAccess` 为可选（`fieldAccess?: Record<string, FieldAccess>`），但 `use-permission-guard.ts` 直接访问 `access?.fieldAccess[field]` 不会报错（可选链），而 `lib/api/permission.ts` 中定义为必填。类型不一致。`apps/webui/src/lib/types/entity/access.ts:7` vs `apps/webui/src/lib/api/permission.ts:11`

### 建议

- 统一为可选（`fieldAccess?: ...`），因为后端可能不返回该字段。`use-permission-guard.ts` 已正确处理了 undefined 情况。

---

## 模块：lib/queries/

### 问题

- [minor] `use-entity-list.ts` 和 `use-entity-detail.ts` 依赖 `@/lib/_mock/entities` mock 数据，生产构建时 mock 代码会被打包进 bundle。`apps/webui/src/lib/queries/use-entity-list.ts:4`、`apps/webui/src/lib/queries/use-entity-detail.ts:4`
- [minor] `use-entity-detail.ts` 的 `queryFn` 中直接调用 `fetch(\`/api/${entity.apiPath}/${id}\`)` 而非使用 `client.ts` 的 `request()` 或 `fetchRecord()`，缺少认证头和统一错误处理。`apps/webui/src/lib/queries/use-entity-detail.ts:15-19`
- [minor] `use-notifications.ts` 使用 mock 数据且 TODO 标记待替换，当前可接受但需跟踪。`apps/webui/src/lib/queries/use-notifications.ts:18-27`

### 建议

- Mock 数据应通过环境变量或 MSW（Mock Service Worker）隔离，避免生产 bundle 包含 mock 代码。可使用 `if (process.env.NODE_ENV === 'development')` 动态导入。
- `use-entity-detail.ts` 应使用 `fetchRecord()` 替代直接 `fetch`。

---

## 模块：vitest.config.ts

### 问题

- 无问题。配置正确，正确排除了 `.accept.test.ts` 验收测试文件。

### 建议

- 无。

---

## 模块：tsconfig.json

### 问题

- [minor] `exclude` 中排除了所有 `*.test.ts` 和 `*.spec.ts` 文件，这意味着 IDE 中测试文件不会获得项目级类型检查（依赖 vitest 的 `globals: true`）。这是常见做法但需注意测试文件中的类型错误不会被 `tsc --noEmit` 捕获。`apps/webui/tsconfig.json:33-37`

### 建议

- 可添加 `tsconfig.test.json` 继承主配置并 include 测试文件，供 CI 中单独检查测试类型。当前不阻塞。

---

## 汇总

| 级别 | 数量 |
|------|------|
| blocker | 0 |
| major | 3 (7 原始, 4 已修复) |
| minor | 22 |

### Major 问题清单

| # | 文件 | 问题 |
|---|------|------|
| ~~1~~ | ~~`lib/api/client.ts:49-51`~~ | ~~SSR 环境下 Zustand store 未初始化导致崩溃~~ ✅ 已修复 |
| 2 | `lib/api/client.ts:72-74` | Token 刷新失败后硬跳转中断并发请求 |
| ~~3~~ | ~~`lib/api/dashboard.ts` 等 4 文件~~ | ~~私有 `req()` 函数重复且缺少认证头~~ ✅ 已修复 |
| ~~4~~ | ~~`lib/api/permission.ts:22-28`~~ | ~~`fetchEntityAccess` 未注入认证头~~ ✅ 已修复 |
| 5 | `lib/hooks/use-websocket.ts:79-82` | 无限重连无上限 |
| 6 | `lib/hooks/use-record-presence.ts:52` | WebSocket URL 构造不安全 |
| ~~7~~ | ~~`lib/hooks/use-chatter-config.ts:24-35`~~ | ~~useEffect 依赖 configs 导致潜在无限循环~~ ✅ 已修复 |

### 优先修复建议

1. **最高优先级**：统一 API 调用层——将 dashboard/notification/stats/chat/permission 中的私有 `req()` 替换为 `client.ts` 的 `request()`。这是系统性问题，上线后会导致大面积 401。
2. **高优先级**：修复 `use-chatter-config.ts` 无限循环风险。
3. **中优先级**：`use-websocket.ts` 添加最大重连次数；`use-record-presence.ts` 修复 URL 构造。
4. **低优先级**：类型统一、mock 隔离、安全头补充等 minor 问题可在后续迭代中逐步修复。
