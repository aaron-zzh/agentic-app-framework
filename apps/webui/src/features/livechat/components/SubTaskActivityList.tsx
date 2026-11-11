/**
 * SubTaskActivityList——多智能体协作子任务活动卡片列表
 * 读取 agent-run-store 的 subTaskActivities（AG-UI ActivitySnapshot/ActivityDelta 投影，
 * activityType="SUBTASK"），在对话消息流里展示协调者拆分的每个子智能体的角色与实时进度
 * @author AaronZZH & Kiro
 */
"use client"

import { CheckCircle2, CircleDashed, Loader2, Users, XCircle } from "lucide-react"
import { useAgentRunStore } from "../runtime/agent-run-store"
import { useRoleNames } from "./use-role-names"

const KIND_LABEL: Record<string, string> = {
  COORDINATOR: "协调者",
  EXECUTOR: "执行者",
  EVALUATOR: "评估者",
  AGGREGATOR: "聚合者"
}

const STATUS_LABEL: Record<string, string> = {
  RUNNING: "进行中",
  COMPLETED: "已完成",
  FAILED: "失败",
  CANCELED: "已取消"
}

const STATUS_ICON: Record<string, typeof Loader2> = {
  RUNNING: Loader2,
  COMPLETED: CheckCircle2,
  FAILED: XCircle,
  CANCELED: XCircle
}

export function SubTaskActivityList() {
  const activities = useAgentRunStore((s) => s.subTaskActivities)
  const roleName = useRoleNames()
  const list = Object.values(activities)

  if (list.length === 0) return null

  const agentCount = list.filter((activity) => activity.kind === "EXECUTOR").length

  return (
    <div className="flex flex-col gap-1.5 border-t px-3 py-2">
      <div className="flex items-center gap-1.5 font-medium text-foreground text-xs">
        <Users className="size-3" />
        <span>多智能体协作{agentCount > 0 ? `（${agentCount} 个执行者）` : ""}</span>
      </div>
      {list.map((activity) => {
        const Icon = STATUS_ICON[activity.status] ?? CircleDashed
        const spinning = activity.status === "RUNNING"
        const name = roleName(activity.roleKey) ?? activity.roleKey
        return (
          <div
            key={activity.subTaskId}
            className="flex items-center gap-2 text-muted-foreground text-xs"
          >
            <Icon className={`size-3 shrink-0 ${spinning ? "animate-spin" : ""}`} />
            <span className="shrink-0 font-medium">
              {KIND_LABEL[activity.kind] ?? activity.kind}
            </span>
            {name && <span className="min-w-0 truncate text-foreground">{name}</span>}
            <span className="ml-auto shrink-0">
              {STATUS_LABEL[activity.status] ?? activity.status}
            </span>
          </div>
        )
      })}
    </div>
  )
}
