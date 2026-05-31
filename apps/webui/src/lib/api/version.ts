/**
 * 版本历史 API 客户端
 * @author AaronZZH & Kiro
 */

import { buildApiUrl } from "./base"
import { ApiError } from "./client"

export interface RecordVersion {
  id: string
  version: number
  entityType: string
  entityId: string
  data: Record<string, unknown>
  userId: string
  userName?: string
  createdAt: string
  summary?: string
}

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(buildApiUrl(path), {
    headers: { "Content-Type": "application/json", ...init?.headers },
    ...init
  })
  if (!res.ok) throw new ApiError(res.status, `请求失败: ${res.statusText}`)
  const json = await res.json()
  if (json.code !== 0) throw new ApiError(json.code, json.message ?? "未知错误")
  return json.data as T
}

export const versionApi = {
  /** 获取记录版本列表 */
  list: (entitySlug: string, id: string) => req<RecordVersion[]>(`/${entitySlug}/${id}/versions`),

  /** 恢复到指定版本 */
  restore: (entitySlug: string, id: string, version: number) =>
    req<Record<string, unknown>>(`/${entitySlug}/${id}/versions/${version}/restore`, {
      method: "POST"
    })
}
