/**
 * 素材资源 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { ListParams } from "@/lib/api/rest/entity/crud"
import { mediaAssetApi, type RegenerateParams } from "@/lib/api/rest/media/media-asset"

const KEYS = {
  all: ["media-assets"] as const,
  list: (params: ListParams) => ["media-assets", "list", params] as const,
  search: (keyword: string) => ["media-assets", "search", keyword] as const,
  detail: (id: number) => ["media-assets", "detail", id] as const,
  variants: (id: number) => ["media-assets", "variants", id] as const,
  categories: ["media-assets", "categories"] as const,
  tags: ["media-assets", "tags"] as const
}

/** 素材列表——生成面板用 */
export function useMediaAssets(params: ListParams = {}) {
  return useQuery({
    queryKey: KEYS.list(params),
    queryFn: () => mediaAssetApi.list(params)
  })
}

/** 素材搜索——@提及用 */
export function useMediaAssetSearch(keyword: string) {
  return useQuery({
    queryKey: KEYS.search(keyword),
    queryFn: () => mediaAssetApi.search(keyword),
    enabled: keyword.length > 0
  })
}

/** 素材库列表（新 MediaAssetVO 类型，素材库页面用） */
export function useMediaAssetList(params: ListParams = {}) {
  return useQuery({
    queryKey: ["media-asset-library", "list", params] as const,
    queryFn: () => mediaAssetApi.list(params)
  })
}

/** 素材详情 */
export function useMediaAssetDetail(id: number | null) {
  return useQuery({
    queryKey: KEYS.detail(id as NonNullable<typeof id>),
    queryFn: () => mediaAssetApi.getById(id as NonNullable<typeof id>),
    enabled: id !== null
  })
}

/** 删除素材 */
export function useDeleteMediaAsset() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => mediaAssetApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: KEYS.all })
      queryClient.invalidateQueries({ queryKey: ["media-asset-library"] })
    }
  })
}

/** 重新生成素材 */
export function useRegenerateAsset() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (params: RegenerateParams) => mediaAssetApi.regenerate(params),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: KEYS.all })
      queryClient.invalidateQueries({ queryKey: ["media-asset-library"] })
    }
  })
}

/** 素材变体列表 */
export function useMediaAssetVariants(id: number | null) {
  return useQuery({
    queryKey: KEYS.variants(id as NonNullable<typeof id>),
    queryFn: () => mediaAssetApi.getVariants(id as NonNullable<typeof id>),
    enabled: id !== null
  })
}

/** 素材分类树 */
export function useMediaCategories() {
  return useQuery({
    queryKey: KEYS.categories,
    queryFn: () => mediaAssetApi.getCategories(),
    staleTime: 5 * 60 * 1000
  })
}

/** 素材标签列表 */
export function useMediaTags() {
  return useQuery({
    queryKey: KEYS.tags,
    queryFn: () => mediaAssetApi.getTags(),
    staleTime: 5 * 60 * 1000
  })
}
