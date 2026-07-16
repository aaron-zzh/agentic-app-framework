/**
 * 积分+结算 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"
import type { PageResult } from "../entity/crud"

/** 积分余额 */
export interface CreditBalanceVO {
  balance: number
  frozen: number
  totalEarned: number
  totalSpent: number
}

/** 积分流水类型（与后端 CreditTransactionType 对齐） */
export type CreditTransactionType = "EARN" | "SPEND" | "FREEZE" | "UNFREEZE" | "EXPIRE"

/** 积分流水记录 */
export interface CreditTransactionVO {
  id: string
  type: CreditTransactionType
  amount: number
  balanceAfter: number
  source: string
  /** 消费分类（仅 SPEND 类型有值），对应字典 credit_transaction_category */
  category?: string
  /** 业务备注，优先于 source 展示 */
  remark?: string
  bizType?: string
  bizId: string
  createTime: string
}

/** 积分转 Token 规则 */
export interface CreditTokenRuleVO {
  id: string
  name: string
  creditAmount: number
  tokenAmount: number
  status: "ENABLED" | "DISABLED"
}

/** 充值订单响应 */
export interface RechargeOrderVO {
  orderNo: string
  payOrderId: string
}

/** 积分分组明细（按 batch_type 汇总） */
export interface CreditGroupVO {
  /** 分组标识：SUBSCRIPTION / TOPUP / REWARD / WEEKLY / MANUAL */
  batchType: string
  /** 分组显示名 */
  label: string
  /** 该分组总余额 */
  remain: number
  /** 子项明细（可选，如套餐积分/购买积分） */
  items?: { label: string; remain: number }[]
}

export const creditsApi = {
  /** 获取积分余额 */
  getBalance: () => backendApi.get<CreditBalanceVO>("/credits/balance"),

  /** 获取积分流水（分页） */
  getTransactions: (page = 0, size = 20) =>
    backendApi.get<PageResult<CreditTransactionVO>>(
      `/credits/transactions?page=${page}&size=${size}`
    ),

  /** 获取积分分组明细（按 batch_type 汇总） */
  getGroups: () => backendApi.get<CreditGroupVO[]>("/credits/groups"),

  /** 兑换积分码 */
  redeem: (code: string) =>
    backendApi.post<number>("/billing/credit-redeem-codes/redeem", { code }),

  /** 创建充值订单 */
  createRecharge: (amount: number) =>
    backendApi.post<RechargeOrderVO>("/biz/orders", { type: "RECHARGE", amount }),

  /** 获取积分转 Token 兑换规则 */
  getTokenRules: () => backendApi.get<CreditTokenRuleVO[]>("/credit-token-rules")
}

import { type QueryClient, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
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
