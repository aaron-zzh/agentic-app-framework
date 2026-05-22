/**
 * ChatterThread——消息列表区域
 * 基于 assistant-ui ThreadPrimitive 渲染对话消息流
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { MessagePrimitive, ThreadPrimitive } from "@assistant-ui/react"

/**
 * 对话消息流组件
 * 使用 ThreadPrimitive 渲染消息列表，自动滚动到底部
 */
export function ChatterThread() {
  return (
    <ThreadPrimitive.Root className="flex min-h-0 flex-1 flex-col">
      <ThreadPrimitive.Viewport className="min-h-0 flex-1 overflow-y-auto p-4">
        <ThreadPrimitive.Messages>
          {({ message }) => (
            <div
              className={`mb-3 flex ${message.role === "user" ? "justify-end" : "justify-start"}`}
            >
              <div
                className={`max-w-[80%] rounded-lg px-3 py-2 text-sm ${
                  message.role === "user" ? "bg-primary text-primary-foreground" : "bg-muted"
                }`}
              >
                <MessagePrimitive.Content />
              </div>
            </div>
          )}
        </ThreadPrimitive.Messages>
      </ThreadPrimitive.Viewport>
    </ThreadPrimitive.Root>
  )
}
