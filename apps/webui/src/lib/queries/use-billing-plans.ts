import { useMutation, useQuery } from "@tanstack/react-query"
import { billingPlansApi } from "@/lib/api/rest/billing/plans"

export function useSubscriptionPlans() {
  return useQuery({
    queryKey: ["billing", "plans"],
    queryFn: billingPlansApi.getPlans,
    staleTime: 5 * 60 * 1000
  })
}

export function useCreditPackages() {
  return useQuery({
    queryKey: ["billing", "credit-packages"],
    queryFn: billingPlansApi.getCreditPackages,
    staleTime: 5 * 60 * 1000
  })
}

export function useSubscribe() {
  return useMutation({
    mutationFn: ({
      planCode,
      billingCycle
    }: {
      planCode: string
      billingCycle: "monthly" | "yearly"
    }) => billingPlansApi.subscribe(planCode, billingCycle)
  })
}

export function usePurchaseCredits() {
  return useMutation({
    mutationFn: (packageId: string) => billingPlansApi.purchaseCredits(packageId)
  })
}
