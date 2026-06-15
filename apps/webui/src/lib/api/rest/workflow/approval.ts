/**
 * 审批流程 API 客户端——对接 ApprovalController + WorkflowController 审批相关端点
 * @author AaronZZH
 */

import type { PageResult } from "../entity/crud"
import { request } from "../entity/crud"

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
    request<void>("/system/workflow/approval/add-sign-before", {
      method: "POST",
      body: JSON.stringify({ taskId, assignee })
    }),

  /** 后加签 */
  addSignAfter: (taskId: string, assignee: string) =>
    request<void>("/system/workflow/approval/add-sign-after", {
      method: "POST",
      body: JSON.stringify({ taskId, assignee })
    }),

  /** 转签 */
  transfer: (taskId: string, targetAssignee: string, reason: string) =>
    request<void>("/system/workflow/approval/transfer", {
      method: "POST",
      body: JSON.stringify({ taskId, targetAssignee, reason })
    }),

  /** 撤回 */
  withdraw: (processInstanceId: string, initiator: string) =>
    request<void>("/system/workflow/approval/withdraw", {
      method: "POST",
      body: JSON.stringify({ processInstanceId, initiator })
    }),

  /** 查询审批时间线 */
  getTimeline: (processInstanceId: string) =>
    request<ApprovalRecordVO[]>(`/system/workflow/approval/timeline/${processInstanceId}`),

  /** 查询投票进度 */
  getVoteProgress: (processInstanceId: string) =>
    request<VoteProgress>(`/system/workflow/approval/vote-progress/${processInstanceId}`),

  /** 审批统计 */
  getStats: (assignee: string) =>
    request<ApprovalStats>(`/system/workflow/approval/stats?assignee=${assignee}`),

  /** 我的待办 */
  myPendingTasks: () => request<WorkflowTaskVO[]>("/system/workflow/tasks/my-pending"),

  /** 我发起的流程 */
  myInitiated: (pageNo = 1, pageSize = 20) =>
    request<PageResult<ProcessInstanceVO>>(
      `/system/workflow/instances/my-initiated?pageNo=${pageNo}&pageSize=${pageSize}`
    ),

  /** 历史流程（已办） */
  historyInstances: (pageNo = 1, pageSize = 20) =>
    request<PageResult<ProcessInstanceVO>>(
      `/system/workflow/instances/history?finished=true&pageNo=${pageNo}&pageSize=${pageSize}`
    )
}
