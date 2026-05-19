/**
 * 多人在线感知 Hook：追踪同一记录的在线编辑者
 * 直接管理 WebSocket 连接（需要 send 能力，useWebSocket 不暴露）
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useEffect, useRef, useState } from "react"

/** 在线用户信息 */
export interface PresenceUser {
  id: string
  name: string
  avatar?: string
}

/** WS 消息类型 */
interface PresenceMessage {
  type: "join" | "leave" | "viewers"
  entityType: string
  entityId: string
  user?: PresenceUser
  userId?: string
  viewers?: PresenceUser[]
}

interface UseRecordPresenceOptions {
  entityType: string
  entityId: string
  /** 当前用户信息 */
  currentUser: PresenceUser
}

/**
 * 在线感知 hook，进入记录时广播 join，离开时广播 leave
 */
export function useRecordPresence({ entityType, entityId, currentUser }: UseRecordPresenceOptions) {
  const [viewers, setViewers] = useState<PresenceUser[]>([])
  const wsRef = useRef<WebSocket | null>(null)

  const handleMessage = useCallback(
    (event: MessageEvent) => {
      if (event.data === "pong") return
      const msg = JSON.parse(event.data as string) as PresenceMessage
      if (msg.entityType !== entityType || msg.entityId !== entityId) return

      if (msg.type === "viewers" && msg.viewers) {
        setViewers(msg.viewers.filter((u) => u.id !== currentUser.id))
      } else if (msg.type === "join" && msg.user) {
        if (msg.user.id === currentUser.id) return
        setViewers((prev) => (prev.some((u) => u.id === msg.user!.id) ? prev : [...prev, msg.user!]))
      } else if (msg.type === "leave" && msg.userId) {
        setViewers((prev) => prev.filter((u) => u.id !== msg.userId))
      }
    },
    [entityType, entityId, currentUser.id]
  )

  useEffect(() => {
    const ws = new WebSocket(`/ws/presence?userId=${currentUser.id}`)
    wsRef.current = ws

    ws.onopen = () => {
      ws.send(JSON.stringify({ type: "join", entityType, entityId, user: currentUser }))
    }

    ws.onmessage = handleMessage

    return () => {
      // 离开时发送 leave（连接仍 open 时）
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: "leave", entityType, entityId, userId: currentUser.id }))
      }
      ws.close()
      wsRef.current = null
    }
  }, [entityType, entityId, currentUser, handleMessage])

  return { viewers }
}
