/**
 * PageDef API 客户端
 * @author AaronZZH & Kiro
 */

import type { PageDefRecord } from "@/lib/types/page"

import { backendApi } from "../backend-client"

/** 创建 PageDef 请求体 */
export interface PageDefCreateInput {
  slug: string
  title: string
  config: Record<string, unknown>
}

export const pageDefApi = {
  /** 获取所有 PageDef */
  list: () => backendApi.get<PageDefRecord[]>("/system/page-defs"),

  /** 获取单个 PageDef */
  get: (id: string) => backendApi.get<PageDefRecord>(`/system/page-defs/${id}`),

  /** 根据 slug 获取已发布的 PageDef（支持多段路径如 pricing/enterprise） */
  getBySlug: (slug: string) => backendApi.get<PageDefRecord>(`/system/page-defs/slug/${slug}`),

  /** 创建 PageDef */
  create: (data: PageDefCreateInput) => backendApi.post<PageDefRecord>("/system/page-defs", data),

  /** 更新 PageDef */
  update: (id: string, data: PageDefCreateInput) =>
    backendApi.put<PageDefRecord>(`/system/page-defs/${id}`, data),

  /** 发布 PageDef */
  publish: (id: string) => backendApi.post<PageDefRecord>(`/system/page-defs/${id}/publish`),

  /** 回滚到上一版本 */
  rollback: (id: string) => backendApi.post<PageDefRecord>(`/system/page-defs/${id}/rollback`),

  /** 删除 PageDef */
  delete: (id: string) => backendApi.delete<void>(`/system/page-defs/${id}`)
}
