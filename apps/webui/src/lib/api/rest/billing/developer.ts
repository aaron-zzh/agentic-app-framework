import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { backendApi } from "../backend-client"

export interface DeveloperSubscriptionPlan {
  id: number
  code: string
  name: string
  durationDays: number
  price: number
  includedTokens: number
  allowManagedGateway: boolean
  allowSubProxy: boolean
  maxProxyDepth: number
  status: string
  sortOrder: number
}

export interface DeveloperSubscription {
  id: number
  planCode: string | null
  planName: string | null
  startAt: string
  endAt: string | null
  status: string
}

export interface DeveloperTokenAccount {
  developerId: number
  balanceTokens: number
  frozenTokens: number
  totalEarnedTokens: number
  totalSpentTokens: number
}

export const developerApi = {
  plans: () => backendApi.get<DeveloperSubscriptionPlan[]>("/developer/subscription/plans"),
  currentSubscription: () =>
    backendApi.get<DeveloperSubscription | null>("/developer/subscription/current"),
  tokenAccount: () => backendApi.get<DeveloperTokenAccount>("/developer/tokens/account"),
  subscribe: (planCode: string) =>
    backendApi.post<number>("/developer/subscription/subscribe", { planCode }),
  adminPlans: () =>
    backendApi.get<DeveloperSubscriptionPlan[]>("/developer/admin/subscription-plans?size=100"),
  updatePlan: (id: number, dto: Partial<Omit<DeveloperSubscriptionPlan, "id" | "code">>) =>
    backendApi.put<DeveloperSubscriptionPlan>(`/developer/admin/subscription-plans/${id}`, dto),
  createRedeemCode: (dto: {
    type: string
    tokenAmount?: number
    planCode?: string
    expiresAt?: string
    remark?: string
  }) =>
    backendApi.post<{
      id: number
      code: string
      codePrefix: string
      tokenAmount: number
      licenseJwt?: string
    }>("/developer/admin/redeem-codes", dto)
}

export const developerBillingKeys = {
  plans: ["developerBilling", "plans"] as const,
  subscription: ["developerBilling", "subscription"] as const,
  tokenAccount: ["developerBilling", "tokenAccount"] as const
}

export function useDeveloperPlans(enabled = true) {
  return useQuery({
    queryKey: developerBillingKeys.plans,
    queryFn: developerApi.plans,
    enabled
  })
}

export function useCurrentDeveloperSubscription(enabled = true) {
  return useQuery({
    queryKey: developerBillingKeys.subscription,
    queryFn: developerApi.currentSubscription,
    enabled
  })
}

export function useDeveloperTokenAccount(enabled = true) {
  return useQuery({
    queryKey: developerBillingKeys.tokenAccount,
    queryFn: developerApi.tokenAccount,
    enabled
  })
}

export function useSubscribeDeveloperPlan() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: developerApi.subscribe,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: developerBillingKeys.subscription })
      qc.invalidateQueries({ queryKey: developerBillingKeys.tokenAccount })
    }
  })
}
