import { useMutation, useQuery } from "@tanstack/react-query"
import { billingPlansApi } from "@/lib/api/rest/billing/plans"
import { useAuthStore } from "@/lib/store/auth-store"

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
  return useMutation({
    mutationFn: ({
      planCode,
      billingCycle,
      channelCode
    }: {
      planCode: string
      billingCycle: "monthly" | "yearly"
      channelCode: string
    }) => billingPlansApi.subscribe(planCode, billingCycle, channelCode)
  })
}

export function usePurchaseCredits() {
  return useMutation({
    mutationFn: ({ packageId, channelCode }: { packageId: string; channelCode?: string }) =>
      billingPlansApi.purchaseCredits(packageId, channelCode)
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
