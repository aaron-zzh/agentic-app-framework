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
