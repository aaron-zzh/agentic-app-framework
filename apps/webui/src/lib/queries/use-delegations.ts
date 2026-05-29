/**
 * 审批委托 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { type DelegationCreateReq, delegationApi } from "@/lib/api/delegation"

const QUERY_KEY = ["delegations"]

/** 查询委托列表 */
export function useDelegations() {
  return useQuery({
    queryKey: QUERY_KEY,
    queryFn: delegationApi.list
  })
}

/** 创建委托 */
export function useCreateDelegation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: DelegationCreateReq) => delegationApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: QUERY_KEY })
  })
}

/** 取消委托 */
export function useCancelDelegation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => delegationApi.cancel(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: QUERY_KEY })
  })
}
