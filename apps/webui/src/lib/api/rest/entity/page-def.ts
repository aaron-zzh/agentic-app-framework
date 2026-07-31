/**
 * PageDef API 客户端
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
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

const PAGE_DEFS_KEY = ["page-defs"]

/** 查询所有 PageDef */
export function usePageDefs() {
  return useQuery({
    queryKey: PAGE_DEFS_KEY,
    queryFn: () => pageDefApi.list()
  })
}

/** 查询单个 PageDef */
export function usePageDef(id: string | undefined) {
  return useQuery({
    queryKey: [...PAGE_DEFS_KEY, id],
    queryFn: () => pageDefApi.get(id as NonNullable<typeof id>),
    enabled: !!id
  })
}

/** 根据 slug 查询已发布的 PageDef */
export function usePageDefBySlug(slug: string | undefined) {
  return useQuery({
    queryKey: [...PAGE_DEFS_KEY, "slug", slug],
    queryFn: () => pageDefApi.getBySlug(slug as NonNullable<typeof slug>),
    enabled: !!slug,
    staleTime: 60_000
  })
}

/** 创建 PageDef */
export function useCreatePageDef() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: PageDefCreateInput) => pageDefApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: PAGE_DEFS_KEY })
  })
}

/** 更新 PageDef */
export function useUpdatePageDef() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: PageDefCreateInput }) =>
      pageDefApi.update(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: PAGE_DEFS_KEY })
  })
}

/** 发布 PageDef */
export function usePublishPageDef() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => pageDefApi.publish(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: PAGE_DEFS_KEY })
  })
}

/** 回滚 PageDef */
export function useRollbackPageDef() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => pageDefApi.rollback(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: PAGE_DEFS_KEY })
  })
}

/** 删除 PageDef */
export function useDeletePageDef() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => pageDefApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: PAGE_DEFS_KEY })
  })
}
