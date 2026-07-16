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
