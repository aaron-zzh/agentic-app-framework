import { request } from "../entity/crud"

export interface SystemConfigVO {
  key: string
  name: string
  value: string | null
  defaultValue: string | null
  valueType: "string" | "integer" | "boolean" | "json"
  category: string
  visible: boolean
}

export const systemConfigApi = {
  listByCategory: (category: string) =>
    request<SystemConfigVO[]>(`/system/configs?category=${category}`)
}
