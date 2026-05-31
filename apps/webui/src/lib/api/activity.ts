/**
 * 活动流 API 客户端
 * @author AaronZZH & Kiro
 */

import { buildApiUrl } from "./base"
import { ApiError } from "./client"

export type ActivityType = "create" | "update" | "status_change" | "comment" | "schedule"

export interface ActivityItem {
  id: string
  type: ActivityType
  entityType: string
  entityId: string
  actorId: string
  actorName: string
  actorAvatar?: string
  content?: string
  /** 字段变更（update 类型） */
  changes?: { field: string; label: string; oldValue: unknown; newValue: unknown }[]
  /** 提及的用户 */
  mentions?: string[]
  createdAt: string
}

export interface ScheduledActivity {
  id: string
  entityType: string
  entityId: string
  type: "call" | "email" | "meeting" | "todo"
  title: string
  assigneeId: string
  assigneeName?: string
  dueDate: string
  done: boolean
  createdAt: string
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

export const activityApi = {
  /** 获取活动流（操作日志 + 评论混合） */
  list: (entityType: string, entityId: string) =>
    req<ActivityItem[]>(`/activity-log?entityType=${entityType}&entityId=${entityId}`),

  /** 发布评论 */
  comment: (entityType: string, entityId: string, content: string, mentions?: string[]) =>
    req<ActivityItem>("/comments", {
      method: "POST",
      body: JSON.stringify({ entityType, entityId, content, mentions })
    }),

  /** 删除评论 */
  deleteComment: (id: string) => req<void>(`/comments/${id}`, { method: "DELETE" }),

  /** 获取活动调度列表 */
  schedules: (entityType: string, entityId: string) =>
    req<ScheduledActivity[]>(`/scheduled-activities?entityType=${entityType}&entityId=${entityId}`),

  /** 创建活动调度 */
  createSchedule: (data: Omit<ScheduledActivity, "id" | "done" | "createdAt">) =>
    req<ScheduledActivity>("/scheduled-activities", {
      method: "POST",
      body: JSON.stringify(data)
    }),

  /** 完成活动调度 */
  completeSchedule: (id: string) =>
    req<void>(`/scheduled-activities/${id}/complete`, { method: "POST" })
}
