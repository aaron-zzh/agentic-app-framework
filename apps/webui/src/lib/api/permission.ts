/**
 * 权限 API 调用——获取实体级权限配置
 * @author AaronZZH & Kiro
 */

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

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "/api"

/** 获取指定实体的权限配置 */
export async function fetchEntityAccess(slug: string): Promise<EntityAccess> {
  const res = await fetch(`${BASE_URL}/permissions/entity/${slug}`)
  if (!res.ok) {
    throw new Error(`获取权限失败: ${res.statusText}`)
  }
  const json = await res.json()
  return json.data ?? json
}
