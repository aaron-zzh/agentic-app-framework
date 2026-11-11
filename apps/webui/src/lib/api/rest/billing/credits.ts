/**
 * 积分+结算 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"
import type { PageResult } from "../crud"

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
import { format, subDays } from "date-fns"
import { useCallback } from "react"
import { useAuthStore } from "@/lib/store/auth-store"

const BALANCE_KEY = ["credits", "balance"]
const TRANSACTIONS_KEY = ["credits", "transactions"]
const TOKEN_RULES_KEY = ["credits", "token-rules"]
const GROUPS_KEY = ["credits", "groups"]
/** 图表聚合取最近 100 条流水；积分没有用户级统计接口，消耗频繁时统计会偏小 */
const SPEND_STATS_PAGE_SIZE = 100

/** 每日积分消耗点，date 为 yyyy-MM-dd */
export interface CreditSpendTrendPoint {
  date: string
  amount: number
}

/** 积分消耗分类汇总行，字段与 ExpensesCategoryWidget 的 categories 行一致 */
export interface CreditSpendCategoryRow {
  biz_type: string
  total: number
}

/** 截至 now 的最近 days 天日期 key（含当天），按时间升序 */
function recentDayKeys(days: number, now: Date): string[] {
  const count = Math.max(0, Math.floor(days))
  return Array.from({ length: count }, (_, index) =>
    format(subDays(now, count - 1 - index), "yyyy-MM-dd")
  )
}

/** 流水所属日期 key：createTime 前 10 位（yyyy-MM-dd） */
function transactionDayKey(tx: CreditTransactionVO): string {
  return tx.createTime.slice(0, 10)
}

/**
 * 聚合最近 days 天每日 SPEND 金额，没有消耗的日期补 0。
 *
 * @example
 * aggregateCreditSpendTrend(page.list, 14) // => [{ date: "2026-10-27", amount: 0 }, ...]
 */
export function aggregateCreditSpendTrend(
  transactions: CreditTransactionVO[],
  days: number,
  now: Date = new Date()
): CreditSpendTrendPoint[] {
  const dayKeys = recentDayKeys(days, now)
  const totals = new Map(dayKeys.map((day) => [day, 0]))
  for (const tx of transactions) {
    if (tx.type !== "SPEND") continue
    const day = transactionDayKey(tx)
    const current = totals.get(day)
    if (current === undefined) continue
    totals.set(day, current + Math.abs(tx.amount))
  }
  return dayKeys.map((date) => ({ date, amount: totals.get(date) ?? 0 }))
}

/**
 * 聚合最近 days 天 SPEND 金额并按 category 汇总，category 为空归入 other，按金额降序。
 *
 * @example
 * aggregateCreditSpendCategories(page.list, 30) // => [{ biz_type: "chat", total: 120 }, ...]
 */
export function aggregateCreditSpendCategories(
  transactions: CreditTransactionVO[],
  days: number,
  now: Date = new Date()
): CreditSpendCategoryRow[] {
  const dayKeys = recentDayKeys(days, now)
  const firstDay = dayKeys[0]
  const lastDay = dayKeys[dayKeys.length - 1]
  if (firstDay === undefined || lastDay === undefined) return []
  const totals = new Map<string, number>()
  for (const tx of transactions) {
    if (tx.type !== "SPEND") continue
    const day = transactionDayKey(tx)
    if (day < firstDay || day > lastDay) continue
    const key = tx.category || "other"
    totals.set(key, (totals.get(key) ?? 0) + Math.abs(tx.amount))
  }
  return Array.from(totals.entries())
    .map(([biz_type, total]) => ({ biz_type, total }))
    .sort((left, right) => right.total - left.total)
}

/** 积分流水查询配置，分页列表与图表派生 hook 共用同一 queryKey 规则 */
function creditTransactionsQuery(page: number, size: number) {
  return {
    queryKey: [...TRANSACTIONS_KEY, page, size],
    queryFn: () => creditsApi.getTransactions(page, size)
  }
}

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
  return useQuery(creditTransactionsQuery(page, size))
}

/**
 * 近 N 天每日积分消耗趋势，与 useCreditTransactions(0, 100) 共用一份缓存。
 *
 * @example
 * const { data: points = [] } = useCreditSpendTrend(14)
 */
export function useCreditSpendTrend(days = 14) {
  const select = useCallback(
    (page: PageResult<CreditTransactionVO>) => aggregateCreditSpendTrend(page.list, days),
    [days]
  )
  return useQuery({ ...creditTransactionsQuery(0, SPEND_STATS_PAGE_SIZE), select })
}

/**
 * 近 N 天积分消耗分类，与 useCreditTransactions(0, 100) 共用一份缓存。
 *
 * @example
 * const { data: categories = [] } = useCreditSpendCategories(30)
 */
export function useCreditSpendCategories(days = 30) {
  const select = useCallback(
    (page: PageResult<CreditTransactionVO>) => aggregateCreditSpendCategories(page.list, days),
    [days]
  )
  return useQuery({ ...creditTransactionsQuery(0, SPEND_STATS_PAGE_SIZE), select })
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
