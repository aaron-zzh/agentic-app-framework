/**
 * 实体 CRUD Hooks——单条查询、创建/更新、删除
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { fromEntityDef } from "@/lib/api/rest/crud"
import { createRecord, deleteRecords, fetchRecord, updateRecord } from "@/lib/api/rest/entity/crud"
import type { EntityDef } from "@/lib/types/entity"

/** 单条记录查询 */
export function useEntityRecord(entity: EntityDef, id: string | undefined) {
  const resource = fromEntityDef(entity)
  return useQuery<Record<string, unknown>>({
    queryKey: [entity.slug, "record", id],
    queryFn: () => fetchRecord(resource, id ?? ""),
    enabled: !!id
  })
}

/** 创建/更新记录（optimistic update） */
export function useEntityMutation(entity: EntityDef, id?: string) {
  const queryClient = useQueryClient()
  const resource = fromEntityDef(entity)

  return useMutation({
    mutationFn: (data: Record<string, unknown>) =>
      id ? updateRecord(resource, id, data) : createRecord(resource, data),
    onSuccess: () => {
      // 刷新列表和当前记录缓存
      queryClient.invalidateQueries({ queryKey: [entity.slug, "list"] })
      if (id) {
        queryClient.invalidateQueries({ queryKey: [entity.slug, "record", id] })
      }
    }
  })
}

/** 删除记录（支持批量） */
export function useEntityDelete(entity: EntityDef) {
  const queryClient = useQueryClient()
  const resource = fromEntityDef(entity)

  return useMutation({
    mutationFn: (ids: string[]) => deleteRecords(resource, ids),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [entity.slug, "list"] })
    }
  })
}
