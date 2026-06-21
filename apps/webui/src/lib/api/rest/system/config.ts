import { request } from "../entity/crud"

export interface SystemConfigVO {
  id: number
  configKey: string
  name: string
  description?: string
  value: string | null
  defaultValue: string | null
  valueType: "string" | "integer" | "boolean" | "json"
  category: string
  visible: boolean
  editable: boolean
  updateTime?: string
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
    }),

  /**
   * 按 key 查询公开配置项 value（无需登录）
   *
   * <p>仅后端白名单中的 key 可访问；非白名单 key 返回 404。
   */
  getPublic: (key: string) => request<string>(`/public/system/configs/${encodeURIComponent(key)}`)
}
