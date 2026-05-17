/**
 * 活动流 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { activityApi } from "@/lib/api/activity"

const key = (entityType: string, entityId: string) => [entityType, entityId, "activities"]

export function useActivities(entityType: string, entityId: string, enabled = true) {
  return useQuery({
    queryKey: key(entityType, entityId),
    queryFn: () => activityApi.list(entityType, entityId),
    enabled: enabled && !!entityType && !!entityId
  })
}

export function useAddComment(entityType: string, entityId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ content, mentions }: { content: string; mentions?: string[] }) =>
      activityApi.comment(entityType, entityId, content, mentions),
    onSuccess: () => qc.invalidateQueries({ queryKey: key(entityType, entityId) })
  })
}

export function useDeleteComment(entityType: string, entityId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => activityApi.deleteComment(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: key(entityType, entityId) })
  })
}

export function useSchedules(entityType: string, entityId: string, enabled = true) {
  return useQuery({
    queryKey: [entityType, entityId, "schedules"],
    queryFn: () => activityApi.schedules(entityType, entityId),
    enabled: enabled && !!entityType && !!entityId
  })
}

export function useCreateSchedule(entityType: string, entityId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: activityApi.createSchedule,
    onSuccess: () => qc.invalidateQueries({ queryKey: [entityType, entityId, "schedules"] })
  })
}

export function useCompleteSchedule(entityType: string, entityId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => activityApi.completeSchedule(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: [entityType, entityId, "schedules"] })
  })
}
