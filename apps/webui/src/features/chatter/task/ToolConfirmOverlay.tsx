"use client"

/** ToolConfirmOverlay——基于公开任务事件展示持久化 HITL 确认。 @author AaronZZH & Kiro */

import { useMutation, useQueries, useQueryClient } from "@tanstack/react-query"
import { CheckIcon, XIcon } from "lucide-react"
import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  type AafAiTaskEvent,
  type DelegatedTaskVO,
  delegatedTaskApi,
  delegatedTaskKeys,
  type HumanApprovalDecisionRequest,
  humanApprovalApi
} from "@/lib/api/rest/ai"

interface PendingApproval {
  approvalId: string
  taskId: string
  action: string
}
function text(data: AafAiTaskEvent["data"], key: string): string | null {
  const value = data[key]
  return typeof value === "string" && value.length > 0 ? value : null
}
function findPendingApproval(events: AafAiTaskEvent[]): PendingApproval | null {
  const resolved = new Set(
    events
      .filter(
        (event) =>
          event.type === "aaf.authorization.granted" || event.type === "aaf.authorization.denied"
      )
      .map((event) => text(event.data, "approvalId"))
      .filter((value): value is string => value !== null)
  )
  const requests = events
    .filter(
      (event) =>
        event.type === "aaf.authorization.requested" && event.status === "AWAITING_AUTHORIZATION"
    )
    .toSorted((left, right) => (right.eventOffset ?? 0) - (left.eventOffset ?? 0))
  for (const event of requests) {
    const approvalId = text(event.data, "approvalId")
    if (approvalId && !resolved.has(approvalId))
      return {
        approvalId,
        taskId: event.taskId,
        action: text(event.data, "action") ?? text(event.data, "toolName") ?? "受控工具操作"
      }
  }
  return null
}
function ToolConfirmPanel({
  approval,
  loading,
  onDecision
}: {
  approval: PendingApproval
  loading: boolean
  onDecision: (decision: HumanApprovalDecisionRequest["decision"]) => void
}) {
  return (
    <div className="mx-3 mb-2 rounded-lg border bg-muted/30 p-3">
      <p className="mb-2 font-medium text-sm">AI 请求执行受控操作，需要您确认：</p>
      <div className="mb-3 flex flex-wrap gap-2">
        <Badge variant="secondary">{approval.action}</Badge>
      </div>
      <p className="mb-3 truncate text-muted-foreground text-xs" title={approval.taskId}>
        任务: {approval.taskId}
      </p>
      <div className="flex gap-2">
        <Button size="sm" disabled={loading} onClick={() => onDecision("APPROVED")}>
          <CheckIcon data-icon="inline-start" />
          确认执行
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={loading}
          onClick={() => onDecision("REJECTED")}
        >
          <XIcon data-icon="inline-start" />
          拒绝
        </Button>
      </div>
    </div>
  )
}
export function ToolConfirmOverlay({ tasks }: { tasks: DelegatedTaskVO[] }) {
  const queryClient = useQueryClient()
  const [handledApprovalId, setHandledApprovalId] = useState<string | null>(null)
  const waitingTasks = tasks.filter((task) => task.status === "AWAITING_AUTHORIZATION")
  const queries = useQueries({
    queries: waitingTasks.map((task) => ({
      queryKey: delegatedTaskKeys.events(task.taskId),
      queryFn: () => delegatedTaskApi.listEvents(task.taskId),
      refetchInterval: 3000
    }))
  })
  const pending = findPendingApproval(queries.flatMap((query) => query.data?.events ?? []))
  const decision = useMutation({
    mutationFn: (request: {
      approvalId: string
      decision: HumanApprovalDecisionRequest["decision"]
    }) =>
      humanApprovalApi.decide(request.approvalId, {
        decision: request.decision,
        reason: request.decision === "REJECTED" ? "用户拒绝了工具授权" : undefined
      }),
    onSuccess: (approval) => {
      setHandledApprovalId(approval.approvalId)
      queryClient.invalidateQueries({ queryKey: delegatedTaskKeys.all })
    }
  })
  return pending && pending.approvalId !== handledApprovalId ? (
    <ToolConfirmPanel
      approval={pending}
      loading={decision.isPending}
      onDecision={(value) => decision.mutate({ approvalId: pending.approvalId, decision: value })}
    />
  ) : null
}
