/**
 * useAuth hook——封装登录/登出/token 校验，参考 xueji/demo 模式
 * @author AaronZZH & Kiro
 */

import { useQueryClient } from "@tanstack/react-query"
import { useCallback } from "react"
import { authApi } from "@/lib/api/rest/user/auth"
import { type AuthUser, useAuthStore } from "@/lib/store/auth-store"
import {
  isMockAuthEnabled,
  MOCK_AUTH_ACCESS_TOKEN,
  MOCK_AUTH_REFRESH_TOKEN,
  mockedUser
} from "./mock-user"

export function useAuth() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const accessToken = useAuthStore((state) => state.accessToken)
  const user = useAuthStore((state) => state.user)
  const isChecking = useAuthStore((state) => state.isChecking)
  const setTokens = useAuthStore((state) => state.setTokens)
  const setUser = useAuthStore((state) => state.setUser)
  const setChecking = useAuthStore((state) => state.setChecking)
  const clearAuth = useAuthStore((state) => state.clearAuth)
  const qc = useQueryClient()

  /** 校验当前 token 有效性，并拉取最新用户信息 */
  const checkAuth = useCallback(async () => {
    if (isMockAuthEnabled()) {
      setTokens(MOCK_AUTH_ACCESS_TOKEN, MOCK_AUTH_REFRESH_TOKEN)
      setUser(mockedUser)
      setChecking(false)
      return { isValid: true }
    }

    // 从 store 直接读取最新 token，避免 useCallback 依赖 accessToken 导致 token 刷新时循环触发
    const currentToken = useAuthStore.getState().accessToken
    if (!currentToken) return { isValid: false, reason: "no_token" as const }
    setChecking(true)
    try {
      const info = await qc.fetchQuery({
        queryKey: ["auth", "me"],
        queryFn: () => authApi.me(),
        staleTime: 30_000
      })
      setUser(info as AuthUser)
      return { isValid: true }
    } catch {
      clearAuth()
      qc.clear()
      return { isValid: false, reason: "invalid_token" as const }
    } finally {
      setChecking(false)
    }
  }, [qc, setChecking, setTokens, setUser, clearAuth])

  /** 登录：写 tokens + 拉用户信息 */
  const login = useCallback(
    async (email: string, password: string) => {
      const result = await authApi.login(email, password)
      setTokens(result.accessToken, result.refreshToken)
      const info = await authApi.me()
      setUser(info as AuthUser)
      return result
    },
    [setTokens, setUser]
  )

  /** 登出：清除本地状态 + 通知后端 */
  const logout = useCallback(async () => {
    const state = useAuthStore.getState()
    if (state.accessToken && state.refreshToken) {
      try {
        await authApi.logout(state.accessToken, state.refreshToken)
      } catch {
        // 后端失败不影响本地清除
      }
    }
    clearAuth()
    qc.clear()
  }, [clearAuth, qc])

  return {
    isAuthenticated,
    accessToken,
    user,
    isChecking,
    checkAuth,
    login,
    logout
  }
}
