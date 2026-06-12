/**
 * useAigcTaskStream——订阅 AIGC 任务事件流（SSE）
 * 一次连接监听当前用户所有任务的进度/完成/失败事件
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect, useRef } from "react"
import { buildSseUrl } from "@/lib/api/config"
import { useAuthStore } from "@/lib/store/auth-store"

export interface AigcTaskEvent {
  id: number
  userId: number
  type: "IMAGE" | "VIDEO" | "MUSIC" | "MODEL_3D"
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
  /** 是否启用，默认 true */
  enabled?: boolean
}

/**
 * 订阅 AIGC 任务 SSE 事件流
 * 连接断开时自动重连（最多5次，指数退避）
 */
export function useAigcTaskStream(options: UseAigcTaskStreamOptions = {}) {
  const { onCreated, onProgress, onCompleted, onFailed, enabled = true } = options
  const esRef = useRef<EventSource | null>(null)
  const retryRef = useRef(0)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // 用 ref 存回调，避免每次回调变化都重建 SSE 连接
  const cbRef = useRef({ onCreated, onProgress, onCompleted, onFailed })
  cbRef.current = { onCreated, onProgress, onCompleted, onFailed }

  useEffect(() => {
    if (!enabled) return

    function connect() {
      const token = useAuthStore.getState().accessToken
      const url = buildSseUrl("/aigc/tasks/stream", token)
      const es = new EventSource(url, { withCredentials: true })
      esRef.current = es

      const parse = (e: MessageEvent): AigcTaskEvent | null => {
        try {
          return JSON.parse(e.data)
        } catch {
          return null
        }
      }

      es.addEventListener("task.created", (e) => {
        const task = parse(e)
        if (task) cbRef.current.onCreated?.(task)
      })
      es.addEventListener("task.progress", (e) => {
        const task = parse(e)
        if (task) cbRef.current.onProgress?.(task)
      })
      es.addEventListener("task.completed", (e) => {
        const task = parse(e)
        if (task) cbRef.current.onCompleted?.(task)
      })
      es.addEventListener("task.failed", (e) => {
        const task = parse(e)
        if (task) cbRef.current.onFailed?.(task)
      })

      es.onerror = () => {
        es.close()
        esRef.current = null
        if (retryRef.current < 5) {
          const delay = Math.min(1000 * 2 ** retryRef.current, 30000)
          retryRef.current += 1
          timerRef.current = setTimeout(connect, delay)
        }
      }

      es.onopen = () => {
        retryRef.current = 0
      }
    }

    connect()

    return () => {
      esRef.current?.close()
      esRef.current = null
      if (timerRef.current) clearTimeout(timerRef.current)
    }
  }, [enabled])
}
