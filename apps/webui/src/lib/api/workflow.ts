/**
 * 审批工作流 API 客户端
 * @author AaronZZH & Kiro
 */

import { buildApiUrl } from "./base"
import { ApiError } from "./client"

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

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(buildApiUrl(path), {
    headers: { "Content-Type": "application/json", ...init?.headers },
    ...init
  })
  if (!res.ok) throw new ApiError(res.status, `请求失败: ${res.statusText}`)
  const json = await res.json()
  if (json.code !== 0) throw new ApiError(json.code, json.message ?? "未知错误")
  return json.data as T
}

export const workflowApi = {
  /** 发起审批 */
  start: (body: { entityType: string; entityId: string; assignee: string }) =>
    req<WorkflowStatusVO>("/workflow/start", { method: "POST", body: JSON.stringify(body) }),

  /** 审批通过 */
  complete: (body: { taskId: string; comment: string }) =>
    req<void>("/workflow/complete", { method: "POST", body: JSON.stringify(body) }),

  /** 驳回 */
  reject: (body: { taskId: string; comment: string }) =>
    req<void>("/workflow/reject", { method: "POST", body: JSON.stringify(body) }),

  /** 获取流程状态 */
  getStatus: (processInstanceId: string) => req<WorkflowStatusVO>(`/workflow/${processInstanceId}`),

  /** 获取审批历史 */
  getHistory: (processInstanceId: string) =>
    req<HistoryItem[]>(`/workflow/${processInstanceId}/history`)
}
