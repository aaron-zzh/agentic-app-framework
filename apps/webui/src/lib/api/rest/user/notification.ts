/**
 * 通知 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"
import type { PageResult } from "../entity/crud"

export type NotificationType = "approval" | "system" | "mention" | "task" | "change"

export interface NotificationItem {
  id: number
  type: NotificationType
  title: string
  body?: string
  entityType?: string
  entityId?: string
  relatedUrl?: string
  isRead: boolean
  createTime: string
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
    if (params.read !== undefined) qs.set("isRead", String(params.read))
    const q = qs.toString()
    return backendApi.get<PageResult<NotificationItem>>(`/notifications${q ? `?${q}` : ""}`)
  },

  unreadCount: () => backendApi.get<number>("/notifications/unread-count"),

  markRead: (ids?: number[]) => backendApi.put<void>("/notifications/read", ids ?? []),

  remove: (ids: number[]) => backendApi.delete<void>("/notifications", { data: { ids } })
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

const KEYS = {
  all: ["notifications"] as const,
  list: (params: NotificationListParams) => ["notifications", "list", params] as const,
  unreadCount: ["notifications", "unread-count"] as const
}

/** 通知列表 */
export function useNotifications(params: NotificationListParams = {}) {
  return useQuery({
    queryKey: KEYS.list(params),
    queryFn: () => notificationApi.list(params)
  })
}

/** 未读计数 */
export function useUnreadCount() {
  return useQuery({
    queryKey: KEYS.unreadCount,
    queryFn: () => notificationApi.unreadCount(),
    refetchInterval: 60_000
  })
}

/** 标记已读 */
export function useMarkRead() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ids?: number[]) => notificationApi.markRead(ids),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}

/** 删除通知 */
export function useRemoveNotifications() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ids: number[]) => notificationApi.remove(ids),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}
