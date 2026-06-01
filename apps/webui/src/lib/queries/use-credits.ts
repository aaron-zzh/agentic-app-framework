/**
 * 积分+结算 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { creditsApi } from "@/lib/api/rest/billing/credits"

const BALANCE_KEY = ["credits", "balance"]
const TRANSACTIONS_KEY = ["credits", "transactions"]
const TOKEN_RULES_KEY = ["credits", "token-rules"]

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
  return useQuery({
    queryKey: ["credits", "groups"],
    queryFn: creditsApi.getGroups,
    staleTime: 60 * 1000
  })
}

/** 创建充值订单 */
export function useCreateRecharge() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (amount: number) => creditsApi.createRecharge(amount),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: BALANCE_KEY })
      qc.invalidateQueries({ queryKey: TRANSACTIONS_KEY })
    }
  })
}
