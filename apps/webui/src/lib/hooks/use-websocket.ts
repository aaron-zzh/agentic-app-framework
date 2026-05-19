/**
 * useWebSocket——通用 WebSocket hook，支持断线重连 + 心跳保活
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useEffect, useRef, useState } from "react"

export interface UseWebSocketOptions {
  /** WebSocket 地址 */
  url: string
  /** 收到消息回调 */
  onMessage?: (data: string) => void
  /** 连接成功回调 */
  onOpen?: () => void
  /** 连接关闭回调 */
  onClose?: () => void
  /** 是否启用（默认 true） */
  enabled?: boolean
}

export type WebSocketStatus = "connecting" | "connected" | "disconnected"

/** 重连初始延迟（ms） */
const RECONNECT_BASE = 1000
/** 重连最大延迟（ms） */
const RECONNECT_MAX = 30000
/** 心跳间隔（ms） */
const HEARTBEAT_INTERVAL = 30000

export function useWebSocket({ url, onMessage, onOpen, onClose, enabled = true }: UseWebSocketOptions) {
  const [status, setStatus] = useState<WebSocketStatus>("disconnected")
  const wsRef = useRef<WebSocket | null>(null)
  const retryCountRef = useRef(0)
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const heartbeatTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // 用 ref 保存最新回调，避免重连时闭包过期
  const onMessageRef = useRef(onMessage)
  onMessageRef.current = onMessage
  const onOpenRef = useRef(onOpen)
  onOpenRef.current = onOpen
  const onCloseRef = useRef(onClose)
  onCloseRef.current = onClose

  const clearTimers = useCallback(() => {
    if (retryTimerRef.current) {
      clearTimeout(retryTimerRef.current)
      retryTimerRef.current = null
    }
    if (heartbeatTimerRef.current) {
      clearInterval(heartbeatTimerRef.current)
      heartbeatTimerRef.current = null
    }
  }, [])

  const startHeartbeat = useCallback(() => {
    heartbeatTimerRef.current = setInterval(() => {
      if (wsRef.current?.readyState === WebSocket.OPEN) {
        wsRef.current.send("ping")
      }
    }, HEARTBEAT_INTERVAL)
  }, [])

  const connect = useCallback(() => {
    if (!enabled) return

    setStatus("connecting")
    const ws = new WebSocket(url)
    wsRef.current = ws

    ws.onopen = () => {
      setStatus("connected")
      retryCountRef.current = 0
      startHeartbeat()
      onOpenRef.current?.()
    }

    ws.onmessage = (event: MessageEvent) => {
      // 忽略心跳响应
      if (event.data === "pong") return
      onMessageRef.current?.(event.data as string)
    }

    ws.onclose = () => {
      setStatus("disconnected")
      clearTimers()
      onCloseRef.current?.()
      // 指数退避重连
      const delay = Math.min(RECONNECT_BASE * 2 ** retryCountRef.current, RECONNECT_MAX)
      retryCountRef.current += 1
      retryTimerRef.current = setTimeout(connect, delay)
    }

    ws.onerror = () => {
      ws.close()
    }
  }, [url, enabled, clearTimers, startHeartbeat])

  useEffect(() => {
    if (!enabled) return
    connect()
    return () => {
      clearTimers()
      wsRef.current?.close()
      wsRef.current = null
    }
  }, [connect, enabled, clearTimers])

  return { status }
}
