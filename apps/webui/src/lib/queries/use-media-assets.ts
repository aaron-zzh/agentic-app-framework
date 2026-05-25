/**
 * 素材资源 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import type { ListParams } from "@/lib/api/client"
import { mediaAssetApi } from "@/lib/api/media-asset"

const KEYS = {
  all: ["media-assets"] as const,
  list: (params: ListParams) => ["media-assets", "list", params] as const,
  search: (keyword: string) => ["media-assets", "search", keyword] as const,
}

/** 素材列表（分页+筛选） */
export function useMediaAssets(params: ListParams = {}) {
  return useQuery({
    queryKey: KEYS.list(params),
    queryFn: () => mediaAssetApi.list(params),
  })
}

/** 素材搜索（用于 @引用） */
export function useMediaAssetSearch(keyword: string) {
  return useQuery({
    queryKey: KEYS.search(keyword),
    queryFn: () => mediaAssetApi.search(keyword),
    enabled: keyword.length > 0,
  })
}
