import { useMutation, useQuery } from "@tanstack/react-query"
import { licenseApi } from "@/lib/api/rest/billing/license"
import { useAuthStore } from "@/lib/store/auth-store"

export const licenseStatusKey = ["license", "current"] as const
export const officialConsoleSummaryKey = ["official", "console", "summary"] as const

export function useLicenseStatus() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  return useQuery({
    queryKey: licenseStatusKey,
    queryFn: licenseApi.current,
    staleTime: 60 * 1000,
    enabled: isAuthenticated
  })
}

export function useOfficialConsoleSummary(enabled = true) {
  return useQuery({
    queryKey: officialConsoleSummaryKey,
    queryFn: licenseApi.officialSummary,
    enabled
  })
}

export function useIssueLicense() {
  return useMutation({
    mutationFn: licenseApi.issue
  })
}
