/**
 * ExecutorPlanSummary——委托任务的执行计划步骤只读投影（AAF-114 #11409 任务摘要组件）。
 *
 * 只携带 canonical planId 与 revision，从权威服务端投影（DelegatedTaskController.executorPlans）
 * 轮询读取实时状态，不在客户端建立独立的计划生命周期状态或 durable Zustand 副本。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useQuery } from "@tanstack/react-query"
import { CheckCircle2, CircleDashed, Loader2, XCircle } from "lucide-react"
import type { DelegatedTaskStatus } from "@/lib/api/rest/ai"
import {
  delegatedTaskApi,
  type ExecutorPlanStepSummary,
  isDelegatedTaskTerminal
} from "@/lib/api/rest/ai"
import { cn } from "@/lib/utils"

const STEP_STATUS_ICON: Record<ExecutorPlanStepSummary["status"], typeof CheckCircle2> = {
  PENDING: CircleDashed,
  RUNNING: Loader2,
  COMPLETED: CheckCircle2,
  FAILED: XCircle,
  CANCELLED: XCircle
}

function StepRow({ step }: { step: ExecutorPlanStepSummary }) {
  const Icon = STEP_STATUS_ICON[step.status]
  return (
    <div className="flex items-center gap-1.5 py-0.5 text-xs">
      <Icon
        className={cn(
          "size-3 shrink-0",
          step.status === "RUNNING" && "animate-spin text-primary",
          step.status === "COMPLETED" && "text-emerald-600",
          step.status === "FAILED" && "text-destructive"
        )}
      />
      <span className="truncate text-muted-foreground">{step.title}</span>
    </div>
  )
}

/** 单个委托任务的执行计划摘要；只在任务未进入终态时轮询，避免对已完结任务持续请求。 */
export function ExecutorPlanSummary({
  taskId,
  taskStatus
}: {
  taskId: string
  taskStatus: DelegatedTaskStatus
}) {
  const { data: plans } = useQuery({
    queryKey: ["delegated-tasks", "executor-plans", taskId],
    queryFn: () => delegatedTaskApi.listExecutorPlans(taskId),
    enabled: !isDelegatedTaskTerminal(taskStatus),
    refetchInterval: 3000
  })

  if (!plans || plans.length === 0) return null

  return (
    <div className="mt-1 space-y-1.5 border-border/40 border-l pl-2">
      {plans.map((plan) => (
        <div key={`${plan.planId}-${plan.revision}`}>
          {plan.steps.map((step) => (
            <StepRow key={step.stepKey} step={step} />
          ))}
        </div>
      ))}
    </div>
  )
}
