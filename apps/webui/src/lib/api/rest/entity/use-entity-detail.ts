/**
 * useEntityDetail——获取单条实体记录
 * @author AaronZZH & Kiro
 */

import { useQuery, useQueryClient } from "@tanstack/react-query"
import { crudDetailKey } from "@/lib/api/rest/crud"
import type { EntityDef } from "@/lib/types/entity"
import { fetchRecord, type PageResult } from "../crud"
import { entityQueryWindowPrefix } from "./use-entity-query-window"

interface UseEntityDetailOptions {
  queryToken?: string
  initialData?: Record<string, unknown>
}

export function useEntityDetail(
  entity: EntityDef,
  id: string | undefined,
  options: UseEntityDetailOptions = {}
) {
  const queryClient = useQueryClient()
  const initialData =
    options.initialData ?? findListInitialData(queryClient, entity, id, options.queryToken)

  return useQuery<Record<string, unknown> | null>({
    queryKey: crudDetailKey(entity, id, {
      queryToken: options.queryToken,
      fieldSet: "detail"
    }),
    queryFn: async () => {
      if (!id) return null
      return fetchRecord(entity, id, {
        queryToken: options.queryToken,
        fieldSet: "detail"
      })
    },
    enabled: !!id,
    initialData: initialData ?? undefined
  })
}

function findListInitialData(
  queryClient: ReturnType<typeof useQueryClient>,
  entity: EntityDef,
  id: string | undefined,
  queryToken?: string
): Record<string, unknown> | undefined {
  if (!id) return undefined
  const windows = queryClient.getQueriesData<PageResult<Record<string, unknown>>>({
    queryKey: entityQueryWindowPrefix(entity)
  })
  for (const [, window] of windows) {
    if (!window) continue
    if (queryToken && window.queryToken !== queryToken) continue
    const record = window.list.find((item) => String(item.id) === id)
    if (record) return record
  }
  return undefined
}
