"use client"

/**
 * AuthProvider——应用启动时校验 token 有效性并拉取用户信息
 * 挂在 workspace layout，确保进入工作区时用户数据已就绪
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
