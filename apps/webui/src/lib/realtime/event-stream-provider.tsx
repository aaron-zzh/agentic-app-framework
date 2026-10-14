/**
 * 全局事件 SSE 单例：登录后全应用只建立一条 `GET /events/stream` fetch 连接。
 *
 * 挂在根 layout 的 AuthProvider 内，workspace 与 Studio 共用；收到事件后去重、持久化游标，
 * 再分发给 event-registry 中按类型前缀订阅的消费者。事件仅作变化通知，不写入本地状态。
 * @author AaronZZH & Kiro
 */

"use client"

import { WifiOff } from "lucide-react"
import { useEffect, useState } from "react"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { backendStreamFetch } from "@/lib/api/streaming-client"
import { useAuthStore } from "@/lib/store/auth-store"
import { useOrgStore } from "@/lib/store/org-store"
import { clearRecentEvents, publishEvent } from "./event-registry"
import { acceptEventEnvelope, cursorValue, eventCursorKey, readEventStream } from "./event-stream"

const RECONNECT_DELAY_MS = 1_000
/** 连接中断超过该时长才提示，避免短暂重连造成闪烁。 */
const DISCONNECT_NOTICE_DELAY_MS = 3_000

type EventConnectionState = "connected" | "disconnected" | "reconnecting"

function waitForReconnect(signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const onAbort = () => {
      clearTimeout(timer)
      resolve()
    }
    const timer = setTimeout(() => {
      signal.removeEventListener("abort", onAbort)
      resolve()
    }, RECONNECT_DELAY_MS)
    signal.addEventListener("abort", onAbort, { once: true })
  })
}

export function EventStreamProvider({ children }: { children: React.ReactNode }) {
  const ownerId = useAuthStore((state) => state.user?.id ?? null)
  const scope = useOrgStore((state) => state.currentScope)
  const [connectionState, setConnectionState] = useState<EventConnectionState>("connected")
  const [noticeVisible, setNoticeVisible] = useState(false)

  useEffect(() => {
    if (ownerId === null) return
    const cursorKey = eventCursorKey(ownerId, scope)
    let currentCursor = cursorValue(localStorage.getItem(cursorKey))
    const controller = new AbortController()
    const seenEventIds = new Set<bigint>()

    const handleEvent = (data: string) => {
      const accepted = acceptEventEnvelope(data, seenEventIds, currentCursor)
      if (accepted === null) return
      if (accepted.cursor > currentCursor) {
        currentCursor = accepted.cursor
        localStorage.setItem(cursorKey, currentCursor.toString())
      }
      publishEvent(accepted.event)
    }

    const connect = async () => {
      while (!controller.signal.aborted) {
        try {
          const response = await backendStreamFetch(
            `/events/stream?after=${encodeURIComponent(currentCursor.toString())}`,
            { headers: { Accept: "text/event-stream" }, signal: controller.signal }
          )
          if (!response.ok) throw new Error(`全局事件 SSE 请求失败（${response.status}）`)
          if (!response.body) throw new Error("全局事件 SSE 响应缺少流")
          setConnectionState("connected")
          await readEventStream(response, handleEvent)
          if (!controller.signal.aborted) setConnectionState("disconnected")
        } catch (error) {
          if (controller.signal.aborted) return
          if (error instanceof DOMException && error.name === "AbortError") return
          setConnectionState("disconnected")
        }
        if (controller.signal.aborted) return
        setConnectionState("reconnecting")
        await waitForReconnect(controller.signal)
      }
    }

    void connect()
    return () => {
      controller.abort()
      clearRecentEvents()
    }
  }, [ownerId, scope])

  const connected = connectionState === "connected"
  useEffect(() => {
    if (connected) {
      setNoticeVisible(false)
      return
    }
    const timer = setTimeout(() => setNoticeVisible(true), DISCONNECT_NOTICE_DELAY_MS)
    return () => clearTimeout(timer)
  }, [connected])

  return (
    <>
      {children}
      {ownerId !== null && noticeVisible && (
        <Alert
          role="status"
          aria-live="polite"
          className="fixed right-4 bottom-4 z-40 w-auto max-w-sm bg-background/95 shadow-lg"
        >
          <WifiOff />
          <AlertDescription>连接中断，服务端任务继续运行，正在重连…</AlertDescription>
        </Alert>
      )}
    </>
  )
}
