/**
 * 通知偏好 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
  type NotificationPreference,
  notificationPreferenceApi
} from "@/lib/api/rest/user/notification-preference"

const KEY = ["notification-preferences"] as const

export function useNotificationPreference() {
  return useQuery({
    queryKey: KEY,
    queryFn: () => notificationPreferenceApi.get()
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
