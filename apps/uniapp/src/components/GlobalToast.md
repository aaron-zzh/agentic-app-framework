# GlobalToast 全局提示组件

## 概述

GlobalToast 基于 Wot UI 的 `wd-toast` 封装，提供统一的全局轻提示能力。

## 组件特性

- 基于 `wd-toast` 实现
- 支持 `success`、`error`、`info`、`warning` 四种快捷调用
- 支持自定义位置、时长、图标和遮罩
- 自动记录当前页面路径，仅在触发页面展示
- 兼容支付宝小程序场景

## 使用前提

请确保在页面树中挂载了组件实例：

```vue
<template>
  <GlobalToast />
  <slot />
</template>
```

## 使用方式

```ts
import { useGlobalToast } from '@/composables/useGlobalToast'

const toast = useGlobalToast()
```

## API

### show(option)

显示普通提示。

```ts
toast.show('这是一条提示信息')

toast.show({
  msg: '自定义提示',
  duration: 3000,
  position: 'top',
})
```

默认配置：

- `duration: 2000`
- `show: false`
- 调用时自动补齐 `show: true`
- 未传 `position` 时默认使用 `middle`

### success(option)

成功提示，默认附带 `success` 图标，`duration` 为 `1500`。

```ts
toast.success('操作成功')
```

### error(option)

错误提示，默认附带 `error` 图标，`direction` 为 `vertical`。

```ts
toast.error('操作失败')
```

### info(option)

信息提示，默认附带 `info` 图标。

```ts
toast.info('这是一条信息')
```

### warning(option)

警告提示，默认附带 `warning` 图标。

```ts
toast.warning('警告信息')
```

### close()

手动关闭当前提示。

```ts
toast.close()
```

## 参数说明

### ToastOptions

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| msg | string | - | 提示内容 |
| duration | number | `2000` | 持续时间，`0` 表示不自动关闭 |
| position | string | `middle` | 显示位置：`top` \| `middle` \| `bottom` |
| iconName | string | - | 图标名称 |
| direction | string | - | 布局方向：`horizontal` \| `vertical` |
| cover | boolean | `false` | 是否显示遮罩 |
| show | boolean | `true` | 是否显示，内部状态字段 |

### 参数形式

所有方法都支持两种调用方式：

1. 字符串：作为 `msg` 使用
2. 对象：传入完整 `ToastOptions`

## 示例

```ts
const { success, error, info } = useGlobalToast()

async function handleSubmit() {
  try {
    await submitData()
    success('提交成功')
  }
  catch (e) {
    error('提交失败')
  }
}

function remind() {
  info({
    msg: '请检查填写内容',
    position: 'top',
  })
}
```

## 注意事项

1. 组件会记录 `currentPage`，只在触发时所在页面展示提示。
2. 同时仅展示一个提示，新提示会覆盖旧提示。
3. 使用 `duration: 0` 时需要手动调用 `close()`。
