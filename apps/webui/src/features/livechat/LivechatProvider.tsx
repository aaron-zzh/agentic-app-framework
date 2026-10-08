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
import type { ChatMessageVO } from "@/lib/api/rest/ai"
import { useChatMessages } from "@/lib/api/rest/ai"
import { useWebSocket } from "@/lib/hooks/use-websocket"
import type { IMRuntimeConfig } from "./runtime"

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
  config: IMRuntimeConfig
  children: ReactNode
}

export function LivechatProvider({ config, children }: LivechatProviderProps) {
  const { userId, sessionId } = config
  const [messages, setMessages] = useState<ThreadMessage[]>([])
  const [isRunning, setIsRunning] = useState(false)
  const wsUrl = `${process.env.NEXT_PUBLIC_WS_URL ?? "ws://localhost:8080"}/ws/im?userId=${userId}&sessionId=${sessionId}`

  // 加载历史消息
  const { data: history } = useChatMessages(sessionId)
  const historyLoaded = useRef(false)
  const messageIdsRef = useRef(new Set<string>())

  useEffect(() => {
    if (history && !historyLoaded.current) {
      const mapped = history.map(toThreadMessage)
      setMessages(mapped)
      // 初始化 ID 缓存
      mapped.forEach((msg) => {
        messageIdsRef.current.add(msg.id)
      })
      historyLoaded.current = true
    }
  }, [history])

  // WebSocket 实时消息：统一信封 {type: "im_message", data: ImMessage}
  // ImMessage 字段扁平：{conversationId, messageId, senderId, content}，无 role/createdAt，
  // 按 senderId 是否等于当前用户判断消息角色。
  const handleWsMessage = useCallback(
    (raw: string) => {
      try {
        const envelope: {
          type?: string
          data?: {
            conversationId?: number
            messageId?: number
            senderId?: number
            content?: string
          }
        } = JSON.parse(raw)
        if (envelope.type !== "im_message" || !envelope.data) return
        const { messageId, senderId, content } = envelope.data
        if (messageId == null || content == null) return
        const isSelf = String(senderId) === String(userId)
        const msg: ChatMessageVO = {
          id: String(messageId),
          sessionId,
          role: isSelf ? "user" : "assistant",
          content,
          createdAt: new Date().toISOString()
        }
        setMessages((prev) => {
          // 去重优化：O(1) Set 查找替代 O(n) 数组遍历
          if (messageIdsRef.current.has(msg.id)) return prev
          messageIdsRef.current.add(msg.id)
          return [...prev, toThreadMessage(msg)]
        })
        setIsRunning(false)
      } catch {
        // 忽略非 JSON 消息（如心跳）
      }
    },
    [userId, sessionId]
  )

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
      const { chatApi } = await import("@/lib/api/rest/ai/chat")
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
