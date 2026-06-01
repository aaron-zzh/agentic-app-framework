/**
 * Spring Boot REST API 客户端。
 */

import type { AxiosError, AxiosRequestConfig, InternalAxiosRequestConfig } from "axios"
import { API_BASE_URL } from "../config"
import { ApiError } from "../errors"
import { RestApiClient } from "../api-client"
import type { ApiResult } from "../types"

export type { ApiResult } from "../types"

type RetriableConfig = InternalAxiosRequestConfig & {
  _aafRetry?: boolean
}

type RefreshAccessToken = () => Promise<string | null>

let refreshAccessToken: RefreshAccessToken | null = null
let refreshPromise: Promise<string | null> | null = null

export const backendApi = new RestApiClient(API_BASE_URL)
export const backendClient = backendApi.getInstance()

export function setBackendAccessToken(token: string | null): void {
  backendApi.setHeader("Authorization", token ? `Bearer ${token}` : null)
}

export function setBackendOrgId(orgId: string | null): void {
  backendApi.setHeader("X-Org-Id", orgId)
}

export function setBackendWorkspaceId(workspaceId: string | null): void {
  backendApi.setHeader("X-Workspace-Id", workspaceId)
}

export function registerBackendTokenRefresh(handler: RefreshAccessToken): void {
  refreshAccessToken = handler
}

function isAuthRequest(url?: string): boolean {
  if (!url) return false
  return url.startsWith("/auth/") || url.includes("/auth/")
}

async function refreshTokenOnce(): Promise<string | null> {
  if (!refreshAccessToken) return null
  if (refreshPromise) return refreshPromise
  refreshPromise = refreshAccessToken().finally(() => {
    refreshPromise = null
  })
  return refreshPromise
}

function redirectToLogin() {
  if (typeof window !== "undefined" && process.env.NODE_ENV !== "test") {
    window.location.href = "/auth/login"
  }
}

backendClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResult<unknown>>) => {
    const response = error.response
    const config = error.config as RetriableConfig | undefined

    if (response?.status === 401 && config && !config._aafRetry && !isAuthRequest(config.url)) {
      config._aafRetry = true
      const newToken = await refreshTokenOnce()
      if (newToken) {
        setBackendAccessToken(newToken)
        config.headers.set("Authorization", `Bearer ${newToken}`)
        return backendClient.request(config)
      }
      redirectToLogin()
      throw new ApiError(401, "登录已过期，请重新登录")
    }

    throw backendApi.normalizeError(error)
  }
)

export function backendRequest<T>(
  path: string,
  config: AxiosRequestConfig = {}
): Promise<T> {
  return backendApi.request<T>({ url: path, ...config })
}
