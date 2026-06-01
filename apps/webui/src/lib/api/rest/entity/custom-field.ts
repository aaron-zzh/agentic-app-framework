/**
 * 用户自定义字段 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"

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

export const customFieldApi = {
  /** 获取实体的自定义字段列表 */
  list: (slug: string) => backendApi.get<CustomFieldRecord[]>(`/entity-defs/${slug}/fields`),

  /** 添加自定义字段 */
  create: (slug: string, data: CustomFieldInput) =>
    backendApi.post<CustomFieldRecord>(`/entity-defs/${slug}/fields`, data),

  /** 隐藏字段（逻辑删除） */
  hide: (slug: string, fieldName: string) =>
    backendApi.delete<void>(`/entity-defs/${slug}/fields/${fieldName}`)
}
