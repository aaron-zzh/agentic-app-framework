/**
 * Spring Boot REST API 客户端。
 */

import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig
} from "axios"
import { clearAxiosAuth, setAxiosAuth } from "@/lib/auth/utils"
import { notify } from "@/lib/notification"
import { RestApiClient } from "../api-client"
import { API_BASE_URL } from "../config"
import { ApiError } from "../errors"
import type { ApiResult } from "../types"

export type { ApiResult } from "../types"

// 扩展 AxiosRequestConfig，支持按请求关闭自动错误提示
declare module "axios" {
  interface AxiosRequestConfig {
    showError?: boolean
    /** 请求成功后自动 toast 提示，传字符串则用该字符串，传 true 则用"操作成功" */
    showSuccess?: boolean | string
  }
}

type RetriableConfig = InternalAxiosRequestConfig & {
  _aafRetry?: boolean
}

type RefreshAccessToken = () => Promise<string | null>

let refreshAccessToken: RefreshAccessToken | null = null
let refreshPromise: Promise<string | null> | null = null
let backendUnavailableNotified = false

export const backendApi = new RestApiClient(API_BASE_URL)
export const backendClient = backendApi.getInstance()

export function setBackendOrgId(orgId: string | null): void {
  backendApi.setHeader("X-Org-Id", orgId)
}

export function setBackendWorkspaceId(workspaceId: string | null): void {
  backendApi.setHeader("X-Workspace-Id", workspaceId)
}

export function setBackendSourceApp(sourceApp: string): void {
  backendApi.setHeader("X-Source-App", sourceApp)
}

export function registerBackendTokenRefresh(handler: RefreshAccessToken): void {
  refreshAccessToken = handler
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
    // 已在登录页则不再跳转
    if (window.location.pathname.startsWith("/login")) return
    window.location.href = "/login"
  }
}

function resolveErrorMessage(error: AxiosError<ApiResult<unknown>>): string {
  if (error.code === "ERR_NETWORK") return "网络连接失败，请稍后重试"
  if (error.code === "ECONNABORTED" || error.message?.includes("timeout"))
    return "请求超时，请稍后重试"

  const { status, data } = error.response ?? {}
  if (data?.message) return data.message

  switch (status) {
    case 400:
      return "请求参数错误"
    case 401:
      return "登录已过期，请重新登录"
    case 403:
      return "权限不足，请联系管理员"
    case 404:
      return "请求的资源不存在"
    case 500:
      return "服务器内部错误，请稍后重试"
    case 502:
      return "网关错误，请稍后重试"
    case 503:
      return "服务暂时不可用，请稍后重试"
    case 504:
      return "网关超时，请稍后重试"
    default:
      return error.message || "系统开小差了"
  }
}

function isBackendUnavailable(error: AxiosError<ApiResult<unknown>>): boolean {
  return !error.response && (error.code === "ERR_NETWORK" || error.code === "ECONNABORTED")
}

function shouldNotifyError(
  error: AxiosError<ApiResult<unknown>>,
  config: RetriableConfig | undefined
): boolean {
  if (typeof window === "undefined") return false
  if (config?.showError === false) return false

  if (isBackendUnavailable(error)) {
    if (backendUnavailableNotified) return false
    backendUnavailableNotified = true
  }

  return true
}

backendClient.interceptors.request.use((config) => {
  const authorization = axios.defaults.headers.common.Authorization
  if (typeof authorization === "string") {
    config.headers.set("Authorization", authorization)
  } else {
    config.headers.delete("Authorization")
  }
  return config
})

backendClient.interceptors.response.use(
  (response) => {
    backendUnavailableNotified = false
    const data = response.data as ApiResult<unknown> | undefined
    // login 请求时把验证码验证结果附加到数据上
    if (response.config.url?.includes("/auth/login") && data?.data) {
      ;(data.data as Record<string, unknown>).verifyCode = response.headers["x-captcha-verify-code"]
    }
    const cfg = response.config as { showError?: boolean; showSuccess?: boolean | string }
    if (data && typeof data.code === "number") {
      if (
        data.code !== 0 &&
        data.message &&
        cfg.showError !== false &&
        typeof window !== "undefined"
      ) {
        notify.error(data.message)
      }
      if (data.code === 0 && cfg.showSuccess && typeof window !== "undefined") {
        notify.success(typeof cfg.showSuccess === "string" ? cfg.showSuccess : "操作成功")
      }
    }
    return response
  },
  async (error: AxiosError<ApiResult<unknown>>) => {
    const response = error.response
    const config = error.config as RetriableConfig | undefined

    if (response?.status === 401) {
      if (config && !config._aafRetry) {
        config._aafRetry = true
        const newToken = await refreshTokenOnce()
        if (newToken) {
          setAxiosAuth(newToken)
          config.headers.set("Authorization", `Bearer ${newToken}`)
          return backendClient.request(config)
        }
      }
      clearAxiosAuth()
      redirectToLogin()
      throw new ApiError(401, "登录已过期，请重新登录")
    }

    // 统一错误提示：非认证接口、showError 未显式关闭时弹 toast
    if (shouldNotifyError(error, config)) {
      notify.error(resolveErrorMessage(error))
    }

    throw backendApi.normalizeError(error)
  }
)

export function backendRequest<T>(path: string, config: AxiosRequestConfig = {}): Promise<T> {
  return backendApi.request<T>({ url: path, ...config })
}

// webui 固定来源为 web
setBackendSourceApp("web")
