/**
 * WorkflowPanel——审批工作流面板（状态 + 操作按钮 + 时间线）
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Separator } from "@/components/ui/separator"
import { Textarea } from "@/components/ui/textarea"
import {
  useWorkflowComplete,
  useWorkflowHistory,
  useWorkflowReject,
  useWorkflowStart,
  useWorkflowStatus
} from "@/lib/queries/use-workflow"

interface WorkflowPanelProps {
  processInstanceId?: string
  entityType: string
  entityId: string
  currentUserId: string
}

/** 状态文本映射 */
const STATUS_MAP: Record<
  string,
  { label: string; variant: "default" | "secondary" | "destructive" }
> = {
  running: { label: "审批中", variant: "default" },
  completed: { label: "已通过", variant: "secondary" },
  rejected: { label: "已驳回", variant: "destructive" }
}

export function WorkflowPanel({
  processInstanceId,
  entityType,
  entityId,
  currentUserId
}: WorkflowPanelProps) {
  const { data: status } = useWorkflowStatus(processInstanceId)
  const { data: history } = useWorkflowHistory(processInstanceId)
  const startMutation = useWorkflowStart()
  const completeMutation = useWorkflowComplete(processInstanceId)
  const rejectMutation = useWorkflowReject(processInstanceId)

  const [dialogType, setDialogType] = useState<"complete" | "reject" | null>(null)
  const [comment, setComment] = useState("")

  /** 当前用户是否为审批人 */
  const isAssignee = status?.currentTask?.assignee === currentUserId

  /** 提交审批 */
  function handleStart() {
    startMutation.mutate({ entityType, entityId, assignee: currentUserId })
  }

  /** 确认操作 */
  function handleConfirm() {
    if (!status?.currentTask || !dialogType) return
    const taskId = status.currentTask.taskId
    if (dialogType === "complete") {
      completeMutation.mutate({ taskId, comment }, { onSuccess: () => closeDialog() })
    } else {
      rejectMutation.mutate({ taskId, comment }, { onSuccess: () => closeDialog() })
    }
  }

  function closeDialog() {
    setDialogType(null)
    setComment("")
  }

  return (
    <div className="space-y-4">
      {/* 状态栏 + 操作按钮 */}
      <div className="flex items-center gap-3">
        {!processInstanceId ? (
          <Button onClick={handleStart} disabled={startMutation.isPending}>
            提交审批
          </Button>
        ) : (
          <>
            {status && (
              <Badge variant={STATUS_MAP[status.status]?.variant ?? "default"}>
                {STATUS_MAP[status.status]?.label ?? status.status}
              </Badge>
            )}
            {isAssignee && status?.status === "running" && (
              <>
                <Button size="sm" onClick={() => setDialogType("complete")}>
                  审批通过
                </Button>
                <Button size="sm" variant="outline" onClick={() => setDialogType("reject")}>
                  驳回
                </Button>
              </>
            )}
          </>
        )}
      </div>

      {/* 审批时间线 */}
      {history && history.length > 0 && (
        <>
          <Separator />
          <div className="space-y-3">
            <h4 className="font-medium text-sm">审批记录</h4>
            <div className="relative ml-3 border-border border-l pl-6">
              {history.map((item, idx) => (
                <div key={`${item.endTime}-${idx}`} className="relative pb-4 last:pb-0">
                  {/* 时间线节点 */}
                  <div className="absolute top-0.5 -left-[calc(0.75rem+1px)]">
                    <Avatar className="h-6 w-6">
                      <AvatarFallback className="text-[10px]">
                        {item.assignee?.slice(0, 1)?.toUpperCase() ?? "?"}
                      </AvatarFallback>
                    </Avatar>
                  </div>
                  {/* 内容 */}
                  <div className="text-sm">
                    <span className="font-medium">{item.assignee}</span>
                    <span className="ml-2 text-muted-foreground">{item.action}</span>
                    {item.comment && <p className="mt-0.5 text-muted-foreground">{item.comment}</p>}
                    <p className="mt-0.5 text-muted-foreground text-xs">{item.endTime}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      {/* 确认对话框 */}
      <Dialog
        open={dialogType !== null}
        onOpenChange={(open) => {
          if (!open) closeDialog()
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{dialogType === "complete" ? "审批通过" : "驳回"}</DialogTitle>
          </DialogHeader>
          <Textarea
            placeholder="请输入审批意见"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
          />
          <DialogFooter>
            <Button variant="outline" onClick={closeDialog}>
              取消
            </Button>
            <Button
              onClick={handleConfirm}
              disabled={completeMutation.isPending || rejectMutation.isPending}
            >
              确认
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
