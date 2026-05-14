---
level: Practice
layer: Product
purpose: AAF 小程序/APP 目录结构设计（apps/uniapp）
status: published
version: 1.1.0
date: 2026-05-14
author: AaronZZH
changelog:
  - 2026-05-14 | v1.1 基于 wot-starter 重构，加入 platform/、build/、request/stream 等
  - 2026-05-14 | v1.0 初版
gains:
  - 了解 uniapp 目录分层规则与各层职责
  - 新成员能快速定位代码放置位置
  - 掌握与 webui 共享层（packages/）的边界
---

# AAF 小程序/APP 目录结构设计

> 技术选型依据见 [tech-stack.md](./tech-stack.md) | 与 webui 对比见 [comparison.md](./comparison.md)
>
> 以 **wot-starter** 为基础，在其目录结构上补充 AAF 特有的 `platform/`、`request/stream`、`build/` 分层等。

## 一、设计原则

### 1.1 对话优先，轻量化

uniapp 端以对话交互为主，UI 轻量，不引入 webui 的双栏工作区模式。目录结构比 webui 更扁平，减少嵌套层级。

### 1.2 分层与依赖方向

```text
packages/  ←  composables/  ←  pages/
（共享层）    （逻辑层）        （路由层）
                  ↑
            components/  ←  pages/
            （组件层）
                  ↑
    utils/ + platform/ + api/  ←  所有层均可依赖
            （基础层）
```

依赖方向规则（单向，禁止反向引用）：
- `pages/` → 可引用 components/ composables/ utils/ store/ api/
- `components/` → 可引用 composables/ utils/，禁止引用 pages/
- `composables/` → 可引用 utils/ store/ api/ platform/，禁止引用 components/ pages/
- `platform/` → 仅依赖 uni API，禁止引用业务层
- `utils/` → 零内部依赖
- `packages/` → 跨 webui 和 uniapp 共享，零内部依赖

### 1.3 各层职责

| 层 | 职责 | 示例 |
|----|------|------|
| `pages/` | 路由页面，组合 components + composables | chat/index.vue, home/index.vue |
| `subPages/` | 分包页面，避免主包超限 | subPages/settings/ |
| `layouts/` | 约定式布局（vite-plugin-uni-layouts） | default.vue, tabbar.vue |
| `components/` | 无业务语义的纯 UI 组件 | ChatBubble.vue, StreamText.vue |
| `composables/` | 组合式逻辑，可复用的 hooks | useChat.ts, useAuth.ts |
| `store/` | Pinia 状态模块 | app.ts, user.ts, chat.ts |
| `api/` | alova 请求层（含 mock） | core/, mock/, apiDefinitions.ts |
| `request/` | 流式通信（SSE/WebSocket） | stream.ts, stream_h5.ts |
| `platform/` | 平台差异化抽象层 | index.ts, provider/weixin/ |
| `router/` | 路由守卫与权限拦截 | index.ts |
| `utils/` | 纯函数工具 | format.ts, storage.ts |
| `styles/` | 全局样式、主题变量 | uni.scss |
| `static/` | 静态资源 | logo.png |

## 二、目录树

```text
apps/uniapp/
├── src/
│   ├── pages/                        # 主包路由页面（vite-plugin-uni-pages）
│   │   ├── index/
│   │   │   └── index.vue             # 首页
│   │   ├── chat/
│   │   │   ├── index.vue             # 对话列表
│   │   │   └── [id].vue              # 对话详情（动态路由）
│   │   ├── agent/
│   │   │   └── index.vue             # 智能体广场
│   │   └── profile/
│   │       └── index.vue             # 个人中心
│   │
│   ├── subPages/                     # 分包页面（@uni-ku/bundle-optimizer 管理）
│   │   ├── knowledge/                # 知识库（分包）
│   │   └── settings/                 # 设置（分包）
│   │
│   ├── layouts/                      # 约定式布局
│   │   ├── default.vue               # 空白布局
│   │   └── tabbar.vue                # 含底部 tabbar 的布局
│   │
│   ├── components/                   # 通用 UI 组件
│   │   ├── chat/
│   │   │   ├── ChatBubble.vue        # 对话气泡
│   │   │   ├── MessageInput.vue      # 输入框
│   │   │   └── StreamText.vue        # 流式文字渲染
│   │   ├── common/
│   │   │   ├── GlobalToast.vue       # 全局 Toast（wot-starter 内置）
│   │   │   ├── GlobalMessage.vue     # 全局 Message
│   │   │   └── GlobalLoading.vue     # 全局 Loading
│   │   └── agent/
│   │       └── AgentCard.vue
│   │
│   ├── composables/                  # 组合式逻辑
│   │   ├── useChat.ts                # 对话逻辑（调用 request/stream）
│   │   ├── useAuth.ts                # 认证状态
│   │   ├── useAgent.ts               # 智能体操作
│   │   ├── useTheme.ts               # 主题切换（wot-starter 内置）
│   │   ├── useTabbar.ts              # tabbar 状态
│   │   ├── useGlobalToast.ts         # 全局 Toast composable
│   │   └── useGlobalLoading.ts       # 全局 Loading composable
│   │
│   ├── store/                        # Pinia 状态
│   │   ├── index.ts                  # store 入口 + 持久化配置
│   │   ├── app.ts                    # 应用全局状态（含 init() 启动序列）
│   │   ├── user.ts                   # 用户状态
│   │   ├── chat.ts                   # 对话状态
│   │   └── themeStore.ts             # 主题状态（wot-starter 内置）
│   │
│   ├── api/                          # alova 请求层
│   │   ├── core/
│   │   │   ├── instance.ts           # alova 实例（@alova/adapter-uniapp）
│   │   │   ├── middleware.ts         # 全局中间件（token 注入、错误处理）
│   │   │   └── handlers.ts           # 响应处理
│   │   ├── mock/                     # @alova/mock 开发 mock
│   │   ├── apiDefinitions.ts         # alova gen 生成的接口定义
│   │   ├── createApis.ts             # alova gen 生成的请求方法
│   │   └── globals.d.ts              # alova gen 生成的类型
│   │
│   ├── request/                      # 流式通信（SSE/WebSocket）
│   │   ├── stream.ts                 # 微信小程序 SSE（wx.request enableChunked）
│   │   ├── stream_h5.ts              # H5 SSE（fetchEventSource，仅 #ifdef H5）
│   │   └── websocket.ts              # WebSocket 封装（可选）
│   │
│   ├── platform/                     # 平台差异化抽象层（借鉴 kids-app）
│   │   ├── index.ts                  # 统一导出：name/provider/platform/pay/share/checkNetwork
│   │   └── provider/
│   │       └── weixin/               # 微信专属能力（登录/支付/分享）
│   │
│   ├── router/                       # 路由守卫
│   │   └── index.ts                  # uni.addInterceptor 权限拦截（借鉴 kids-app）
│   │
│   ├── utils/                        # 纯函数工具
│   │   └── index.ts
│   │
│   ├── uni.scss                      # uni-app 全局样式变量
│   ├── static/                       # 静态资源
│   ├── App.vue                       # 应用根组件（调用 useAppStore.init()）
│   ├── App.ku.vue                    # uni-ku-root 版本（wot-starter 内置）
│   ├── main.ts                       # 入口文件
│   ├── env.d.ts
│   ├── auto-imports.d.ts             # unplugin-auto-import 生成
│   └── components.d.ts               # vite-plugin-uni-components 生成
│
├── build/                            # Vite 配置分层（借鉴 kids-app）
│   ├── plugins/
│   │   └── index.ts                  # 所有 Vite 插件集中注册
│   └── config/
│       └── proxy.ts                  # 开发代理配置
│
├── docs/                             # wot-starter 内置文档站（VitePress）
├── pages.config.ts                   # 路由配置（替代 pages.json）
├── manifest.config.ts                # 应用配置（替代 manifest.json）
├── alova.config.ts                   # alova gen 配置
├── uno.config.ts                     # UnoCSS 配置
├── vite.config.ts                    # Vite 配置（引用 build/）
├── tsconfig.json
└── package.json
```

## 三、关键模块说明

### 3.1 store/app.ts — 统一启动序列

借鉴 kids-app 的 `init()` 模式，在 `App.vue` 的 `onLaunch` 中调用一次：

```typescript
// store/app.ts
export const useAppStore = defineStore('app', () => {
  async function init() {
    // 1. 检查网络
    const online = await platform.checkNetwork()
    if (!online) return router.error('NetworkError')
    // 2. 加载远程配置（可选）
    // 3. 设置主题
    useThemeStore().init()
    // 4. 检查登录态
    const userStore = useUserStore()
    if (userStore.isLogin) await userStore.refreshToken()
  }
  return { init }
})
```

### 3.2 platform/index.ts — 平台抽象层

借鉴 kids-app 思路，用 Composition API 重写，消除 `#ifdef` 散落：

```typescript
// platform/index.ts
const platform = {
  name,           // 'WechatMiniProgram' | 'H5' | 'App'
  provider,       // 'wechat' | ''
  checkNetwork,   // () => Promise<boolean>
  getCapsule,     // 胶囊按钮信息
  navbar,         // 导航栏高度
}
export default platform
```

### 3.3 router/index.ts — 权限拦截

借鉴 kids-app 的 `uni.addInterceptor` 方式，在导航层统一拦截：

```typescript
// router/index.ts
uni.addInterceptor('navigateTo', {
  invoke(args) {
    return checkPermission(args.url)
  },
})
uni.addInterceptor('redirectTo', {
  invoke(args) {
    return checkPermission(args.url)
  },
})
```

### 3.4 request/stream.ts — 微信小程序 SSE

```typescript
// 微信小程序不支持标准 SSE，用 wx.request enableChunked 模拟
function streamPost(url, data, onData, onError?, onComplete?) {
  const requestTask = wx.request({
    url: baseUrl + url,
    method: 'POST',
    enableChunked: true,
    // ...
  })
  requestTask.onChunkReceived((res) => onData(decode(res.data)))
  return requestTask // 返回 task 供外部 abort()
}
```

### 3.5 api/core/instance.ts — alova 实例

```typescript
import { createAlova } from 'alova'
import AdapterUniapp from '@alova/adapter-uniapp'

export const alovaInstance = createAlova({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  ...AdapterUniapp(),
  beforeRequest(method) {
    const token = uni.getStorageSync('token')
    if (token) method.config.headers['Authorization'] = `Bearer ${token}`
  },
})
```

## 四、文件放置决策树

```text
这段代码应该放哪里？
│
├── webui 和 uniapp 都需要？ → packages/（共享层）
│
├── 平台差异化（微信/H5/APP）？ → platform/
│
├── 流式通信（SSE/WebSocket）？ → request/
│
├── 常规 HTTP 请求？ → api/
│
├── 纯 UI，无业务语义？ → components/
│
├── 可复用的组合式逻辑？ → composables/
│
├── 全局状态？ → store/
│
├── 纯函数工具？ → utils/
│
├── 路由守卫/权限？ → router/
│
└── 页面路由？ → pages/ 或 subPages/（分包）
```

## 五、与 webui 目录结构对比

| 维度 | webui（apps/webui） | uniapp（apps/uniapp） |
|------|--------------------|--------------------|
| 路由层 | `app/`（Next.js App Router） | `pages/` + `subPages/`（分包） |
| 布局层 | `app/layout.tsx` | `layouts/`（vite-plugin-uni-layouts） |
| 功能层 | `features/`（复杂功能模块） | 无（直接用 composables） |
| 组件层 | `components/`（ui/ + common/ + form/） | `components/`（按业务域分组） |
| 逻辑层 | `lib/`（api/ + queries/ + store/） | `composables/` + `api/` + `store/` |
| 流式通信 | 无独立层（assistant-ui 封装） | `request/`（自研双端实现） |
| 平台层 | 无（仅 Web） | `platform/`（多端差异抽象） |
| 状态管理 | TanStack Query + Zustand | alova（请求策略）+ Pinia（UI 状态） |
| 样式 | Tailwind v4 | UnoCSS + `@wot-ui/unocss-preset` |
| 构建配置 | Next.js 内置 | `build/`（plugins/ + config/） |
| 共享层 | `packages/`（待建设） | `packages/`（同上） |
