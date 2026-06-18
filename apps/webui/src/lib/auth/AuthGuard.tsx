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
  const { isAuthenticated, isChecking } = useAuth()
  const [checked, setChecked] = useState(false)

  useEffect(() => {
    if (isChecking) return
    if (!isAuthenticated) {
      router.replace(`${paths.auth.login}?redirect=${encodeURIComponent(pathname)}`)
    } else {
      setChecked(true)
    }
  }, [isAuthenticated, isChecking, router, pathname])

  if (!checked) return <SplashScreen />

  return <>{children}</>
}
