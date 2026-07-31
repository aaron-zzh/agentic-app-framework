/**
 * 实体权限 API 与 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"

import { backendApi } from "../backend-client"

/** 字段级权限 */
export interface FieldAccess {
  visible: boolean
  editable: boolean
}

/** 实体级权限（后端根据当前用户计算后返回） */
export interface EntityAccess {
  read: boolean
  create: boolean
  update: boolean
  delete: boolean
  fieldAccess: Record<string, FieldAccess>
}

/** 获取指定实体的权限配置 */
export function fetchEntityAccess(slug: string): Promise<EntityAccess> {
  return backendApi.get<EntityAccess>(`/permissions/entity/${slug}`)
}

/** 查询当前用户对指定实体的权限 */
export function useEntityAccess(entitySlug: string) {
  return useQuery<EntityAccess>({
    queryKey: [entitySlug, "access"],
    queryFn: () => fetchEntityAccess(entitySlug),
    staleTime: 5 * 60 * 1000
  })
}
