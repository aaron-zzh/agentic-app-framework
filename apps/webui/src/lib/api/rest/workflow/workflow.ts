/**
 * 审批工作流 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"

/** 工作流状态 */
export interface WorkflowStatusVO {
  processInstanceId: string
  status: "running" | "completed" | "rejected"
  currentTask?: {
    taskId: string
    assignee: string
    createTime: string
  }
}

/** 审批历史条目 */
export interface HistoryItem {
  activityName: string
  assignee: string
  action: string
  comment: string
  endTime: string
}

export const workflowApi = {
  /** 发起审批 */
  start: (body: { entityType: string; entityId: string; assignee: string }) =>
    backendApi.post<WorkflowStatusVO>("/workflow/start", body),

  /** 审批通过 */
  complete: (body: { taskId: string; comment: string }) =>
    backendApi.post<void>("/workflow/complete", body),

  /** 驳回 */
  reject: (body: { taskId: string; comment: string }) =>
    backendApi.post<void>("/workflow/reject", body),

  /** 获取流程状态 */
  getStatus: (processInstanceId: string) => backendApi.get<WorkflowStatusVO>(`/workflow/${processInstanceId}`),

  /** 获取审批历史 */
  getHistory: (processInstanceId: string) =>
    backendApi.get<HistoryItem[]>(`/workflow/${processInstanceId}/history`)
}
