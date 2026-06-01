/**
 * 开发文档 TanStack Query hooks（对接 /api/autodev/docs）
 * @author AaronZZH & Kiro
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { autodevDocApi } from "@/lib/api/rest/knowledge/autodev-document"

export const autodevDocKeys = {
  tree: ["autodev-docs", "tree"] as const,
  detail: (id: number) => ["autodev-docs", id] as const,
  relations: (id: number) => ["autodev-docs", id, "relations"] as const,
  search: (q: string) => ["autodev-docs", "search", q] as const
}

export function useAutodevDocTree() {
  return useQuery({ queryKey: autodevDocKeys.tree, queryFn: autodevDocApi.tree })
}

export function useAutodevDoc(id: number | null) {
  return useQuery({
    queryKey: autodevDocKeys.detail(id as NonNullable<typeof id>),
    queryFn: () => autodevDocApi.get(id as NonNullable<typeof id>),
    enabled: id != null
  })
}

export function useAutodevDocRelations(id: number | null) {
  return useQuery({
    queryKey: autodevDocKeys.relations(id as NonNullable<typeof id>),
    queryFn: () => autodevDocApi.relations(id as NonNullable<typeof id>),
    enabled: id != null
  })
}

export function useUpdateAutodevDoc() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, content }: { id: number; content: string }) =>
      autodevDocApi.update(id, content),
    onSuccess: (_, { id }) => {
      qc.invalidateQueries({ queryKey: autodevDocKeys.detail(id) })
      qc.invalidateQueries({ queryKey: autodevDocKeys.tree })
    }
  })
}

export function useCreateAutodevDoc() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: autodevDocApi.create,
    onSuccess: () => qc.invalidateQueries({ queryKey: autodevDocKeys.tree })
  })
}

export function useImportAutodevDocs() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: autodevDocApi.import,
    onSuccess: () => qc.invalidateQueries({ queryKey: autodevDocKeys.tree })
  })
}
