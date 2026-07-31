/**
 * 回收站 API 客户端
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { notify } from "@/lib/notification"
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

const KEYS = {
  all: ["trash"] as const,
  list: (params: TrashListParams) => ["trash", "list", params] as const
}

/** 回收站列表 */
export function useTrashList(params: TrashListParams = {}) {
  return useQuery({
    queryKey: KEYS.list(params),
    queryFn: () => trashApi.list(params)
  })
}

/** 恢复记录 */
export function useTrashRestore() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ids: string[]) => trashApi.restore(ids),
    onSuccess: () => {
      notify.success("恢复成功")
      qc.invalidateQueries({ queryKey: KEYS.all })
    },
    onError: () => {
      notify.error("恢复失败")
    }
  })
}

/** 彻底删除 */
export function useTrashPurge() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ids: string[]) => trashApi.purge(ids),
    onSuccess: () => {
      notify.success("已彻底删除")
      qc.invalidateQueries({ queryKey: KEYS.all })
    },
    onError: () => {
      notify.error("删除失败")
    }
  })
}
