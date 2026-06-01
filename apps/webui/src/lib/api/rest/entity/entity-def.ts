/**
 * EntityDef 管理 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"

/** 后端存储的实体定义记录 */
export interface EntityDefRecord {
  id: string
  slug: string
  config: Record<string, unknown>
  builtin: boolean
  enabled: boolean
  version: number
  createdAt: string
  updatedAt: string
}

/** 创建/更新请求体 */
export interface EntityDefInput {
  slug: string
  config: Record<string, unknown>
  enabled?: boolean
}

export const entityDefApi = {
  /** 获取所有实体定义 */
  list: () => backendApi.get<EntityDefRecord[]>("/entity-defs"),

  /** 获取单个实体定义 */
  get: (id: string) => backendApi.get<EntityDefRecord>(`/entity-defs/${id}`),

  /** 创建实体定义 */
  create: (data: EntityDefInput) =>
    backendApi.post<EntityDefRecord>("/entity-defs", data),

  /** 更新实体定义 */
  update: (id: string, data: EntityDefInput) =>
    backendApi.put<EntityDefRecord>(`/entity-defs/${id}`, data),

  /** 删除实体定义 */
  delete: (id: string) => backendApi.delete<void>(`/entity-defs/${id}`)
}
