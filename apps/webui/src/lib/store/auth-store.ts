/**
 * 认证状态 Store——Token 管理 + 用户基本信息缓存
 *
 * 仅缓存 token 和最小用户信息（用于路由守卫判断），
 * 完整用户数据由 TanStack Query 管理。
 *
 * @author AaronZZH & Kiro
 */

import axios from "axios"
import { create } from "zustand"
import { persist } from "zustand/middleware"
import { buildApiUrl } from "@/lib/api/config"
import { type ApiResult, registerBackendTokenRefresh } from "@/lib/api/rest/backend-client"
import { clearAxiosAuth, setAxiosAuth } from "@/lib/auth/utils"

export interface AuthUser {
  id: string
  username: string
  email: string
  nickname: string
  avatar?: string
  roles?: string[]
}

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: AuthUser | null
  isAuthenticated: boolean
  isChecking: boolean
  setTokens: (accessToken: string, refreshToken: string) => void
  setUser: (user: AuthUser) => void
  setChecking: (isChecking: boolean) => void
  clearAuth: () => void
}

interface TokenPair {
  accessToken: string
  refreshToken: string
}

/** 同步写入 cookie（非 httpOnly，仅供 proxy 服务端路由守卫判断登录状态）
 *  后端的 HttpOnly Cookie 因跨域（8080→3000）无法被 Next.js proxy 读取，需前端补写同域 cookie */
function syncTokenCookie(token: string | null) {
  if (typeof document === "undefined") return
  if (token) {
    // biome-ignore lint/suspicious/noDocumentCookie: 需要直接操作 cookie 同步认证状态
    document.cookie = `aaf-token=${token}; path=/; max-age=604800; SameSite=Lax`
  } else {
    // biome-ignore lint/suspicious/noDocumentCookie: 需要直接操作 cookie 清除认证
    document.cookie = "aaf-token=; path=/; max-age=0; expires=Thu, 01 Jan 1970 00:00:00 GMT"
  }
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,
      isChecking: false,

      setChecking: (isChecking) => set({ isChecking }),

      setTokens: (accessToken, refreshToken) => {
        setAxiosAuth(accessToken)
        syncTokenCookie(accessToken)
        set({ accessToken, refreshToken, isAuthenticated: true })
      },

      setUser: (user) => set({ user }),

      clearAuth: () => {
        clearAxiosAuth()
        syncTokenCookie(null)
        set({
          accessToken: null,
          refreshToken: null,
          user: null,
          isAuthenticated: false
        })
      }
    }),
    { name: "aaf-auth" }
  )
)

registerBackendTokenRefresh(async () => {
  const { refreshToken, setTokens, clearAuth } = useAuthStore.getState()
  if (!refreshToken) {
    clearAuth()
    return null
  }

  try {
    const response = await axios.post<ApiResult<TokenPair>>(
      buildApiUrl("/auth/refresh"),
      { refreshToken },
      { timeout: 10_000 }
    )
    if (response.data.code !== 0) {
      clearAuth()
      return null
    }
    setTokens(response.data.data.accessToken, response.data.data.refreshToken)
    return response.data.data.accessToken
  } catch {
    clearAuth()
    return null
  }
})

const initialToken = useAuthStore.getState().accessToken
if (initialToken) setAxiosAuth(initialToken)
