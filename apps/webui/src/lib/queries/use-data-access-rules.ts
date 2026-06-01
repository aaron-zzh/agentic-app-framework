/**
 * 行级数据权限规则 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { type DataAccessRuleInput, dataAccessApi } from "@/lib/api/rest/admin/data-access"

const KEYS = {
  all: ["data-access-rules"] as const
}

/** 规则列表 */
export function useDataAccessRules() {
  return useQuery({
    queryKey: KEYS.all,
    queryFn: () => dataAccessApi.list()
  })
}

/** 创建规则 */
export function useCreateDataAccessRule() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: DataAccessRuleInput) => dataAccessApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.all })
  })
}

/** 更新规则 */
export function useUpdateDataAccessRule() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: DataAccessRuleInput }) =>
      dataAccessApi.update(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.all })
  })
}

/** 删除规则 */
export function useDeleteDataAccessRule() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => dataAccessApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.all })
  })
}
