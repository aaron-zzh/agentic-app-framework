import { useMutation, useQuery } from "@tanstack/react-query"
import { buildApiUrl } from "@/lib/api/config"
import { useAuthStore } from "@/lib/store/auth-store"
import { backendApi } from "../backend-client"

export interface LicenseStatus {
  identityValid: boolean
  tier: string
  userId: string | null
  expiresAt: string | null
  upgradeUrl: string
  features: string[]
  licenseFileLocations: string[]
}

export interface OfficialConsoleSummary {
  ownerUserId: string | null
  tier: string
  enabledModules: string[]
}

export interface LicenseIssueRequest {
  subject: string
  tier: string
  org?: string
  features: string[]
  expiresAt: string
}

export interface LicenseIssueResult {
  token: string
  subject: string
  tier: string
  features: string[]
  expiresAt: string
}

export const licenseApi = {
  current: () => backendApi.get<LicenseStatus>("/license/current"),
  officialSummary: () => backendApi.get<OfficialConsoleSummary>("/official/console/summary"),
  issue: (data: LicenseIssueRequest) =>
    backendApi.post<LicenseIssueResult>("/official/console/licenses", data),
  sourceDownloadUrl: () => buildApiUrl("/license/source-code"),
  sourceDownloadHeaders: () => {
    const token = useAuthStore.getState().accessToken
    return token ? { Authorization: `Bearer ${token}` } : undefined
  }
}

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
