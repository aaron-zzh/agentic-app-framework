/**
 * 业务文档 TanStack Query hooks（对接 /api/docs）
 * @author AaronZZH & Kiro
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { docApi } from "@/lib/api/document"

export const docKeys = {
  tree: ["docs", "tree"] as const,
  detail: (id: number) => ["docs", id] as const,
  search: (q: string) => ["docs", "search", q] as const
}

export function useDocTree() {
  return useQuery({ queryKey: docKeys.tree, queryFn: docApi.tree })
}

export function useDocument(id: number | null) {
  return useQuery({
    queryKey: docKeys.detail(id ?? 0),
    queryFn: () => docApi.get(id ?? 0),
    enabled: id != null
  })
}

export function useDocSearch(q: string) {
  return useQuery({
    queryKey: docKeys.search(q),
    queryFn: () => docApi.search(q),
    enabled: q.length > 0
  })
}

export function useUpdateDocument() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, content }: { id: number; content: string }) => docApi.update(id, content),
    onSuccess: (_, { id }) => {
      qc.invalidateQueries({ queryKey: docKeys.detail(id) })
      qc.invalidateQueries({ queryKey: docKeys.tree })
    }
  })
}

export function useCreateDocument() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: docApi.create,
    onSuccess: () => qc.invalidateQueries({ queryKey: docKeys.tree })
  })
}
