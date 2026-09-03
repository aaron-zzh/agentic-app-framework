/**
 * SubTaskActivityList——多智能体协作子任务活动卡片列表
 * 读取 agent-run-store 的 subTaskActivities（AG-UI ActivitySnapshot/ActivityDelta 投影，
 * activityType="SUBTASK"），在对话消息流里展示协调者拆分的每个子任务的实时进度
 * @author AaronZZH & Kiro
 */
"use client"

import { CheckCircle2, CircleDashed, Loader2, XCircle } from "lucide-react"
import { useAgentRunStore } from "../runtime/agent-run-store"

const KIND_LABEL: Record<string, string> = {
  COORDINATOR: "协调者",
  EXECUTOR: "执行者",
  EVALUATOR: "评估者",
  AGGREGATOR: "聚合者"
}

const STATUS_ICON: Record<string, typeof Loader2> = {
  RUNNING: Loader2,
  COMPLETED: CheckCircle2,
  FAILED: XCircle,
  CANCELED: XCircle
}

export function SubTaskActivityList() {
  const activities = useAgentRunStore((s) => s.subTaskActivities)
  const list = Object.values(activities)

  if (list.length === 0) return null

  return (
    <div className="flex flex-col gap-1.5 border-t px-3 py-2">
      {list.map((activity) => {
        const Icon = STATUS_ICON[activity.status] ?? CircleDashed
        const spinning = activity.status === "RUNNING"
        return (
          <div
            key={activity.subTaskId}
            className="flex items-center gap-2 text-muted-foreground text-xs"
          >
            <Icon className={`size-3 ${spinning ? "animate-spin" : ""}`} />
            <span className="font-medium">{KIND_LABEL[activity.kind] ?? activity.kind}</span>
            {activity.roleKey && <span className="truncate">· {activity.roleKey}</span>}
            <span className="ml-auto">{activity.status}</span>
          </div>
        )
      })}
    </div>
  )
}
