/**
 * EntityDef 管理 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import {
  type EntityDefInput,
  type EntityDefUpdateInput,
  entityDefApi
} from "@/lib/api/rest/entity/entity-def"

const ENTITY_DEFS_KEY = ["entity-defs"]
const ENTITY_BOOTSTRAP_KEY = ["entity-def-bootstrap"]

interface UseEntityDefsOptions {
  enabled?: boolean
}

/** 查询工作区视图引擎需要的完整实体元数据。 */
export function useEntityBootstrap({ enabled = true }: UseEntityDefsOptions = {}) {
  return useQuery({
    queryKey: ENTITY_BOOTSTRAP_KEY,
    queryFn: () => entityDefApi.bootstrap(),
    enabled
  })
}

/** 查询实体编辑器需要的 EntityDef 记录。 */
export function useEntityDefs({ enabled = true }: UseEntityDefsOptions = {}) {
  return useQuery({
    queryKey: ENTITY_DEFS_KEY,
    queryFn: () => entityDefApi.list(),
    enabled
  })
}

/** 查询单个实体定义 */
export function useEntityDef(id: number | undefined) {
  return useQuery({
    queryKey: [...ENTITY_DEFS_KEY, id],
    queryFn: () => entityDefApi.get(id as number),
    enabled: id !== undefined
  })
}

/** 创建实体定义 */
export function useCreateEntityDef() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: EntityDefInput) => entityDefApi.create(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ENTITY_DEFS_KEY })
      qc.invalidateQueries({ queryKey: ENTITY_BOOTSTRAP_KEY })
    }
  })
}

/** 更新实体定义 */
export function useUpdateEntityDef() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: EntityDefUpdateInput }) =>
      entityDefApi.update(id, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ENTITY_DEFS_KEY })
      qc.invalidateQueries({ queryKey: ENTITY_BOOTSTRAP_KEY })
    }
  })
}

/** 删除实体定义 */
export function useDeleteEntityDef() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => entityDefApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ENTITY_DEFS_KEY })
      qc.invalidateQueries({ queryKey: ENTITY_BOOTSTRAP_KEY })
    }
  })
}
