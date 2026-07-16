import { backendApi } from "../backend-client"

/**
 * 订阅套餐 & 积分充值套餐 API 类型
 */

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

/** 支付订单（与后端 PayOrderVO 对应） */
export interface PayOrderVO {
  id: number
  merchantOrderNo: string
  amount: number
  /** 0=待支付 10=成功 30=已关闭 */
  status: number
  channelCode: string
  codeUrl?: string
  /** 关联业务订单类型，见 BizOrderType（与后端 BizOrderTypeEnum.code 对应），无关联业务订单时为空 */
  bizOrderType?: BizOrderType
}

/** 业务订单类型（与后端 BizOrderTypeEnum 对应） */
export const BIZ_ORDER_TYPE = {
  /** 直接充值 */
  RECHARGE: "RECHARGE",
  /** 积分套餐购买 */
  CREDIT_PACKAGE: "CREDIT_PACKAGE",
  /** 购买 */
  PURCHASE: "PURCHASE",
  /** 订阅 */
  SUBSCRIPTION: "SUBSCRIPTION"
} as const

export type BizOrderType = (typeof BIZ_ORDER_TYPE)[keyof typeof BIZ_ORDER_TYPE]

/** 当前订阅信息 */
export interface SubscriptionVO {
  id: number
  planCode: string | null
  planName: string | null
  startAt: string
  endAt: string | null
  status: string
}

/** 用户权益额度 */
export interface EntitlementQuotaVO {
  id: number
  code: string | null
  name: string | null
  type: "BOOLEAN" | "COUNTABLE" | null
  unit: string | null
  total: number
  used: number
  remain: number
  nextResetAt: string | null
}

export const billingPlansApi = {
  /** 获取所有启用的订阅套餐（含权益列表） */
  getPlans: () => backendApi.get<SubscriptionPlanVO[]>("/billing/subscription/plans"),

  /** 获取当前用户的有效订阅，无订阅返回 null */
  getCurrentSubscription: () =>
    backendApi.get<SubscriptionVO | null>("/billing/subscription/current"),

  /** 获取积分充值套餐列表 */
  getCreditPackages: () => backendApi.get<CreditPackageVO[]>("/billing/credit-packages"),

  /** 购买订阅套餐，返回支付单（免费套餐直接激活返回 null） */
  subscribe: (planCode: string, billingCycle: "monthly" | "yearly", channelCode: string) =>
    backendApi.post<PayOrderVO | null>("/billing/subscription/subscribe", {
      planCode,
      billingCycle,
      channelCode
    }),

  /** 购买积分套餐，返回支付单（含 codeUrl） */
  purchaseCredits: (packageId: string, channelCode?: string) =>
    backendApi.post<PayOrderVO>("/billing/credit-packages/purchase", {
      packageId,
      channelCode: channelCode ?? "MOCK"
    }),

  /** 获取当前用户所有权益额度 */
  getEntitlementQuotas: () => backendApi.get<EntitlementQuotaVO[]>("/billing/entitlement/quotas")
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useAuthStore } from "@/lib/store/auth-store"
import { invalidateCreditQueries } from "./credits"

export function useSubscriptionPlans() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  return useQuery({
    queryKey: ["billing", "plans"],
    queryFn: billingPlansApi.getPlans,
    staleTime: 5 * 60 * 1000,
    enabled: isAuthenticated
  })
}

export function useCurrentSubscription() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  return useQuery({
    queryKey: ["billing", "subscription", "current"],
    queryFn: billingPlansApi.getCurrentSubscription,
    staleTime: 60 * 1000,
    enabled: isAuthenticated
  })
}

export function useCreditPackages() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  return useQuery({
    queryKey: ["billing", "credit-packages"],
    queryFn: billingPlansApi.getCreditPackages,
    staleTime: 5 * 60 * 1000,
    enabled: isAuthenticated
  })
}

export function useSubscribe() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({
      planCode,
      billingCycle,
      channelCode
    }: {
      planCode: string
      billingCycle: "monthly" | "yearly"
      channelCode: string
    }) => billingPlansApi.subscribe(planCode, billingCycle, channelCode),
    onSuccess: () => {
      invalidateCreditQueries(qc)
      qc.invalidateQueries({ queryKey: ["billing", "subscription", "current"] })
      qc.invalidateQueries({ queryKey: ["billing", "entitlement", "quotas"] })
    }
  })
}

export function usePurchaseCredits() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ packageId, channelCode }: { packageId: string; channelCode?: string }) =>
      billingPlansApi.purchaseCredits(packageId, channelCode),
    onSuccess: () => invalidateCreditQueries(qc)
  })
}

export function useEntitlementQuotas() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  return useQuery({
    queryKey: ["billing", "entitlement", "quotas"],
    queryFn: billingPlansApi.getEntitlementQuotas,
    staleTime: 60 * 1000,
    enabled: isAuthenticated
  })
}
