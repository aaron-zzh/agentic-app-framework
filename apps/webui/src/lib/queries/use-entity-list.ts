/**
 * useEntityList——基于 TanStack Query 的通用列表查询 Hook
 * @author AaronZZH & Kiro
 */

import { keepPreviousData, useQuery } from "@tanstack/react-query"
import type { EntityDef } from "@/features/entity-engine/types"
import { fetchList, type ListParams, type PageResult } from "@/lib/api/client"
import { _mockEntityData } from "@/lib/_mock/entities"

export interface UseEntityListResult {
  data: Record<string, unknown>[]
  pagination: { page: number; pageSize: number; total: number }
  isLoading: boolean
  isFetching: boolean
  error: Error | null
}

export function useEntityList(entity: EntityDef, params: ListParams = {}): UseEntityListResult {
  const { page = 1, pageSize = 20, sort, search, ...filters } = params

  const queryKey = [entity.slug, "list", { page, pageSize, sort, search, ...filters }]

  const { data, isLoading, isFetching, error } = useQuery<PageResult<Record<string, unknown>>>({
    queryKey,
    queryFn: async () => {
      // TODO: 后端就绪后移除 mock，直接用 fetchList
      const mock = _mockEntityData[entity.slug]
      if (mock) {
        return { list: mock, total: mock.length, page, pageSize }
      }
      return fetchList(entity.apiPath, { page, pageSize, sort, search, ...filters })
    },
    placeholderData: keepPreviousData
  })

  return {
    data: data?.list ?? [],
    pagination: {
      page: data?.page ?? page,
      pageSize: data?.pageSize ?? pageSize,
      total: data?.total ?? 0
    },
    isLoading,
    isFetching,
    error: error as Error | null
  }
}
