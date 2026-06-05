/**
 * AI 对话页——全屏分栏布局
 *
 * 布局结构（保留 workspace sidebar + header）：
 * ┌──────────┬──────────────────────────────────────┐
 * │ 会话列表  │         Chatter（AI 对话面板）         │
 * │ (240px)  │         layout="panel"               │
 * └──────────┴──────────────────────────────────────┘
 *
 * 参考：tmp/nextjs/next-ts/src/sections/chat/layout.tsx
 *
 * @author AaronZZH & Kiro
 */

import { AiChatView } from "@/sections/ai/AiChatView"

export default function AiChatPage() {
  return <AiChatView />
}
