/**
 * 运行实况画布顶部居中的任务切换条：任务名称 + 状态点，点击切换画布上展示的任务流程。
 * @author AaronZZH & Kiro
 */

"use client"

import { cn } from "@/lib/utils/cn"
import type { RunTaskStatus, RunTaskSummary } from "./types"

const STATUS_DOT: Record<RunTaskStatus, string> = {
  running: "bg-indigo-400 shadow-[0_0_6px_rgba(129,140,248,0.9)] animate-pulse",
  awaiting: "bg-amber-400 shadow-[0_0_6px_rgba(251,191,36,0.9)]",
  failed: "bg-rose-400",
  completed: "bg-emerald-400"
}

const STATUS_LABELS: Record<RunTaskStatus, string> = {
  running: "运行中",
  awaiting: "待审批",
  failed: "已驳回",
  completed: "已完成"
}

interface RunTaskSwitcherProps {
  tasks: RunTaskSummary[]
  activeTaskId: string
  onSelect: (taskId: string) => void
}

export function RunTaskSwitcher({ tasks, activeTaskId, onSelect }: RunTaskSwitcherProps) {
  return (
    <div
      role="tablist"
      aria-label="运行中的任务"
      className="pointer-events-auto absolute top-0 left-1/2 flex max-w-full -translate-x-1/2 gap-1 overflow-x-auto rounded-full border border-foreground/10 bg-background/50 p-1 backdrop-blur-md"
    >
      {tasks.map((task) => {
        const active = task.id === activeTaskId
        return (
          <button
            key={task.id}
            type="button"
            role="tab"
            aria-selected={active}
            title={`${task.name}（${STATUS_LABELS[task.status]}）`}
            onClick={() => onSelect(task.id)}
            className={cn(
              "flex shrink-0 items-center gap-1.5 rounded-full px-3 py-1 text-xs outline-none transition-colors focus-visible:ring-2 focus-visible:ring-primary",
              active
                ? "bg-primary/20 text-foreground"
                : "text-muted-foreground hover:bg-foreground/[0.06] hover:text-foreground"
            )}
          >
            <span className={cn("size-1.5 rounded-full", STATUS_DOT[task.status])} />
            <span className="max-w-40 truncate">{task.name}</span>
          </button>
        )
      })}
    </div>
  )
}
