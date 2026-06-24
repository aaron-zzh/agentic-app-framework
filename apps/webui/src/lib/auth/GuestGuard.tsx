"use client"

/**
 * GuestGuard——仅允许未登录用户访问（登录/注册页）
 * 已登录时跳转到 returnTo 或工作区，避免闪烁。
 *
 * @author AaronZZH & Kiro
 */

import { useRouter, useSearchParams } from "next/navigation"
import { useEffect, useState } from "react"
import { SplashScreen } from "@/components/common/SplashScreen"
import { paths } from "@/lib/constants/paths"
import { useAuth } from "./use-auth"

export function GuestGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { isAuthenticated, isChecking } = useAuth()
  const [checked, setChecked] = useState(false)
  const redirectTo = searchParams.get("redirect") ?? paths.studio.welcome

  useEffect(() => {
    // isChecking 可能因页面跳转残留为 true，加 300ms 超时兜底
    if (isChecking) {
      const timeout = setTimeout(() => setChecked(true), 300)
      return () => clearTimeout(timeout)
    }
    if (isAuthenticated) {
      const dest = redirectTo === paths.studio.welcome ? "/studio" : redirectTo
      router.replace(dest)
    } else {
      setChecked(true)
    }
  }, [isAuthenticated, isChecking, router, redirectTo])

  if (!checked) return <SplashScreen />

  return (
    <div className="w-full max-w-[420px] rounded-2xl border bg-background/92 p-8 shadow-2xl shadow-black/10 backdrop-blur-md transition-colors duration-300 dark:border-white/10 dark:bg-background/86 dark:shadow-black/35">
      {children}
    </div>
  )
}
