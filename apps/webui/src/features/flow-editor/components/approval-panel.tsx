/**
 * 审批操作面板——操作按钮 + 意见输入 + 时间线 + 投票进度
 * @author Kiro
 */

"use client"

import { useId, useState } from "react"
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
import { Progress } from "@/components/ui/progress"
import { Separator } from "@/components/ui/separator"
import { Textarea } from "@/components/ui/textarea"
import type { ApprovalOperationType } from "@/lib/api/approval"
import {
  useAddSignAfter,
  useAddSignBefore,
  useApprovalTimeline,
  useTransferSign,
  useVoteProgress,
  useWithdraw
} from "@/lib/queries/use-approval"
import { useWorkflowComplete, useWorkflowReject } from "@/lib/queries/use-workflow"

interface ApprovalPanelProps {
  processInstanceId: string
  taskId?: string
  /** 当前用户是否为审批人 */
  isAssignee?: boolean
  /** 当前用户是否为发起人 */
  isInitiator?: boolean
  currentUserId: string
  /** 是否为会签场景 */
  showVoteProgress?: boolean
}

/** 操作类型标签映射 */
const OP_LABELS: Record<ApprovalOperationType, { label: string; color: string }> = {
  APPROVE: { label: "通过", color: "text-green-600" },
  REJECT: { label: "驳回", color: "text-red-600" },
  DELEGATE: { label: "委派", color: "text-blue-600" },
  ADD_SIGN: { label: "加签", color: "text-purple-600" },
  TRANSFER: { label: "转签", color: "text-orange-600" },
  WITHDRAW: { label: "撤回", color: "text-gray-600" }
}

type DialogAction =
  | "complete"
  | "reject"
  | "addSignBefore"
  | "addSignAfter"
  | "transfer"
  | "withdraw"
  | null

export function ApprovalPanel({
  processInstanceId,
  taskId,
  isAssignee = false,
  isInitiator = false,
  currentUserId,
  showVoteProgress = false
}: ApprovalPanelProps) {
  const { data: timeline } = useApprovalTimeline(processInstanceId)
  const { data: voteProgress } = useVoteProgress(showVoteProgress ? processInstanceId : undefined)

  const completeMutation = useWorkflowComplete(processInstanceId)
  const rejectMutation = useWorkflowReject(processInstanceId)
  const addSignBeforeMutation = useAddSignBefore()
  const addSignAfterMutation = useAddSignAfter()
  const transferMutation = useTransferSign()
  const withdrawMutation = useWithdraw()

  const formId = useId()
  const [dialogAction, setDialogAction] = useState<DialogAction>(null)
  const [comment, setComment] = useState("")
  const [targetUser, setTargetUser] = useState("")

  const isPending =
    completeMutation.isPending ||
    rejectMutation.isPending ||
    addSignBeforeMutation.isPending ||
    addSignAfterMutation.isPending ||
    transferMutation.isPending ||
    withdrawMutation.isPending

  function handleConfirm() {
    if (!dialogAction) return
    const onSuccess = () => closeDialog()

    switch (dialogAction) {
      case "complete":
        if (taskId) completeMutation.mutate({ taskId, comment }, { onSuccess })
        break
      case "reject":
        if (taskId) rejectMutation.mutate({ taskId, comment }, { onSuccess })
        break
      case "addSignBefore":
        if (taskId) addSignBeforeMutation.mutate({ taskId, assignee: targetUser }, { onSuccess })
        break
      case "addSignAfter":
        if (taskId) addSignAfterMutation.mutate({ taskId, assignee: targetUser }, { onSuccess })
        break
      case "transfer":
        if (taskId)
          transferMutation.mutate(
            { taskId, targetAssignee: targetUser, reason: comment },
            { onSuccess }
          )
        break
      case "withdraw":
        withdrawMutation.mutate({ processInstanceId, initiator: currentUserId }, { onSuccess })
        break
    }
  }

  function closeDialog() {
    setDialogAction(null)
    setComment("")
    setTargetUser("")
  }

  /** 对话框标题 */
  const dialogTitles: Record<string, string> = {
    complete: "审批通过",
    reject: "驳回",
    addSignBefore: "前加签",
    addSignAfter: "后加签",
    transfer: "转签",
    withdraw: "撤回"
  }

  /** 是否需要目标用户输入 */
  const needsTargetUser =
    dialogAction === "addSignBefore" ||
    dialogAction === "addSignAfter" ||
    dialogAction === "transfer"

  return (
    <div className="space-y-4">
      {/* 操作按钮区 */}
      <div className="flex flex-wrap gap-2">
        {isAssignee && taskId && (
          <>
            <Button size="sm" onClick={() => setDialogAction("complete")}>
              同意
            </Button>
            <Button size="sm" variant="destructive" onClick={() => setDialogAction("reject")}>
              拒绝
            </Button>
            <Button size="sm" variant="outline" onClick={() => setDialogAction("addSignBefore")}>
              前加签
            </Button>
            <Button size="sm" variant="outline" onClick={() => setDialogAction("addSignAfter")}>
              后加签
            </Button>
            <Button size="sm" variant="outline" onClick={() => setDialogAction("transfer")}>
              转签
            </Button>
          </>
        )}
        {isInitiator && (
          <Button size="sm" variant="ghost" onClick={() => setDialogAction("withdraw")}>
            撤回
          </Button>
        )}
      </div>

      {/* 投票进度（会签场景） */}
      {showVoteProgress && voteProgress && (
        <>
          <Separator />
          <div className="space-y-2">
            <h4 className="font-medium text-sm">投票进度</h4>
            <Progress value={(voteProgress.approved / voteProgress.total) * 100} className="h-2" />
            <div className="flex gap-3 text-muted-foreground text-xs">
              <span>已通过：{voteProgress.approved}</span>
              <span>已拒绝：{voteProgress.rejected}</span>
              <span>待审批：{voteProgress.pending}</span>
              <span>总计：{voteProgress.total}</span>
            </div>
          </div>
        </>
      )}

      {/* 审批时间线 */}
      {timeline && timeline.length > 0 && (
        <>
          <Separator />
          <div className="space-y-3">
            <h4 className="font-medium text-sm">审批记录</h4>
            <div className="relative ml-3 border-border border-l pl-6">
              {timeline.map((record) => (
                <div key={record.id} className="relative pb-4 last:pb-0">
                  <div className="absolute top-0.5 -left-[calc(0.75rem+1px)]">
                    <Avatar className="h-6 w-6">
                      <AvatarFallback className="text-[10px]">
                        {record.assignee?.slice(0, 1)?.toUpperCase() ?? "?"}
                      </AvatarFallback>
                    </Avatar>
                  </div>
                  <div className="text-sm">
                    <span className="font-medium">{record.assignee}</span>
                    <Badge variant="outline" className="ml-2 text-[10px]">
                      <span className={OP_LABELS[record.operationType]?.color}>
                        {OP_LABELS[record.operationType]?.label ?? record.operationType}
                      </span>
                    </Badge>
                    {record.comment && (
                      <p className="mt-0.5 text-muted-foreground">{record.comment}</p>
                    )}
                    <p className="mt-0.5 text-muted-foreground text-xs">{record.operationTime}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      {/* 操作确认对话框 */}
      <Dialog
        open={dialogAction !== null}
        onOpenChange={(open) => {
          if (!open) closeDialog()
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{dialogAction ? dialogTitles[dialogAction] : ""}</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            {needsTargetUser && (
              <div>
                <label htmlFor={`${formId}-target-user`} className="font-medium text-sm">
                  目标用户
                </label>
                <input
                  id={`${formId}-target-user`}
                  className="mt-1 w-full rounded-md border border-input px-3 py-1.5 text-sm"
                  value={targetUser}
                  onChange={(e) => setTargetUser(e.target.value)}
                  placeholder="输入用户名"
                />
              </div>
            )}
            {dialogAction !== "withdraw" && (
              <Textarea
                placeholder="请输入审批意见"
                value={comment}
                onChange={(e) => setComment(e.target.value)}
              />
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={closeDialog}>
              取消
            </Button>
            <Button onClick={handleConfirm} disabled={isPending}>
              确认
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
