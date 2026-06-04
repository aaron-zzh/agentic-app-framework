/**
 * useEntityList——基础列表查询 Hook。
 *
 * 通用实体列表引擎默认使用 useEntityQueryWindow，以获得 queryToken、ids 和字段集。
 * 本 Hook 保留给简单列表、旧接口兼容和不需要详情快速切换的视图。
 * @author AaronZZH & Kiro
 */

import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { _mockEntityData } from "@/lib/_mock/entities"
import { fromEntityDef } from "@/lib/api/rest/crud"
import { fetchList, type ListParams, type PageResult } from "@/lib/api/rest/entity/crud"
import { useUIStore } from "@/lib/store/ui-store"
import type { EntityDef } from "@/lib/types/entity"

export interface UseEntityListResult {
  data: Record<string, unknown>[]
  pagination: { page: number; pageSize: number; total: number }
  isLoading: boolean
  isFetching: boolean
  error: Error | null
}

export function useEntityList(entity: EntityDef, params: ListParams = {}): UseEntityListResult {
  const { page = 1, pageSize = 20, sort, search, ...filters } = params
  const workspaceId = useUIStore((s) => s.currentWorkspace?.id)
  const resource = fromEntityDef(entity)

  // workspaceId 加入 queryKey，切换工作区时自动重新请求
  const queryKey = [entity.slug, "list", { workspaceId, page, pageSize, sort, search, ...filters }]

  const { data, isLoading, isFetching, error } = useQuery<PageResult<Record<string, unknown>>>({
    queryKey,
    queryFn: async () => {
      // mock 仅在开发环境且后端未就绪时使用
      if (process.env.NODE_ENV === "development") {
        const mock = _mockEntityData[entity.slug]
        if (mock) {
          return { list: mock, total: mock.length, page, pageSize }
        }
      }
      return fetchList(resource, { pageNo: page, page, pageSize, sort, search, ...filters })
    },
    placeholderData: keepPreviousData
  })

  return {
    data: data?.list ?? [],
    pagination: {
      page: data?.page ?? data?.pageNo ?? page,
      pageSize: data?.pageSize ?? pageSize,
      total: data?.total ?? 0
    },
    isLoading,
    isFetching,
    error: error as Error | null
  }
}
