/**
 * 回收站 API 客户端
 * @author AaronZZH & Kiro
 */

import { buildApiUrl } from "./base"
import { ApiError, type PageResult } from "./client"

/** 回收站记录 */
export interface TrashItemVO {
  id: string
  entityType: string
  title: string
  deletedBy: string
  deletedAt: string
}

export interface TrashListParams {
  page?: number
  pageSize?: number
  entityType?: string
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

export const trashApi = {
  /** 回收站列表 */
  list: (params: TrashListParams = {}) => {
    const qs = new URLSearchParams()
    if (params.page) qs.set("page", String(params.page))
    if (params.pageSize) qs.set("pageSize", String(params.pageSize))
    if (params.entityType) qs.set("entityType", params.entityType)
    const q = qs.toString()
    return req<PageResult<TrashItemVO>>(`/trash${q ? `?${q}` : ""}`)
  },

  /** 恢复记录 */
  restore: (ids: string[]) =>
    req<void>("/trash/restore", { method: "POST", body: JSON.stringify({ ids }) }),

  /** 彻底删除 */
  purge: (ids: string[]) =>
    req<void>("/trash/purge", { method: "DELETE", body: JSON.stringify({ ids }) })
}
