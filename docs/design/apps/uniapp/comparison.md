---
level: Explanation
layer: Product
purpose: AAF uniapp 与 webui 技术选型对比
status: published
version: 1.1.0
date: 2026-05-14
author: AaronZZH
changelog:
  - 2026-05-14 | v1.1 UI 库换为 wot-design-uni v2，HTTP 换为 alova，加入 kids-app 借鉴点
  - 2026-05-14 | v1.0 初版
gains:
  - 理解两端技术选型差异的原因
  - 明确共享层边界，避免重复建设
  - 指导跨端功能的实现策略
---

# uniapp 与 webui 技术选型对比

> 两端共用同一套后端 API 和语义组件 DSL，前端渲染层各自独立。本文档记录差异点与共享策略。

## 一、整体定位对比

| 维度 | webui | uniapp |
|------|-------|--------|
| 目标平台 | 桌面 Web（主入口） | 微信小程序（主）+ H5 + APP |
| 交互模式 | 对话 + 工作区双栏，功能丰富 | 对话优先，轻量卡片，移动优先 |
| 用户场景 | 深度使用，复杂工作流 | 碎片化使用，快速查看/操作 |
| 首屏目标 | < 2s（SSR + PPR） | < 1.5s（小程序包体积优化） |
| 脚手架基础 | 自建（Next.js） | wot-starter |

## 二、技术栈对比

### 2.1 框架与语言

| 类别 | webui | uniapp | 差异说明 |
|------|-------|--------|---------|
| 框架 | Next.js 16 + React 19 | UniApp 3 + Vue 3 | 不同框架，共享后端 API |
| 语言 | TypeScript 6 | TypeScript ~5.5 | 版本略有差异，规范一致 |
| 构建 | Turbopack（Next.js 内置） | Vite 5 | 均为现代构建工具 |
| 渲染模式 | SSR + RSC + Streaming | CSR（小程序无 SSR） | 小程序不支持服务端渲染 |

### 2.2 样式方案

| 类别 | webui | uniapp | 差异说明 |
|------|-------|--------|---------|
| 原子化 CSS | Tailwind v4 | UnoCSS + `@uni-helper/unocss-preset-uni` | 小程序不支持 Tailwind |
| 组件样式 | shadcn/ui（Tailwind 驱动） | wot-design-uni（CSS 变量驱动） | 各自生态 |
| 主题系统 | CSS 变量 + OKLCH | 三层 design token（wot-ui v2）+ CSS 变量 | v2 起两端均用 CSS 变量，理念对齐 |
| UnoCSS 集成 | 无（用 Tailwind） | `@wot-ui/unocss-preset`（v2） | design token 映射为原子类 |
| 单位 | px / rem | rpx（小程序）/ px（H5） | 需注意多端单位适配 |

### 2.3 状态管理

| 类别 | webui | uniapp | 差异说明 |
|------|-------|--------|---------|
| 服务端状态 | TanStack Query | alova 请求策略 | 理念相似，生态不同 |
| 客户端状态 | Zustand | Pinia | Vue 生态用 Pinia |
| 持久化 | localStorage / Cookie | uni.storage（多端适配） | 存储 API 不同 |
| URL 状态 | nuqs（类型安全 searchParams） | 路由参数（pages.config.ts） | 小程序无 URL 概念 |

### 2.4 路由系统

| 类别 | webui | uniapp | 差异说明 |
|------|-------|--------|---------|
| 路由方案 | Next.js App Router（文件路由） | vite-plugin-uni-pages（文件路由） | 理念一致，实现不同 |
| 布局系统 | `app/layout.tsx` | `layouts/`（vite-plugin-uni-layouts） | 均为约定式布局 |
| 路由守卫 | Next.js middleware | `uni.addInterceptor` + @wot-ui/router | 拦截层不同 |
| 权限拦截 | middleware.ts | `router/index.ts`（uni.addInterceptor） | uniapp 在导航层统一拦截 |

### 2.5 数据获取与通信

| 类别 | webui | uniapp | 差异说明 |
|------|-------|--------|---------|
| HTTP | 原生 fetch（Next.js 扩展） | alova + @alova/adapter-uniapp | 小程序不支持 fetch |
| 代码生成 | @graphql-codegen | @alova/wormhole（alova gen） | 均支持从 schema 生成类型和请求代码 |
| AI 对话 | @assistant-ui/react + AG-UI | 自研 SSE/WebSocket 封装 | 小程序无成熟 AI 对话 UI 库 |
| 流式通信（小程序） | 不适用 | wx.request enableChunked | 小程序专属方案 |
| 流式通信（H5） | SSE / WebSocket | fetchEventSource（#ifdef H5） | 标准 SSE |

### 2.6 测试

| 类别 | webui | uniapp | 差异说明 |
|------|-------|--------|---------|
| 单元测试 | Vitest | Vitest + vitest-environment-uniapp | 框架一致，环境不同 |
| 组件测试 | @testing-library/react | 暂无成熟方案 | 小程序组件测试生态弱 |
| E2E | Playwright（规划中） | uni-automator | 各自平台工具 |

### 2.7 AI 辅助开发支持

| 类别 | webui | uniapp | 差异说明 |
|------|-------|--------|---------|
| 组件知识库 | 无专用工具 | `@wot-ui/cli` + MCP | uniapp 端反而更完善 |
| VSCode 插件 | shadcn/ui 无专用插件 | wot-ui-intellisense | 组件补全 + 属性校验 |
| AI skills | `.kiro/skills/` | `.agent/skills/`（wot-starter 内置） | 两端均有，格式不同 |

## 三、共享层策略（packages/）

两端共享的内容放入 `packages/`，避免重复建设。

| 共享内容 | 包名（规划） | 说明 |
|---------|------------|------|
| 业务类型定义 | `@aaf/types` | API 响应类型、业务实体类型 |
| 纯函数工具 | `@aaf/utils` | 格式化、校验、加密等无平台依赖的工具 |
| 语义组件 DSL | `@aaf/schema` | 组件元数据定义，两端共用同一套 schema |
| API 接口定义 | `@aaf/api-types` | OpenAPI schema 生成的 TypeScript 类型 |

**不共享的内容**：
- UI 组件（React vs Vue，渲染引擎不同）
- 路由逻辑（Next.js vs uni-app API 完全不同）
- 状态管理（Zustand vs Pinia）
- 样式（Tailwind vs UnoCSS）
- 流式通信（webui 用 assistant-ui 封装，uniapp 自研）

## 四、AI 对话功能实现对比

AAF 核心功能是 AI 对话，两端实现策略不同：

| 维度 | webui | uniapp |
|------|-------|--------|
| 对话 UI 库 | @assistant-ui/react（完整 UI 框架） | 自研组件（wot-design-uni 基础组件拼装） |
| 协议 | AG-UI 协议 | SSE / WebSocket（自行解析） |
| 流式渲染 | streamdown（增量 Markdown 解析） | 自研 StreamText 组件 |
| 工具调用展示 | ToolFallback 组件 | 自研卡片组件 |
| 多模态输入 | Lexical 富文本编辑器 | 原生 textarea + 附件选择 |
| 小程序 SSE | 不适用 | wx.request enableChunked（自研） |

## 五、开发体验对比

| 维度 | webui | uniapp |
|------|-------|--------|
| HMR 速度 | Turbopack（极快） | Vite（快） |
| TypeScript 支持 | 完整（Next.js 原生） | 完整（uni-helper 补全） |
| 路由类型安全 | Next.js 内置 | vite-plugin-uni-pages 生成 |
| 组件自动引入 | 无（Next.js 不需要） | vite-plugin-uni-components |
| 请求代码生成 | @graphql-codegen | alova gen（@alova/wormhole） |
| AI 组件辅助 | 无专用工具 | @wot-ui/cli + wot-ui-intellisense |
| 调试 | 浏览器 DevTools | 微信开发者工具 + vConsole |

## 六、kids-app 工程化借鉴点

kids-app 是 AAF uniapp 端的前身项目，以下工程化思路值得借鉴（代码层面已移植到 wot-starter 基础上）：

| 借鉴点 | 原理 | 在 AAF 中的落地 |
|--------|------|----------------|
| `platform/` 平台抽象层 | 统一封装微信/H5/APP 差异，调用方无需写 `#ifdef` | `src/platform/index.ts` |
| SSE 双端实现 | 小程序用 wx.request enableChunked，H5 用 fetchEventSource | `src/request/stream.ts` + `stream_h5.ts` |
| `store/app.ts` init() 模式 | 统一启动序列：检查网络→加载配置→设置主题→检查登录 | `src/store/app.ts` |
| `build/` Vite 配置分层 | plugins/config 拆到 build/ 目录，vite.config.ts 保持干净 | `build/plugins/` + `build/config/` |
| `uni.addInterceptor` 权限拦截 | 在导航层统一拦截，不在每个页面 onLoad 判断 | `src/router/index.ts` |

## 七、功能覆盖策略

uniapp 端不追求与 webui 功能完全对等，按移动端使用场景裁剪：

| 功能 | webui | uniapp | 说明 |
|------|-------|--------|------|
| AI 对话 | ✅ 完整 | ✅ 完整 | 核心功能，两端均支持 |
| 工作流编辑 | ✅ 可视化编辑器 | ❌ 不支持 | 复杂操作不适合移动端 |
| 知识库管理 | ✅ 完整 | ✅ 查看/简单操作 | 移动端只读为主，放分包 |
| 智能体广场 | ✅ 完整 | ✅ 浏览/使用 | 创建/编辑在 webui |
| 文档画板 | ✅ 完整 | ❌ 不支持 | 画板操作不适合移动端 |
| 用户管理 | ✅ 完整 | ✅ 个人设置 | 管理功能在 webui |
| 消息通知 | ✅ 完整 | ✅ 完整 | 移动端通知更重要 |
| 暗黑模式 | ✅ next-themes | ✅ wot-starter 内置 | 两端均支持 |
| 国际化 | 规划中 | ✅ vue-i18n（wot-starter 内置） | uniapp 端先行 |
