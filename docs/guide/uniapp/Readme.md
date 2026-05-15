---
level: Practice
layer: Product
purpose: AAF uniapp 开发指南索引
status: published
version: 1.0.0
date: 2026-05-15
author: AaronZZH
---

# UniApp 开发指南

AAF 移动端（`apps/uniapp/`）开发指南，基于 wot-starter 脚手架。

> 技术选型决策见 [tech-stack.md](../../design/apps/uniapp/tech-stack.md) | 目录结构见 [directory-structure.md](../../design/apps/uniapp/directory-structure.md)

## 指南列表

| 指南 | 内容 |
|------|------|
| [tabbar.md](./tabbar.md) | 自定义 Tabbar 三件套：配置 / 组件 / 状态 |
| [router.md](./router.md) | `@wot-ui/router` 导航守卫 + `uni.addInterceptor` 权限拦截 |
| [request-and-state.md](./request-and-state.md) | alova 请求用法 + Pinia 持久化 |
| [bundle-optimizer.md](./bundle-optimizer.md) | 分包优化，跨分包异步引用组件 |
| [feedback.md](./feedback.md) | Toast / Loading / Dialog 全局反馈组件用法 |

## 演示代码参考

`tmp/uniapp/wot-starter-demos/` 保存了 wot-starter 原始演示页，开发时可按需查阅：

| 目录 | 内容 |
|------|------|
| `subPages/styles/` | UnoCSS 原子类完整示例（17KB） |
| `subPages/feedback/` | Toast/Loading/Dialog 完整演示代码 |
| `subPages/request/` | alova useRequest/usePagination 示例 |
| `subPages/pinia/` | Pinia store 操作示例 |
| `subPages/router/` | @wot-ui/router 路由跳转示例 |
| `subPages/icon/` | wot-ui 图标完整列表 |
