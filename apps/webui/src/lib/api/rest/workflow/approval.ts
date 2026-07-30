/**
 * 审批流程 API 客户端——对接 ApprovalController + WorkflowController 审批相关端点
 * @author AaronZZH
 */

import { backendApi } from "../backend-client"
import type { PageResult } from "../entity/crud"

/** 审批人策略 */
export type AssigneeStrategy =
  | "FIXED_USER"
  | "ROLE"
  | "DEPARTMENT_HEAD"
  | "INITIATOR_SELECT"
  | "EXPRESSION"

/** 超时策略 */
export type TimeoutStrategy = "AUTO_APPROVE" | "AUTO_REJECT" | "TRANSFER" | "REMIND"

/** 空审批人策略 */
export type EmptyAssigneeStrategy = "SKIP" | "ADMIN" | "ERROR"

/** 会签模式 */
export type CountersignMode = "ALL_APPROVE" | "ANY_APPROVE" | "RATIO"

/** 审批操作类型 */
export type ApprovalOperationType =
  | "APPROVE"
  | "REJECT"
  | "DELEGATE"
  | "ADD_SIGN"
  | "TRANSFER"
  | "WITHDRAW"

/** 审批记录 */
export interface ApprovalRecordVO {
  id: string
  processInstanceId: string
  taskId: string
  assignee: string
  operationType: ApprovalOperationType
  comment: string
  operationTime: string
}

/** 投票进度 */
export interface VoteProgress {
  total: number
  approved: number
  rejected: number
  pending: number
  voters: string[]
  votedUsers: string[]
}

/** 审批统计 */
export interface ApprovalStats {
  total: number
  approved: number
  rejected: number
  avgProcessingHours: number
}

/** 待办任务 */
export interface WorkflowTaskVO {
  taskId: string
  processInstanceId: string
  name: string
  assignee: string
  /** 关联实体类型（用于跳转到实体详情页） */
  entityType?: string
  /** 关联实体 ID */
  entityId?: string
}

/** 流程实例 */
export interface ProcessInstanceVO {
  processInstanceId: string
  processDefinitionKey: string
  processDefinitionName: string
  startTime: string
  endTime?: string
  status: string
  initiator: string
}

export const approvalApi = {
  /** 前加签 */
  addSignBefore: (taskId: string, assignee: string) =>
    backendApi.post<void>("/system/workflow/approval/add-sign-before", { taskId, assignee }),

  /** 后加签 */
  addSignAfter: (taskId: string, assignee: string) =>
    backendApi.post<void>("/system/workflow/approval/add-sign-after", { taskId, assignee }),

  /** 转签 */
  transfer: (taskId: string, targetAssignee: string, reason: string) =>
    backendApi.post<void>("/system/workflow/approval/transfer", { taskId, targetAssignee, reason }),

  /** 撤回 */
  withdraw: (processInstanceId: string) =>
    backendApi.post<void>("/system/workflow/approval/withdraw", { processInstanceId }),

  /** 查询审批时间线 */
  getTimeline: (processInstanceId: string) =>
    backendApi.get<ApprovalRecordVO[]>(`/system/workflow/approval/timeline/${processInstanceId}`),

  /** 查询投票进度 */
  getVoteProgress: (processInstanceId: string) =>
    backendApi.get<VoteProgress>(`/system/workflow/approval/vote-progress/${processInstanceId}`),

  /** 审批统计 */
  getStats: () => backendApi.get<ApprovalStats>("/system/workflow/approval/stats"),

  /** 我的待办 */
  myPendingTasks: () => backendApi.get<WorkflowTaskVO[]>("/system/workflow/tasks/my-pending"),

  /** 我发起的流程 */
  myInitiated: (pageNo = 1, pageSize = 20) =>
    backendApi.get<PageResult<ProcessInstanceVO>>(
      `/system/workflow/instances/my-initiated?pageNo=${pageNo}&pageSize=${pageSize}`
    ),

  /** 历史流程（已办） */
  historyInstances: (pageNo = 1, pageSize = 20) =>
    backendApi.get<PageResult<ProcessInstanceVO>>(
      `/system/workflow/instances/history?finished=true&pageNo=${pageNo}&pageSize=${pageSize}`
    )
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

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
    queryFn: () => approvalApi.getTimeline(processInstanceId ?? ""),
    enabled: !!processInstanceId
  })
}

/** 投票进度 */
export function useVoteProgress(processInstanceId?: string) {
  return useQuery({
    queryKey: ["approval", "vote-progress", processInstanceId],
    queryFn: () => approvalApi.getVoteProgress(processInstanceId ?? ""),
    enabled: !!processInstanceId
  })
}

/** 审批统计 */
export function useApprovalStats() {
  return useQuery({
    queryKey: ["approval", "stats"],
    queryFn: approvalApi.getStats
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
    mutationFn: ({
      taskId,
      targetAssignee,
      reason
    }: {
      taskId: string
      targetAssignee: string
      reason: string
    }) => approvalApi.transfer(taskId, targetAssignee, reason),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["approval"] })
    }
  })
}

/** 撤回 */
export function useWithdraw() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ processInstanceId }: { processInstanceId: string }) =>
      approvalApi.withdraw(processInstanceId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["approval"] })
    }
  })
}
