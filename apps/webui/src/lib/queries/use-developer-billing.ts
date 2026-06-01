import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { developerApi } from "@/lib/api/rest/billing/developer"

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
