/**
 * 用户自定义字段 API 客户端
 * @author AaronZZH & Kiro
 */

import { ApiError } from "./client"

/** 自定义字段类型 */
export type CustomFieldType = "text" | "number" | "date" | "select" | "boolean"

/** 选项配置（select 类型） */
export interface FieldOption {
  label: string
  value: string
}

/** 自定义字段记录 */
export interface CustomFieldRecord {
  name: string
  label: string
  type: CustomFieldType
  options?: FieldOption[]
  hidden: boolean
  createdAt: string
}

/** 创建字段请求体 */
export interface CustomFieldInput {
  name: string
  label: string
  type: CustomFieldType
  options?: FieldOption[]
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

export const customFieldApi = {
  /** 获取实体的自定义字段列表 */
  list: (slug: string) => req<CustomFieldRecord[]>(`/entity-defs/${slug}/fields`),

  /** 添加自定义字段 */
  create: (slug: string, data: CustomFieldInput) =>
    req<CustomFieldRecord>(`/entity-defs/${slug}/fields`, {
      method: "POST",
      body: JSON.stringify(data)
    }),

  /** 隐藏字段（逻辑删除） */
  hide: (slug: string, fieldName: string) =>
    req<void>(`/entity-defs/${slug}/fields/${fieldName}`, { method: "DELETE" })
}
