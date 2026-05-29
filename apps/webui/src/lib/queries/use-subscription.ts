/**
 * 字段变更订阅 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { subscriptionApi, type UpsertSubscriptionReq } from "@/lib/api/subscription"

/** 获取当前用户对某条记录的订阅 */
export function useSubscription(entityType: string, entityId: string | undefined) {
  return useQuery({
    queryKey: ["subscription", entityType, entityId],
    queryFn: () => subscriptionApi.get(entityType, entityId as NonNullable<typeof entityId>),
    enabled: !!entityId
  })
}

/** 获取当前用户在某实体下所有已订阅的记录 ID（列表视图用） */
export function useSubscribedIds(entityType: string) {
  return useQuery({
    queryKey: ["subscription-ids", entityType],
    queryFn: () => subscriptionApi.listIds(entityType)
  })
}

/** 创建/更新订阅 */
export function useUpsertSubscription() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: UpsertSubscriptionReq) => subscriptionApi.upsert(data),
    onSuccess: (_res, vars) => {
      qc.invalidateQueries({ queryKey: ["subscription", vars.entityType, vars.entityId] })
      qc.invalidateQueries({ queryKey: ["subscription-ids", vars.entityType] })
    }
  })
}

/** 取消订阅 */
export function useRemoveSubscription() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ entityType, entityId }: { entityType: string; entityId: string }) =>
      subscriptionApi.remove(entityType, entityId),
    onSuccess: (_res, vars) => {
      qc.invalidateQueries({ queryKey: ["subscription", vars.entityType, vars.entityId] })
      qc.invalidateQueries({ queryKey: ["subscription-ids", vars.entityType] })
    }
  })
}
