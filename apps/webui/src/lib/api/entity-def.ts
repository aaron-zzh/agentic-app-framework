/**
 * EntityDef 管理 API 客户端
 * @author AaronZZH & Kiro
 */

import { ApiError } from "./client"

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

const BASE = process.env.NEXT_PUBLIC_API_URL ?? "/api"

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { "Content-Type": "application/json", ...init?.headers },
    ...init
  })
  if (!res.ok) throw new ApiError(res.status, `请求失败: ${res.statusText}`)
  const json = await res.json()
  if (json.code !== 0) throw new ApiError(json.code, json.message ?? "未知错误")
  return json.data as T
}

export const entityDefApi = {
  /** 获取所有实体定义 */
  list: () => req<EntityDefRecord[]>("/entity-defs"),

  /** 获取单个实体定义 */
  get: (id: string) => req<EntityDefRecord>(`/entity-defs/${id}`),

  /** 创建实体定义 */
  create: (data: EntityDefInput) =>
    req<EntityDefRecord>("/entity-defs", {
      method: "POST",
      body: JSON.stringify(data)
    }),

  /** 更新实体定义 */
  update: (id: string, data: EntityDefInput) =>
    req<EntityDefRecord>(`/entity-defs/${id}`, {
      method: "PUT",
      body: JSON.stringify(data)
    }),

  /** 删除实体定义 */
  delete: (id: string) =>
    req<void>(`/entity-defs/${id}`, { method: "DELETE" })
}
