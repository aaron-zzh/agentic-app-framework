import { buildApiUrl } from "@/lib/api/config"
import { request } from "@/lib/api/rest/entity/crud"
import { useAuthStore } from "@/lib/store/auth-store"

export interface LicenseStatus {
  premium: boolean
  owner: boolean
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
  owner: boolean
  features: string[]
  expiresAt: string
}

export interface LicenseIssueResult {
  token: string
  subject: string
  tier: string
  owner: boolean
  features: string[]
  expiresAt: string
}

export const licenseApi = {
  current: () => request<LicenseStatus>("/license/current"),
  officialSummary: () => request<OfficialConsoleSummary>("/official/console/summary"),
  issue: (data: LicenseIssueRequest) =>
    request<LicenseIssueResult>("/official/console/licenses", {
      method: "POST",
      body: JSON.stringify(data)
    }),
  sourceDownloadUrl: () => buildApiUrl("/license/source-code"),
  sourceDownloadHeaders: () => {
    const token = useAuthStore.getState().accessToken
    return token ? { Authorization: `Bearer ${token}` } : undefined
  }
}
