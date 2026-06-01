/**
 * 待办相关 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { type TodoListParams, todoApi } from "@/lib/api/rest/workflow/todo"

const KEYS = {
  all: ["todos"] as const,
  list: (params: TodoListParams) => ["todos", "list", params] as const
}

/** 待办列表 */
export function useTodos(params: TodoListParams = {}) {
  return useQuery({
    queryKey: KEYS.list(params),
    queryFn: () => todoApi.list(params)
  })
}

/** 标记完成 */
export function useTodoComplete() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => todoApi.complete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}

/** 标记忽略 */
export function useTodoDismiss() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => todoApi.dismiss(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}
