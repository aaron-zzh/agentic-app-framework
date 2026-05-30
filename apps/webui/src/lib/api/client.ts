/**
 * API 客户端基础层——统一 fetch 封装
 * @author AaronZZH & Kiro
 */

/** 分页响应结构（与后端 PageResult<T> 对齐） */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

/** 后端统一响应结构 */
export interface ApiResult<T> {
  code: number
  data: T
  message?: string
}

/** 列表查询参数 */
export interface ListParams extends Record<string, string | number | boolean | string[] | undefined> {
  page?: number
  pageSize?: number
  sort?: string
  search?: string
}

/** API 错误 */
export class ApiError extends Error {
  constructor(
    public code: number,
    message: string
  ) {
    super(message)
    this.name = "ApiError"
  }
}

import { useAuthStore } from "@/lib/store/auth-store"
import { useOrgStore } from "@/lib/store/org-store"
import { useUIStore } from "@/lib/store/ui-store"

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "/api"

/** Token 刷新锁——防止并发刷新 */
let refreshPromise: Promise<boolean> | null = null

/** 尝试刷新 Token，返回是否成功 */
async function tryRefreshToken(): Promise<boolean> {
  if (refreshPromise) return refreshPromise
  refreshPromise = (async () => {
    const { refreshToken, setTokens, clearAuth } = useAuthStore.getState()
    if (!refreshToken) {
      clearAuth()
      return false
    }
    try {
      const res = await fetch(`${BASE_URL}/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken })
      })
      if (!res.ok) {
        clearAuth()
        return false
      }
      const json: ApiResult<{ accessToken: string; refreshToken: string }> = await res.json()
      if (json.code !== 0) {
        clearAuth()
        return false
      }
      setTokens(json.data.accessToken, json.data.refreshToken)
      return true
    } catch {
      clearAuth()
      return false
    } finally {
      refreshPromise = null
    }
  })()
  return refreshPromise
}

/** 跳转登录页（避免循环导入 next/navigation） */
function redirectToLogin() {
  if (typeof window !== "undefined") {
    window.location.href = "/auth/login"
  }
}

/** 通用 fetch 封装 */
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const url = `${BASE_URL}${path}`
  const workspaceId = useUIStore.getState().currentWorkspace?.id
  const orgId = useOrgStore.getState().currentOrgId
  const accessToken = useAuthStore.getState().accessToken

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(workspaceId && { "X-Workspace-Id": workspaceId }),
    ...(orgId && { "X-Org-Id": orgId }),
    ...(accessToken && { Authorization: `Bearer ${accessToken}` }),
    ...(init?.headers as Record<string, string>)
  }

  const res = await fetch(url, { ...init, headers })

  // 401 自动刷新 Token 并重试
  if (res.status === 401 && !path.startsWith("/auth/")) {
    const refreshed = await tryRefreshToken()
    if (refreshed) {
      const newToken = useAuthStore.getState().accessToken
      const retryRes = await fetch(url, {
        ...init,
        headers: { ...headers, Authorization: `Bearer ${newToken}` }
      })
      if (!retryRes.ok) {
        throw new ApiError(retryRes.status, `请求失败: ${retryRes.statusText}`)
      }
      const retryJson: ApiResult<T> = await retryRes.json()
      if (retryJson.code !== 0) {
        throw new ApiError(retryJson.code, retryJson.message ?? "未知错误")
      }
      return retryJson.data
    }
    redirectToLogin()
    throw new ApiError(401, "登录已过期，请重新登录")
  }

  if (!res.ok) {
    throw new ApiError(res.status, `请求失败: ${res.statusText}`)
  }

  const json: ApiResult<T> = await res.json()
  if (json.code !== 0) {
    throw new ApiError(json.code, json.message ?? "未知错误")
  }
  return json.data
}

/** 构建查询字符串 */
function buildQuery(params: ListParams): string {
  const entries = Object.entries(params).filter(
    ([, v]) => v !== undefined && v !== null && v !== ""
  )
  if (entries.length === 0) return ""
  const pairs: [string, string][] = []
  for (const [k, v] of entries) {
    if (Array.isArray(v)) {
      for (const item of v) {
        pairs.push([k, String(item)])
      }
    } else {
      pairs.push([k, String(v)])
    }
  }
  return `?${new URLSearchParams(pairs).toString()}`
}

/** 获取分页列表 */
export function fetchList<T = Record<string, unknown>>(
  apiPath: string,
  params: ListParams
): Promise<PageResult<T>> {
  return request<PageResult<T>>(`${apiPath}${buildQuery(params)}`)
}

/** 获取单条记录 */
export function fetchRecord<T = Record<string, unknown>>(apiPath: string, id: string): Promise<T> {
  return request<T>(`${apiPath}/${id}`)
}

/** 创建记录 */
export function createRecord<T = Record<string, unknown>>(
  apiPath: string,
  data: Record<string, unknown>
): Promise<T> {
  return request<T>(apiPath, { method: "POST", body: JSON.stringify(data) })
}

/** 更新记录 */
export function updateRecord<T = Record<string, unknown>>(
  apiPath: string,
  id: string,
  data: Record<string, unknown>
): Promise<T> {
  return request<T>(`${apiPath}/${id}`, { method: "PUT", body: JSON.stringify(data) })
}

/** 删除记录 */
export function deleteRecord(apiPath: string, ids: string[]): Promise<void> {
  return request<void>(apiPath, { method: "DELETE", body: JSON.stringify({ ids }) })
}
