import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useAuthStore } from "@/lib/store/auth-store"
import { backendApi } from "../backend-client"
import { invalidateCreditQueries } from "./credits"

export type BillingCycle = "MONTH" | "QUARTER" | "YEAR" | "PERPETUAL"

export interface PlanEntitlementVO {
  code: string
  name: string
  type: "BOOLEAN" | "COUNTABLE"
  unit: string | null
  quota: number
  resetCycle: "NONE" | "DAILY" | "MONTHLY" | "YEARLY"
  refillPrice: number
}

export interface SubscriptionSkuVO {
  id: string
  skuCode: string
  billingCycle: BillingCycle
  cycleMonths: number
  price: number
  marketPrice: number
  status: string
  sort: number
}

export interface SubscriptionPlanVO {
  id: string
  code: string
  name: string
  monthlyCredits: number
  ext: string | null
  skus: SubscriptionSkuVO[]
  entitlements: PlanEntitlementVO[]
  status: string
  sort: number
}

export interface CreditPackageVO {
  id: string
  name: string
  credits: number
  bonusCredits: number
  price: number
  group: string | null
  recommended: boolean
}

export interface PayOrderVO {
  id: number
  merchantOrderNo: string
  amount: number
  status: number
  channelCode: string
  codeUrl?: string
  bizOrderType?: BizOrderType
}

export const BIZ_ORDER_TYPE = {
  RECHARGE: "RECHARGE",
  CREDIT_PACKAGE: "CREDIT_PACKAGE",
  PURCHASE: "PURCHASE",
  SUBSCRIPTION: "SUBSCRIPTION"
} as const

export type BizOrderType = (typeof BIZ_ORDER_TYPE)[keyof typeof BIZ_ORDER_TYPE]

export interface SubscriptionVO {
  id: number
  planCode: string | null
  planName: string | null
  skuCode: string | null
  billingCycle: BillingCycle | null
  cycleMonths: number | null
  startAt: string
  endAt: string | null
  status: string
  cancelledAt: string | null
  pendingPlanName: string | null
  pendingSkuCode: string | null
  pendingBillingCycle: BillingCycle | null
}

export interface SubscriptionCheckoutStatusVO {
  payOrderId: number
  payStatus: "UNPAID" | "PAID"
  fulfillmentStatus: "PENDING" | "FULFILLED" | "CLOSED" | "COMPENSATION_PENDING"
  exceptionCode: string | null
  compensationResolvedAt: string | null
  compensationResult: string | null
}

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

const SUBSCRIPTION_PENDING_PAYMENT_EXISTS = 9_000_010
const PENDING_PAYMENT_MESSAGE = "已有待支付会员订单"
const PAY_ORDER_ID_MESSAGE_PATTERN = /(?:payOrderId|支付单(?:ID|Id|id)?)[=：:]\s*(\d+)/u

interface ApiErrorShape {
  code: number
  message: string
  data?: unknown
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null
}

function isApiErrorShape(value: unknown): value is ApiErrorShape {
  return isRecord(value) && typeof value.code === "number" && typeof value.message === "string"
}

function parsePositiveOrderId(value: unknown): number | null {
  const candidate = typeof value === "string" && value.trim() ? Number(value) : value
  return typeof candidate === "number" && Number.isSafeInteger(candidate) && candidate > 0
    ? candidate
    : null
}

/** 从 live checkout 冲突错误中提取既有支付单 ID；优先结构化 data，兼容稳定消息。 */
export function getPendingSubscriptionPayOrderId(error: unknown): number | null {
  if (!isApiErrorShape(error)) return null
  const isPendingPaymentConflict =
    error.code === SUBSCRIPTION_PENDING_PAYMENT_EXISTS ||
    (error.code === 409 && error.message.includes(PENDING_PAYMENT_MESSAGE))
  if (!isPendingPaymentConflict) return null

  const structuredId = isRecord(error.data)
    ? parsePositiveOrderId(error.data.payOrderId)
    : parsePositiveOrderId(error.data)
  if (structuredId) return structuredId

  const messageMatch = error.message.match(PAY_ORDER_ID_MESSAGE_PATTERN)
  return parsePositiveOrderId(messageMatch?.[1])
}

export const billingPlansApi = {
  getPlans: () => backendApi.get<SubscriptionPlanVO[]>("/billing/subscription-plans/catalog"),
  getCurrentSubscription: () => backendApi.get<SubscriptionVO | null>("/billing/subscriptions/me"),
  getCreditPackages: () => backendApi.get<CreditPackageVO[]>("/billing/credit-packages"),
  getPayOrder: (payOrderId: number) =>
    backendApi.get<PayOrderVO>(`/pay/orders/${payOrderId}`, { showError: false }),
  getSubscriptionCheckout: (payOrderId: number) =>
    backendApi.get<SubscriptionCheckoutStatusVO>(`/billing/subscriptions/checkouts/${payOrderId}`, {
      showError: false
    }),
  subscribe: (skuCode: string, channelCode: string) =>
    backendApi.post<PayOrderVO | null>(
      "/billing/subscriptions/subscribe",
      { skuCode, channelCode },
      { showError: false }
    ),
  cancelSubscription: () => backendApi.post<SubscriptionVO>("/billing/subscriptions/me/cancel", {}),
  downgrade: (skuCode: string) =>
    backendApi.post<SubscriptionVO>("/billing/subscriptions/me/downgrade", { skuCode }),
  cancelPendingDowngrade: () =>
    backendApi.delete<SubscriptionVO>("/billing/subscriptions/me/pending-downgrade"),
  purchaseCredits: (packageId: string, channelCode?: string) =>
    backendApi.post<PayOrderVO>("/billing/credit-packages/purchase", {
      packageId,
      channelCode: channelCode ?? "MOCK"
    }),
  getEntitlementQuotas: () => backendApi.get<EntitlementQuotaVO[]>("/billing/entitlement-quotas/me")
}

export function useSubscriptionPlans() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  return useQuery({
    queryKey: ["billing", "plans"],
    queryFn: billingPlansApi.getPlans,
    staleTime: 5 * 60 * 1000,
    enabled: isAuthenticated
  })
}

export function useCurrentSubscription() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  return useQuery({
    queryKey: ["billing", "subscription", "current"],
    queryFn: billingPlansApi.getCurrentSubscription,
    staleTime: 60 * 1000,
    enabled: isAuthenticated
  })
}

export function useCreditPackages() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  return useQuery({
    queryKey: ["billing", "credit-packages"],
    queryFn: billingPlansApi.getCreditPackages,
    staleTime: 5 * 60 * 1000,
    enabled: isAuthenticated
  })
}

function invalidateSubscriptionQueries(queryClient: ReturnType<typeof useQueryClient>) {
  invalidateCreditQueries(queryClient)
  queryClient.invalidateQueries({ queryKey: ["billing", "plans"] })
  queryClient.invalidateQueries({ queryKey: ["billing", "subscription", "current"] })
  queryClient.invalidateQueries({ queryKey: ["billing", "entitlement", "quotas"] })
}

export function useSubscribe() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ skuCode, channelCode }: { skuCode: string; channelCode: string }) =>
      billingPlansApi.subscribe(skuCode, channelCode),
    onSuccess: () => invalidateSubscriptionQueries(queryClient)
  })
}

export function useCancelSubscription() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: billingPlansApi.cancelSubscription,
    onSuccess: () => invalidateSubscriptionQueries(queryClient)
  })
}

export function useDowngradeSubscription() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (skuCode: string) => billingPlansApi.downgrade(skuCode),
    onSuccess: () => invalidateSubscriptionQueries(queryClient)
  })
}

export function useCancelPendingDowngrade() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: billingPlansApi.cancelPendingDowngrade,
    onSuccess: () => invalidateSubscriptionQueries(queryClient)
  })
}

export function usePurchaseCredits() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ packageId, channelCode }: { packageId: string; channelCode?: string }) =>
      billingPlansApi.purchaseCredits(packageId, channelCode),
    onSuccess: () => invalidateCreditQueries(queryClient)
  })
}

export function useEntitlementQuotas() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  return useQuery({
    queryKey: ["billing", "entitlement", "quotas"],
    queryFn: billingPlansApi.getEntitlementQuotas,
    staleTime: 60 * 1000,
    enabled: isAuthenticated
  })
}
