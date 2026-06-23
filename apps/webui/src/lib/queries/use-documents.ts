/**
 * 文档管理 TanStack Query hooks
 * @author AaronZZH & Kiro
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { DocCreateParams, DocUpdateParams } from "@/lib/api/rest/system/document"
import { documentApi } from "@/lib/api/rest/system/document"

export const docKeys = {
  list: ["docs", "list"] as const,
  tree: ["docs", "tree"] as const,
  detail: (id: number) => ["docs", id] as const,
  relations: (id: number) => ["docs", id, "relations"] as const,
  search: (q: string) => ["docs", "search", q] as const,
  published: ["docs", "published"] as const
}

export function useDocList() {
  return useQuery({ queryKey: docKeys.list, queryFn: documentApi.list })
}

export function useDocTree() {
  return useQuery({ queryKey: docKeys.tree, queryFn: documentApi.tree })
}

export function useDocument(id: number | null) {
  return useQuery({
    queryKey: docKeys.detail(id as number),
    queryFn: () => documentApi.get(id as number),
    enabled: id != null && id > 0
  })
}

export function useDocRelations(id: number | null) {
  return useQuery({
    queryKey: docKeys.relations(id as number),
    queryFn: () => documentApi.relations(id as number),
    enabled: id != null
  })
}

export function useDocSearch(q: string) {
  return useQuery({
    queryKey: docKeys.search(q),
    queryFn: () => documentApi.search(q),
    enabled: q.length > 0
  })
}

export function usePublishedDocs() {
  return useQuery({ queryKey: docKeys.published, queryFn: documentApi.published })
}

export function useUpdateDocument() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...params }: { id: number } & DocUpdateParams) =>
      documentApi.update(id, params),
    onSuccess: (_, { id }) => {
      qc.invalidateQueries({ queryKey: docKeys.detail(id) })
      qc.invalidateQueries({ queryKey: docKeys.tree })
      qc.invalidateQueries({ queryKey: docKeys.published })
    }
  })
}

export function usePublishDocument() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => documentApi.publish(id),
    onSuccess: (_, id) => {
      qc.invalidateQueries({ queryKey: docKeys.detail(id) })
      qc.invalidateQueries({ queryKey: docKeys.tree })
      qc.invalidateQueries({ queryKey: docKeys.published })
    }
  })
}

export function useUnpublishDocument() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => documentApi.unpublish(id),
    onSuccess: (_, id) => {
      qc.invalidateQueries({ queryKey: docKeys.detail(id) })
      qc.invalidateQueries({ queryKey: docKeys.tree })
      qc.invalidateQueries({ queryKey: docKeys.published })
    }
  })
}

export function useImportDocs() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: documentApi.import,
    onSuccess: () => qc.invalidateQueries({ queryKey: docKeys.tree })
  })
}

export function useCreateDocument() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (params: DocCreateParams) => documentApi.create(params),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: docKeys.list })
      qc.invalidateQueries({ queryKey: docKeys.tree })
      qc.invalidateQueries({ queryKey: docKeys.published })
    }
  })
}
