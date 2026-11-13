/**
 * TaskApprovalCard——持久任务等待授权时的审批卡片，只在任务弹窗中渲染。
 *
 * 当前对话流内的审批由 ToolConfirmOverlay 处理；这里是用户主动打开任务弹窗时的处理入口（含无实时流的后台任务），
 * 按任务查询待处理授权，用户在卡片上批准或拒绝；决定通过 AG-UI resume 提交，服务端恢复发起审批的节点。
 *
 * @example
 * <TaskApprovalCard task={task} />
 * @author AaronZZH & Kiro
 */

"use client"

import { useQuery, useQueryClient } from "@tanstack/react-query"
import { ShieldAlert } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { resumeAssistantAgUi } from "@/lib/api/assistant-agui"
import {
  chatKeys,
  type TaskDetails,
  type TaskPendingApproval,
  taskApi,
  taskKeys
} from "@/lib/api/rest/ai"

function ApprovalItem({
  approval,
  submitting,
  onDecision
}: {
  approval: TaskPendingApproval
  submitting: boolean
  onDecision: (approved: boolean) => void
}) {
  return (
    <li className="flex flex-col gap-2 rounded-md border border-amber-500/40 bg-amber-500/5 p-2">
      <div className="flex flex-wrap items-center gap-1.5">
        <ShieldAlert className="size-4 text-amber-600" />
        <span className="font-medium text-sm">AI 请求执行受控操作</span>
        <Badge variant="outline" className="font-mono">
          {approval.action}
        </Badge>
        <Badge variant="secondary">{approval.reversible ? "可撤销" : "不可撤销"}</Badge>
      </div>
      <p className="text-xs">{approval.reason}</p>
      <p className="text-muted-foreground text-xs">影响：{approval.impact}</p>
      <div className="flex gap-2">
        <Button type="button" size="sm" disabled={submitting} onClick={() => onDecision(true)}>
          批准执行
        </Button>
        <Button
          type="button"
          size="sm"
          variant="outline"
          disabled={submitting}
          onClick={() => onDecision(false)}
        >
          拒绝
        </Button>
      </div>
    </li>
  )
}

export function TaskApprovalCard({ task }: { task: TaskDetails }) {
  const queryClient = useQueryClient()
  const [submittingId, setSubmittingId] = useState<string | null>(null)
  const awaiting = task.status === "AWAITING_AUTHORIZATION"
  const { data: approvals = [] } = useQuery({
    queryKey: taskKeys.approvals(task.taskId),
    queryFn: () => taskApi.approvals(task.taskId),
    enabled: awaiting
  })
  // 只在任务弹窗中渲染（见 TaskPanel）：弹窗是用户主动打开的处理入口，未审批的请求都应可操作；
  // 当前对话流里的同一审批由 ToolConfirmOverlay 处理，审批后任务查询刷新，这里随之消失
  const pendingApprovals = approvals

  if (!awaiting || pendingApprovals.length === 0) return null

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: taskKeys.all })
    void queryClient.invalidateQueries({ queryKey: chatKeys.messages(task.conversationId) })
  }

  const decide = (approval: TaskPendingApproval, approved: boolean) => {
    setSubmittingId(approval.approvalId)
    // 决定落库后服务端在后台恢复节点；这里只等待恢复流结束，任务进度由任务查询刷新
    void resumeAssistantAgUi(
      { threadId: task.conversationId, approvalId: approval.approvalId, approved },
      {
        onDone: () => {
          setSubmittingId(null)
          refresh()
        },
        onError: (error) => {
          setSubmittingId(null)
          toast.error(`提交授权决定失败：${error.message}`)
          refresh()
        }
      }
    )
    toast.success(approved ? "已提交批准，任务将继续执行" : "已提交拒绝，该操作不会执行")
  }

  return (
    <ul className="mt-2 flex flex-col gap-2" aria-label="待处理授权">
      {pendingApprovals.map((approval) => (
        <ApprovalItem
          key={approval.approvalId}
          approval={approval}
          submitting={submittingId === approval.approvalId}
          onDecision={(approved) => decide(approval, approved)}
        />
      ))}
    </ul>
  )
}
