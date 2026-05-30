/**
 * 权限 API 调用——获取实体级权限配置
 * @author AaronZZH & Kiro
 */

import type { EntityAccess } from "@/lib/types/entity/access"
import { request } from "./client"

export type { EntityAccess, FieldAccess } from "@/lib/types/entity/access"

/** 获取指定实体的权限配置 */
export async function fetchEntityAccess(slug: string): Promise<EntityAccess> {
  return request<EntityAccess>(`/permissions/entity/${slug}`)
}
