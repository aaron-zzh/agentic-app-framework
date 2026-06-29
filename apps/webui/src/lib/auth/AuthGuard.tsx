"use client"

/**
 * AuthGuard——需要登录才能访问的页面守卫
 * 未登录时跳转到登录页，并携带 redirect 参数以便登录后返回。
 *
 * @author AaronZZH & Kiro
 */

import { usePathname, useRouter } from "next/navigation"
import { useEffect, useState } from "react"
import { SplashScreen } from "@/components/common/SplashScreen"
import { paths } from "@/lib/constants/paths"
import { useAuth } from "./use-auth"

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const pathname = usePathname()
  const { isAuthenticated } = useAuth()
  const [checked, setChecked] = useState(false)

  useEffect(() => {
    if (!isAuthenticated) {
      // 未登录：跳转登录页，携带 redirect 参数以便登录后返回当前页
      router.replace(`${paths.auth.login}?redirect=${encodeURIComponent(pathname)}`)
    } else {
      setChecked(true)
    }
  }, [isAuthenticated, router, pathname])

  if (!checked) return <SplashScreen />

  return <>{children}</>
}
