/**
 * 素材资源 API 客户端
 * @author AaronZZH & Kiro
 */

import { fetchList, request, type ListParams, type PageResult } from "./client"
import type { MediaAsset } from "@/features/aigc/types"

const API_PATH = "/media-assets"

export const mediaAssetApi = {
  /** 素材列表（分页+筛选） */
  list: (params: ListParams = {}): Promise<PageResult<MediaAsset>> =>
    fetchList<MediaAsset>(API_PATH, params),

  /** 素材搜索（关键词匹配名称/标签） */
  search: (keyword: string): Promise<MediaAsset[]> =>
    request<MediaAsset[]>(`${API_PATH}/search?keyword=${encodeURIComponent(keyword)}`),
}
