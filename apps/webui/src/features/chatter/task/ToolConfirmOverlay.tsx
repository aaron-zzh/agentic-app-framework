/**
 * ToolConfirmOverlay——基于委托任务事件展示持久化 HITL 授权确认。
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation, useQueries, useQueryClient } from "@tanstack/react-query"
import { CheckIcon, XIcon } from "lucide-react"
import { useEffect, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  type DelegatedTaskEventVO,
  type DelegatedTaskVO,
  delegatedTaskApi,
  delegatedTaskKeys,
  getDelegatedTaskEventStreamUrl,
  type HumanApprovalDecisionRequest,
  humanApprovalApi,
  parseDelegatedTaskEvent
} from "@/lib/api/rest/ai"

interface PendingApproval {
  approvalId: string
  taskId: string
  action: string
  resource: string | null
  eventOffset: number
}

interface ToolConfirmPanelProps {
  approval: PendingApproval
  loading: boolean
  onDecision: (decision: HumanApprovalDecisionRequest["decision"]) => void
}

function payloadString(payload: Record<string, unknown>, key: string): string | null {
  const value = payload[key]
  return typeof value === "string" && value.length > 0 ? value : null
}

function findPendingApproval(events: DelegatedTaskEventVO[]): PendingApproval | null {
  const resolved = new Set(
    events
      .filter(
        (event) => event.type === "AUTHORIZATION_GRANTED" || event.type === "AUTHORIZATION_DENIED"
      )
      .map((event) => payloadString(event.payload, "approvalId"))
      .filter((approvalId): approvalId is string => approvalId !== null)
  )

  const requests = events
    .filter(
      (event) =>
        event.type === "AUTHORIZATION_REQUESTED" && event.status === "AWAITING_AUTHORIZATION"
    )
    .toSorted((left, right) => right.eventOffset - left.eventOffset)

  for (const event of requests) {
    const approvalId = payloadString(event.payload, "approvalId")
    if (!approvalId || resolved.has(approvalId)) continue
    return {
      approvalId,
      taskId: event.taskId,
      action:
        payloadString(event.payload, "action") ??
        payloadString(event.payload, "toolName") ??
        "受控工具操作",
      resource: payloadString(event.payload, "resource"),
      eventOffset: event.eventOffset
    }
  }

  return null
}

function ToolConfirmPanel({ approval, loading, onDecision }: ToolConfirmPanelProps) {
  return (
    <div className="mx-3 mb-2 rounded-lg border bg-muted/30 p-3">
      <p className="mb-2 font-medium text-sm">AI 请求执行受控操作，需要您确认：</p>
      <div className="mb-3 flex flex-wrap gap-2">
        <Badge variant="secondary">{approval.action}</Badge>
        {approval.resource ? <Badge variant="outline">资源: {approval.resource}</Badge> : null}
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

function MissingApprovalNotice({ taskId }: { taskId: string }) {
  return (
    <div className="mx-3 mb-2 rounded-lg border bg-muted/30 p-3">
      <p className="font-medium text-sm">任务正在等待人工授权</p>
      <p className="mt-1 text-muted-foreground text-xs">
        当前执行事件未提供可确认的 approvalId，暂无法在对话内处理，请前往任务中心查看。
      </p>
      <p className="mt-1 truncate text-muted-foreground text-xs" title={taskId}>
        任务: {taskId}
      </p>
    </div>
  )
}

interface ToolConfirmOverlayProps {
  tasks: DelegatedTaskVO[]
}

export function ToolConfirmOverlay({ tasks }: ToolConfirmOverlayProps) {
  const queryClient = useQueryClient()
  const [handledApprovalId, setHandledApprovalId] = useState<string | null>(null)
  const waitingTasks = tasks.filter((task) => task.status === "AWAITING_AUTHORIZATION")
  const waitingTaskKey = waitingTasks
    .map((task) => task.taskId)
    .toSorted()
    .join("|")
  const eventQueries = useQueries({
    queries: waitingTasks.map((task) => ({
      queryKey: delegatedTaskKeys.events(task.taskId),
      queryFn: () => delegatedTaskApi.listEvents(task.taskId),
      refetchInterval: 3000
    }))
  })

  useEffect(() => {
    if (!waitingTaskKey) return

    const taskIds = waitingTaskKey.split("|")
    const sources = taskIds.map((taskId) => {
      const source = new EventSource(getDelegatedTaskEventStreamUrl(taskId), {
        withCredentials: true
      })
      const handleEvent = (message: MessageEvent<string>) => {
        const event = parseDelegatedTaskEvent(message.data)
        if (!event) return
        queryClient.setQueryData<DelegatedTaskEventVO[]>(
          delegatedTaskKeys.events(taskId),
          (current = []) => {
            if (current.some((item) => item.eventId === event.eventId)) return current
            return [...current, event].toSorted(
              (left, right) => left.eventOffset - right.eventOffset
            )
          }
        )
      }
      source.addEventListener("AUTHORIZATION_REQUESTED", handleEvent)
      source.addEventListener("AUTHORIZATION_GRANTED", handleEvent)
      source.addEventListener("AUTHORIZATION_DENIED", handleEvent)
      return source
    })

    return () => {
      for (const source of sources) source.close()
    }
  }, [queryClient, waitingTaskKey])

  const events = eventQueries.flatMap((query) => query.data ?? [])
  const pendingApproval = findPendingApproval(events)
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

  if (pendingApproval && pendingApproval.approvalId !== handledApprovalId) {
    return (
      <ToolConfirmPanel
        approval={pendingApproval}
        loading={decision.isPending}
        onDecision={(value) =>
          decision.mutate({ approvalId: pendingApproval.approvalId, decision: value })
        }
      />
    )
  }

  const eventsLoaded = eventQueries.every((query) => !query.isLoading)
  if (!pendingApproval && eventsLoaded && waitingTasks.length > 0) {
    return <MissingApprovalNotice taskId={waitingTasks[0].taskId} />
  }

  return null
}
