/**
 * useMutationAction——通用 mutation 封装
 * 成功后自动 toast + invalidate 查询缓存，错误已由 backend-client interceptor 统一处理。
 *
 * @example
 * ```ts
 * const create = useMutationAction(
 *   (data: CreateParams) => documentApi.create(data),
 *   { queryKey: docKeys.tree, successMsg: "创建成功" }
 * )
 * ```
 */

import { type InvalidateQueryFilters, useMutation, useQueryClient } from "@tanstack/react-query"
import { notify } from "@/lib/notification"

interface MutationActionOptions<TData> {
  /** 成功后 invalidate 的 queryKey（单个或多个） */
  queryKey?: InvalidateQueryFilters["queryKey"] | InvalidateQueryFilters["queryKey"][]
  /** 成功提示文字，不传则不弹 */
  successMsg?: string
  /** 额外成功回调 */
  onSuccess?: (data: TData) => void
}

export function useMutationAction<TVariables, TData = unknown>(
  mutationFn: (variables: TVariables) => Promise<TData>,
  options: MutationActionOptions<TData> = {}
) {
  const qc = useQueryClient()
  const { queryKey, successMsg, onSuccess } = options

  return useMutation({
    mutationFn,
    onSuccess: (data) => {
      if (successMsg) notify.success(successMsg)

      const keys = queryKey
        ? Array.isArray(queryKey[0])
          ? (queryKey as InvalidateQueryFilters["queryKey"][])
          : [queryKey]
        : []
      for (const key of keys) {
        qc.invalidateQueries({ queryKey: key })
      }

      onSuccess?.(data)
    }
  })
}
