/**
 * 实体 CRUD Hooks——单条查询、创建/更新、删除
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { createRecord, deleteRecord, fetchRecord, updateRecord } from "@/lib/api/client"
import type { EntityDef } from "@/lib/types/entity"

/** 单条记录查询 */
export function useEntityRecord(entity: EntityDef, id: string | undefined) {
  return useQuery<Record<string, unknown>>({
    queryKey: [entity.slug, "record", id],
    queryFn: () => fetchRecord(entity.apiPath, id ?? ""),
    enabled: !!id
  })
}

/** 创建/更新记录（optimistic update） */
export function useEntityMutation(entity: EntityDef, id?: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: Record<string, unknown>) =>
      id ? updateRecord(entity.apiPath, id, data) : createRecord(entity.apiPath, data),
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

  return useMutation({
    mutationFn: (ids: string[]) => deleteRecord(entity.apiPath, ids),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [entity.slug, "list"] })
    }
  })
}
