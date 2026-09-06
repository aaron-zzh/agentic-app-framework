/**
 * AIGC Media/Asset REST 客户端。
 * @author AaronZZH & Kiro
 */

import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query"
import type { AigcAsset, AigcAssetTag, AigcMedia, AigcMediaType } from "@/features/aigc/types"
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
  categoryIds?: string[]
  uncategorized?: boolean
  tagIds?: string[]
  collectionId?: number
  keyword?: string
}

export interface AssetFilterOption {
  resource: string | null
  id: number
  label: string
  imageUrl: string | null
}

export interface AssetCategoryOption {
  id: number
  label: string
  parentId: number | null
  sortOrder: number
}

export interface AssetCategoryRecord {
  id: number
  name: string
  parentId: number | null
  sortOrder: number
}

export interface AssetCollectionRecord {
  id: number
  name: string
  collectionType: string | null
  description: string | null
}

export interface AssetCategoryCreateInput {
  name: string
  parentId: number | null
  sortOrder: number
}

export interface AssetCollectionCreateInput {
  name: string
  collectionType: string
  description: string | null
}

export interface AssetTagCreateInput {
  name: string
  color: string | null
}

export interface SaveMediaAsAssetParams {
  categoryId?: number
  scope?: string
  copyrightInfo?: string
}

export interface MaterializeUploadedImageInput {
  fileId: number
  name?: string
  originalProjectId: number
}

function normalizeAsset(asset: AigcAsset): AigcAsset {
  return {
    ...asset,
    tags: Array.isArray(asset.tags) ? asset.tags : []
  }
}

export const MEDIA_QUERY_KEY = ["aigc.media"] as const
export const ASSET_QUERY_KEY = ["aigc.asset"] as const
export const ASSET_FILTER_OPTIONS_QUERY_KEY = ["aigc.asset", "filter-options"] as const

export const mediaApi = {
  list: (params: MediaListParams = {}): Promise<PageResult<AigcMedia>> =>
    backendApi.get<PageResult<AigcMedia>>(`/aigc/media${buildQuery(params)}`),
  getById: (id: number): Promise<AigcMedia> => backendApi.get<AigcMedia>(`/aigc/media/${id}`),
  getByVersionId: (mediaVersionId: number): Promise<AigcMedia> =>
    backendApi.get<AigcMedia>(`/aigc/media/versions/${mediaVersionId}`),
  materializeUploadedImage: (input: MaterializeUploadedImageInput): Promise<AigcMedia> =>
    backendApi.post<AigcMedia>("/aigc/media/uploaded-images/_materialize", input),
  saveAsAsset: async (id: number, params?: SaveMediaAsAssetParams): Promise<AigcAsset> =>
    normalizeAsset(await backendApi.post<AigcAsset>(`/aigc/media/${id}/asset`, params))
}

export const assetApi = {
  list: async (params: AssetListParams = {}): Promise<PageResult<AigcAsset>> => {
    const page = await backendApi.get<PageResult<AigcAsset>>(`/aigc/assets${buildQuery(params)}`)
    return { ...page, list: page.list.map(normalizeAsset) }
  },
  categoryOptions: async (): Promise<AssetCategoryOption[]> => {
    const page = await backendApi.get<PageResult<AssetCategoryRecord>>(
      "/aigc/asset-categories/_query",
      { params: { pageSize: -1, fieldSet: "list" } }
    )
    return page.list.map((category) => ({
      id: category.id,
      label: category.name,
      parentId: category.parentId,
      sortOrder: category.sortOrder
    }))
  },
  tagOptions: (): Promise<AssetFilterOption[]> =>
    backendApi.get<AssetFilterOption[]>("/aigc/asset-tags/_options", {
      params: { limit: 100 }
    }),
  collectionOptions: (): Promise<AssetFilterOption[]> =>
    backendApi.get<AssetFilterOption[]>("/aigc/asset-collections/_options", {
      params: { limit: 100 }
    }),
  createCategory: (input: AssetCategoryCreateInput): Promise<AssetCategoryRecord> =>
    backendApi.post<AssetCategoryRecord>("/aigc/asset-categories", input),
  createCollection: (input: AssetCollectionCreateInput): Promise<AssetCollectionRecord> =>
    backendApi.post<AssetCollectionRecord>("/aigc/asset-collections", input),
  createTag: (input: AssetTagCreateInput): Promise<AigcAssetTag> =>
    backendApi.post<AigcAssetTag>("/aigc/asset-tags", input),
  deleteCategory: (id: number): Promise<void> =>
    backendApi.delete<void>(`/aigc/asset-categories/${id}`),
  deleteCollection: (id: number): Promise<void> =>
    backendApi.delete<void>(`/aigc/asset-collections/${id}`),
  deleteTag: (id: number): Promise<void> => backendApi.delete<void>(`/aigc/asset-tags/${id}`),
  replaceTags: (assetId: number, tagIds: number[]): Promise<AigcAssetTag[]> =>
    backendApi.put<AigcAssetTag[]>(`/aigc/assets/${assetId}/tags`, { tagIds })
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

/** 按单个媒体版本 ID 查询封面预览，查询结果按版本独立缓存。 */
export function useMediaByVersionId(mediaVersionId: number | null) {
  return useQuery({
    queryKey: [...MEDIA_QUERY_KEY, "version", mediaVersionId] as const,
    queryFn: () => mediaApi.getByVersionId(mediaVersionId as number),
    enabled: mediaVersionId !== null
  })
}

/** 并行查询资产分类、标签和集合筛选选项。 */
export function useAssetFilterOptions() {
  const [categories, tags, collections] = useQueries({
    queries: [
      {
        queryKey: [...ASSET_FILTER_OPTIONS_QUERY_KEY, "categories"] as const,
        queryFn: assetApi.categoryOptions
      },
      {
        queryKey: [...ASSET_FILTER_OPTIONS_QUERY_KEY, "tags"] as const,
        queryFn: assetApi.tagOptions
      },
      {
        queryKey: [...ASSET_FILTER_OPTIONS_QUERY_KEY, "collections"] as const,
        queryFn: assetApi.collectionOptions
      }
    ]
  })

  return {
    categories: categories.data ?? [],
    tags: tags.data ?? [],
    collections: collections.data ?? [],
    isLoading: categories.isLoading || tags.isLoading || collections.isLoading
  }
}

/** 快速创建资产分类。 */
export function useCreateAssetCategory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: assetApi.createCategory,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ASSET_FILTER_OPTIONS_QUERY_KEY })
  })
}

/** 快速创建资产集合。 */
export function useCreateAssetCollection() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: assetApi.createCollection,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ASSET_FILTER_OPTIONS_QUERY_KEY })
  })
}

/** 快速创建资产标签。 */
export function useCreateAssetTag() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: assetApi.createTag,
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: [...ASSET_FILTER_OPTIONS_QUERY_KEY, "tags"]
      })
  })
}

/** 删除资产分类。 */
export function useDeleteAssetCategory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: assetApi.deleteCategory,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: [...ASSET_FILTER_OPTIONS_QUERY_KEY, "categories"] })
  })
}

/** 删除资产集合。 */
export function useDeleteAssetCollection() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: assetApi.deleteCollection,
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: [...ASSET_FILTER_OPTIONS_QUERY_KEY, "collections"]
      })
  })
}

/** 删除资产标签。 */
export function useDeleteAssetTag() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: assetApi.deleteTag,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: [...ASSET_FILTER_OPTIONS_QUERY_KEY, "tags"] })
  })
}

/** 整体替换资产标签，并同步当前资产列表缓存。 */
export function useReplaceAssetTags() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ assetId, tagIds }: { assetId: number; tagIds: number[] }) =>
      assetApi.replaceTags(assetId, tagIds),
    onSuccess: (tags, { assetId }) => {
      queryClient.setQueriesData<PageResult<AigcAsset>>(
        { queryKey: [...ASSET_QUERY_KEY, "list"] },
        (page) =>
          page
            ? {
                ...page,
                list: page.list.map((asset) => (asset.id === assetId ? { ...asset, tags } : asset))
              }
            : page
      )
      queryClient.invalidateQueries({ queryKey: ASSET_FILTER_OPTIONS_QUERY_KEY })
    }
  })
}

/** 按媒体版本 ID 并行查询媒体详情。 */
export function useMediaVersionDetails(mediaVersionIds: number[]) {
  return useQueries({
    queries: mediaVersionIds.map((mediaVersionId) => ({
      queryKey: [...MEDIA_QUERY_KEY, "version", mediaVersionId] as const,
      queryFn: () => mediaApi.getByVersionId(mediaVersionId)
    }))
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
