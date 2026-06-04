"use client"

/**
 * AuthProvider——应用启动时校验 token 有效性并拉取用户信息
 * 挂在 workspace layout，确保进入工作区时用户数据已就绪
 *
 * @author AaronZZH & Kiro
 */

import { useEffect } from "react"
import { useAuth } from "./use-auth"

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const { checkAuth } = useAuth()

  useEffect(() => {
    // 内部调用查询用户信息接口确认，未登录时会跳转登录页
    checkAuth()
  }, [checkAuth])

  return <>{children}</>
}
