/**
 * 通知 API 客户端
 * @author AaronZZH & Kiro
 */

import { ApiError, type PageResult } from "./client"

export type NotificationType = "approval" | "system" | "mention" | "task" | "change"

export interface NotificationItem {
  id: string
  type: NotificationType
  title: string
  body?: string
  entityType?: string
  entityId?: string
  read: boolean
  createdAt: string
}

export interface NotificationListParams {
  page?: number
  pageSize?: number
  type?: NotificationType
  read?: boolean
}

const BASE = process.env.NEXT_PUBLIC_API_URL ?? "/api"

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { "Content-Type": "application/json", ...init?.headers },
    ...init
  })
  if (!res.ok) throw new ApiError(res.status, `请求失败: ${res.statusText}`)
  const json = await res.json()
  if (json.code !== 0) throw new ApiError(json.code, json.message ?? "未知错误")
  return json.data as T
}

export const notificationApi = {
  list: (params: NotificationListParams = {}) => {
    const qs = new URLSearchParams()
    if (params.page) qs.set("page", String(params.page))
    if (params.pageSize) qs.set("pageSize", String(params.pageSize))
    if (params.type) qs.set("type", params.type)
    if (params.read !== undefined) qs.set("read", String(params.read))
    const q = qs.toString()
    return req<PageResult<NotificationItem>>(`/notifications${q ? `?${q}` : ""}`)
  },

  unreadCount: () => req<{ count: number }>("/notifications/unread-count"),

  markRead: (ids?: string[]) =>
    req<void>("/notifications/read", { method: "PUT", body: JSON.stringify({ ids }) }),

  remove: (ids: string[]) =>
    req<void>("/notifications", { method: "DELETE", body: JSON.stringify({ ids }) })
}
