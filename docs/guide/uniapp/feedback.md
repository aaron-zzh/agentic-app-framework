---
level: Practice
layer: Product
purpose: AAF uniapp 全局反馈组件（Toast/Loading/Dialog）使用指南
status: published
version: 1.0.0
date: 2026-05-15
author: AaronZZH
gains:
  - 掌握 useGlobalToast/Loading/Dialog 的正确用法
  - 了解各参数含义和常见场景
---

# 全局反馈组件

wot-starter 内置三个全局反馈 composable，已在 `App.ku.vue` 中挂载，页面直接使用无需额外注册。

## Toast

```typescript
const { show, success, error, warning, info } = useGlobalToast()

// 快捷方法
success({ msg: '操作成功！' })
error({ msg: '操作失败！' })
warning({ msg: '警告提示！' })
info({ msg: '信息提示！' })

// 完整参数
show({
  msg: '自定义消息',
  duration: 3000,           // 显示时长（ms），默认 2000
  position: 'middle',       // 'top' | 'middle' | 'bottom'
})
```

## Loading

```typescript
const { loading, close } = useGlobalLoading()

// 显示
loading('加载中...')
// 或带遮罩
loading({ msg: '处理中...', cover: true })

// 关闭
close()
```

## Dialog

```typescript
const { confirm, alert, prompt } = useGlobalDialog()

// 确认框
confirm({
  title: '确认删除',
  msg: '此操作不可撤销',
  confirmButtonText: '删除',
  cancelButtonText: '取消',
  success() { /* 确认回调 */ },
  fail() { /* 取消回调 */ },
})

// 提示框（无取消按钮）
alert({ title: '提示', msg: '操作已完成' })

// 输入框
prompt({
  title: '请输入昵称',
  success(value) { console.log(value) },
})
```

## 注意事项

- 这三个 composable 基于 `provide/inject`，必须在 `setup()` 中调用
- `App.ku.vue` 中已声明 `<global-toast />`、`<global-loading />`、`<global-dialog />`，无需在页面重复声明
- 同一页面多个弹层需要通过 `selector` 区分（wot-ui 限制）
