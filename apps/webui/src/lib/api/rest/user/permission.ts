/**
 * 实体权限 API 与 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"

import { backendApi } from "../backend-client"

/** 与后端 AigcAuthorities 逐字一致的正式权限码。 */
export const AIGC_AUTHORITY_CODES = {
  PROJECT_ACTION: "aigc:project:action",
  OBJECT_VERSION_ADOPT: "aigc:object-version:adopt",
  PROJECT_REVIEW: "aigc:project:review",
  PROJECT_LIFECYCLE: "aigc:project:lifecycle",
  WORK_COLLECT: "aigc:work:collect",
  WORK_PUBLISH: "aigc:work:publish",
  WORK_ARCHIVE: "aigc:work:archive",
  TIMELINE_READ: "aigc:timeline:read",
  TIMELINE_CREATE: "aigc:timeline:create",
  TIMELINE_UPDATE: "aigc:timeline:update",
  TIMELINE_DELETE: "aigc:timeline:delete"
} as const

export type AigcAuthorityCode = (typeof AIGC_AUTHORITY_CODES)[keyof typeof AIGC_AUTHORITY_CODES]

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
  capabilities?: readonly string[]
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

/** CRUD 只读取后端正式布尔字段。 */
export function hasEntityOperation(
  access: EntityAccess | undefined,
  operation: "read" | "create" | "update" | "delete"
): boolean {
  if (!access) return false
  return access[operation]
}

/** 自定义动作只接受后端 capabilities 中的完整 authority code。 */
export function hasEntityCapability(
  access: EntityAccess | undefined,
  authorityCode: AigcAuthorityCode
): boolean {
  return access?.capabilities?.includes(authorityCode) === true
}
