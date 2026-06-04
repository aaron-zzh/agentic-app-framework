/**
 * useEntityDetail——获取单条实体记录
 * @author AaronZZH & Kiro
 */

import { useQuery, useQueryClient } from "@tanstack/react-query"
import { _mockEntityData } from "@/lib/_mock/entities"
import { fromEntityDef } from "@/lib/api/rest/crud"
import { fetchRecord, type PageResult } from "@/lib/api/rest/entity/crud"
import type { EntityDef } from "@/lib/types/entity"

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
  const resource = fromEntityDef(entity)
  const initialData =
    options.initialData ?? findListInitialData(queryClient, entity.slug, id, options.queryToken)

  return useQuery<Record<string, unknown> | null>({
    queryKey: [entity.slug, "detail", { id, queryToken: options.queryToken, fieldSet: "detail" }],
    queryFn: async () => {
      if (!id) return null
      // mock 仅在开发环境且后端未就绪时使用
      if (process.env.NODE_ENV === "development") {
        const mock = _mockEntityData[entity.slug]
        if (mock) {
          return mock.find((r) => r.id === id) ?? null
        }
      }
      return fetchRecord(resource, id, {
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
  entitySlug: string,
  id: string | undefined,
  queryToken?: string
): Record<string, unknown> | undefined {
  if (!id) return undefined
  const windows = queryClient.getQueriesData<PageResult<Record<string, unknown>>>({
    queryKey: [entitySlug, "queryWindow"]
  })
  for (const [, window] of windows) {
    if (!window) continue
    if (queryToken && window.queryToken !== queryToken) continue
    const record = window.list.find((item) => String(item.id) === id)
    if (record) return record
  }
  return undefined
}
