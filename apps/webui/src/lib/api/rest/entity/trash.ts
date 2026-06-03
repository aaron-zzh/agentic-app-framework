/**
 * 回收站 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"
import type { PageResult } from "./crud"

/** 回收站记录 */
export interface TrashItemVO {
  id: string
  entityType: string
  title: string
  deletedBy: string
  deletedAt: string
}

export interface TrashListParams {
  page?: number
  pageSize?: number
  entityType?: string
}

export const trashApi = {
  /** 回收站列表 */
  list: (params: TrashListParams = {}) =>
    backendApi.get<PageResult<TrashItemVO>>("/trash", { params }),

  /** 恢复记录 */
  restore: (ids: string[]) => backendApi.post<void>("/trash/restore", { ids }),

  /** 彻底删除 */
  purge: (ids: string[]) => backendApi.delete<void>("/trash/purge", { data: { ids } })
}
