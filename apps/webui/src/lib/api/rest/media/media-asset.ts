/**
 * 素材资源 API 客户端
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { MediaAssetVO, MediaCategoryVO, MediaTagVO } from "@/features/aigc/types"
import { backendApi } from "../backend-client"
import { buildQuery, type ListParams, type PageResult } from "../entity/crud"

/** 旧路径——生成面板内的素材引用（保留兼容） */
const LEGACY_PATH = "/media-assets"
/** 新路径——素材库管理 */
const API_PATH = "/aigc/assets"

export interface RegenerateParams {
  assetId: number
  newPrompt?: string
  newSeed?: number
  newStyle?: string
  modelId?: string
}

export const mediaAssetApi = {
  /** 素材列表（旧接口，生成面板用） */
  legacyList: (params: ListParams = {}): Promise<PageResult<MediaAssetVO>> =>
    backendApi.get<PageResult<MediaAssetVO>>(`${LEGACY_PATH}${buildQuery(params)}`),

  /** 素材搜索（旧接口，@提及用） */
  legacySearch: (keyword: string): Promise<MediaAssetVO[]> =>
    backendApi.get<MediaAssetVO[]>(`${LEGACY_PATH}/search?keyword=${encodeURIComponent(keyword)}`),

  /** 素材列表（分页+筛选） */
  list: (params: ListParams = {}): Promise<PageResult<MediaAssetVO>> =>
    backendApi.get<PageResult<MediaAssetVO>>(`${API_PATH}${buildQuery(params)}`),

  /** 素材搜索（关键词匹配名称/标签） */
  search: (keyword: string): Promise<MediaAssetVO[]> =>
    backendApi.get<MediaAssetVO[]>(`${API_PATH}/search?keyword=${encodeURIComponent(keyword)}`),

  /** 获取单条素材详情 */
  getById: (id: number): Promise<MediaAssetVO> => backendApi.get<MediaAssetVO>(`${API_PATH}/${id}`),

  /** 更新素材（名称/标签/分类） */
  update: (
    id: number,
    dto: { name?: string; tags?: string; categoryId?: number | null }
  ): Promise<MediaAssetVO> => backendApi.put<MediaAssetVO>(`${API_PATH}/${id}`, dto),

  /** 删除素材 */
  delete: (id: number): Promise<void> => backendApi.delete<void>(`${API_PATH}/${id}`),

  /** 重新生成素材 */
  regenerate: (params: RegenerateParams): Promise<MediaAssetVO> =>
    backendApi.post<MediaAssetVO>(`${API_PATH}/regenerate`, params),

  /** 获取素材变体列表 */
  getVariants: (id: number): Promise<MediaAssetVO[]> =>
    backendApi.get<MediaAssetVO[]>(`${API_PATH}/${id}/variants`),

  /** 获取分类树 */
  getCategories: (): Promise<MediaCategoryVO[]> =>
    backendApi.get<MediaCategoryVO[]>("/aigc/categories"),

  /** 创建分类 */
  createCategory: (dto: {
    name: string
    parentId?: number | null
    sortOrder?: number
  }): Promise<MediaCategoryVO> => backendApi.post<MediaCategoryVO>("/aigc/categories", dto),

  /** 更新分类 */
  updateCategory: (
    id: number,
    dto: { name: string; parentId?: number | null; sortOrder?: number }
  ): Promise<MediaCategoryVO> => backendApi.put<MediaCategoryVO>(`/aigc/categories/${id}`, dto),

  /** 删除分类 */
  deleteCategory: (id: number): Promise<void> => backendApi.delete<void>(`/aigc/categories/${id}`),

  /** 获取标签列表 */
  getTags: (): Promise<MediaTagVO[]> => backendApi.get<MediaTagVO[]>("/aigc/tags"),

  /** 移动素材到指定分组 */
  moveToGroup: (assetId: number, groupId: number): Promise<void> =>
    backendApi.patch<void>(`${API_PATH}/${assetId}/group`, { groupId }),

  /** 删除素材组及组内所有素材和文件 */
  deleteGroup: (groupId: number): Promise<void> =>
    backendApi.delete<void>(`${API_PATH}/group/${groupId}`)
}

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

/** 更新素材（名称/标签/分类） */
export function useUpdateMediaAsset() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      id,
      ...dto
    }: {
      id: number
      name?: string
      tags?: string
      categoryId?: number | null
    }) => mediaAssetApi.update(id, dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: KEYS.all })
      queryClient.invalidateQueries({ queryKey: ["media-asset-library"] })
    }
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

/** 创建分类 */
export function useCreateCategory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (dto: { name: string; parentId?: number | null }) =>
      mediaAssetApi.createCategory(dto),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: KEYS.categories })
  })
}

/** 删除分类 */
export function useDeleteCategory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => mediaAssetApi.deleteCategory(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: KEYS.categories })
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
