---
title: 全局反馈组件
iframe: true
iframeFormatter: subPages/feedback/index
---

# 全局反馈组件

本项目基于 Pinia 和 Wot UI 封装了三类可全局调用的反馈能力：GlobalLoading、GlobalToast、GlobalDialog。它们适合在网络请求中间件、路由导航守卫以及其他不方便直接依赖页面内 hook 实例的场景中使用。

:::tip 提示
Wot UI 原生提供了 useToast、useDialog 等函数式能力，但调用侧通常仍要依赖对应组件实例。这里的全局反馈组件通过 Pinia 保存状态，并在组件内部监听状态后再调用 wd-toast 或 wd-dialog，因此可以把触发逻辑放到更靠近业务流程的位置，例如请求拦截器和路由守卫。
:::

## 使用前提

使用这套全局反馈封装前，需要保证页面树中已经挂载以下组件实例：

- GlobalLoading
- GlobalToast
- GlobalDialog

本项目通过组件自动导入支持直接使用这些组件；业务侧只需要调用对应的 composable：

```vue
<template>
  <GlobalLoading />
  <GlobalToast />
  <GlobalDialog />
  <slot />
</template>
```

三个组件都会记录触发时所在页面路径，并且仅在相同页面内展示，避免切页后在错误页面继续显示旧反馈。

## 全局加载

### 概述

GlobalLoading 基于 Wot UI 的 wd-toast 封装，适合配合 axios、alova 等请求流程显示全局加载状态。

### 组件特性

- 基于 wd-toast 实现
- 默认不自动关闭
- 默认显示遮罩，防止重复操作
- 自动记录当前页面路径，只在触发页面展示

### 使用

```ts
import { useGlobalLoading } from '@/composables/useGlobalLoading'

const loading = useGlobalLoading()
```

### API

#### loading(option)

显示加载状态。

```ts
loading.loading('加载中...')

loading.loading({
  msg: '数据加载中',
  cover: true,
})
```

默认会合并以下配置：

- iconName: loading
- duration: 0
- cover: true
- position: middle
- show: true

#### close()

关闭加载状态。

```ts
loading.close()
```

### 参数说明

#### ToastOptions

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| msg | string | - | 加载文案 |
| iconName | string | loading | 图标名称 |
| duration | number | 0 | 持续时间，0 表示不自动关闭 |
| cover | boolean | true | 是否显示遮罩 |
| position | string | middle | 显示位置：top \| middle \| bottom |
| show | boolean | true | 是否显示，内部状态字段 |

#### 参数形式

支持两种调用方式：

1. 字符串：作为 msg 使用
2. 对象：传入完整 ToastOptions

### 示例

```ts
const { loading, close } = useGlobalLoading()

async function fetchData() {
  try {
    loading('正在加载数据...')
    await api.getData()
  }
  finally {
    close()
  }
}
```

## 全局提示

### 概述

GlobalToast 基于 Wot UI 的 wd-toast 封装，提供统一的全局轻提示能力。

### 组件特性

- 基于 wd-toast 实现
- 支持 success、error、info、warning 四种快捷调用
- 支持自定义位置、时长、图标和遮罩
- 自动记录当前页面路径，只在触发页面展示

### 使用

```ts
import { useGlobalToast } from '@/composables/useGlobalToast'

const toast = useGlobalToast()
```

### API

#### show(option)

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

- duration: 2000
- show: false
- 调用时自动补齐 show: true
- 未传 position 时默认使用 middle

#### success(option)

成功提示，默认附带 success 图标，duration 为 1500。

```ts
toast.success('操作成功')
```

#### error(option)

错误提示，默认附带 error 图标，direction 为 vertical。

```ts
toast.error('操作失败')
```

#### info(option)

信息提示，默认附带 info 图标。

```ts
toast.info('这是一条信息')
```

#### warning(option)

警告提示，默认附带 warning 图标。

```ts
toast.warning('警告信息')
```

#### close()

手动关闭当前提示。

```ts
toast.close()
```

### 参数说明

#### ToastOptions

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| msg | string | - | 提示内容 |
| duration | number | 2000 | 持续时间，0表示不自动关闭 |
| position | string | middle | 显示位置：top \| middle \| bottom |
| iconName | string | - | 图标名称 |
| direction | string | - | 布局方向：horizontal \| vertical |
| cover | boolean | false | 是否显示遮罩 |
| show | boolean | true | 是否显示，内部状态字段 |

#### 参数形式

所有方法都支持两种调用方式：

1. 字符串：作为 msg 使用
2. 对象：传入完整 ToastOptions

## 全局弹窗

### 概述

GlobalDialog 基于 Wot UI 的 wd-dialog 封装，用于统一处理提醒、确认和输入等交互流程。

### 组件特性

- 基于 wd-dialog 实现
- 支持 alert、confirm、prompt 三种模式
- 支持 success、fail 回调
- 自动记录当前页面路径，只在触发页面展示
- 内部统一设置取消按钮和确认按钮为非圆角

### 使用

```ts
import { useGlobalDialog } from '@/composables/useGlobalDialog'

const dialog = useGlobalDialog()
```

### API

#### show(option)

显示通用弹窗。

```ts
dialog.show({
  title: '提示',
  msg: '这是一条消息',
  success: (res) => console.log('成功', res),
  fail: (res) => console.log('失败', res),
})

dialog.show('简单提示')
```

注意：当参数为字符串时，实际会作为 title 使用。

#### alert(option)

显示提醒弹窗，只显示确认按钮。

```ts
dialog.alert('操作完成')

dialog.alert({
  title: '提醒',
  msg: '请注意查看结果',
})
```

#### confirm(option)

显示确认弹窗，自动开启取消按钮。

```ts
dialog.confirm('确定要删除吗？')

dialog.confirm({
  title: '确认删除',
  msg: '删除后不可恢复，确定要删除吗？',
  success: (res) => {
    if (res.action === 'confirm') {
      console.log('用户确认删除')
    }
  },
  fail: (res) => {
    console.log('用户取消删除')
  },
})
```

#### prompt(option)

显示输入弹窗，自动开启取消按钮。

```ts
dialog.prompt('请输入您的姓名')

dialog.prompt({
  title: '输入信息',
  msg: '请输入新的名称',
  inputValue: '默认值',
  inputPlaceholder: '请输入内容',
  success: (res) => {
    if (res.action === 'confirm') {
      console.log('用户输入:', res.value)
    }
  },
})
```

#### close()

手动关闭弹窗。

```ts
dialog.close()
```

### 参数说明

#### GlobalDialogOptions

GlobalDialogOptions 基于 Wot UI 的 DialogOptions 扩展，并额外支持 success、fail 回调。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| title | string | - | 弹窗标题 |
| msg | string | - | 弹窗内容 |
| type | string | - | 弹窗类型：alert \| confirm \| prompt |
| showCancelButton | boolean | 自动设置 | alert 为 false，confirm 和 prompt 为 true |
| inputValue | string | - | prompt 模式下的输入框默认值 |
| inputPlaceholder | string | - | prompt 模式下的输入框占位文案 |
| success | Function | - | 点击确认后的回调 |
| fail | Function | - | 取消或关闭后的回调 |
| confirmButtonText | string | 取决于 wd-dialog 默认值 | 确认按钮文本 |
| cancelButtonText | string | 取决于 wd-dialog 默认值 | 取消按钮文本 |

#### DialogResult

回调参数会透传 wd-dialog 的结果对象，常用字段如下：

| 参数 | 类型 | 说明 |
|------|------|------|
| action | string | 用户操作：confirm \| cancel |
| value | string | 输入框的值，仅 prompt 模式可用 |

#### 参数形式

所有方法都支持两种调用方式：

1. 字符串：作为 title 使用
2. 对象：传入完整 GlobalDialogOptions

### 示例

```ts
const { confirm, alert, prompt } = useGlobalDialog()
const { success, warning } = useGlobalToast()

alert({
  title: '重要提醒',
  msg: '这是一个重要提醒',
})

confirm({
  title: '确认操作',
  msg: '确定继续吗？',
  success: (res) => {
    if (res.action === 'confirm') {
      success('继续执行')
    }
  },
})

prompt({
  title: '输入信息',
  msg: '请输入您的姓名',
  success: (res) => {
    if (res.action === 'confirm' && String(res.value || '').trim()) {
      success(`您好，${res.value}`)
    }
    else {
      warning('输入不能为空')
    }
  },
})
```

## 典型场景

### 请求中间件中显示全局加载

```ts
const globalLoading = useGlobalLoading()

globalLoading.loading('请求中...')

try {
  await request()
}
finally {
  globalLoading.close()
}
```

### 路由守卫中显示确认弹窗

```ts
const { confirm } = useGlobalDialog()

confirm({
  title: '离开当前页面',
  msg: '表单尚未保存，确定离开吗？',
  success: (res) => {
    if (res.action === 'confirm') {
      // 继续跳转
    }
  },
})
```

## 注意事项

1. GlobalLoading 和 GlobalToast 底层都调用 wd-toast，但使用了不同 selector，不会互相覆盖。
2. GlobalDialog 使用 wd-dialog，内容字段应使用 msg，而不是 message。
3. 三个 composable 都会记录 currentPage，仅在触发时所在页面展示反馈。
4. 使用 duration: 0 的提示或加载时，需要手动调用 close()。
5. 支付宝小程序场景下组件内部做了兼容处理，业务侧无需额外处理。
