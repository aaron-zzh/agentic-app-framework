import { request } from "../entity/crud"

export interface SystemConfigVO {
  id: number
  key: string
  name: string
  description?: string
  value: string | null
  defaultValue: string | null
  valueType: "string" | "integer" | "boolean" | "json"
  category: string
  visible: boolean
  editable: boolean
}

export const systemConfigApi = {
  /** 按分类查询（category="*" 查全部） */
  listByCategory: (category: string) =>
    request<SystemConfigVO[]>(`/system/configs?category=${category}`),

  /** 查全部配置 */
  listAll: () => request<SystemConfigVO[]>(`/system/configs?category=*`),

  /** 更新配置值 */
  update: (key: string, value: string) =>
    request<void>(`/system/configs`, {
      method: "PUT",
      body: JSON.stringify({ key, value })
    })
}
