/**
 * PageDef TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { pageDefApi, type PageDefCreateInput } from "@/lib/api/page-def"

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
    queryFn: () => pageDefApi.get(id!),
    enabled: !!id
  })
}

/** 根据 slug 查询已发布的 PageDef */
export function usePageDefBySlug(slug: string | undefined) {
  return useQuery({
    queryKey: [...PAGE_DEFS_KEY, "slug", slug],
    queryFn: () => pageDefApi.getBySlug(slug!),
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
