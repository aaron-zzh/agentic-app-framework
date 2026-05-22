/**
 * 文档管理 TanStack Query hooks
 * @author AaronZZH & Kiro
 */
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { documentApi } from "@/lib/api/document"

export const docKeys = {
  tree: ["docs", "tree"] as const,
  detail: (id: number) => ["docs", id] as const,
  relations: (id: number) => ["docs", id, "relations"] as const,
  search: (q: string) => ["docs", "search", q] as const,
}

export function useDocTree() {
  return useQuery({
    queryKey: docKeys.tree,
    queryFn: documentApi.tree,
  })
}

export function useDocument(id: number | null) {
  return useQuery({
    queryKey: docKeys.detail(id!),
    queryFn: () => documentApi.get(id!),
    enabled: id != null,
  })
}

export function useDocRelations(id: number | null) {
  return useQuery({
    queryKey: docKeys.relations(id!),
    queryFn: () => documentApi.relations(id!),
    enabled: id != null,
  })
}

export function useDocSearch(q: string) {
  return useQuery({
    queryKey: docKeys.search(q),
    queryFn: () => documentApi.search(q),
    enabled: q.length > 0,
  })
}

export function useUpdateDocument() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, content }: { id: number; content: string }) =>
      documentApi.update(id, content),
    onSuccess: (_, { id }) => {
      qc.invalidateQueries({ queryKey: docKeys.detail(id) })
      qc.invalidateQueries({ queryKey: docKeys.tree })
    },
  })
}

export function useImportDocs() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: documentApi.import,
    onSuccess: () => qc.invalidateQueries({ queryKey: docKeys.tree }),
  })
}
