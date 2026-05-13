---
level: Practice
layer: Product
purpose: 前端实时数据方案（四场景统一策略）
status: draft
version: 2.0.0
date: 2026-05-11
author: AaronZZH
---

# 前端实时数据方案

## 一、结论

| 场景 | 方案 | 传输协议 |
|------|------|---------|
| AI 对话（流式） | assistant-ui + AG-UI runtime | SSE（AG-UI 协议） |
| 数据列表实时刷新 | TanStack Query + SSE invalidation | SSE |
| 客服/IM 聊天 | assistant-ui + ExternalStoreRuntime | WebSocket |
| 文档协同编辑 | Yjs + WebSocket provider | WebSocket |

不自建 Provider 抽象层

## 二、各场景方案

### 2.1 AI 对话（assistant-ui + AG-UI）

assistant-ui 通过 `@assistant-ui/react-ag-ui` runtime 处理：
- 流式 token 输出
- Tool Call 渲染
- Agent 状态推送（思考中/执行中/完成）
- 中断与取消

前端无需手动处理 SSE 解析，runtime 内置。

```tsx
// 对话页面只需包裹 Provider
<AssistantRuntimeProvider runtime={agUiRuntime}>
  <Thread />
</AssistantRuntimeProvider>
```

### 2.2 数据列表实时刷新（SSE → Query Invalidation）

传统数据页面（文档列表、知识库、工作流列表等）通过 TanStack Query 管理，后端变更时 SSE 推送事件触发 invalidation：

```typescript
// lib/hooks/use-live-invalidation.ts
'use client';

import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';

export function useLiveInvalidation(url: string, enabled = true) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!enabled) return;
    const source = new EventSource(url);
    source.onmessage = (e) => {
      const { resource } = JSON.parse(e.data);
      queryClient.invalidateQueries({ queryKey: [resource] });
    };
    return () => source.close();
  }, [url, enabled, queryClient]);
}
```

根布局挂一次即可：

```tsx
// app/(workspace)/layout.tsx
useLiveInvalidation('/api/events/live');
```

后端事件格式：

```json
{ "resource": "documents", "type": "updated", "ids": ["doc-123"] }
```

### 2.3 客服/IM 聊天

> 详细设计见 [结构化视图模式 · 第三十八章](../interaction-mode-structured-view.md#三十八在线客服与聊天模块livechat--chatbot)

复用 assistant-ui 的多线程能力。客服场景本质是"人对人对话 + 可选 AI 介入"，与 AI 对话共享同一套 UI 组件（Thread/ThreadList），区别仅在 runtime 配置：

| 场景 | Runtime | 传输 | 后端服务 |
|------|---------|------|---------|
| AI 助理对话 | `useAgUiRuntime` | SSE（AG-UI） | Agent 服务 |
| 客服/机器人 | `useExternalStoreRuntime` | WebSocket | Livechat 服务 |
| 内部 IM | `useExternalStoreRuntime` | WebSocket | IM 服务 |

不需要单独引入 IM SDK 或聊天框架。

#### LivechatRuntime 实现要点

```typescript
// ExternalStoreRuntime 适配 WebSocket 消息流
const livechatRuntime = useExternalStoreRuntime({
  messages: livechatMessages,  // 来自 WebSocket 的消息状态
  onNew: async (msg) => {
    ws.send(JSON.stringify({ type: 'chat/send_message', body: msg.content[0].text }));
  },
  convertMessage: (msg) => ({
    id: msg.id,
    role: msg.authorType === 'guest' ? 'user' : 'assistant',
    content: [{ type: 'text', text: msg.body }],
  }),
});
```

#### WebSocket 事件协议

```text
客户端 → 服务端：
  chat/send_message   { sessionId, body, attachments? }
  chat/bot_answer     { sessionId, answerId }
  chat/typing         { sessionId, isTyping }

服务端 → 客户端：
  chat/new_message    { sessionId, message }
  chat/typing         { sessionId, userId, isTyping }
  chat/session_update { sessionId, status, operator? }
  chat/operator_joined { sessionId, operator }
```

#### 机器人 → AI Agent → 人工 的 runtime 切换

```text
会话创建（status='bot'）
  → LivechatRuntime 处理机器人脚本步骤
  → step.type === 'ai_answer'
    → 后端切换为 Agent 处理（仍通过 WebSocket 推送）
    → 前端 Thread 无感知（消息格式统一）
  → step.type === 'forward_operator'
    → 后端分配客服 → 推送 chat/operator_joined
    → 前端 Thread 无感知（新消息来自真人客服）
```

关键设计：**runtime 不切换，后端路由切换**。前端始终使用同一个 LivechatRuntime，后端根据会话状态决定消息由谁处理（bot/agent/human）。

#### AI 感知辅助（客服人员侧）

客服工作台集成第三十五章 AI 感知能力，为客服人员提供实时辅助：

- 智能回复建议（基于对话上下文 + 知识库检索）
- 访客情绪分析（实时标注 positive/neutral/negative）
- 意图分类（自动标记咨询/投诉/购买等）
- 相关知识推送（侧边栏展示匹配的知识库条目）

这些能力通过 AIAwarenessService 的 `registerCollector('livechat', ...)` 注册，建议通过 assistant-ui 的 ToolUI 渲染。

### 2.4 文档协同编辑

Yjs + WebSocket provider，独立于上述三个场景：

- Lexical 编辑器绑定 `@lexical/yjs`
- WebSocket provider 连接协同服务端
- 冲突解决由 Yjs CRDT 自动处理

与 TanStack Query 无交互——文档内容走 Yjs 同步，文档元数据（标题/标签/权限）走 Query。

## 三、为什么不引入 Refine LiveProvider

1. **场景已被完全覆盖**——assistant-ui（对话流）+ EventSource（推送 invalidation）+ Yjs（协同），无缝隙
2. **LiveProvider 解决的是 CRUD 表格自动刷新**——AAF 的实时需求远超此范围
3. **引入即并行抽象**——TanStack Query 已有 invalidation 机制，再加一层 Provider 违反"禁止并行抽象"
4. **传输层已确定**——SSE + WebSocket，不需要"传输无关"的抽象

## 四、架构总览

```text
┌─────────────────────────────────────────────────────┐
│                    前端                               │
├──────────────┬──────────────┬───────────┬───────────┤
│ AI 对话       │ 数据列表      │ 客服/IM    │ 文档协同   │
│ assistant-ui │ TanStack Query│ assistant-ui│ Yjs       │
│ AgUiRuntime  │ + invalidation│ External-  │ + Lexical │
│              │              │ StoreRuntime│           │
├──────────────┼──────────────┼───────────┼───────────┤
│     SSE      │     SSE      │    WS     │    WS     │
│  (AG-UI)     │              │           │           │
└──────┬───────┴──────┬───────┴─────┬─────┴─────┬─────┘
       │              │             │           │
┌──────┴──────────────┴─────────────┴───────────┴─────┐
│                    后端                               │
│  Agent 服务（AG-UI）│ 事件总线 │ Livechat/IM │ 协同服务 │
│                    │         │ + AI 感知   │          │
└─────────────────────────────────────────────────────┘
```


## 五、同步源抽象与分层同步

### 5.1 同步源接口（DocSource）

文档协同需要对接多种存储后端（云端 WebSocket、本地 IndexedDB、UniApp SQLite）。抽象统一的同步源接口，任何存储只需实现三个方法：

```typescript
interface DocSource {
  name: string
  pull(docId: string, state: Uint8Array): Promise<{ data: Uint8Array; state?: Uint8Array } | null>
  push(docId: string, data: Uint8Array): Promise<void>
  subscribe(
    onUpdate: (docId: string, data: Uint8Array) => void,
    onDisconnect: (reason: string) => void
  ): () => void
}
```

| 方法 | 职责 |
|------|------|
| `pull` | 拉取远端最新状态（Yjs update 二进制） |
| `push` | 推送本地变更到远端 |
| `subscribe` | 订阅远端实时变更推送 |

实现示例：

| 平台 | DocSource 实现 | 存储 |
|------|---------------|------|
| Web | `WebSocketDocSource` | 云端协同服务 |
| Web（离线缓存） | `IndexedDBDocSource` | 浏览器 IndexedDB |
| UniApp | `SQLiteDocSource` | 本地 SQLite |

### 5.2 分层同步（Main + Shadow Peer）

多端场景下，同步分为主同步源（云端）和影子同步源（本地持久化），保证离线可用 + 在线同步：

```text
┌────────────────────────────────────────────┐
│  Yjs Doc（内存，用户操作直接修改，零延迟）    │
└───────────┬────────────────────┬───────────┘
            │ push/pull          │ push/pull
            ▼                    ▼
     ┌─────────────┐     ┌─────────────┐
     │ Main Peer   │     │ Shadow Peer │
     │ (云端 WS)   │     │ (本地存储)   │
     └─────────────┘     └─────────────┘
```

**同步流程**：
1. 启动时先连接 Main Peer，拉取云端最新状态
2. Main 同步完成后启动 Shadow Peer，将最新状态写入本地
3. 运行时双向同步：用户操作 → push 到 Main + Shadow
4. 离线时：只 push 到 Shadow，网络恢复后 CRDT 自动合并到 Main

**设计原则**：
- 写入即生效——用户操作直接修改内存 Yjs Doc，UI 立即响应，不等网络
- 离线可用——Shadow Peer 保证本地有完整数据副本
- 冲突自动解决——Yjs CRDT 保证最终一致性，无需手动处理

### 5.3 三层同步职责

| 层 | 职责 | 数据格式 |
|----|------|---------|
| Doc Sync | 文档 CRDT 状态同步 | Yjs update 二进制增量 |
| Blob Sync | 二进制资源同步（图片/附件） | 内容寻址（SHA256 hash） |
| Awareness Sync | 用户在线状态/光标位置 | Yjs Awareness 协议 |

### 5.4 与现有方案的关系

当前 §2.4 的"Yjs + WebSocket provider"是 Main Peer 的具体实现。本节补充的是：
- 同步源的**通用抽象**（DocSource 接口），使多端实现可插拔
- **Shadow Peer** 层（本地持久化），使离线和多端同步成为可能
- Blob 和 Awareness 的独立同步通道
