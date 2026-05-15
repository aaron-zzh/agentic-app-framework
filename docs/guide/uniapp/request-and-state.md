---
level: Practice
layer: Product
purpose: AAF uniapp 请求层与状态管理指南
status: published
version: 1.0.0
date: 2026-05-15
author: AaronZZH
gains:
  - 掌握 alova 在 uniapp 中的正确用法
  - 理解 store 只管 UI 状态、alova 管服务端数据的边界
  - 掌握 Pinia 持久化配置
---

# 请求与状态管理

## alova 请求

AAF uniapp 使用 alova + `@alova/adapter-uniapp`，实例配置在 `src/api/core/instance.ts`。

### 基础用法

```typescript
// src/api/user.ts
import { alovaInstance } from './core/instance'

export const getUserInfo = (id: string) =>
  alovaInstance.Get<UserInfo>(`/user/${id}`)
```

```vue
<script setup lang="ts">
const { data, loading } = useRequest(getUserInfo('123'))
</script>
```

### 分页

```typescript
const { data: list, isLastPage, loadMore } = usePagination(
  (page, pageSize) => getMessageList({ page, pageSize }),
  { initialPage: 1, initialPageSize: 20 },
)
```

> 服务端数据（列表、详情）**不要复制到 Pinia store**，直接用 alova 的 `useRequest`/`usePagination` 管理。

## Pinia 状态管理

store 只存**纯客户端 UI 状态**：登录态、主题、tabbar 选中项、流式对话实时状态等。

### 持久化

项目内置 `src/store/persist.ts`，默认持久化所有 store 到 `uni.storage`。排除某个 store：

```typescript
// src/store/persist.ts
export function persistPlugin(context: PiniaPluginContext) {
  persist(context, ['temp']) // 排除列表加 store.$id
}
```

### VueUse + uni.storage 轻量方案

```typescript
const uniStorage = {
  getItem: (key: string) => uni.getStorageSync(key) || null,
  setItem: (key: string, value: string) => uni.setStorageSync(key, value),
  removeItem: (key: string) => uni.removeStorageSync(key),
}

export const useAuth = createGlobalState(() => {
  const token = useStorage('token', '', uniStorage)
  return { token, isLogin: computed(() => !!token.value) }
})
```
