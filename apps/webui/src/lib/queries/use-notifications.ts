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

/** 通知列表（后端就绪前使用 mock） */
export function useNotifications(params: NotificationListParams = {}) {
  const mockList = _notifications.map((n) => ({
    id: n.id,
    type: n.type,
    title: n.title,
    body: n.description,
    read: !n.isUnRead,
    createdAt: n.createdAt
  }))

  return useQuery({
    queryKey: KEYS.list(params),
    // TODO: 后端就绪后替换为 notificationApi.list(params)
    queryFn: async () => ({
      list: mockList,
      total: mockList.length,
      page: 1,
      pageSize: 20
    })
  })
}

/** 未读计数 */
export function useUnreadCount() {
  return useQuery({
    queryKey: KEYS.unreadCount,
    // TODO: 后端就绪后替换为 notificationApi.unreadCount()
    queryFn: async () => ({ count: _notifications.filter((n) => n.isUnRead).length }),
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
