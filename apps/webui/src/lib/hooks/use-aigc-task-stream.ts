/**
 * useAigcTaskStream——订阅 AIGC 任务事件流（SSE 单例）
 *
 * 全局共享一条 SSE 连接，多个组件订阅不会建立多条连接。
 * 最后一个订阅者卸载时自动关闭连接。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
import { useEffect, useRef } from "react"
import { buildSseUrl } from "@/lib/api/config"
import { invalidateCreditQueries } from "@/lib/queries/use-credits"

export interface AigcTaskEvent {
  id: number
  userId: number
  type: "IMAGE" | "VIDEO" | "MUSIC" | "MODEL_3D" | "VOICE" | "IMAGE_PROCESS"
  status: "PENDING" | "RUNNING" | "SUCCESS" | "FAIL"
  provider?: string
  model?: string
  prompt?: string
  taskId?: string
  resultUrl?: string
  ossUrl?: string
  errorMsg?: string
  createTime: string
  updateTime: string
}

export type AigcTaskEventType = "task.created" | "task.progress" | "task.completed" | "task.failed"

export interface UseAigcTaskStreamOptions {
  onCreated?: (task: AigcTaskEvent) => void
  onProgress?: (task: AigcTaskEvent) => void
  onCompleted?: (task: AigcTaskEvent) => void
  onFailed?: (task: AigcTaskEvent) => void
  onReconnect?: () => void
  enabled?: boolean
}

// ─── 单例 SSE 管理 ───────────────────────────────────────────────────────────

type Subscriber = Required<Omit<UseAigcTaskStreamOptions, "enabled" | "onReconnect">> & {
  onReconnect?: () => void
}

let es: EventSource | null = null
let retryCount = 0
let retryTimer: ReturnType<typeof setTimeout> | null = null
let isFirstConnect = true
const subscribers = new Set<Subscriber>()

function emit(type: keyof Subscriber, task: AigcTaskEvent) {
  for (const sub of subscribers) {
    ;(sub[type] as ((t: AigcTaskEvent) => void) | undefined)?.(task)
  }
}

function parse(e: MessageEvent): AigcTaskEvent | null {
  try {
    return JSON.parse(e.data)
  } catch {
    return null
  }
}

function connect() {
  const url = buildSseUrl("/aigc/tasks/stream")
  const source = new EventSource(url, { withCredentials: true })
  es = source

  source.addEventListener("task.created", (e) => {
    const t = parse(e)
    if (t) emit("onCreated", t)
  })
  source.addEventListener("task.progress", (e) => {
    const t = parse(e)
    if (t) emit("onProgress", t)
  })
  source.addEventListener("task.completed", (e) => {
    const t = parse(e)
    if (t) emit("onCompleted", t)
  })
  source.addEventListener("task.failed", (e) => {
    const t = parse(e)
    if (t) emit("onFailed", t)
  })

  source.onopen = () => {
    if (!isFirstConnect)
      subscribers.forEach((s) => {
        s.onReconnect?.()
      })
    isFirstConnect = false
    retryCount = 0
  }

  source.onerror = () => {
    source.close()
    es = null
    if (subscribers.size > 0 && retryCount < 5) {
      const delay = Math.min(1000 * 2 ** retryCount, 30000)
      retryCount++
      retryTimer = setTimeout(connect, delay)
    }
  }
}

function ensureConnected() {
  if (!es || es.readyState === EventSource.CLOSED) connect()
}

function disconnect() {
  if (retryTimer) {
    clearTimeout(retryTimer)
    retryTimer = null
  }
  es?.close()
  es = null
  isFirstConnect = true
  retryCount = 0
}

// ─── Hook ────────────────────────────────────────────────────────────────────

export function useAigcTaskStream(options: UseAigcTaskStreamOptions = {}) {
  const { onCreated, onProgress, onCompleted, onFailed, onReconnect, enabled = true } = options
  const qc = useQueryClient()

  // 用 ref 稳定回调引用，避免 effect 重跑
  const subRef = useRef<Subscriber>({
    onCreated: onCreated ?? (() => {}),
    onProgress: onProgress ?? (() => {}),
    onCompleted: onCompleted ?? (() => {}),
    onFailed: onFailed ?? (() => {}),
    onReconnect
  })
  subRef.current = {
    onCreated: onCreated ?? (() => {}),
    onProgress: onProgress ?? (() => {}),
    // 任务完成/失败后积分余额已变更，统一在此刷新，调用方无需关心
    onCompleted: (t) => {
      invalidateCreditQueries(qc)
      onCompleted?.(t)
    },
    onFailed: (t) => {
      invalidateCreditQueries(qc)
      onFailed?.(t)
    },
    onReconnect
  }

  useEffect(() => {
    if (!enabled) return

    // 注册一个稳定的代理订阅者（指向 ref，不会因回调变化而重新注册）
    const proxy: Subscriber = {
      onCreated: (t) => subRef.current.onCreated(t),
      onProgress: (t) => subRef.current.onProgress(t),
      onCompleted: (t) => subRef.current.onCompleted(t),
      onFailed: (t) => subRef.current.onFailed(t),
      onReconnect: () => subRef.current.onReconnect?.()
    }

    subscribers.add(proxy)
    ensureConnected()

    return () => {
      subscribers.delete(proxy)
      if (subscribers.size === 0) disconnect()
    }
  }, [enabled])
}
