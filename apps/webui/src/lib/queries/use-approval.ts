/**
 * 审批流程 TanStack Query Hooks
 * @author Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { approvalApi } from "@/lib/api/approval"

/** 我的待办任务 */
export function useMyPendingTasks() {
  return useQuery({
    queryKey: ["approval", "pending"],
    queryFn: approvalApi.myPendingTasks
  })
}

/** 我发起的流程 */
export function useMyInitiated(pageNo = 1, pageSize = 20) {
  return useQuery({
    queryKey: ["approval", "initiated", pageNo, pageSize],
    queryFn: () => approvalApi.myInitiated(pageNo, pageSize)
  })
}

/** 已办列表 */
export function useApprovalHistory(pageNo = 1, pageSize = 20) {
  return useQuery({
    queryKey: ["approval", "history", pageNo, pageSize],
    queryFn: () => approvalApi.historyInstances(pageNo, pageSize)
  })
}

/** 审批时间线 */
export function useApprovalTimeline(processInstanceId?: string) {
  return useQuery({
    queryKey: ["approval", "timeline", processInstanceId],
    queryFn: () => approvalApi.getTimeline(processInstanceId!),
    enabled: !!processInstanceId
  })
}

/** 投票进度 */
export function useVoteProgress(processInstanceId?: string) {
  return useQuery({
    queryKey: ["approval", "vote-progress", processInstanceId],
    queryFn: () => approvalApi.getVoteProgress(processInstanceId!),
    enabled: !!processInstanceId
  })
}

/** 审批统计 */
export function useApprovalStats(assignee?: string) {
  return useQuery({
    queryKey: ["approval", "stats", assignee],
    queryFn: () => approvalApi.getStats(assignee!),
    enabled: !!assignee
  })
}

/** 前加签 */
export function useAddSignBefore() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ taskId, assignee }: { taskId: string; assignee: string }) =>
      approvalApi.addSignBefore(taskId, assignee),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["approval"] })
    }
  })
}

/** 后加签 */
export function useAddSignAfter() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ taskId, assignee }: { taskId: string; assignee: string }) =>
      approvalApi.addSignAfter(taskId, assignee),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["approval"] })
    }
  })
}

/** 转签 */
export function useTransferSign() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ taskId, targetAssignee, reason }: { taskId: string; targetAssignee: string; reason: string }) =>
      approvalApi.transfer(taskId, targetAssignee, reason),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["approval"] })
    }
  })
}

/** 撤回 */
export function useWithdraw() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ processInstanceId, initiator }: { processInstanceId: string; initiator: string }) =>
      approvalApi.withdraw(processInstanceId, initiator),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["approval"] })
    }
  })
}
