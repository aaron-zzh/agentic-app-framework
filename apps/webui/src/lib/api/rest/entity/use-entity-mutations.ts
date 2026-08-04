/**
 * 实体 CRUD Hooks——单条查询、创建/更新、删除
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { crudDetailKey, crudKey, fromEntityDef } from "@/lib/api/rest/crud"
import type { EntityDef } from "@/lib/types/entity"
import { createRecord, deleteRecords, fetchRecord, updateRecord } from "./crud"

/** 单条记录查询 */
export function useEntityRecord(entity: EntityDef, id: string | undefined) {
  const resource = fromEntityDef(entity)
  return useQuery<Record<string, unknown>>({
    queryKey: crudDetailKey(resource, id, { fieldSet: "detail" }),
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
    onSuccess: () => queryClient.invalidateQueries({ queryKey: crudKey(resource) })
  })
}

/** 删除记录（支持批量） */
export function useEntityDelete(entity: EntityDef) {
  const queryClient = useQueryClient()
  const resource = fromEntityDef(entity)

  return useMutation({
    mutationFn: (ids: string[]) => deleteRecords(resource, ids),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: crudKey(resource) })
  })
}
