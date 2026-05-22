/**
 * LivechatPanel——聊天面板（Thread + Composer）
 * 基于 assistant-ui 统一组件渲染对话界面
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ComposerPrimitive, MessagePrimitive, ThreadPrimitive } from "@assistant-ui/react"

/**
 * 聊天面板组件
 * 内部使用 assistant-ui 的 ThreadPrimitive 组件（含 Composer），
 * 需要包裹在 LivechatProvider 或 AssistantRuntimeProvider 内使用
 */
export function LivechatPanel() {
  return (
    <ThreadPrimitive.Root className="flex h-full flex-col">
      <ThreadPrimitive.Viewport className="min-h-0 flex-1 overflow-y-auto p-4">
        <ThreadPrimitive.Messages>
          {({ message }) => (
            <div className={`mb-3 flex ${message.role === "user" ? "justify-end" : "justify-start"}`}>
              <div className={`max-w-[80%] rounded-lg px-3 py-2 text-sm ${message.role === "user" ? "bg-primary text-primary-foreground" : "bg-muted"}`}>
                <MessagePrimitive.Content />
              </div>
            </div>
          )}
        </ThreadPrimitive.Messages>
      </ThreadPrimitive.Viewport>
      <ComposerPrimitive.Root className="border-t p-3">
        <ComposerPrimitive.Input
          className="w-full resize-none rounded-md border bg-background px-3 py-2 text-sm outline-none placeholder:text-muted-foreground focus:ring-1 focus:ring-ring"
          placeholder="输入消息..."
        />
        <ComposerPrimitive.Send />
      </ComposerPrimitive.Root>
    </ThreadPrimitive.Root>
  )
}
