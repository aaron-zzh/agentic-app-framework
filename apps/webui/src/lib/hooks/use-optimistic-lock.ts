/**
 * useOptimisticLock——乐观锁冲突检测与处理 hook
 *
 * 包装 useEntityMutation，检测 409 状态码，触发冲突对话框。
 *
 * @example
 * ```tsx
 * const { mutate, conflictProps } = useOptimisticLock(entity, id)
 * // 提交时自动携带 version
 * mutate({ ...formData, version: currentVersion })
 * // 渲染冲突对话框
 * <ConflictDialog {...conflictProps} />
 * ```
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useCallback, useState } from "react"
import { ApiError, fetchRecord, updateRecord } from "@/lib/api/client"
import type { EntityDef } from "@/lib/types/entity"

interface ConflictState {
  open: boolean
  myData: Record<string, unknown> | null
  serverData: Record<string, unknown> | null
}

interface UseOptimisticLockOptions {
  /** 更新成功回调 */
  onSuccess?: () => void
}

export function useOptimisticLock(
  entity: EntityDef,
  id: string,
  options?: UseOptimisticLockOptions
) {
  const queryClient = useQueryClient()
  const [conflict, setConflict] = useState<ConflictState>({
    open: false,
    myData: null,
    serverData: null
  })

  const mutation = useMutation({
    mutationFn: (data: Record<string, unknown>) => updateRecord(entity.apiPath, id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [entity.slug, "list"] })
      queryClient.invalidateQueries({ queryKey: [entity.slug, "record", id] })
      options?.onSuccess?.()
    },
    onError: async (error: Error, variables: Record<string, unknown>) => {
      if (error instanceof ApiError && error.code === 409) {
        // 获取服务端最新数据
        const serverData = await fetchRecord<Record<string, unknown>>(entity.apiPath, id)
        setConflict({ open: true, myData: variables, serverData })
      }
    }
  })

  /** 强制覆盖：用本地数据 + 服务端最新 version 重新提交 */
  const handleOverwrite = useCallback(() => {
    if (!conflict.myData || !conflict.serverData) return
    const version = conflict.serverData.version
    mutation.mutate({ ...conflict.myData, version })
    setConflict({ open: false, myData: null, serverData: null })
  }, [conflict, mutation])

  /** 刷新：放弃本地修改，使用服务端数据 */
  const handleRefresh = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: [entity.slug, "record", id] })
    setConflict({ open: false, myData: null, serverData: null })
  }, [queryClient, entity.slug, id])

  /** 取消：关闭对话框，保持当前状态 */
  const handleCancel = useCallback(() => {
    setConflict({ open: false, myData: null, serverData: null })
  }, [])

  return {
    /** 提交数据（需包含 version 字段） */
    mutate: mutation.mutate,
    mutateAsync: mutation.mutateAsync,
    isPending: mutation.isPending,
    /** 传给 ConflictDialog 的 props */
    conflictProps: {
      open: conflict.open,
      myData: conflict.myData,
      serverData: conflict.serverData,
      onOverwrite: handleOverwrite,
      onRefresh: handleRefresh,
      onCancel: handleCancel
    }
  }
}
