/**
 * 权限 API 调用——获取实体级权限配置
 * @author AaronZZH & Kiro
 */

import { request } from "../entity/crud"

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
export async function fetchEntityAccess(slug: string): Promise<EntityAccess> {
  return request<EntityAccess>(`/permissions/entity/${slug}`)
}
