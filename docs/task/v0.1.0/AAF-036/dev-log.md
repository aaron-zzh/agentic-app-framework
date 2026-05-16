# 开发记录：AAF-036 移动端脚手架

执行者：AI/developer-uniapp

## #3614 脚手架 Demo 清理

✅ 2026-05-15 — developer-uniapp

- 演示页已不存在（前序任务已清理），`pages/about/` 和 `DemoBlock.vue` 均已删除
- `pages.config.ts` 无残留演示路由

## #3617 AI 对话消息列表（虚拟列表）

✅ 2026-05-15 — developer-uniapp

- 修复 SSE 回调中 `messageList.value[0]` 错误引用
- 改用独立 `streamingMsg` ref 追踪当前流式 AI 消息对象
- `onStop` 同步修复，停止时清空 `streamingMsg`

> z-paging 聊天记录模式下，`v-model` 绑定的 `messageList` 不直接反映内部列表顺序，不能用下标访问最新消息，必须在 `addChatRecordData` 前保留对象引用。

## #3625 对话内嵌交互组件（widget）

✅ 2026-05-15 — developer-uniapp

- 创建 `ChatWidget.vue`，支持 form/select/confirm/card 四种类型
- form：`wd-form` 动态渲染字段，支持 text/number/textarea/select
- select：单选直接提交，多选需点确认按钮
- confirm：双按钮确认/取消
- card：图片+标题+KV列表+操作按钮
- 提交后 `widget.submitted = true` 隐藏组件，emit `widgetSubmit` 事件

## #3626 对话内联路由跳转

✅ 2026-05-15 — developer-uniapp

- `ChatBubble.vue` 监听 `mp-html` 的 `@linktap` 事件
- `route://页面名?参数` → `uni.navigateTo`
- `tel://号码` → `uni.makePhoneCall`
- `http(s)://` → 微信端跳 webview 页，H5 端 `window.open`
- `[id].vue` 监听 `@widget-submit` 直接调用 `onSend(text)`

## 实现文件

| 文件 | 说明 |
|------|------|
| `src/pages/chat/[id].vue` | 修复 streamingMsg ref，监听 widgetSubmit |
| `src/components/chat/ChatBubble.vue` | 集成 ChatWidget，实现 linktap 拦截 |
| `src/components/chat/ChatWidget.vue` | 新建，四种 widget 类型渲染 |

## 注意事项

- webview 跳转依赖 `pages/webview/index.vue` 页面存在（#3626 中微信端），该页面尚未创建，需后续补充
- widget 的 schema 类型为 `unknown`，后端下发时需与前端约定具体结构
