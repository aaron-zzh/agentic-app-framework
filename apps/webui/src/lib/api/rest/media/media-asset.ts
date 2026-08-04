/**
 * AIGC Media/Asset REST 客户端。
 * @author AaronZZH & Kiro
 */

import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query"
import type { AigcAsset, AigcMedia, AigcMediaType } from "@/features/aigc/types"
import { backendApi } from "../backend-client"
import { buildQuery, type ListParams, type PageResult } from "../entity/crud"

export interface MediaListParams extends ListParams {
  mediaType?: AigcMediaType
  sourceType?: string
  projectId?: number
  keyword?: string
}

export interface AssetListParams extends ListParams {
  mediaType?: AigcMediaType
  categoryId?: number
  keyword?: string
}

export interface SaveMediaAsAssetParams {
  categoryId?: number
  scope?: string
  copyrightInfo?: string
}

export const MEDIA_QUERY_KEY = ["aigc.media"] as const
export const ASSET_QUERY_KEY = ["aigc.asset"] as const

export const mediaApi = {
  list: (params: MediaListParams = {}): Promise<PageResult<AigcMedia>> =>
    backendApi.get<PageResult<AigcMedia>>(`/aigc/media${buildQuery(params)}`),
  getById: (id: number): Promise<AigcMedia> => backendApi.get<AigcMedia>(`/aigc/media/${id}`),
  saveAsAsset: (id: number, params?: SaveMediaAsAssetParams): Promise<AigcAsset> =>
    backendApi.post<AigcAsset>(`/aigc/media/${id}/asset`, params)
}

export const assetApi = {
  list: (params: AssetListParams = {}): Promise<PageResult<AigcAsset>> =>
    backendApi.get<PageResult<AigcAsset>>(`/aigc/assets${buildQuery(params)}`)
}

/** 查询持久化媒体。 */
export function useMediaList(params: MediaListParams = {}) {
  return useQuery({
    queryKey: [...MEDIA_QUERY_KEY, "list", params] as const,
    queryFn: () => mediaApi.list(params)
  })
}

/** 查询已保存资产。 */
export function useAssetList(params: AssetListParams = {}) {
  return useQuery({
    queryKey: [...ASSET_QUERY_KEY, "list", params] as const,
    queryFn: () => assetApi.list(params)
  })
}

/** 查询多个媒体详情；对象留在 TanStack Query，调用方仅持有 ID。 */
export function useMediaDetails(ids: number[]) {
  return useQueries({
    queries: ids.map((id) => ({
      queryKey: [...MEDIA_QUERY_KEY, "detail", id] as const,
      queryFn: () => mediaApi.getById(id)
    }))
  })
}

/** 查询单个媒体。 */
export function useMediaDetail(id: number | null) {
  return useQuery({
    queryKey: [...MEDIA_QUERY_KEY, "detail", id] as const,
    queryFn: () => mediaApi.getById(id as number),
    enabled: id !== null
  })
}

/** 将媒体幂等保存为资产，并失效媒体与资产查询。 */
export function useSaveMediaAsAsset() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ mediaId, params }: { mediaId: number; params?: SaveMediaAsAssetParams }) =>
      mediaApi.saveAsAsset(mediaId, params),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: MEDIA_QUERY_KEY })
      queryClient.invalidateQueries({ queryKey: ASSET_QUERY_KEY })
    }
  })
}
