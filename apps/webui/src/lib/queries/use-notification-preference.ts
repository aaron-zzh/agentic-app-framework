/**
 * 通知偏好 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
  type NotificationPreference,
  notificationPreferenceApi
} from "@/lib/api/notification-preference"

const KEY = ["notification-preferences"] as const

/** 默认偏好（后端就绪前的 mock） */
const DEFAULT_PREFERENCE: NotificationPreference = {
  items: [
    { type: "approval", channels: { inApp: true, email: true, wechat: false } },
    { type: "system", channels: { inApp: true, email: false, wechat: false } },
    { type: "task", channels: { inApp: true, email: true, wechat: true } },
    { type: "mention", channels: { inApp: true, email: false, wechat: false } },
    { type: "change", channels: { inApp: true, email: false, wechat: false } }
  ],
  quietStart: "22:00",
  quietEnd: "08:00"
}

export function useNotificationPreference() {
  return useQuery({
    queryKey: KEY,
    // TODO: 后端就绪后替换为 notificationPreferenceApi.get()
    queryFn: async () => DEFAULT_PREFERENCE
  })
}

export function useUpdateNotificationPreference() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: NotificationPreference) => notificationPreferenceApi.update(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY })
    }
  })
}
