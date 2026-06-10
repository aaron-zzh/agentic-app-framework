/**
 * useExportProgress——SSE 导出进度推送 Hook
 * @author AaronZZH & Kiro
 *
 * 场景：大数据量导出等耗时操作，后端通过 SSE 推送进度
 * 后端端点：GET /api/{entity}/export-progress/{taskId}（text/event-stream）
 *
 * 用法：
 * ```tsx
 * const { startExport, progress, cancel } = useExportProgress(entity)
 * await startExport({ format: 'csv', fields: ['name', 'status'] })
 * ```
 */

import { useCallback, useRef, useState } from "react"
import { toast } from "sonner"
import { buildSseUrl } from "@/lib/api/config"
import { useAuthStore } from "@/lib/store/auth-store"

import type { EntityDef } from "@/lib/types/entity"

export type ExportStatus = "idle" | "pending" | "running" | "completed" | "failed"

export interface ExportProgress {
  status: ExportStatus
  current: number
  total: number
  percentage: number
  taskId?: string
  downloadUrl?: string
  errorMessage?: string
}

interface ExportOptions {
  format: "csv" | "xlsx" | "pdf"
  fields?: string[]
}

/** SSE 导出进度 Hook */
export function useExportProgress(entity: EntityDef) {
  const [progress, setProgress] = useState<ExportProgress>({
    status: "idle",
    current: 0,
    total: 0,
    percentage: 0
  })
  const esRef = useRef<EventSource | null>(null)

  const closeSSE = useCallback(() => {
    if (esRef.current) {
      esRef.current.close()
      esRef.current = null
    }
  }, [])

  /** 开始导出（触发后端任务，然后通过 SSE 接收进度） */
  const startExport = useCallback(
    async (opts: ExportOptions) => {
      setProgress({ status: "pending", current: 0, total: 0, percentage: 0 })

      try {
        // 1. 发起导出任务，获取 taskId
        const params = new URLSearchParams({
          format: opts.format,
          ...(opts.fields ? { fields: opts.fields.join(",") } : {})
        })
        const res = await fetch(`${entity.apiPath}/export?${params}`)
        const json = await res.json()
        const taskId = json.data?.taskId as string | undefined

        if (!taskId) {
          // 小数据量：直接下载
          const url = json.data?.downloadUrl as string | undefined
          if (url) {
            triggerDownload(url, `${entity.slug}.${opts.format}`)
            setProgress({
              status: "completed",
              current: 0,
              total: 0,
              percentage: 100,
              downloadUrl: url
            })
            toast.success("导出完成")
          }
          return
        }

        // 2. 建立 SSE 连接接收进度
        setProgress((p) => ({ ...p, status: "running", taskId }))
        const es = new EventSource(
          buildSseUrl(
            `${entity.apiPath}/export-progress/${taskId}`,
            useAuthStore.getState().accessToken
          )
        )
        esRef.current = es

        es.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data) as {
              taskId: string
              current: number
              total: number
              percentage: number
              status: string
              errorMessage?: string
              downloadUrl?: string
            }

            const newProgress: ExportProgress = {
              status: (data.status?.toLowerCase() as ExportStatus) ?? "running",
              current: data.current,
              total: data.total,
              percentage: data.percentage,
              taskId: data.taskId,
              downloadUrl: data.downloadUrl,
              errorMessage: data.errorMessage
            }
            setProgress(newProgress)

            if (newProgress.status === "completed") {
              closeSSE()
              if (newProgress.downloadUrl) {
                triggerDownload(newProgress.downloadUrl, `${entity.slug}.${opts.format}`)
              }
              toast.success("导出完成")
            } else if (newProgress.status === "failed") {
              closeSSE()
              toast.error(newProgress.errorMessage ?? "导出失败")
            }
          } catch {
            // 忽略解析错误
          }
        }

        es.onerror = () => {
          closeSSE()
          setProgress((p) => ({
            ...p,
            status: "failed",
            errorMessage: "连接中断"
          }))
          toast.error("导出进度连接中断")
        }
      } catch {
        setProgress({
          status: "failed",
          current: 0,
          total: 0,
          percentage: 0,
          errorMessage: "请求失败"
        })
        toast.error("导出请求失败")
      }
    },
    [entity, closeSSE]
  )

  /** 取消导出 */
  const cancel = useCallback(() => {
    closeSSE()
    // 通过 setState 回调读取最新 taskId，避免依赖 progress 对象引用
    setProgress((p) => {
      if (p.taskId) {
        fetch(`/api/tasks/${p.taskId}/cancel`, { method: "POST" }).catch(() => {})
      }
      return { ...p, status: "idle" }
    })
  }, [closeSSE])

  /** 重置 */
  const reset = useCallback(() => {
    closeSSE()
    setProgress({ status: "idle", current: 0, total: 0, percentage: 0 })
  }, [closeSSE])

  return { startExport, progress, cancel, reset }
}

/** 触发浏览器下载 */
function triggerDownload(url: string, filename: string) {
  const a = document.createElement("a")
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}
