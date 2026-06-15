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
        set({ accessToken, refreshToken, isAuthenticated: true })
      },

      setUser: (user) => set({ user }),

      clearAuth: () => {
        clearAxiosAuth()
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
