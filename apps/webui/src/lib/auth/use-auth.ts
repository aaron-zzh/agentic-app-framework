/**
 * useAuth hook——封装登录/登出/token 校验，参考 xueji/demo 模式
 * @author AaronZZH & Kiro
 */

import { useQueryClient } from "@tanstack/react-query"
import { useCallback } from "react"
import { authApi, organizationApi, workspaceApi } from "@/lib/api/rest/user"
import { type AuthUser, useAuthStore } from "@/lib/store/auth-store"
import {
  hasSuperAdminRole,
  type ScopeSelection,
  useOrgStore
} from "@/lib/store/org-store"
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

  /** 强制刷新当前登录用户信息（GET /auth/me），把后端最新字段同步进 auth store */
  const refreshUser = useCallback(async () => {
    if (isMockAuthEnabled()) {
      setUser(mockedUser)
      return
    }
    const info = await authApi.me()
    setUser({ ...info.user, roles: info.roles } as AuthUser)
    // 顺手让 ["auth", "me"] 缓存失效，下次组件订阅时自动拿到最新
    qc.invalidateQueries({ queryKey: ["auth", "me"] })
  }, [qc, setUser])

  /** 恢复并校验当前用户的稳定组织范围。 */
  const ensureOrgContext = useCallback(async () => {
    const authUser = useAuthStore.getState().user
    if (!authUser) return
    const orgState = useOrgStore.getState()
    const persistedScope = orgState.beginRestore(authUser.id)
    try {
      const [orgs, defaultContext] = await Promise.all([
        organizationApi.list(),
        organizationApi.defaultContext()
      ])
      const isSuperAdmin = hasSuperAdminRole(authUser.roles)
      let validScope: ScopeSelection | null = null

      if (persistedScope?.kind === "all-organizations") {
        if (isSuperAdmin || orgs.length >= 2) validScope = persistedScope
      } else if (persistedScope?.kind === "all-workspaces") {
        const org = orgs.find((item) => item.id === persistedScope.orgId)
        if (org && (isSuperAdmin || org.memberRole === "owner" || org.memberRole === "admin")) {
          validScope = persistedScope
        }
      } else if (persistedScope?.kind === "workspace") {
        const orgExists = orgs.some((item) => item.id === persistedScope.orgId)
        if (orgExists) {
          const page = await workspaceApi.list(persistedScope.orgId, false)
          if (
            page.list.some(
              (workspace) =>
                workspace.orgId === persistedScope.orgId &&
                workspace.id === persistedScope.workspaceId
            )
          ) {
            validScope = persistedScope
          }
        }
      }

      orgState.commitScope(
        authUser.id,
        validScope ?? {
          kind: "workspace",
          orgId: defaultContext.orgId,
          workspaceId: defaultContext.workspaceId
        }
      )
    } catch {
      orgState.failRestore("工作区上下文不可用")
    }
  }, [])

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
      setUser({ ...info.user, roles: info.roles } as AuthUser)
      await ensureOrgContext()
      return { isValid: true }
    } catch {
      // 清理token
      clearAuth()
      // 登出时清掉所有缓存
      qc.clear()
      return { isValid: false, reason: "invalid_token" as const }
    } finally {
      setChecking(false)
    }
  }, [qc, setChecking, setTokens, setUser, clearAuth, ensureOrgContext])

  /** 登录：写 tokens + 拉用户信息 */
  const login = useCallback(
    async (email: string, password: string) => {
      const result = await authApi.login(email, password)
      setTokens(result.accessToken, result.refreshToken)
      const info = await authApi.me()
      setUser({ ...info.user, roles: info.roles } as AuthUser)
      await ensureOrgContext()
      return result
    },
    [setTokens, setUser, ensureOrgContext]
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
    isAdmin: user?.roles?.some((r) => r === "admin" || r === "super_admin") ?? false,
    checkAuth,
    refreshUser,
    login,
    logout
  }
}
