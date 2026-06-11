/**
 * 订阅套餐 & 积分充值套餐 API 类型
 */

import { request } from "../entity/crud"

/** 套餐权益项 */
export interface PlanEntitlementVO {
  code: string
  name: string
  type: "BOOLEAN" | "COUNTABLE"
  unit: string | null
  quota: number
  resetCycle: "NONE" | "DAILY" | "MONTHLY" | "YEARLY"
  refillPrice: number
}

/** 订阅套餐 */
export interface SubscriptionPlanVO {
  id: string
  code: string
  name: string
  /** 有效天数（0=永久） */
  durationDays: number
  /** 月付价格（分） */
  price: number
  /** 年付价格（分），后端按 price*12*折扣 计算 */
  yearlyPrice: number
  /** 划线价（分） */
  marketPrice: number
  /** 每月发放积分数 */
  monthlyCredits: number
  /** 扩展配置（JSON 字符串） */
  ext: string | null
  entitlements: PlanEntitlementVO[]
}

/** 积分充值套餐 */
export interface CreditPackageVO {
  id: string
  name: string
  /** 积分数 */
  credits: number
  /** 赠送积分 */
  bonusCredits: number
  /** 售价（分） */
  price: number
  /** 套餐分组标签（如"会员积分充值"、"专属积分包"） */
  group: string | null
  /** 是否推荐 */
  recommended: boolean
}

export const billingPlansApi = {
  /** 获取所有启用的订阅套餐（含权益列表） */
  getPlans: () => request<SubscriptionPlanVO[]>("/billing/subscription/plans"),

  /** 获取积分充值套餐列表 */
  getCreditPackages: () => request<CreditPackageVO[]>("/billing/credit-packages"),

  /** 购买订阅套餐 */
  subscribe: (planCode: string, billingCycle: "monthly" | "yearly") =>
    request<{ orderNo: string }>("/billing/subscribe", {
      method: "POST",
      body: JSON.stringify({ planCode, billingCycle })
    }),

  /** 购买积分套餐 */
  purchaseCredits: (packageId: string) =>
    request<{ orderNo: string }>("/billing/credit-packages/purchase", {
      method: "POST",
      body: JSON.stringify({ packageId })
    })
}
