---
level: Practice
layer: Product
purpose: AAF 小程序/APP 目录结构设计（apps/uniapp）
status: published
version: 2.0.0
date: 2026-05-15
author: AaronZZH
changelog:
  - 2026-05-15 | v2.0 以第一性原则重写，聚焦 AAF 最佳实践，去除脚手架实现细节
  - 2026-05-14 | v1.1 基于 wot-starter 结构调整
  - 2026-05-14 | v1.0 初版
gains:
  - 理解 AAF uniapp 端的目录分层原则
  - 新成员能快速定位代码放置位置
  - 掌握与 webui 共享层（packages/）的边界
---

# AAF 小程序/APP 目录结构设计

> 技术选型依据见 [tech-stack.md](./tech-stack.md) | 移动管理端方案见 [mobile-admin.md](./mobile-admin.md)

## 一、设计原则

### 1.1 三个核心问题

目录结构回答三个问题：

1. **这段代码属于哪个关注点？**（路由 / UI / 逻辑 / 数据 / 平台）
2. **它的复用范围是什么？**（仅此页面 / 跨页面 / 跨端）
3. **它依赖谁，谁依赖它？**（依赖方向单向，禁止循环）

### 1.2 分层与依赖方向

```text
packages/（跨端共享）
    ↑
pages/ + subPages/（路由层）
    ↑
components/（UI 层）    composables/（逻辑层）
    ↑                        ↑
        store/ + api/ + request/ + platform/（基础层）
                        ↑
                    utils/（工具层）
```

规则：上层可引用下层，禁止反向。同层之间：`composables/` 可引用 `store/`，`components/` 禁止引用 `store/`（通过 props/emit 通信）。

### 1.3 分包策略

小程序主包限制 2MB，分包策略：
- **主包**：启动必需的页面（首页、对话、个人中心）+ 全局基础设施
- **业务分包**：按功能域划分，各自独立，互不依赖
- **Echarts 分包**：图表库体积大，单独分包按需加载

## 二、目录树

```text
apps/uniapp/
├── src/
│   ├── pages/                    # 主包：启动必需页面
│   │   ├── index/                # 首页（tabbar）
│   │   ├── chat/                 # AI 对话（tabbar）
│   │   │   └── [id].vue          # 对话详情（动态路由）
│   │   └── profile/              # 个人中心（tabbar）
│   │
│   ├── subPages/                 # 业务分包（按功能域）
│   │   ├── agent/                # 智能体广场
│   │   ├── knowledge/            # 知识库
│   │   └── admin/                # 管理端（角色权限控制）
│   │       ├── dashboard/        # 数据看板
│   │       ├── users/            # 用户管理
│   │       └── audit/            # 内容审核
│   │
│   ├── subEcharts/               # Echarts 分包（图表按需加载）
│   │
│   ├── layouts/                  # 约定式布局
│   │   ├── default.vue           # 空白布局（登录页等）
│   │   └── tabbar.vue            # 含底部导航的布局
│   │
│   ├── components/               # 纯 UI 组件（无业务语义，无 store 依赖）
│   │   ├── chat/
│   │   │   ├── ChatBubble.vue    # 对话气泡（mp-html 渲染，支持 Markdown）
│   │   │   ├── MessageInput.vue  # 输入框（含图片选择入口）
│   │   │   ├── ImagePicker.vue   # 图片选择预览（多模态输入）
│   │   │   └── StreamText.vue    # 流式文字渲染
│   │   ├── agent/
│   │   │   └── AgentCard.vue     # 智能体卡片
│   │   ├── poster/
│   │   │   ├── PosterPreview.vue # 固定模板海报（lime-painter 声明式）
│   │   │   └── PosterEditor.vue  # 可编辑海报（gesti 手势交互）
│   │   └── common/
│   │       ├── GlobalToast.vue   # 全局 Toast
│   │       ├── GlobalLoading.vue # 全局 Loading
│   │       └── GlobalDialog.vue  # 全局 Dialog
│   │
│   ├── composables/              # 可复用组合式逻辑
│   │   ├── useChat.ts            # 对话逻辑（调用 request/stream）
│   │   ├── useAuth.ts            # 认证状态
│   │   ├── useAgent.ts           # 智能体操作
│   │   ├── useUploader.ts        # 文件上传（S3预签名+图片压缩）
│   │   ├── useTheme.ts           # 主题切换
│   │   ├── useTabbar.ts          # tabbar 状态
│   │   ├── useGlobalToast.ts     # 全局 Toast
│   │   ├── useGlobalLoading.ts   # 全局 Loading
│   │   └── useGlobalDialog.ts    # 全局 Dialog
│   │
│   ├── store/                    # Pinia 全局状态（纯客户端 UI 状态）
│   │   ├── app.ts                # 应用状态 + init() 启动序列
│   │   ├── user.ts               # 用户状态（登录态、权限）
│   │   ├── chat.ts               # 对话状态（消息列表、流式状态）
│   │   └── theme.ts              # 主题状态
│   │
│   ├── api/                      # alova 请求层（服务端数据）
│   │   ├── core/
│   │   │   ├── instance.ts       # alova 实例 + 拦截器
│   │   │   └── handlers.ts       # 统一响应处理
│   │   ├── chat.ts               # 对话接口
│   │   ├── agent.ts              # 智能体接口
│   │   ├── user.ts               # 用户接口
│   │   └── mock/                 # 开发 mock（@alova/mock）
│   │
│   ├── request/                  # 流式通信（SSE/WebSocket，alova 不覆盖此场景）
│   │   ├── stream.ts             # 微信小程序 SSE（wx.request enableChunked）
│   │   ├── stream_h5.ts          # H5 SSE（fetchEventSource，#ifdef H5）
│   │   └── websocket.ts          # WebSocket 封装
│   │
│   ├── platform/                 # 平台差异化抽象（调用方无需写 #ifdef）
│   │   ├── index.ts              # 统一接口：name/checkNetwork/capsule/navbar
│   │   └── provider/
│   │       └── weixin/           # 微信专属：登录/支付/分享/JS-SDK
│   │
│   ├── router/                   # 路由守卫与权限拦截
│   │   └── index.ts              # @wot-ui/router + uni.addInterceptor 权限层
│   │
│   ├── utils/                    # 纯函数工具（无副作用，无平台依赖）
│   │   └── index.ts
│   │
│   ├── uni_modules/              # uni_modules 插件（源码形式，不走 npm）
│   │   ├── mp-html/              # 富文本渲染（wot-starter 内置）
│   │   ├── z-paging/             # 虚拟列表分页（聊天记录模式）
│   │   ├── lime-painter/         # 声明式海报生成
│   │   ├── lime-qrcode/          # 二维码生成
│   │   └── qiun-data-charts/     # 轻量图表（备选，主方案用 uni-echarts）
│   │
│   ├── static/                   # 静态资源
│   ├── uni.scss                  # 全局样式变量
│   ├── App.vue                   # 根组件（onLaunch 调用 useAppStore.init()）
│   └── main.ts                   # 入口
│
├── pages.config.ts               # 路由配置（TypeScript，替代 pages.json）
├── manifest.config.ts            # 应用配置（TypeScript，替代 manifest.json）
├── alova.config.ts               # alova gen 配置（OpenAPI → 代码生成）
├── uno.config.ts                 # UnoCSS 配置
├── vite.config.ts                # Vite 配置
└── package.json
```

## 三、各层职责边界

| 层 | 职责 | 禁止 |
|----|------|------|
| `pages/` | 路由页面，组合 components + composables | 含业务逻辑，直接调用 api/ |
| `subPages/` | 同 pages/，按功能域分包 | 跨分包直接引用 |
| `components/` | 纯 UI，接受 props，emit 事件 | 引用 store/、api/、router/ |
| `composables/` | 可复用逻辑，封装 store + api 的组合 | 引用 components/ |
| `store/` | 纯客户端 UI 状态 | 存放服务端数据（用 alova 管） |
| `api/` | alova 请求，服务端数据获取与缓存 | 含 UI 逻辑 |
| `request/` | SSE/WebSocket 流式通信 | 含业务逻辑（只负责传输） |
| `platform/` | 平台差异封装 | 含业务逻辑 |
| `router/` | 路由守卫、权限拦截 | 含页面逻辑 |
| `utils/` | 纯函数 | 副作用、平台 API 调用 |

## 四、文件放置决策树

```text
这段代码应该放哪里？
│
├── webui 和 uniapp 都需要？ → packages/（跨端共享）
│
├── 平台差异（微信/H5/APP 行为不同）？ → platform/
│
├── SSE / WebSocket 流式通信？ → request/
│
├── 常规 HTTP 请求 / 数据缓存？ → api/
│
├── 全局状态（UI 状态，非服务端数据）？ → store/
│
├── 可复用逻辑（跨页面的 hooks）？ → composables/
│
├── 纯 UI 组件（无业务语义）？ → components/
│
├── 纯函数工具（无副作用）？ → utils/
│
├── 路由守卫 / 权限？ → router/
│
└── 页面路由？
    ├── 启动必需（tabbar 页）→ pages/
    └── 按需加载 → subPages/{功能域}/
```

## 五、关键设计决策

### 5.1 store 只管 UI 状态，不管服务端数据

服务端数据（列表、详情、分页）由 alova 的请求策略管理（`useRequest`、`usePagination`），不复制到 store。store 只存：登录态、主题、tabbar 选中项、流式对话的实时状态等纯客户端状态。

### 5.2 request/ 与 api/ 分离

`api/` 用 alova 处理标准 HTTP（有缓存、重试、分页策略）。`request/` 处理 SSE/WebSocket 流式通信（alova 不覆盖此场景），两者职责清晰，不混用。

### 5.3 platform/ 消除 #ifdef 散落

所有平台差异集中在 `platform/` 封装，业务代码调用 `platform.checkNetwork()` 而不是写 `#ifdef H5 ... #endif`。平台判断只在 `platform/` 内部出现。

### 5.4 分包按功能域，不按技术层

`subPages/admin/` 是管理端所有页面，`subPages/knowledge/` 是知识库所有页面。不按"所有列表页"/"所有详情页"分包——那样会导致分包间依赖混乱。

### 5.5 App.vue 只做初始化编排

```typescript
// App.vue
onLaunch(async () => {
  await useAppStore().init()  // 检查网络 → 设置主题 → 检查登录态
})
```

`init()` 内部按顺序调用各 store/composable，App.vue 本身不含业务逻辑。
