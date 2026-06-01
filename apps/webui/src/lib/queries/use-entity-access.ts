/**
 * useEntityAccess——获取实体权限配置的 TanStack Query hook
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import { type EntityAccess, fetchEntityAccess } from "@/lib/api/rest/user/permission"

/** 查询实体权限，staleTime 较长（权限不频繁变化） */
export function useEntityAccess(entitySlug: string) {
  const { data, isLoading } = useQuery<EntityAccess>({
    queryKey: ["entityAccess", entitySlug],
    queryFn: () => fetchEntityAccess(entitySlug),
    staleTime: 5 * 60 * 1000,
    enabled: !!entitySlug
  })

  return { access: data, isLoading }
}
