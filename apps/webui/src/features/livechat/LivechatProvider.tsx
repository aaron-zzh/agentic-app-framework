/**
 * LivechatProvider——ExternalStoreRuntime 配置
 * 将 WebSocket 消息转为 assistant-ui 格式，提供统一 runtime
 *
 * @author AaronZZH & Kiro
 */

"use client"

import {
  type AppendMessage,
  AssistantRuntimeProvider,
  type ExternalStoreThreadListAdapter,
  type ThreadMessage,
  useExternalStoreRuntime
} from "@assistant-ui/react"
import { type ReactNode, useCallback, useEffect, useRef, useState } from "react"
import type { ChatMessageVO } from "@/lib/api/chat"
import { useWebSocket } from "@/lib/hooks/use-websocket"
import { useChatMessages } from "@/lib/queries/use-chat"
import type { LivechatRuntimeConfig } from "./runtime"

/** 后端 ChatMessageVO → assistant-ui ThreadMessage */
function toThreadMessage(msg: ChatMessageVO): ThreadMessage {
  const isUser = msg.role === "user"
  if (isUser) {
    return {
      id: msg.id,
      role: "user" as const,
      content: [{ type: "text" as const, text: msg.content }],
      createdAt: new Date(msg.createdAt),
      attachments: [],
      metadata: { custom: {} }
    } as ThreadMessage
  }
  return {
    id: msg.id,
    role: "assistant" as const,
    content: [{ type: "text" as const, text: msg.content }],
    createdAt: new Date(msg.createdAt),
    status: { type: "complete" as const, reason: "stop" as const },
    metadata: { custom: {} }
  } as unknown as ThreadMessage
}

interface LivechatProviderProps {
  config: LivechatRuntimeConfig
  children: ReactNode
}

export function LivechatProvider({ config, children }: LivechatProviderProps) {
  const { userId, sessionId } = config
  const [messages, setMessages] = useState<ThreadMessage[]>([])
  const [isRunning, setIsRunning] = useState(false)
  const wsUrl = `${process.env.NEXT_PUBLIC_WS_URL ?? "ws://localhost:8080"}/ws/chat?userId=${userId}&sessionId=${sessionId}`

  // 加载历史消息
  const { data: history } = useChatMessages(sessionId)
  const historyLoaded = useRef(false)

  useEffect(() => {
    if (history && !historyLoaded.current) {
      setMessages(history.map(toThreadMessage))
      historyLoaded.current = true
    }
  }, [history])

  // WebSocket 实时消息
  const handleWsMessage = useCallback((raw: string) => {
    try {
      const msg: ChatMessageVO = JSON.parse(raw)
      setMessages((prev) => {
        // 去重：避免历史加载和 WS 推送重复
        if (prev.some((m) => m.id === msg.id)) return prev
        return [...prev, toThreadMessage(msg)]
      })
      setIsRunning(false)
    } catch {
      // 忽略非 JSON 消息（如心跳）
    }
  }, [])

  useWebSocket({
    url: wsUrl,
    onMessage: handleWsMessage,
    enabled: !!sessionId
  })

  // 发送消息回调
  const onNew = useCallback(
    async (message: AppendMessage) => {
      const textPart = message.content.find((c) => c.type === "text")
      const text = textPart && "text" in textPart ? textPart.text : ""
      if (!text.trim()) return

      // 乐观添加用户消息
      const userMsg: ThreadMessage = {
        id: `temp-${Date.now()}`,
        role: "user" as const,
        content: [{ type: "text" as const, text }],
        createdAt: new Date(),
        attachments: [],
        metadata: { custom: {} }
      } as ThreadMessage
      setMessages((prev) => [...prev, userMsg])
      setIsRunning(true)

      // 通过 REST 发送（后端会通过 WS 推送响应）
      const { chatApi } = await import("@/lib/api/chat")
      await chatApi.sendMessage({ sessionId, content: text })
    },
    [sessionId]
  )

  // 线程列表适配器（单线程模式）
  const threadListAdapter: ExternalStoreThreadListAdapter = {
    threadId: sessionId,
    threads: [],
    archivedThreads: [],
    onSwitchToNewThread: async () => {},
    onSwitchToThread: () => {}
  }

  const runtime = useExternalStoreRuntime({
    messages,
    isRunning,
    onNew,
    convertMessage: undefined,
    adapters: {
      threadList: threadListAdapter
    }
  })

  return <AssistantRuntimeProvider runtime={runtime}>{children}</AssistantRuntimeProvider>
}
