/**
 * 回收站 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { type TrashListParams, trashApi } from "@/lib/api/rest/entity/trash"
import { notify } from "@/lib/notification"

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
