/**
 * LivechatPanel——聊天面板（Thread + Composer）
 * 基于 assistant-ui 统一组件渲染对话界面
 *
 * @author AaronZZH & Kiro
 */

"use client"

import {
  ActionBarPrimitive,
  AuiIf,
  ComposerPrimitive,
  MessagePrimitive,
  ThreadPrimitive,
  useMessage,
  useThread
} from "@assistant-ui/react"
import { CheckIcon, CopyIcon, SendHorizonal } from "lucide-react"
import { Button } from "@/components/ui/button"
import { formatMessageTime } from "@/lib/utils/format-time"
import { MarkdownMessage } from "./components/MarkdownMessage"

/** 助手消息 */
function AssistantMessage() {
  const message = useMessage()

  return (
    <MessagePrimitive.Root className="mb-3 flex flex-col items-start">
      <div className="max-w-[80%] rounded-lg bg-muted px-3 py-2 text-sm">
        <MarkdownMessage />
      </div>
      <div className="mt-1 flex items-center gap-2 px-1">
        {message.createdAt && (
          <time
            className="text-muted-foreground text-xs"
            dateTime={message.createdAt.toISOString()}
            title={message.createdAt.toLocaleString("zh-CN")}
          >
            {formatMessageTime(message.createdAt.toISOString())}
          </time>
        )}
        <ActionBarPrimitive.Root hideWhenRunning autohide="not-last" className="flex gap-1">
          <ActionBarPrimitive.Copy asChild>
            <button
              type="button"
              className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
              aria-label="复制"
            >
              <AuiIf condition={(s) => s.message.isCopied}>
                <CheckIcon className="size-3.5" />
              </AuiIf>
              <AuiIf condition={(s) => !s.message.isCopied}>
                <CopyIcon className="size-3.5" />
              </AuiIf>
            </button>
          </ActionBarPrimitive.Copy>
        </ActionBarPrimitive.Root>
      </div>
    </MessagePrimitive.Root>
  )
}

/** 用户消息 */
function UserMessage() {
  const message = useMessage()

  return (
    <MessagePrimitive.Root className="mb-3 flex flex-col items-end">
      <div className="max-w-[80%] rounded-2xl bg-primary px-3 py-2 text-primary-foreground text-sm">
        <MessagePrimitive.Content />
      </div>
      {message.createdAt && (
        <time
          className="mt-1 px-1 text-muted-foreground text-xs"
          dateTime={message.createdAt.toISOString()}
          title={message.createdAt.toLocaleString("zh-CN")}
        >
          {formatMessageTime(message.createdAt.toISOString())}
        </time>
      )}
    </MessagePrimitive.Root>
  )
}

/**
 * 聊天面板组件
 * 内部使用 assistant-ui 的 ThreadPrimitive 组件（含 Composer），
 * 需要包裹在 LivechatProvider 或 AssistantRuntimeProvider 内使用
 */
export function LivechatPanel() {
  const isLoading = useThread((s) => s.isRunning)

  return (
    <ThreadPrimitive.Root className="flex h-full flex-col">
      <ThreadPrimitive.Viewport className="min-h-0 flex-1 overflow-y-auto p-4">
        <ThreadPrimitive.Messages>
          {({ message }) => (message.role === "user" ? <UserMessage /> : <AssistantMessage />)}
        </ThreadPrimitive.Messages>

        {/* 加载状态指示器 */}
        {isLoading && (
          <div className="mb-3 flex items-start">
            <div className="max-w-[80%] rounded-lg bg-muted px-3 py-2">
              <div className="flex items-center gap-2 text-muted-foreground text-sm">
                <div className="flex gap-1">
                  <div className="size-1.5 animate-bounce rounded-full bg-current [animation-delay:-0.3s]" />
                  <div className="size-1.5 animate-bounce rounded-full bg-current [animation-delay:-0.15s]" />
                  <div className="size-1.5 animate-bounce rounded-full bg-current" />
                </div>
                <span>正在输入...</span>
              </div>
            </div>
          </div>
        )}
      </ThreadPrimitive.Viewport>
      <ComposerPrimitive.Root className="border-t p-3">
        <ComposerPrimitive.Input
          className="w-full resize-none rounded-md border bg-background px-3 py-2 text-sm outline-none placeholder:text-muted-foreground focus:ring-1 focus:ring-ring"
          placeholder="输入消息..."
        />
        <ComposerPrimitive.Send asChild>
          <Button type="submit" size="icon" variant="ghost" aria-label="发送">
            <SendHorizonal className="size-4" />
          </Button>
        </ComposerPrimitive.Send>
      </ComposerPrimitive.Root>
    </ThreadPrimitive.Root>
  )
}
