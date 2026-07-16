"use client"

/**
 * AuthProvider——应用启动时校验 token 有效性并拉取用户信息
 * 挂在根 layout，覆盖所有路由（含营销页/公开页）：这类页面上的 FloatingChatter 等组件
 * 同样会在 isAuthenticated=true 时发起需要组织上下文的请求，必须统一在此校正 X-Org-Id，
 * 不能只覆盖 workspace/studio 局部路由。
 * token 失效时由 API 拦截器统一跳转登录页（backend-client.ts redirectToLogin）
 *
 * @author AaronZZH & Kiro
 */

import { useEffect } from "react"
import { useAuth } from "./use-auth"

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const { checkAuth } = useAuth()

  useEffect(() => {
    checkAuth()
  }, [checkAuth])

  return <>{children}</>
}
