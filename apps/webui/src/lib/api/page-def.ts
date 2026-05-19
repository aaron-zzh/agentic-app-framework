/**
 * PageDef API 客户端
 * @author AaronZZH & Kiro
 */

import type { PageDefRecord } from "@/features/page-engine/types"

import { ApiError } from "./client"

/** 创建 PageDef 请求体 */
export interface PageDefCreateInput {
  slug: string
  title: string
  config: Record<string, unknown>
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

export const pageDefApi = {
  /** 获取所有 PageDef */
  list: () => req<PageDefRecord[]>("/system/page-defs"),

  /** 获取单个 PageDef */
  get: (id: string) => req<PageDefRecord>(`/system/page-defs/${id}`),

  /** 根据 slug 获取已发布的 PageDef（支持多段路径如 pricing/enterprise） */
  getBySlug: (slug: string) => req<PageDefRecord>(`/system/page-defs/slug/${slug}`),

  /** 创建 PageDef */
  create: (data: PageDefCreateInput) =>
    req<PageDefRecord>("/system/page-defs", {
      method: "POST",
      body: JSON.stringify(data)
    }),

  /** 更新 PageDef */
  update: (id: string, data: PageDefCreateInput) =>
    req<PageDefRecord>(`/system/page-defs/${id}`, {
      method: "PUT",
      body: JSON.stringify(data)
    }),

  /** 发布 PageDef */
  publish: (id: string) =>
    req<PageDefRecord>(`/system/page-defs/${id}/publish`, { method: "POST" }),

  /** 回滚到上一版本 */
  rollback: (id: string) =>
    req<PageDefRecord>(`/system/page-defs/${id}/rollback`, { method: "POST" }),

  /** 删除 PageDef */
  delete: (id: string) => req<void>(`/system/page-defs/${id}`, { method: "DELETE" })
}
