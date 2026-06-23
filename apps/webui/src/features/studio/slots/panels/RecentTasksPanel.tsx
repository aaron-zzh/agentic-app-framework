/**
 * 任务进度面板——挂载时加载最近 5 条任务，同时监听 AIGC SSE 流实时更新
 * @author AaronZZH & Kiro
 */

"use client"

import { CheckCircle2, Loader2, Trash2, XCircle } from "lucide-react"
import Link from "next/link"
import { useCallback, useEffect, useState } from "react"
import { request } from "@/lib/api/rest/crud/client"
import type { PageResult } from "@/lib/api/types"
import { type AigcTaskEvent, useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"
import { useCancelAigcTask } from "@/lib/queries/use-cancel-aigc-task"

export function RecentTasksPanel() {
  const [tasks, setTasks] = useState<AigcTaskEvent[]>([])
  const [loading, setLoading] = useState(true)
  const deleteTask = useCancelAigcTask()

  // 挂载时拉最近 5 条历史任务
  useEffect(() => {
    request<PageResult<AigcTaskEvent>>("/aigc/tasks?pageNo=1&pageSize=5")
      .then((res) => setTasks(res.list ?? []))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  useAigcTaskStream({
    onCreated: useCallback((task: AigcTaskEvent) => {
      setTasks((prev) => [task, ...prev.filter((t) => t.id !== task.id)].slice(0, 5))
    }, []),
    onProgress: useCallback((task: AigcTaskEvent) => {
      setTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
    }, []),
    onCompleted: useCallback((task: AigcTaskEvent) => {
      setTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
    }, []),
    onFailed: useCallback((task: AigcTaskEvent) => {
      setTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
    }, [])
  })

  if (loading) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-2 text-center">
        <Loader2 className="size-5 animate-spin opacity-40" />
        <p className="text-muted-foreground text-xs">加载中...</p>
      </div>
    )
  }

  if (tasks.length === 0) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-2 text-center">
        <p className="text-muted-foreground text-xs">暂无任务</p>
        <Link
          href="/studio/assets/history"
          className="text-primary text-xs underline-offset-2 hover:underline"
        >
          查看历史任务
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-1.5">
      {tasks.map((t) => (
        <div
          key={t.id}
          className="group flex items-center gap-2 rounded border border-foreground/[0.06] bg-foreground/[0.02] px-2 py-1.5"
        >
          {t.status === "SUCCESS" ? (
            <CheckCircle2 className="size-3.5 shrink-0 text-emerald-400" />
          ) : t.status === "FAIL" ? (
            <XCircle className="size-3.5 shrink-0 text-rose-400" />
          ) : (
            <Loader2 className="size-3.5 shrink-0 animate-spin text-violet-400" />
          )}
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-1.5">
              <span className="rounded bg-foreground/[0.06] px-1 text-[10px]">{t.type}</span>
              <span className="truncate text-xs">{t.prompt || "—"}</span>
            </div>
            <p className="truncate text-[10px] text-muted-foreground">
              {t.status} · {t.model || t.provider}
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-1">
            {t.status === "SUCCESS" && (
              <Link
                href="/studio/assets/history"
                className="text-[10px] text-primary underline-offset-2 hover:underline"
              >
                查看
              </Link>
            )}
            <button
              type="button"
              onClick={() =>
                deleteTask.mutate(t.id, {
                  onSuccess: () => setTasks((prev) => prev.filter((x) => x.id !== t.id))
                })
              }
              disabled={deleteTask.isPending}
              className="rounded p-0.5 text-muted-foreground opacity-0 transition-opacity hover:text-rose-400 group-hover:opacity-100"
              aria-label="删除任务"
            >
              <Trash2 className="size-3" />
            </button>
          </div>
        </div>
      ))}
    </div>
  )
}
