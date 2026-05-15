# AAF UniApp

AAF 移动端应用（小程序 / H5 / APP），基于 [wot-starter](https://github.com/wot-ui/wot-starter) 脚手架。

> 技术选型见 [tech-stack.md](../../docs/design/apps/uniapp/tech-stack.md) | 目录结构见 [directory-structure.md](../../docs/design/apps/uniapp/directory-structure.md)

## 功能定位

| 角色 | 入口 | 核心功能 |
|------|------|---------|
| **普通用户** | 主包 `pages/` | AI 对话、智能体广场、个人中心 |
| **管理员** | 分包 `subPages/admin/` | 数据看板、用户管理、内容审核、系统配置 |

同一小程序，角色切换，管理端按需加载不影响主包体积。详见 [mobile-admin.md](../../docs/design/apps/uniapp/mobile-admin.md)。

## 技术栈

- **框架**：UniApp + Vue 3 + TypeScript
- **UI**：wot-design-uni v2（`@wot-ui/ui`）
- **样式**：UnoCSS + `@uni-helper/unocss-preset-uni`
- **状态**：Pinia（持久化到 `uni.storage`）
- **请求**：alova + `@alova/adapter-uniapp`
- **路由**：`@wot-ui/router` + `uni.addInterceptor`
- **流式通信**：SSE 双端实现 + WebSocket（`@hyoga/uni-socket.io`）

## 开发命令

```bash
# H5 开发
pnpm nx run uniapp:dev

# 微信小程序开发
pnpm nx run uniapp:dev:mp-weixin

# 构建微信小程序
pnpm nx run uniapp:build:mp-weixin

# 构建 H5
pnpm nx run uniapp:build

# Lint
pnpm nx run uniapp:lint

# 类型检查
pnpm nx run uniapp:typecheck
```

## 环境配置

- 开发环境默认连接本地后端 `http://localhost:8080`，见 `.env.development`
- 如需覆盖，复制 `.env.local.example` 为 `.env.local`

## 开发指南

- [Tabbar 配置](../../docs/guide/uniapp/tabbar.md)
- [路由与权限拦截](../../docs/guide/uniapp/router.md)
- [请求与状态管理](../../docs/guide/uniapp/request-and-state.md)
- [分包优化](../../docs/guide/uniapp/bundle-optimizer.md)

## ⚠️ 版本约束注意事项

uni-app 内部绑定 **Vue 3.4.x**，以下依赖必须与之对齐，**不可随意升级**：

| 包 | 锁定版本 | 原因 |
|---|---|---|
| `vue` | `~3.4.38` | uni-app 内部依赖 `@vue/shared@3.4.x`，Vue 3.5 会导致 `compiler-sfc` 内部 API 不兼容 |
| `pinia` | `^2.3.1` | Pinia 3.x 依赖 `@vue/devtools-api` 等 Vue 3.5+ 生态包，在 uni-app 环境下缺失 |
| `vue-i18n` | `^9.14.0` | v10/v11 不再支持，且依赖 Vue 3.5+ |
| `@vueuse/core` | `^11.0.3` | v12+ 要求 Vue 3.5+；v11 对应 `@vueuse/shared@^11.0.3` |
| `@vueuse/shared` | `^11.0.3` | 必须与 `@vueuse/core` 版本一致 |

> 等待 uni-app 官方支持 Vue 3.5 后，上述限制才能解除。关注 [@dcloudio/uni-app](https://www.npmjs.com/package/@dcloudio/uni-app) 版本更新。

**pnpm monorepo 额外注意**：pnpm 默认不自动安装 peer dependencies（`auto-install-peers=false`）。如遇 `Could not resolve` 报错，先检查是否为缺失的 peer dep，手动补装即可。
