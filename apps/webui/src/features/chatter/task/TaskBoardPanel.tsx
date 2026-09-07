/**
 * TaskBoardPanel——当前对话的委托任务进度面板。
 * @author AaronZZH & Kiro
 */

"use client"

import {
  Ban,
  CircleCheckBig,
  CircleDashed,
  CircleX,
  LoaderCircle,
  type LucideIcon,
  MessageCircleQuestion,
  PauseCircle,
  ShieldAlert
} from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Progress, ProgressLabel, ProgressValue } from "@/components/ui/progress"
import { ScrollArea } from "@/components/ui/scroll-area"
import type { TaskBoardProgress } from "@/features/chatter/hooks/use-task-board"
import { ExecutorPlanSummary } from "@/features/chatter/task/ExecutorPlanSummary"
import type { DelegatedTaskStatus, DelegatedTaskVO } from "@/lib/api/rest/ai"
import { cn } from "@/lib/utils"

interface StatusMeta {
  icon: LucideIcon
  label: string
}

const STATUS_META: Record<DelegatedTaskStatus, StatusMeta> = {
  PENDING: { icon: CircleDashed, label: "等待中" },
  RUNNING: { icon: LoaderCircle, label: "执行中" },
  PAUSED: { icon: PauseCircle, label: "已暂停" },
  AWAITING_AUTHORIZATION: { icon: ShieldAlert, label: "等待授权" },
  AWAITING_CLARIFICATION: { icon: MessageCircleQuestion, label: "等待澄清" },
  COMPLETED: { icon: CircleCheckBig, label: "已完成" },
  CANCELED: { icon: Ban, label: "已取消" },
  FAILED: { icon: CircleX, label: "失败" }
}

interface TaskBoardPanelProps {
  tasks: DelegatedTaskVO[]
  progress: TaskBoardProgress
  isLoading?: boolean
}

function TaskItem({ task }: { task: DelegatedTaskVO }) {
  const meta = STATUS_META[task.status]
  const StatusIcon = meta.icon

  return (
    <div className="flex items-start gap-2 border-border/50 border-b py-2 last:border-b-0">
      <StatusIcon
        className={cn("size-4 shrink-0", task.status === "RUNNING" && "animate-spin")}
        aria-hidden="true"
      />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm leading-tight" title={task.taskId}>
          任务 {task.taskId}
        </p>
        <p className="mt-0.5 text-muted-foreground text-xs">
          {meta.label} · 优先级 {task.priority} · {task.ownerKind}
        </p>
        <ExecutorPlanSummary taskId={task.taskId} taskStatus={task.status} />
      </div>
      <Badge variant="outline">{meta.label}</Badge>
    </div>
  )
}

export function TaskBoardPanel({ tasks, progress, isLoading }: TaskBoardPanelProps) {
  if (isLoading || tasks.length === 0) return null

  const completed = progress.byStatus.COMPLETED
  const percent = progress.total > 0 ? Math.round((completed / progress.total) * 100) : 0
  const statusGroups = Object.entries(progress.byStatus).filter(([, count]) => count > 0) as [
    DelegatedTaskStatus,
    number
  ][]

  return (
    <div className="border-border/50 border-t bg-muted/30 px-3 py-2">
      <Progress value={percent} className="mb-2">
        <ProgressLabel className="text-xs">
          任务进度 {completed}/{progress.total} 完成
        </ProgressLabel>
        <ProgressValue className="text-xs" />
      </Progress>

      <div className="mb-1 flex flex-wrap gap-1">
        {statusGroups.map(([status, count]) => (
          <Badge key={status} variant="secondary">
            {STATUS_META[status].label} {count}
          </Badge>
        ))}
      </div>

      <ScrollArea className="h-48">
        <div className="pr-2">
          {tasks.map((task) => (
            <TaskItem key={task.taskId} task={task} />
          ))}
        </div>
      </ScrollArea>
    </div>
  )
}
