/**
 * 积分+结算 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { type QueryClient, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { creditsApi } from "@/lib/api/rest/billing/credits"
import { useAuthStore } from "@/lib/store/auth-store"

const BALANCE_KEY = ["credits", "balance"]
const TRANSACTIONS_KEY = ["credits", "transactions"]
const TOKEN_RULES_KEY = ["credits", "token-rules"]
const GROUPS_KEY = ["credits", "groups"]

/**
 * 积分变更后统一失效查询：余额 + 流水 + 分组明细。
 * 任何会导致积分增减的操作（充值、订阅、AI 调用、任务奖励等）成功后调用。
 */
export function invalidateCreditQueries(qc: QueryClient) {
  qc.invalidateQueries({ queryKey: BALANCE_KEY })
  qc.invalidateQueries({ queryKey: TRANSACTIONS_KEY })
  qc.invalidateQueries({ queryKey: GROUPS_KEY })
}

/** 查询积分余额 */
export function useCreditBalance() {
  return useQuery({
    queryKey: BALANCE_KEY,
    queryFn: creditsApi.getBalance
  })
}

/** 查询积分流水（分页） */
export function useCreditTransactions(page: number, size = 20) {
  return useQuery({
    queryKey: [...TRANSACTIONS_KEY, page, size],
    queryFn: () => creditsApi.getTransactions(page, size)
  })
}

/** 查询积分转 Token 规则 */
export function useTokenRules() {
  return useQuery({
    queryKey: TOKEN_RULES_KEY,
    queryFn: creditsApi.getTokenRules
  })
}

/** 查询积分分组明细（按 batch_type 汇总） */
export function useCreditGroups() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  return useQuery({
    queryKey: ["credits", "groups"],
    queryFn: creditsApi.getGroups,
    staleTime: 60 * 1000,
    enabled: isAuthenticated
  })
}

/** 创建充值订单 */
export function useCreateRecharge() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (amount: number) => creditsApi.createRecharge(amount),
    onSuccess: () => invalidateCreditQueries(qc)
  })
}
