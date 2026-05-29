/**
 * TaskBoardPanel——子任务进度可视化面板
 * 展示当前会话的子任务列表、整体进度条、依赖关系和结果摘要
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronDown, ChevronRight } from "lucide-react"
import { useState } from "react"
import { Progress, ProgressLabel, ProgressValue } from "@/components/ui/progress"
import type { SubTask } from "@/lib/api/task-board"

/** 状态图标映射 */
const STATUS_ICON: Record<SubTask["status"], string> = {
  PENDING: "⏳",
  RUNNING: "🔄",
  DONE: "✅",
  FAILED: "❌"
}

/** 状态文本映射 */
const STATUS_TEXT: Record<SubTask["status"], string> = {
  PENDING: "等待中",
  RUNNING: "执行中...",
  DONE: "已完成",
  FAILED: "失败"
}

interface TaskBoardPanelProps {
  tasks: SubTask[]
  progress: { total: number; done: number; failed: number; running: number }
  /** 所有任务的 id→description 映射，用于展示依赖名称 */
  isLoading?: boolean
}

/** 单个子任务行 */
function TaskItem({ task, taskMap }: { task: SubTask; taskMap: Map<string, string> }) {
  const [expanded, setExpanded] = useState(false)
  const hasDeps = task.dependsOn.length > 0
  const hasResult = task.status === "DONE" && task.result

  return (
    <div className="border-border/50 border-b py-2 last:border-b-0">
      <div className="flex items-start gap-2">
        <span className="shrink-0 text-sm">{STATUS_ICON[task.status]}</span>
        <div className="min-w-0 flex-1">
          <p className="text-sm leading-tight">{task.description}</p>
          {/* 依赖关系 */}
          {hasDeps && task.status === "PENDING" && (
            <p className="mt-0.5 text-muted-foreground text-xs">
              等待: {task.dependsOn.map((id) => taskMap.get(id) ?? id).join(", ")}
            </p>
          )}
          {/* 执行中状态 */}
          {task.status === "RUNNING" && (
            <p className="mt-0.5 text-muted-foreground text-xs">{STATUS_TEXT.RUNNING}</p>
          )}
          {/* 失败状态 */}
          {task.status === "FAILED" && task.result && (
            <p className="mt-0.5 text-destructive text-xs">{task.result}</p>
          )}
        </div>
        {/* 结果折叠按钮 */}
        {hasResult && (
          <button
            type="button"
            onClick={() => setExpanded(!expanded)}
            className="shrink-0 rounded p-0.5 text-muted-foreground hover:bg-muted"
            aria-label={expanded ? "收起结果" : "展开结果"}
          >
            {expanded ? (
              <ChevronDown className="size-3.5" />
            ) : (
              <ChevronRight className="size-3.5" />
            )}
          </button>
        )}
      </div>
      {/* 结果摘要 */}
      {expanded && hasResult && (
        <p className="mt-1 ml-6 rounded bg-muted/50 px-2 py-1 text-muted-foreground text-xs">
          {task.result}
        </p>
      )}
    </div>
  )
}

export function TaskBoardPanel({ tasks, progress, isLoading }: TaskBoardPanelProps) {
  if (isLoading || tasks.length === 0) return null

  const percent = progress.total > 0 ? Math.round((progress.done / progress.total) * 100) : 0
  const taskMap = new Map(tasks.map((t) => [t.id, t.description]))

  return (
    <div className="border-border/50 border-t bg-muted/30 px-3 py-2">
      {/* 进度头部 */}
      <Progress value={percent} className="mb-2">
        <ProgressLabel className="text-xs">
          📋 任务进度 {progress.done}/{progress.total} 完成
        </ProgressLabel>
        <ProgressValue className="text-xs" />
      </Progress>

      {/* 任务列表 */}
      <div className="max-h-48 overflow-y-auto">
        {tasks.map((task) => (
          <TaskItem key={task.id} task={task} taskMap={taskMap} />
        ))}
      </div>
    </div>
  )
}
