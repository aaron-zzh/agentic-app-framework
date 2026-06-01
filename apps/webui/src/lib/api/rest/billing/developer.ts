import { request } from "@/lib/api/rest/entity/crud"

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
  plans: () => request<DeveloperSubscriptionPlan[]>("/developer/subscription/plans"),
  currentSubscription: () => request<DeveloperSubscription | null>("/developer/subscription/current"),
  tokenAccount: () => request<DeveloperTokenAccount>("/developer/tokens/account"),
  subscribe: (planCode: string) =>
    request<number>("/developer/subscription/subscribe", {
      method: "POST",
      body: JSON.stringify({ planCode })
    })
}
