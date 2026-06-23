/**
 * 生成历史 API 客户端
 * @author AaronZZH & Kiro
 */

import { type PageResult, request } from "../entity/crud"

/** 生成历史记录 */
export interface GenerationHistoryItem {
  id: string
  type: "image" | "video"
  prompt: string
  model: string
  thumbnail: string
  url: string
  createdAt: string
  width?: number
  height?: number
}

interface HistoryParams {
  page?: number
  size?: number
  type?: "image" | "video"
}

export const generationHistoryApi = {
  /** 获取生成历史列表 */
  list: (params: HistoryParams): Promise<PageResult<GenerationHistoryItem>> => {
    const query = new URLSearchParams({
      page: String(params.page ?? 0),
      size: String(params.size ?? 20),
      ...(params.type && { type: params.type })
    })
    return request<PageResult<GenerationHistoryItem>>(`/aigc/history?${query.toString()}`)
  }
}
