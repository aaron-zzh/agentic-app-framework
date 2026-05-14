# GlobalDialog 全局弹窗组件

## 概述

GlobalDialog 基于 Wot UI 的 `wd-dialog` 封装，通过 Pinia 状态管理提供全局可调用的弹窗能力，用于统一处理提醒、确认、输入等交互流程。

## 组件特性

- 基于 `wd-dialog` 实现
- 支持 `alert`、`confirm`、`prompt` 三种模式
- 支持 `success`、`fail` 回调
- 自动记录当前页面路径，仅在触发页面展示
- 内部统一设置取消按钮和确认按钮为非圆角
- 兼容支付宝小程序场景

## 使用前提

请确保在页面树中挂载了组件实例：

```vue
<template>
  <GlobalDialog />
  <slot />
</template>
```

## 使用方式

```ts
import { useGlobalDialog } from '@/composables/useGlobalDialog'

const dialog = useGlobalDialog()
```

## API

### show(option)

显示通用弹窗。

```ts
dialog.show({
  title: '提示',
  msg: '这是一条消息',
  success: (res) => console.log('成功', res),
  fail: (res) => console.log('失败', res),
})

// 字符串参数会作为 title
// 等价于 dialog.show({ title: '简单提示' })
dialog.show('简单提示')
```

### alert(option)

显示提醒弹窗，只显示确认按钮。

```ts
dialog.alert('操作完成')

dialog.alert({
  title: '提醒',
  msg: '请注意查看结果',
})
```

### confirm(option)

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

### prompt(option)

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

### close()

手动关闭弹窗。

```ts
dialog.close()
```

## 参数说明

### GlobalDialogOptions

GlobalDialogOptions 基于 Wot UI 的 DialogOptions 扩展，并额外支持 `success`、`fail` 回调。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| title | string | - | 弹窗标题 |
| msg | string | - | 弹窗内容 |
| type | string | - | 弹窗类型：`alert` \| `confirm` \| `prompt` |
| showCancelButton | boolean | 自动设置 | `alert` 为 `false`，`confirm` 和 `prompt` 为 `true` |
| inputValue | string | - | `prompt` 模式下输入框默认值 |
| inputPlaceholder | string | - | `prompt` 模式下输入框占位文案 |
| success | Function | - | 点击确认后的回调 |
| fail | Function | - | 取消或关闭后的回调 |
| confirmButtonText | string | 跟随 wd-dialog 默认值 | 确认按钮文本 |
| cancelButtonText | string | 跟随 wd-dialog 默认值 | 取消按钮文本 |

### DialogResult

回调参数会透传 `wd-dialog` 的结果对象，常用字段如下：

| 参数 | 类型 | 说明 |
|------|------|------|
| action | string | 用户操作：`confirm` \| `cancel` |
| value | string | 输入框的值，仅 `prompt` 模式可用 |

## 注意事项

1. 内容字段使用 `msg`，不是 `message`。
2. 传入字符串参数时，字符串会作为 `title`。
3. 组件会记录 `currentPage`，只在触发时所在页面展示弹窗。
4. 同时仅展示一个弹窗，新调用会覆盖前一次状态。
