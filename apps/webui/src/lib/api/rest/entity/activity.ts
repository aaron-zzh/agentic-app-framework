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

/** 与后端 TodoVO 对齐 */
export interface ScheduledActivity {
  id: string
  assigneeId: number
  assigneeName?: string
  title: string
  /** 对应字典 sys_todo_category：todo / call / email / meeting */
  category: "todo" | "call" | "email" | "meeting"
  sourceEntity?: string
  sourceId?: number
  /** 对应字典 sys_todo_status：pending / done / ignored */
  status: "pending" | "done" | "ignored"
  dueDate?: string
  createTime: string
  /** UI 兼容字段：done = status === 'done' */
  done: boolean
}

export const activityApi = {
  /** 获取活动流（操作日志 + 评论混合时间线） */
  list: (entityType: string, entityId: string) =>
    backendApi.get<ActivityItem[]>(`/api/${entityType}/${entityId}/activities`),

  /** 发布评论 */
  comment: (entityType: string, entityId: string, content: string, mentions?: string[]) =>
    backendApi.post<ActivityItem>(`/api/${entityType}/${entityId}/comments`, {
      content,
      mentions
    }),

  /** 删除评论 */
  deleteComment: (entityType: string, entityId: string, commentId: string) =>
    backendApi.delete<void>(`/api/${entityType}/${entityId}/comments/${commentId}`),

  /** 获取实体关联待办列表 */
  schedules: (entityType: string, entityId: string) =>
    backendApi
      .get<Omit<ScheduledActivity, "done">[]>(`/api/todos/by-entity/${entityType}/${entityId}`)
      .then((list) => list.map((s) => ({ ...s, done: s.status === "done" }))),

  /** 创建待办 */
  createSchedule: (data: {
    title: string
    category: string
    sourceEntity: string
    sourceId: string
    assigneeId?: number
    dueDate?: string
  }) =>
    backendApi
      .post<Omit<ScheduledActivity, "done">>("/api/todos", {
        ...data,
        sourceId: Number(data.sourceId)
      })
      .then((s) => ({ ...s, done: s.status === "done" })),

  /** 完成待办（更新状态为 done） */
  completeSchedule: (id: string) =>
    backendApi.put<void>(`/api/todos/${id}/status`, { status: "done" })
}
