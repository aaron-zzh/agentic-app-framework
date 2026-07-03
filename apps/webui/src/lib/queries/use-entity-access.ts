/**
 * useEntityAccess——查询当前用户对指定实体的权限
 *
 * 调用 GET /api/permissions/entity/{slug}，由后端按用户角色动态计算 read/create/update/delete。
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import { backendApi } from "@/lib/api/rest/backend-client"
import type { EntityAccess } from "@/lib/types/entity"

export function useEntityAccess(entitySlug: string) {
  return useQuery<EntityAccess>({
    queryKey: [entitySlug, "access"],
    queryFn: () => backendApi.get<EntityAccess>(`/permissions/entity/${entitySlug}`),
    staleTime: 5 * 60 * 1000
  })
}
