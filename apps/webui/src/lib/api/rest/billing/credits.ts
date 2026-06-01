/**
 * 积分+结算 API 客户端
 * @author AaronZZH & Kiro
 */

import { type PageResult, request } from "../entity/crud"

/** 积分余额 */
export interface CreditBalanceVO {
  balance: number
  frozen: number
  totalEarned: number
  totalSpent: number
}

/** 积分流水类型（与后端 CreditTransactionType 对齐） */
export type CreditTransactionType = "EARN" | "SPEND" | "FREEZE" | "UNFREEZE"

/** 积分流水记录 */
export interface CreditTransactionVO {
  id: string
  type: CreditTransactionType
  amount: number
  balanceAfter: number
  source: string
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

export const creditsApi = {
  /** 获取积分余额 */
  getBalance: () => request<CreditBalanceVO>("/credits/balance"),

  /** 获取积分流水（分页） */
  getTransactions: (page = 0, size = 20) =>
    request<PageResult<CreditTransactionVO>>(`/credits/transactions?page=${page}&size=${size}`),

  /** 创建充值订单 */
  createRecharge: (amount: number) =>
    request<RechargeOrderVO>("/biz/orders", {
      method: "POST",
      body: JSON.stringify({ type: "RECHARGE", amount })
    }),

  /** 获取积分转 Token 兑换规则 */
  getTokenRules: () => request<CreditTokenRuleVO[]>("/credit-token-rules")
}
