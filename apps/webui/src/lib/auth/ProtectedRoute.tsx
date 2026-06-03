"use client"

/**
 * ProtectedRoute——页面内局部权限守卫
 * 用于按钮/区块级别的权限控制，比 middleware 更细粒度。
 *
 * @author AaronZZH & Kiro
 */

import type { ReactNode } from "react"
import { useAuth } from "./use-auth"

interface Props {
  children: ReactNode
  /** 需要登录，默认 true */
  requireAuth?: boolean
  /** 需要满足任一角色 */
  requiredRoles?: string[]
  /** 无权限时的降级 UI，默认 null */
  fallback?: ReactNode
}

export function ProtectedRoute({
  children,
  requireAuth = true,
  requiredRoles = [],
  fallback = null
}: Props) {
  const { isAuthenticated, user } = useAuth()

  if (requireAuth && !isAuthenticated) return <>{fallback}</>

  if (requiredRoles.length > 0) {
    // user.roles 待后续权限体系落地后替换
    const userRoles: string[] = (user as unknown as { roles?: string[] })?.roles ?? []
    const hasRole = requiredRoles.some((r) => userRoles.includes(r))
    if (!hasRole) return <>{fallback}</>
  }

  return <>{children}</>
}
