/**
 * 素材资源 API 客户端
 * @author AaronZZH & Kiro
 */

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

  /** 获取标签列表 */
  getTags: (): Promise<MediaTagVO[]> => backendApi.get<MediaTagVO[]>("/aigc/tags"),

  /** 移动素材到指定分组 */
  moveToGroup: (assetId: number, groupId: number): Promise<void> =>
    backendApi.patch<void>(`${API_PATH}/${assetId}/group`, { groupId }),

  /** 删除素材组及组内所有素材和文件 */
  deleteGroup: (groupId: number): Promise<void> =>
    backendApi.delete<void>(`${API_PATH}/group/${groupId}`)
}
