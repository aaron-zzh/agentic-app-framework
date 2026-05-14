---
level: Practice
layer: Model
purpose: AAF 前端组件与目录规范，开发时查阅
status: published
version: 1.0.0
date: 2026-05-05
author: AaronZZH
gains:
  - 能正确组织前端目录和组件
  - 能按规范编写组件和调用 API
---

# 前端编码规范（webui）

## 目录约定

```
app/                  Next.js App Router 页面（路由即目录）
  (auth)/             路由组，不影响 URL
  chat/
    page.tsx          页面组件（Server Component 优先）
    layout.tsx
components/           共享组件
  ui/                 纯展示组件（无业务逻辑）
  feature/            业务组件（含状态和逻辑）
lib/
  api/                API 客户端（按模块分文件）
  hooks/              自定义 Hook
  utils/              工具函数
```

## 状态管理

- TanStack Query 管服务器状态，Zustand 管客户端状态，React Context 仅用于平台管道
- 永远不把服务器数据复制到 Zustand
- WS 事件只 invalidate query，不直接写 store

## 组件三层分离

中等以上复杂度的组件必须分离为三层：

| 层 | 职责 | 位置 | 关心什么 |
|----|------|------|---------|
| **逻辑层**（hook） | 状态管理、API 调用、数据转换 | `lib/hooks/` 或 `packages/core/` | 不关心 UI 长什么样 |
| **UI 层**（组件） | 渲染、样式、布局、动画、无障碍 | `components/` | 不关心数据从哪来 |
| **业务层**（页面） | 组装逻辑 + UI，传递 props | `app/` 页面文件 | 不关心实现细节 |

```tsx
// ✅ 逻辑层：可复用、可测试、可跨端共享
function useChatMessages(agentId: string) {
  const { messages, append, stop } = useChat({ api: `/api/chat/${agentId}` });
  return { messages, send: append, stop };
}

// ✅ UI 层：纯渲染，通过 props 接收数据
function ChatPanel({ messages, onSend, onStop }: ChatPanelProps) {
  return (/* JSX */);
}

// ✅ 业务层：页面组装
export default function AgentChatPage({ params }: { params: { id: string } }) {
  const chat = useChatMessages(params.id);
  return <ChatPanel messages={chat.messages} onSend={chat.send} onStop={chat.stop} />;
}
```

**判断标准**：
- 简单组件（按钮/输入框/卡片）：不需要分离，直接写
- 中等组件（对话面板/表单/列表）：抽 hook 分离逻辑
- 复杂组件（工作流编辑器/协作面板）：必须分离，否则不可维护

**禁止**：在 UI 组件内直接调用 API、直接操作 store、包含业务判断逻辑。

## 组件规范

- 页面级组件（`page.tsx`）默认 Server Component，需要交互时加 `'use client'`
- 组件文件名 PascalCase：`ChatPanel.tsx`
- 每个文件只导出一个组件

```tsx
// ✅ Server Component（默认）
export default async function ChatPage() {
  const data = await fetchData()
  return <ChatPanel data={data} />
}

// ✅ Client Component（需要时）
'use client'
export function ChatInput({ onSend }: Props) { ... }
```

## API 调用规范

统一在 `lib/api/` 下按模块封装，不在组件内直接 fetch：

```ts
// lib/api/chat.ts
export async function sendMessage(content: string) {
  const res = await fetch('/api/chat', { method: 'POST', body: JSON.stringify({ content }) })
  if (!res.ok) throw new Error(await res.text())
  return res.json()
}
```

## SSE 流式接收

```ts
const source = new EventSource('/api/chat/stream')
source.onmessage = (e) => setContent(prev => prev + e.data)
source.onerror = () => source.close()
```

> 技术选型见 webui 技术选型

## 注释规范

- **注释语言统一中文**，禁止中英混用（与 `docs/` 真理源一致）
- TypeScript 类型必须显式，禁 `any` / 禁 `@ts-ignore`（特殊情况加注释解释）
- **文件级注释**：每个模块文件顶部加 JSDoc，说明用途 + `@author`
- **导出函数/类**：一句话说明，复杂逻辑加用法示例
- **不加** `@since` / `@version`（用 git blame 追溯）

```ts
/**
 * 实体注册表：管理所有 EntityDef 的注册、解析和查找
 * @author AaronZZH & Kiro
 */
```


## 响应式布局

- 组件内部响应式优先使用 container queries（`@container` + `@断点:`），仅在需要响应视口时使用传统断点（`md:` / `lg:`）
- 判断标准：组件可能被放在不同宽度的容器中 → 用 `@container`；组件始终占满视口宽度 → 用传统断点