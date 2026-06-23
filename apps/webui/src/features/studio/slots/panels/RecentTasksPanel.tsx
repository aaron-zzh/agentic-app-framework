/**
 * 任务进度面板——监听 AIGC SSE 流，列出最近 5 个任务
 * @author AaronZZH & Kiro
 */

"use client"

import { CheckCircle2, Loader2, XCircle } from "lucide-react"
import Link from "next/link"
import { useCallback, useState } from "react"
import { type AigcTaskEvent, useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"

export function RecentTasksPanel() {
  const [tasks, setTasks] = useState<AigcTaskEvent[]>([])

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

  if (tasks.length === 0) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-2 text-center">
        <Loader2 className="size-5 animate-spin opacity-40" />
        <p className="text-muted-foreground text-xs">监听任务事件中...</p>
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
          className="flex items-center gap-2 rounded border border-foreground/[0.06] bg-foreground/[0.02] px-2 py-1.5"
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
        </div>
      ))}
    </div>
  )
}
