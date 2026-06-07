/**
 * 活动流 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { activityApi } from "@/lib/api/rest/entity/activity"

const activityKey = (entityType: string, entityId: string) => [entityType, entityId, "activities"]
const scheduleKey = (entityType: string, entityId: string) => [entityType, entityId, "schedules"]

export function useActivities(entityType: string, entityId: string, enabled = true) {
  return useQuery({
    queryKey: activityKey(entityType, entityId),
    queryFn: () => activityApi.list(entityType, entityId),
    enabled: enabled && !!entityType && !!entityId
  })
}

export function useAddComment(entityType: string, entityId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ content, mentions }: { content: string; mentions?: string[] }) =>
      activityApi.comment(entityType, entityId, content, mentions),
    onSuccess: () => qc.invalidateQueries({ queryKey: activityKey(entityType, entityId) })
  })
}

export function useDeleteComment(entityType: string, entityId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (commentId: string) => activityApi.deleteComment(entityType, entityId, commentId),
    onSuccess: () => qc.invalidateQueries({ queryKey: activityKey(entityType, entityId) })
  })
}

export function useSchedules(entityType: string, entityId: string, enabled = true) {
  return useQuery({
    queryKey: scheduleKey(entityType, entityId),
    queryFn: () => activityApi.schedules(entityType, entityId),
    enabled: enabled && !!entityType && !!entityId
  })
}

export function useCreateSchedule(entityType: string, entityId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: {
      title: string
      category: string
      assigneeId?: number
      dueDate?: string
    }) =>
      activityApi.createSchedule({
        ...data,
        sourceEntity: entityType,
        sourceId: entityId
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: scheduleKey(entityType, entityId) })
  })
}

export function useCompleteSchedule(entityType: string, entityId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => activityApi.completeSchedule(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: scheduleKey(entityType, entityId) })
  })
}
