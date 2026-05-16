/**
 * useEntityList——基于 TanStack Query 的通用列表查询 Hook
 * @author AaronZZH & Kiro
 *
 * queryKey 包含 entity.slug + 所有参数，自动实现缓存隔离
 */

import { keepPreviousData, useQuery } from "@tanstack/react-query"

import type { EntityDef } from "@/features/entity-engine/types"
import { fetchList, type ListParams, type PageResult } from "@/lib/api/client"

/** useEntityList 返回值 */
export interface UseEntityListResult {
  data: Record<string, unknown>[]
  pagination: { page: number; pageSize: number; total: number }
  isLoading: boolean
  isFetching: boolean
  error: Error | null
}

/** 通用列表查询 Hook */
export function useEntityList(entity: EntityDef, params: ListParams = {}): UseEntityListResult {
  const { page = 1, pageSize = 20, sort, search, ...filters } = params

  const queryKey = [entity.slug, "list", { page, pageSize, sort, search, ...filters }]

  const { data, isLoading, isFetching, error } = useQuery<PageResult<Record<string, unknown>>>({
    queryKey,
    queryFn: () => fetchList(entity.apiPath, { page, pageSize, sort, search, ...filters }),
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
