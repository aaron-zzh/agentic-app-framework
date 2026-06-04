"use client"

/**
 * AuthProvider——应用启动时校验 token 有效性并拉取用户信息
 * 挂在 workspace layout，确保进入工作区时用户数据已就绪
 *
 * @author AaronZZH & Kiro
 */

import { usePathname, useRouter } from "next/navigation"
import { useEffect } from "react"
import { toast } from "sonner"
import { paths } from "@/lib/constants/paths"
import { useAuth } from "./use-auth"

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const { checkAuth, isAuthenticated, isChecking } = useAuth()
  const router = useRouter()
  const pathname = usePathname()

  // 仅在挂载时执行一次，避免 token 刷新后 checkAuth 引用变化导致循环
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { checkAuth() }, [])

  useEffect(() => {
    if (!isChecking && !isAuthenticated) {
      toast.error("请先登录", {
        description: "此页面需要登录后才能访问",
        duration: 3000
      })
      setTimeout(() => {
        router.push(`${paths.auth.login}?redirect=${encodeURIComponent(pathname)}`)
      }, 1500)
    }
  }, [isAuthenticated, isChecking, pathname, router])

  return <>{children}</>
}
