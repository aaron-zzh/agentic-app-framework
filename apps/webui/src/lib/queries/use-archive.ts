/**
 * 数据归档 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQueryClient } from "@tanstack/react-query"
import { archiveApi } from "@/lib/api/rest/entity/archive"
import { notify } from "@/lib/notification"

/** 归档记录 */
export function useArchive(entity: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => archiveApi.archive(entity, id),
    onSuccess: () => {
      notify.success("已归档")
      qc.invalidateQueries({ queryKey: [entity] })
    },
    onError: () => {
      notify.error("归档失败")
    }
  })
}

/** 恢复到活跃 */
export function useUnarchive(entity: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => archiveApi.unarchive(entity, id),
    onSuccess: () => {
      notify.success("已恢复到活跃")
      qc.invalidateQueries({ queryKey: [entity] })
    },
    onError: () => {
      notify.error("恢复失败")
    }
  })
}
