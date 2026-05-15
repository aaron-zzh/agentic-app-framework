---
level: Practice
layer: Product
purpose: AAF uniapp 自定义 Tabbar 实现指南
status: published
version: 1.0.0
date: 2026-05-15
author: AaronZZH
gains:
  - 掌握自定义 Tabbar 的三件套结构
  - 能正确添加/修改 Tabbar 项和徽标
---

# 自定义 Tabbar

基于 `@wot-ui/ui` 的 `wd-tabbar` 组件实现，三件套结构：

| 文件 | 职责 |
|------|------|
| `pages.config.ts` | 启用自定义 Tabbar，配置页面路径列表 |
| `src/layouts/tabbar.vue` | Tabbar UI 渲染 |
| `src/composables/useTabbar.ts` | Tabbar 状态与逻辑 |

## 添加 Tabbar 项

**1. `pages.config.ts` 添加页面路径：**

```typescript
tabBar: {
  custom: true,
  list: [
    { pagePath: 'pages/index/index' },
    { pagePath: 'pages/chat/index' },
    { pagePath: 'pages/profile/index' },
  ],
}
```

**2. `useTabbar.ts` 添加对应配置：**

```typescript
const tabbarItems = ref<TabbarItem[]>([
  { name: 'index', active: true, title: '首页', icon: 'home' },
  { name: 'chat', active: false, title: '对话', icon: 'chat' },
  { name: 'profile', active: false, title: '我的', icon: 'user' },
])
```

> `name` 必须与目标页面路由 `name` 一致，`router.pushTab({ name })` 依赖此字段。

## 徽标（未读角标）

```typescript
const { setTabbarItem } = useTabbar()

// 设置未读数
setTabbarItem('chat', 3)
// 清除：传 0 或按业务约定
setTabbarItem('chat', 0)
```

## 注意事项

- APP 端会自动调用 `uni.hideTabBar()` 隐藏原生 Tabbar
- 图标使用 wot-ui 内置图标名，常用：`home` / `chat` / `user` / `setting` / `message`
- 自定义图标通过 `wd-tabbar-item` 的 `#icon` 插槽实现
