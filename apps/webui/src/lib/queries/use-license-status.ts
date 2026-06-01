import { useMutation, useQuery } from "@tanstack/react-query"
import { licenseApi } from "@/lib/api/rest/billing/license"

export const licenseStatusKey = ["license", "current"] as const
export const officialConsoleSummaryKey = ["official", "console", "summary"] as const

export function useLicenseStatus() {
  return useQuery({
    queryKey: licenseStatusKey,
    queryFn: licenseApi.current,
    staleTime: 60 * 1000
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
