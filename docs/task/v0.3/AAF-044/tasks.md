---
level: Practice
layer: Product
purpose: AAF-044 AG-UI 协议层的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# AG-UI 协议层（AAF-044）

> 参考：[assistant-ui](../../tmp/nextjs/assistant-ui) | [AG-UI 协议](../../tmp/ui/agui)
> 负责人：architect + developer-service + developer-webui | 创建：05-19

## 任务列表

1. [ ] #4401 AG-UI 协议服务端实现
   - 实现 AG-UI 协议消息格式（TextMessage/ToolCall/ToolResult 等）
   - SSE 事件流端点（/api/chat/stream）
   - 协议版本协商
   - verify: 服务端按 AG-UI 协议格式输出事件流

2. [ ] #4402 assistant-ui 前端集成
   - 安装 assistant-ui 依赖、配置 Runtime
   - 自定义 ChatAdapter 对接后端 AG-UI 端点
   - 主题定制（匹配 AAF 设计系统）
   - verify: assistant-ui 组件可正常收发消息

3. [ ] #4403 消息类型支持
   - 文本消息（Markdown 渲染）
   - 代码块（语法高亮）
   - 图片/文件附件
   - 工具调用展示（调用中/结果）
   - verify: 各类型消息正确渲染

4. [ ] #4404 工具调用协议
   - Function Calling 请求/响应格式
   - 工具注册与发现（前端展示可用工具）
   - 工具调用确认机制（高风险操作需用户确认）
   - verify: 工具调用→执行→结果回显流程通过

5. [ ] #4405 错误处理与重连
   - SSE 断线重连、消息去重
   - 错误消息展示（模型错误/网络错误/配额超限）
   - 请求超时处理
   - verify: 断线后自动重连，错误友好提示
