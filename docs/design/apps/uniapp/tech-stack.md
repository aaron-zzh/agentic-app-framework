---
level: Practice
layer: Product
purpose: AAF 小程序/APP 技术选型与决策记录
status: published
version: 1.1.0
date: 2026-05-14
author: AaronZZH
changelog:
  - 2026-05-14 | v1.1 UI 库换为 wot-design-uni v2，HTTP 换为 alova，明确以 wot-starter 为脚手架基础
  - 2026-05-14 | v1.0 初版
gains:
  - 了解 uniapp 端各技术选型的决策依据
  - 新成员能快速理解多端适配策略与工程化方案
  - 掌握 wot-starter + wot-design-uni v2 生态的核心价值
---

# 小程序/APP 技术选型（uniapp）

> 目标平台：微信小程序（主）+ H5 + iOS/Android APP。以 **wot-starter** 为脚手架基础，技术选型以现代化工程体验为核心，对齐 webui 的 Vite + TypeScript 技术路线。

## 一、技术栈总览

| 类别 | 选型 | 版本 | 说明 |
|------|------|------|------|
| 跨端框架 | UniApp | 3.x | 一套代码编译到微信小程序 / H5 / iOS / Android |
| 语言 | Vue 3 + TypeScript | Vue ~3.4 / TS ~5.5 | `<script setup>` Composition API，严格模式 |
| 构建工具 | Vite | ^5 | 与 webui 统一，HMR 极速 |
| 脚手架基础 | wot-starter | latest | wot-design-uni 官方模板，开箱即用 |
| UI 组件库 | wot-design-uni | ^1.14（→ v2） | 真正 Vue 3 Composition API，AI 友好 |
| 原子化 CSS | UnoCSS + `@uni-helper/unocss-preset-uni` | 66.x | 多端兼容原子类；v2 起可用 `@wot-ui/unocss-preset` |
| 状态管理 | Pinia | ^2 | Vue 3 官方推荐，支持持久化 |
| 路由 | vite-plugin-uni-pages + @wot-ui/router | ^0.3 / ^1.1 | 文件路由 + 类型安全路由守卫 |
| 布局系统 | vite-plugin-uni-layouts | ^0.1 | 类 Nuxt 的 layouts 系统 |
| 组件自动引入 | vite-plugin-uni-components | ^0.2 | 按需自动引入，零手动注册 |
| HTTP 客户端 | alova + @alova/adapter-uniapp | ^3 | 请求策略库，支持代码生成，替代手动封装 |
| 流式通信 | SSE / WebSocket（自研） | — | AI 对话核心，双端实现（见下） |
| 组合式工具 | @vueuse/core | ^11 | 通用 composable 工具集 |
| 国际化 | vue-i18n | ^9 | 多语言支持 |
| 图表 | uni-echarts | ^2 | 分包加载，避免主包超限 |
| 测试 | Vitest + vitest-environment-uniapp | ^4 | 单元测试，与 webui 统一 |
| 代码规范 | @uni-helper/eslint-config | ^0.5 | uni-app 专属 ESLint 规则 |
| 提交规范 | commitlint + git-cz | — | 与 AAF 主仓库规范一致 |
| 包体积优化 | @uni-ku/bundle-optimizer | ^2 | 主包瘦身，分包策略 |

## 二、脚手架基础：wot-starter

**直接以 wot-starter 为起点**，不从零搭建，不从 kids-app 提取升级。

wot-starter 已内置：uni-helper 插件三件套、UnoCSS、Pinia、@wot-ui/router、alova、vue-i18n、uni-echarts、暗黑模式、全局 Toast/Message/Loading、AI skills、commitlint、包体积优化。

**在 wot-starter 基础上补充的内容**（来自 kids-app 的工程化借鉴）：

| 补充内容 | 来源 | 说明 |
|---------|------|------|
| `src/platform/` 平台抽象层 | kids-app 思路 | 统一封装微信/H5/APP 差异，调用方无需写 `#ifdef` |
| `src/request/stream.ts` | kids-app 直接移植 | 微信小程序 SSE（wx.request enableChunked） |
| `src/request/stream_h5.ts` | kids-app 直接移植 | H5 端 SSE（fetchEventSource） |
| `store/app.ts` init() 模式 | kids-app 思路 | 统一启动序列：检查网络→加载配置→设置主题→检查登录 |
| `build/` Vite 配置分层 | kids-app 思路 | plugins/config 拆到 build/ 目录，vite.config.ts 保持干净 |
| `uni.addInterceptor` 权限拦截 | kids-app 思路 | 在导航层统一拦截，不在每个页面 onLoad 判断 |

## 三、UI 组件库：wot-design-uni v2

### 3.1 选型决策

**选 wot-design-uni，不选 uview-plus**。

| 维度 | wot-design-uni v2 | uview-plus 3.x |
|------|-------------------|----------------|
| API 风格 | `<script setup>` 原生 Vue 3 | Options API + Mixin（Vue 2 迁移） |
| TypeScript | 全 `.ts`，类型完整 | props 在 `.js` 文件 |
| 父子通信 | `provide/inject` | `$parent` 遍历（Vue 2 反模式） |
| 全局状态 | 无全局污染 | `uni.$u` 全局挂载 |
| 主题定制 | 三层 design token（基础/语义/组件变量） | SCSS `!default` 变量 |
| AI 友好 | `@wot-ui/cli` MCP + VSCode 插件 | 无 |
| UnoCSS 集成 | `@wot-ui/unocss-preset`（v2） | 无 |
| 组件数 | 80+ | 80+ |

### 3.2 v1 → v2 策略

wot-starter 当前集成 v1.14，v2（2026-04-22 发布）已有官方迁移指南。

- **新项目**：直接用 v2（`wot-ui.cn`，仓库迁移至 `github.com/wot-ui/wot-ui`）
- **已有项目**：参考迁移指南，主要变化是 form 体系和 design token 层级

### 3.3 AI 辅助开发支持

wot-design-uni v2 提供：
- `@wot-ui/cli`：本地组件知识库，可通过 MCP 接入 AI 工具链，让 Agent 查完再写
- `wot-ui-intellisense`：VSCode 插件，组件补全 + 属性校验 + 悬停文档
- `.agent/skills/wot-ui/`：wot-starter 内置的 AI skill，已包含组件用法约束

## 四、HTTP 与流式通信

### 4.1 常规 HTTP：alova

alova 是请求策略库，不只是 HTTP 客户端：

| 能力 | 说明 |
|------|------|
| `@alova/adapter-uniapp` | uni.request 适配器，多端兼容 |
| `alova gen`（`@alova/wormhole`） | 从 OpenAPI/Swagger 自动生成请求代码和类型 |
| 请求策略 | 内置缓存、重试、分页、乐观更新等策略 |
| Mock | `@alova/mock` 开发阶段 mock 数据 |

与 webui 的 TanStack Query 理念类似，但更适合 uni-app 生态。

### 4.2 流式通信：自研双端实现

小程序不支持标准 SSE，需要两套实现：

| 平台 | 方案 | 原理 |
|------|------|------|
| 微信小程序 | `wx.request` + `enableChunked: true` | 分块接收，手动解析 SSE 格式 |
| H5 | `fetchEventSource`（@microsoft/fetch-event-source） | 标准 SSE，支持 POST + 自定义 headers |

两套实现通过条件编译（`#ifdef H5` / `#ifndef H5`）在同一 composable 中统一调用，上层业务无感知。

## 五、状态管理

| 用途 | 方案 | 说明 |
|------|------|------|
| 全局状态 | Pinia | Vue 3 官方推荐 |
| 持久化 | pinia-plugin-persistedstate | 多端存储适配 |
| 应用初始化 | `useAppStore.init()` | 统一启动序列（借鉴 kids-app） |

**与 webui 的差异**：webui 用 TanStack Query 管服务端状态；uniapp 端用 alova 的请求策略替代，Pinia 只管纯客户端 UI 状态。

## 六、样式方案

| 方案 | 说明 |
|------|------|
| UnoCSS + `@uni-helper/unocss-preset-uni` | 原子化 CSS，多端兼容 |
| `@wot-ui/unocss-preset`（v2） | 把 wot-ui design token 映射为 `wot-` 前缀原子类 |
| SCSS | 组件内样式，主题变量覆盖 |
| `uni.scss` | 全局样式变量 |

v2 的三层 design token（基础变量 → 语义变量 → 组件变量）配合 `@wot-ui/unocss-preset`，主题定制完全不需要改源码。

## 七、关键决策记录

### 7.1 为什么选 wot-starter 而不是从 kids-app 提取

kids-app 是基于旧工具链（pages.json、unocss-preset-weapp、双组件库混用）的历史项目。wot-starter 已经包含了我们计划的全部工程化能力，且质量更高。kids-app 只有 SSE 双端实现、platform 抽象层、build 分层、init() 启动模式这四个工程化思路值得借鉴，其余全部用 wot-starter 的现有实现。

### 7.2 为什么选 wot-design-uni 而不是 uview-plus

uview-plus 是 Vue 2 代码迁移到 Vue 3 运行时的产物，核心是 Options API + Mixin，父子通信靠 `$parent` 遍历，全局状态靠 `uni.$u` 挂载。wot-design-uni 是真正的 Vue 3 Composition API，TypeScript 完整，且 v2 提供了 AI 友好的工具链（CLI + MCP + VSCode 插件），与 AAF 的 AI 原生定位完全对齐。

### 7.3 为什么选 alova 而不是 uni-network / luch-request

uni-network 和 luch-request 只是 HTTP 客户端，需要手动封装缓存、重试、分页等策略。alova 是请求策略库，内置这些能力，且 `alova gen` 可以从 OpenAPI 自动生成请求代码，减少样板代码，与 AAF 的 AI 自动开发理念一致。

### 7.4 为什么选 UniApp 而不是 Taro / React Native / Flutter

微信小程序是主目标平台，UniApp 对微信生态支持最成熟；Vue 3 语法与团队现有能力匹配；一套代码覆盖小程序 + H5 + APP，减少维护成本。Taro 虽然用 React（与 webui 技术栈更近），但 UniApp 的微信生态成熟度和社区规模更有优势。
