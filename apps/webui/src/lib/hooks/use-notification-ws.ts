/**
 * useNotificationWs——通知 WebSocket 订阅，收到消息时刷新缓存 + 显示 toast
 * @author AaronZZH & Kiro
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
import { useCallback } from "react"
import { notify } from "@/lib/notification"
import { useWebSocket } from "./use-websocket"

interface NotificationMessage {
  id: string
  type: string
  title: string
  body?: string
}

/**
 * 连接通知 WebSocket，收到新通知时：
 * 1. invalidate 通知列表和未读计数缓存
 * 2. 显示 toast 提示
 */
export function useNotificationWs(userId: string | undefined) {
  const queryClient = useQueryClient()

  const onMessage = useCallback(
    (raw: string) => {
      let msg: NotificationMessage
      try {
        msg = JSON.parse(raw) as NotificationMessage
      } catch {
        return
      }

      // 刷新通知相关缓存
      queryClient.invalidateQueries({ queryKey: ["notifications"] })

      // Toast 提示
      notify.info(msg.title, { description: msg.body })
    },
    [queryClient]
  )

  const wsUrl = userId
    ? `${getWsBaseUrl()}/ws/notifications?userId=${encodeURIComponent(userId)}`
    : ""

  return useWebSocket({
    url: wsUrl,
    onMessage,
    enabled: !!userId
  })
}

/** 根据当前页面协议推导 WebSocket 地址 */
function getWsBaseUrl(): string {
  if (typeof window === "undefined") return ""
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:"
  return `${protocol}//${window.location.host}`
}
