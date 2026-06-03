/**
 * 活动流 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"

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

export const activityApi = {
  /** 获取活动流（操作日志 + 评论混合） */
  list: (entityType: string, entityId: string) =>
    backendApi.get<ActivityItem[]>("/activity-log", { params: { entityType, entityId } }),

  /** 发布评论 */
  comment: (entityType: string, entityId: string, content: string, mentions?: string[]) =>
    backendApi.post<ActivityItem>("/comments", { entityType, entityId, content, mentions }),

  /** 删除评论 */
  deleteComment: (id: string) => backendApi.delete<void>(`/comments/${id}`),

  /** 获取活动调度列表 */
  schedules: (entityType: string, entityId: string) =>
    backendApi.get<ScheduledActivity[]>("/scheduled-activities", {
      params: { entityType, entityId }
    }),

  /** 创建活动调度 */
  createSchedule: (data: Omit<ScheduledActivity, "id" | "done" | "createdAt">) =>
    backendApi.post<ScheduledActivity>("/scheduled-activities", data),

  /** 完成活动调度 */
  completeSchedule: (id: string) => backendApi.post<void>(`/scheduled-activities/${id}/complete`)
}
