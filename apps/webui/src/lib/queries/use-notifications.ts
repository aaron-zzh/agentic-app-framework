/**
 * 通知相关 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { _notifications } from "@/lib/_mock/notifications"
import { type NotificationListParams, notificationApi } from "@/lib/api/notification"

const KEYS = {
  all: ["notifications"] as const,
  list: (params: NotificationListParams) => ["notifications", "list", params] as const,
  unreadCount: ["notifications", "unread-count"] as const
}

/** 通知列表 */
export function useNotifications(params: NotificationListParams = {}) {
  return useQuery({
    queryKey: KEYS.list(params),
    queryFn: async () => {
      // mock 仅在开发环境使用，生产环境走真实 API
      if (process.env.NODE_ENV === "development") {
        const mockList = _notifications.map((n) => ({
          id: n.id,
          type: n.type,
          title: n.title,
          body: n.description,
          read: !n.isUnRead,
          createdAt: n.createdAt
        }))
        return { list: mockList, total: mockList.length, page: 1, pageSize: 20 }
      }
      return notificationApi.list(params)
    }
  })
}

/** 未读计数 */
export function useUnreadCount() {
  return useQuery({
    queryKey: KEYS.unreadCount,
    queryFn: async () => {
      if (process.env.NODE_ENV === "development") {
        return { count: _notifications.filter((n) => n.isUnRead).length }
      }
      return notificationApi.unreadCount()
    },
    refetchInterval: 60_000
  })
}

/** 标记已读 */
export function useMarkRead() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ids?: string[]) => notificationApi.markRead(ids),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}

/** 删除通知 */
export function useRemoveNotifications() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ids: string[]) => notificationApi.remove(ids),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}
