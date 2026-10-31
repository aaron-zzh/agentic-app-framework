"use client"

/**
 * AuthGuard——需要登录才能访问的页面守卫
 * 未登录时跳转到登录页，并携带 redirect 参数以便登录后返回。
 *
 * @author AaronZZH & Kiro
 */

import { usePathname, useRouter, useSearchParams } from "next/navigation"
import { useEffect, useState } from "react"
import { SplashScreen } from "@/components/common/SplashScreen"
import { paths } from "@/lib/constants/paths"
import { useAuth } from "./use-auth"

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const { isAuthenticated } = useAuth()
  const [checked, setChecked] = useState(false)
  const query = searchParams.toString()
  const redirectTo = query ? `${pathname}?${query}` : pathname

  useEffect(() => {
    if (!isAuthenticated) {
      // 未登录：跳转登录页，保留完整查询参数以便登录后返回当前页。
      router.replace(`${paths.auth.login}?redirect=${encodeURIComponent(redirectTo)}`)
    } else {
      setChecked(true)
    }
  }, [isAuthenticated, router, redirectTo])

  if (!checked) return <SplashScreen />

  return <>{children}</>
}
