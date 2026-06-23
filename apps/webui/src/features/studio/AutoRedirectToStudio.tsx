/**
 * AutoRedirectToStudio——已登录用户自动跳转到驾驶舱
 *
 * 挂在根路径 `/` 营销页顶部。客户端 hydrate 后检查 auth token，
 * 已登录则 replace 到 /studio（不留返回历史，不打断 SEO）。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useRouter } from "next/navigation"
import { useEffect } from "react"
import { useAuthStore } from "@/lib/store/auth-store"

export function AutoRedirectToStudio() {
  const router = useRouter()
  const accessToken = useAuthStore((s) => s.accessToken)

  useEffect(() => {
    if (accessToken) {
      router.replace("/studio?welcome=1")
    }
  }, [accessToken, router])

  return null
}
