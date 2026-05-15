---
level: Practice
layer: Product
purpose: AAF uniapp 路由管理指南（@wot-ui/router）
status: published
version: 1.0.0
date: 2026-05-15
author: AaronZZH
gains:
  - 掌握 @wot-ui/router 编程式导航用法
  - 能正确配置导航守卫实现权限拦截
---

# 路由管理

AAF uniapp 使用 `@wot-ui/router` + `uni.addInterceptor` 双层路由守卫。

> `@wot-ui/router` 对齐 Vue Router API，但受限于小程序平台，不支持 Vue Router 全部特性。

## 编程式导航

```typescript
const router = useRouter()

// 普通跳转（可返回）
router.push({ name: 'chat' })
router.push({ name: 'chat-detail', params: { id: '123' } })

// Tabbar 跳转（必须用 pushTab）
router.pushTab({ name: 'index' })

// 替换当前页（不可返回）
router.replace({ name: 'login' })

// 返回根页面
router.replaceAll({ name: 'index' })
```

## 导航守卫

```typescript
// src/router/index.ts
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()

  // 未登录跳登录页
  if (to.meta?.requiresAuth && !userStore.isLoggedIn) {
    return next({ name: 'login' })
  }

  // 管理端校验管理员角色
  if (to.meta?.requiresAdmin && !userStore.isAdmin) {
    return next({ name: 'index' })
  }

  next()
})
```

## uni.addInterceptor 权限拦截

`@wot-ui/router` 的守卫只覆盖通过 router 发起的跳转。对 `uni.navigateTo` 等原生跳转，需额外用 `uni.addInterceptor` 拦截：

```typescript
// src/router/index.ts
const PROTECTED_PATHS = ['/subPages/admin/']

function interceptNav(method: string) {
  uni.addInterceptor(method, {
    invoke(args) {
      const userStore = useUserStore()
      const url: string = args.url || ''

      if (!userStore.isLoggedIn) {
        uni.redirectTo({ url: '/pages/login/index' })
        return false
      }

      if (PROTECTED_PATHS.some(p => url.startsWith(p)) && !userStore.isAdmin) {
        uni.switchTab({ url: '/pages/index/index' })
        return false
      }
    },
  })
}

;['navigateTo', 'redirectTo', 'reLaunch'].forEach(interceptNav)
```

## 页面 meta 配置

在页面 `<script setup>` 中通过 `definePage` 声明路由元信息：

```typescript
definePage({
  name: 'chat-detail',
  meta: { requiresAuth: true },
})
```
