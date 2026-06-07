/**
 * 通知 API 客户端
 * @author AaronZZH & Kiro
 */

import { type PageResult, request } from "../entity/crud"

export type NotificationType = "approval" | "system" | "mention" | "task" | "change"

export interface NotificationItem {
  id: string
  type: NotificationType
  title: string
  body?: string
  entityType?: string
  entityId?: string
  relatedUrl?: string
  read: boolean
  createdAt: string
}

export interface NotificationListParams {
  page?: number
  pageSize?: number
  type?: NotificationType
  read?: boolean
}

export const notificationApi = {
  list: (params: NotificationListParams = {}) => {
    const qs = new URLSearchParams()
    if (params.page) qs.set("page", String(params.page))
    if (params.pageSize) qs.set("pageSize", String(params.pageSize))
    if (params.type) qs.set("type", params.type)
    if (params.read !== undefined) qs.set("read", String(params.read))
    const q = qs.toString()
    return request<PageResult<NotificationItem>>(`/notifications${q ? `?${q}` : ""}`)
  },

  unreadCount: () => request<{ count: number }>("/notifications/unread-count"),

  markRead: (ids?: string[]) =>
    request<void>("/notifications/read", { method: "PUT", body: JSON.stringify({ ids }) }),

  remove: (ids: string[]) =>
    request<void>("/notifications", { method: "DELETE", body: JSON.stringify({ ids }) })
}
