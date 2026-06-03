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

  useEffect(() => {
    if (isChecking) return
    if (isAuthenticated) {
      const returnTo = searchParams.get("redirect") ?? paths.workspace.root
      router.replace(returnTo)
    } else {
      setChecked(true)
    }
  }, [isAuthenticated, isChecking, router, searchParams])

  if (!checked) return <SplashScreen />

  return <>{children}</>
}
