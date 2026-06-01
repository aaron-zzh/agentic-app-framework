/**
 * useEntityQueryWindow——通用实体列表引擎默认查询 Hook。
 *
 * 调用后端 /_query，返回列表数据、当前窗口 ids、queryToken 和 fieldSet。
 * pageSize=-1 时返回过滤后的完整窗口，由前端本地分页；否则返回服务端分页窗口。
 * @author AaronZZH & Codex
 */

import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { _mockEntityData } from "@/lib/_mock/entities"
import {
  ApiError,
  fetchList,
  fetchQueryWindow,
  type ListParams,
  type PageResult
} from "@/lib/api/rest/entity/crud"
import { useOrgStore } from "@/lib/store/org-store"
import { useUIStore } from "@/lib/store/ui-store"
import type { EntityDef } from "@/lib/types/entity"

export interface UseEntityQueryWindowResult {
  data: Record<string, unknown>[]
  ids: string[]
  queryToken?: string
  pagination: { page: number; pageSize: number; total: number }
  isLoading: boolean
  isFetching: boolean
  error: Error | null
}

export function entityQueryWindowKey(entity: EntityDef, params: unknown = {}) {
  return [entity.slug, "queryWindow", params] as const
}

export function useEntityQueryWindow(
  entity: EntityDef,
  params: ListParams = {}
): UseEntityQueryWindowResult {
  const { page = 1, pageSize = 20, sort, search, ...filters } = params
  const workspaceId = useUIStore((s) => s.currentWorkspace?.id)
  const orgId = useOrgStore((s) => s.currentOrgId)
  const queryParams = {
    workspaceId,
    orgId: orgId ?? undefined,
    pageNo: page,
    pageSize,
    sort,
    search,
    filters,
    fieldSet: "list"
  }

  const { data, isLoading, isFetching, error } = useQuery<PageResult<Record<string, unknown>>>({
    queryKey: entityQueryWindowKey(entity, queryParams),
    queryFn: async () => {
      if (process.env.NODE_ENV === "development") {
        const mock = _mockEntityData[entity.slug]
        if (mock) {
          return {
            list: mock,
            total: mock.length,
            pageNo: page,
            pageSize,
            ids: mock.map((item) => Number(item.id)).filter((id) => !Number.isNaN(id)),
            queryToken: `mock:${entity.slug}:${page}:${pageSize}`,
            fieldSet: "list",
            hasMore: false
          }
        }
      }
      try {
        return await fetchQueryWindow(entity.apiPath, {
          pageNo: page,
          pageSize,
          sort,
          search,
          fieldSet: "list",
          ...filters
        })
      } catch (error) {
        if (error instanceof ApiError && error.code === 404) {
          const fallback = await fetchList<Record<string, unknown>>(entity.apiPath, {
            pageNo: page,
            pageSize,
            sort,
            search,
            ...filters
          })
          const list = fallback.list
          return {
            list,
            total: fallback.total,
            pageNo: fallback.pageNo ?? fallback.page ?? page,
            pageSize: fallback.pageSize ?? pageSize,
            ids: list.map((item) => Number(item.id)).filter((itemId) => !Number.isNaN(itemId)),
            queryToken: `fallback:${entity.slug}:${page}:${pageSize}`,
            fieldSet: "list",
            hasMore: pageSize === -1 ? false : fallback.total > page * pageSize
          }
        }
        throw error
      }
    },
    placeholderData: keepPreviousData
  })

  return {
    data: data?.list ?? [],
    ids: data?.ids?.map(String) ?? [],
    queryToken: data?.queryToken,
    pagination: {
      page: data?.pageNo ?? page,
      pageSize: data?.pageSize ?? pageSize,
      total: data?.total ?? 0
    },
    isLoading,
    isFetching,
    error: error as Error | null
  }
}
