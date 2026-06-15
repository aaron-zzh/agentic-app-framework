/**
 * 通知相关 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { type NotificationListParams, notificationApi } from "@/lib/api/rest/user/notification"

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
