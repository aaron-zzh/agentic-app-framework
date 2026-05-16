---
level: Practice
layer: Model
purpose: AAF 小程序/APP 多端兼容编码规范，开发时查阅
status: published
version: 1.0.0
date: 2026-05-16
author: AaronZZH
changelog:
  - 2026-05-16 | v1.0 基于 wot-starter cursor rules + AAF 实践整理第一版
gains:
  - 能正确使用条件编译处理多端差异
  - 能按规范组织页面和组件
  - 掌握全局反馈、路由导航、样式的正确用法
---

# 小程序/APP 编码规范（uniapp）

> 技术选型见 [tech-stack.md](../../../design/apps/uniapp/tech-stack.md) | 目录结构见 [directory-structure.md](../../../design/apps/uniapp/directory-structure.md)

## 一、目录约定

各层职责边界，详见目录结构文档：

| 层 | 职责 | 禁止 |
|----|------|------|
| `pages/` | 路由页面，组合 components + composables | 含业务逻辑，直接调用 api/ |
| `subPages/` | 同 pages/，按功能域分包 | 跨分包直接引用 |
| `components/` | 纯 UI，接受 props，emit 事件 | 引用 store/、api/、router/ |
| `composables/` | 可复用逻辑，封装 store + api 的组合 | 引用 components/ |
| `store/` | 纯客户端 UI 状态 | 存放服务端数据（用 alova 管） |
| `api/` | alova 请求，服务端数据获取与缓存 | 含 UI 逻辑 |
| `request/` | SSE/WebSocket 流式通信 | 含业务逻辑 |
| `platform/` | 平台差异封装 | 含业务逻辑 |
| `utils/` | 纯函数 | 副作用、平台 API 调用 |

## 二、多端差异处理

### 2.1 条件编译只在 platform/ 内部使用

业务代码不写 `#ifdef`，平台差异统一封装到 `platform/`：

```ts
// ✅ platform/index.ts 内部
// #ifdef MP-WEIXIN
export function checkNetwork() { /* 微信实现 */ }
// #endif
// #ifndef MP-WEIXIN
export function checkNetwork() { /* H5/APP 实现 */ }
// #endif

// ✅ 业务代码调用
import { checkNetwork } from '@/platform'
checkNetwork()

// ❌ 业务代码里直接写条件编译
// #ifdef MP-WEIXIN
wx.getNetworkType(...)
// #endif
```

### 2.2 禁止运行时平台判断

```vue
<!-- ✅ 条件编译（仅在 platform/ 内） -->
<!-- #ifdef MP-WEIXIN -->
<view>微信专属</view>
<!-- #endif -->

<!-- ❌ 运行时判断 -->
<view v-if="platform === 'weixin'">...</view>
```

## 三、样式规范

### 3.1 优先级

1. **UnoCSS 原子类**（首选）：`flex items-center px-4 rounded-2`
2. **wot- 前缀原子类**（使用 wot-ui design token）：`wot-bg-filled-content wot-text-text-main`
3. **SCSS**（仅用于复杂组件内样式，原子类无法表达时）

```vue
<!-- ✅ 优先原子类 -->
<view class="flex items-center gap-3 px-4 py-3 rounded-3 bg-white">

<!-- ✅ 使用 wot-ui token -->
<view class="wot-bg-filled-content wot-rounded-md wot-p-main">

<!-- ❌ 能用原子类的不写内联 style -->
<view style="display: flex; align-items: center; padding: 16px;">
```

### 3.2 尺寸单位

- 用 `rpx`，不用 `px`（UnoCSS 原子类已自动处理）
- 需要写具体数值时：`style="width: 200rpx"`

### 3.3 主题色

使用 campus 主题变量，不硬编码颜色值：

```vue
<!-- ✅ 使用主题变量 -->
<view style="background: var(--wot-primary-6)">
<view class="wot-bg-filled-content">

<!-- ❌ 硬编码颜色 -->
<view style="background: #8e44ad">
```

## 四、全局反馈

禁止直接调用 `uni.showToast` / `uni.showModal`，统一使用封装的 composable：

```ts
// ✅ 正确用法
const toast = useGlobalToast()
toast.success({ msg: '操作成功' })
toast.error({ msg: '操作失败' })
toast.warning({ msg: '请注意' })

const { confirm } = useGlobalDialog()
confirm({ title: '确认删除？', msg: '此操作不可撤销' })

const { loading, close } = useGlobalLoading()
loading('加载中...')
close()

// ❌ 禁止直接调用
uni.showToast({ title: '操作成功', icon: 'success' })
uni.showModal({ title: '确认', content: '...' })
```

## 五、路由导航

禁止直接调用 `uni.navigateTo` 等原生导航 API，统一使用 `useRouter()`：

```ts
const router = useRouter()

// push：压栈（可返回）
router.push({ name: 'chat-detail', params: { id: '123' } })

// replace：替换当前页（不可返回）
router.replace({ name: 'profile' })

// replaceAll：清空页面栈（登录/退出场景）
router.replaceAll({ name: 'index' })

// back：返回
router.back()
```

```ts
// ❌ 禁止直接调用原生导航
uni.navigateTo({ url: '/pages/chat/detail?id=123' })
uni.switchTab({ url: '/pages/index/index' })
```

## 六、状态管理

```ts
// ✅ 服务端数据用 alova 管理
const { data, loading } = useRequest(getChatList)

// ✅ 客户端 UI 状态用 Pinia
const chatStore = useChatStore()
chatStore.setStreamingState(true)

// ❌ 禁止把服务端数据复制到 store
const store = useChatStore()
store.list = await getChatList()  // 双真理源
```

## 七、TypeScript

- 禁止 `any`，禁止 `@ts-ignore`（特殊情况加注释说明原因）
- 组件 props 必须显式类型
- composable 返回值必须有类型

```ts
// ✅
interface ChatMessage {
  id: string
  content: string
  role: 'user' | 'assistant'
}

// ❌
const messages: any[] = []
```

## 八、页面定义

每个页面必须用 `definePage` 声明元信息：

```ts
definePage({
  name: 'chat-detail',        // 路由名（kebab-case）
  meta: { public: false },    // 默认需要登录，公开页加 public: true
  style: { navigationBarTitleText: 'AI 对话' },
})
```

权限控制通过 `meta` 声明，不在页面内部判断：

```ts
// 管理端页面
definePage({
  name: 'admin-users',
  meta: { permission: 'admin:user:list' },
})

// 公开页（tabbar 一级页、登录页、启动页）
definePage({
  name: 'index',
  meta: { public: true },
})
```
