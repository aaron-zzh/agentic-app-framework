/**
 * useBatchOperation——批量操作异步化 Hook
 * @author AaronZZH & Kiro
 *
 * 阈值规则：
 * - ≤100 条：同步执行，等待响应
 * - >100 条：异步执行，返回 taskId，轮询进度
 *
 * 用法：
 * ```tsx
 * const { execute, progress, cancel } = useBatchOperation(entity)
 * await execute('delete', ids)
 * ```
 */

import { useCallback, useRef, useState } from "react"
import { toast } from "sonner"

import type { EntityDef } from "@/lib/types/entity"

/** 同步阈值 */
const SYNC_THRESHOLD = 100
/** 轮询间隔（ms） */
const POLL_INTERVAL = 1500

export type TaskStatus = "idle" | "running" | "completed" | "failed" | "cancelled"

export interface BatchProgress {
  status: TaskStatus
  current: number
  total: number
  percentage: number
  taskId?: string
  errorMessage?: string
}

interface BatchOperationOptions {
  onSuccess?: (result: { success: number; failed: number }) => void
  onError?: (error: string) => void
}

/** 批量操作异步化 Hook */
export function useBatchOperation(entity: EntityDef, options?: BatchOperationOptions) {
  const [progress, setProgress] = useState<BatchProgress>({
    status: "idle",
    current: 0,
    total: 0,
    percentage: 0
  })
  const pollTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const cancelledRef = useRef(false)
  // 用 ref 存储 options，避免 useCallback 依赖对象引用导致不必要重建
  const optionsRef = useRef(options)
  optionsRef.current = options

  const stopPolling = useCallback(() => {
    if (pollTimerRef.current) {
      clearTimeout(pollTimerRef.current)
      pollTimerRef.current = null
    }
  }, [])

  /** 轮询任务进度 */
  const pollProgress = useCallback(
    async (taskId: string) => {
      if (cancelledRef.current) return

      try {
        const res = await fetch(`/api/tasks/${taskId}/progress`)
        const json = await res.json()
        const data = json.data ?? {}

        const newProgress: BatchProgress = {
          status: data.status?.toLowerCase() ?? "running",
          current: data.current ?? 0,
          total: data.total ?? 0,
          percentage: data.percentage ?? 0,
          taskId
        }
        setProgress(newProgress)

        if (newProgress.status === "completed") {
          stopPolling()
          optionsRef.current?.onSuccess?.({ success: data.success ?? data.current, failed: data.failed ?? 0 })
          toast.success(`操作完成：成功 ${data.success ?? data.current} 条`)
        } else if (newProgress.status === "failed") {
          stopPolling()
          optionsRef.current?.onError?.(data.errorMessage ?? "操作失败")
          toast.error(data.errorMessage ?? "批量操作失败")
        } else {
          // 继续轮询
          pollTimerRef.current = setTimeout(() => pollProgress(taskId), POLL_INTERVAL)
        }
      } catch {
        stopPolling()
        setProgress((p) => ({ ...p, status: "failed", errorMessage: "网络错误" }))
      }
    },
    [stopPolling]
  )

  /** 执行批量操作 */
  const execute = useCallback(
    async (action: string, ids: string[], payload?: Record<string, unknown>) => {
      cancelledRef.current = false
      const isAsync = ids.length > SYNC_THRESHOLD

      setProgress({
        status: "running",
        current: 0,
        total: ids.length,
        percentage: 0
      })

      try {
        const res = await fetch(`${entity.apiPath}/batch/${action}`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ ids, ...payload })
        })
        const json = await res.json()

        if (isAsync && json.data?.taskId) {
          // 异步模式：开始轮询
          const taskId = json.data.taskId as string
          setProgress((p) => ({ ...p, taskId }))
          pollTimerRef.current = setTimeout(() => pollProgress(taskId), POLL_INTERVAL)
        } else {
          // 同步模式：直接完成
          const success = json.data?.success ?? ids.length
          const failed = json.data?.failed ?? 0
          setProgress({
            status: "completed",
            current: success,
            total: ids.length,
            percentage: 100
          })
          optionsRef.current?.onSuccess?.({ success, failed })
          toast.success(`操作完成：成功 ${success} 条${failed > 0 ? `，失败 ${failed} 条` : ""}`)
        }
      } catch (_err) {
        setProgress((p) => ({ ...p, status: "failed", errorMessage: "请求失败" }))
        optionsRef.current?.onError?.("请求失败")
        toast.error("批量操作失败")
      }
    },
    [entity.apiPath, pollProgress]
  )

  /** 取消异步任务 */
  const cancel = useCallback(async () => {
    cancelledRef.current = true
    stopPolling()
    // 从最新 progress 中获取 taskId（通过 setState 回调读取）
    setProgress((p) => {
      if (p.taskId) {
        fetch(`/api/tasks/${p.taskId}/cancel`, { method: "POST" }).catch(() => {})
      }
      return { ...p, status: "cancelled" }
    })
  }, [stopPolling])

  /** 重置状态 */
  const reset = useCallback(() => {
    stopPolling()
    cancelledRef.current = false
    setProgress({ status: "idle", current: 0, total: 0, percentage: 0 })
  }, [stopPolling])

  return { execute, progress, cancel, reset }
}
