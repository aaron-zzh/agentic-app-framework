# AG-UI 协议文档

## 概述

AG-UI（Agent-User Interaction）是 AAF 前后端 AI 对话的标准协议。基于 SSE（Server-Sent Events）传输，定义了 Agent 执行过程中的所有事件类型，前端通过 `assistant-ui` 的 `@assistant-ui/react-ag-ui` runtime 消费。

## 传输层

- 协议：SSE（Server-Sent Events）
- 端点：`POST /api/agents/:id/chat`
- Content-Type：`text/event-stream`
- 请求体：JSON（包含消息和上下文）

## 事件类型

### 生命周期事件

| 事件 | 说明 | 时机 |
|------|------|------|
| `RUN_STARTED` | Agent 开始执行 | 收到用户消息后 |
| `RUN_FINISHED` | Agent 执行完成 | 所有步骤完成 |
| `RUN_ERROR` | Agent 执行出错 | 发生不可恢复错误 |

### 文本输出事件

| 事件 | 说明 | 数据 |
|------|------|------|
| `TEXT_MESSAGE_START` | 开始输出文本消息 | `{ messageId }` |
| `TEXT_MESSAGE_CONTENT` | 文本内容增量 | `{ messageId, delta }` |
| `TEXT_MESSAGE_END` | 文本消息结束 | `{ messageId }` |

### 工具调用事件

| 事件 | 说明 | 数据 |
|------|------|------|
| `TOOL_CALL_START` | 开始工具调用 | `{ toolCallId, toolName, args }` |
| `TOOL_CALL_ARGS` | 工具参数增量（流式） | `{ toolCallId, delta }` |
| `TOOL_CALL_END` | 工具调用完成 | `{ toolCallId }` |
| `TOOL_CALL_RESULT` | 工具返回结果 | `{ toolCallId, result }` |

### 状态事件

| 事件 | 说明 | 数据 |
|------|------|------|
| `STATE_DELTA` | Agent 状态变更 | `{ delta: {...} }` |
| `STATE_SNAPSHOT` | Agent 状态快照 | `{ snapshot: {...} }` |

### 步骤事件

| 事件 | 说明 | 数据 |
|------|------|------|
| `STEP_STARTED` | 执行步骤开始 | `{ stepId, stepName }` |
| `STEP_FINISHED` | 执行步骤完成 | `{ stepId }` |

### 人工审批事件

| 事件 | 说明 | 数据 |
|------|------|------|
| `INTERRUPT` | 暂停等待人类确认 | `{ interruptId, description, options }` |

## 消息格式

### 请求格式

```json
{
  "messages": [
    {
      "role": "user",
      "content": [
        { "type": "text", "text": "帮我查询最近的订单" }
      ]
    }
  ],
  "context": {
    "threadId": "thread-123",
    "agentId": "agent-456"
  },
  "config": {
    "model": "gpt-4o",
    "temperature": 0.7,
    "maxTokens": 4096
  }
}
```

### SSE 事件流格式

```text
event: RUN_STARTED
data: {"runId":"run-789"}

event: TEXT_MESSAGE_START
data: {"messageId":"msg-001"}

event: TEXT_MESSAGE_CONTENT
data: {"messageId":"msg-001","delta":"正在"}

event: TEXT_MESSAGE_CONTENT
data: {"messageId":"msg-001","delta":"查询订单..."}

event: TOOL_CALL_START
data: {"toolCallId":"tc-001","toolName":"query_orders","args":{"limit":10}}

event: TOOL_CALL_RESULT
data: {"toolCallId":"tc-001","result":{"orders":[...]}}

event: TEXT_MESSAGE_CONTENT
data: {"messageId":"msg-001","delta":"找到 3 条最近订单：..."}

event: TEXT_MESSAGE_END
data: {"messageId":"msg-001"}

event: RUN_FINISHED
data: {"runId":"run-789"}
```

## 前端接入指南

### 基本接入

```tsx
import { useAgUiRuntime } from '@assistant-ui/react-ag-ui';
import { AssistantRuntimeProvider, Thread } from '@assistant-ui/react';

function ChatPage() {
  const runtime = useAgUiRuntime({
    url: '/api/agents/default/chat',
    headers: { Authorization: `Bearer ${token}` },
  });

  return (
    <AssistantRuntimeProvider runtime={runtime}>
      <Thread />
    </AssistantRuntimeProvider>
  );
}
```

### Tool Call UI 注册

```tsx
import { makeAssistantToolUI } from '@assistant-ui/react';

const WeatherToolUI = makeAssistantToolUI({
  toolName: 'get_weather',
  render: ({ args, result }) => (
    <WeatherCard city={args.city} data={result} />
  ),
});
```

### 人工审批处理

当收到 `INTERRUPT` 事件时，assistant-ui 自动渲染审批 UI。用户确认后发送：

```json
{
  "type": "interrupt_response",
  "interruptId": "int-001",
  "action": "approve"
}
```

## 错误处理

| 错误事件 | 处理方式 |
|---------|---------|
| `RUN_ERROR` | 前端显示错误提示，提供重试按钮 |
| SSE 连接断开 | 自动重连（指数退避，最多 3 次） |
| Token 过期 | 刷新 Token 后重新建立连接 |
