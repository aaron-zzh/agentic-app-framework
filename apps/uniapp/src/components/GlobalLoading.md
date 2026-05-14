# GlobalLoading 全局加载组件

## 概述

GlobalLoading 基于 Wot UI 的 `wd-toast` 封装，适合在请求中间件、路由守卫或业务流程中统一显示加载状态。

## 组件特性

- 基于 `wd-toast` 实现
- 默认不自动关闭（`duration: 0`）
- 默认显示遮罩（`cover: true`）
- 自动记录当前页面路径，仅在触发页面展示
- 兼容支付宝小程序场景

## 使用前提

请确保在页面树中挂载了组件实例：

```vue
<template>
  <GlobalLoading />
  <slot />
</template>
```

## 使用方式

```ts
import { useGlobalLoading } from '@/composables/useGlobalLoading'

const loading = useGlobalLoading()
```

## API

### loading(option)

显示加载状态。

```ts
loading.loading('加载中...')

loading.loading({
  msg: '数据加载中',
  cover: true,
})
```

默认会合并以下配置：

- `iconName: 'loading'`
- `duration: 0`
- `cover: true`
- `position: 'middle'`
- `show: true`

### close()

关闭加载状态。

```ts
loading.close()
```

## 参数说明

### ToastOptions

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| msg | string | - | 加载文案 |
| iconName | string | `loading` | 图标名称 |
| duration | number | `0` | 持续时间，`0` 表示不自动关闭 |
| cover | boolean | `true` | 是否显示遮罩 |
| position | string | `middle` | 显示位置：`top` \| `middle` \| `bottom` |
| show | boolean | `true` | 是否显示，内部状态字段 |

### 参数形式

支持两种调用方式：

1. 字符串：作为 `msg` 使用
2. 对象：传入完整 `ToastOptions`

## 示例

```ts
const { loading: showLoading, close } = useGlobalLoading()

async function fetchData() {
  try {
    showLoading('正在加载数据...')
    await api.getData()
  }
  finally {
    close()
  }
}
```

## 注意事项

1. `duration: 0` 需要手动调用 `close()`。
2. 组件会记录 `currentPage`，只在触发时所在页面显示。
3. GlobalLoading 与 GlobalToast 底层同为 `wd-toast`，但 selector 不同，不会互相覆盖。
