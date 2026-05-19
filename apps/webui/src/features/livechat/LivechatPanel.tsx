/**
 * LivechatPanel——聊天面板（Thread + Composer）
 * 基于 assistant-ui 统一组件渲染对话界面
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Thread } from "@assistant-ui/react"

/**
 * 聊天面板组件
 * 内部使用 assistant-ui 的 Thread 组件（含 Composer），
 * 需要包裹在 LivechatProvider 或 AssistantRuntimeProvider 内使用
 */
export function LivechatPanel() {
  return (
    <div className="flex h-full flex-col">
      <Thread />
    </div>
  )
}
