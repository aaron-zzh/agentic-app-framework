/**
 * EntityDef 管理 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { entityDefApi, type EntityDefInput } from "@/lib/api/entity-def"

const ENTITY_DEFS_KEY = ["entity-defs"]

/** 查询所有实体定义 */
export function useEntityDefs() {
  return useQuery({
    queryKey: ENTITY_DEFS_KEY,
    queryFn: () => entityDefApi.list()
  })
}

/** 查询单个实体定义 */
export function useEntityDef(id: string | undefined) {
  return useQuery({
    queryKey: [...ENTITY_DEFS_KEY, id],
    queryFn: () => entityDefApi.get(id!),
    enabled: !!id
  })
}

/** 创建实体定义 */
export function useCreateEntityDef() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: EntityDefInput) => entityDefApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ENTITY_DEFS_KEY })
  })
}

/** 更新实体定义 */
export function useUpdateEntityDef() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: EntityDefInput }) =>
      entityDefApi.update(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ENTITY_DEFS_KEY })
  })
}

/** 删除实体定义 */
export function useDeleteEntityDef() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => entityDefApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ENTITY_DEFS_KEY })
  })
}
