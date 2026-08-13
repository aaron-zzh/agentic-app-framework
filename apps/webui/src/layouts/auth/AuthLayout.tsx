/**
 * 认证壳层——全屏背景虚化 + 中央亚克力卡片。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import type { ReactNode } from "react"
import { Suspense } from "react"
import { GuestGuard } from "@/lib/auth/GuestGuard"
import { $url } from "@/lib/utils"
import { AuthHeader } from "./AuthHeader"

interface AuthLayoutProps {
  children: ReactNode
}

export function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <div className="relative flex min-h-screen flex-col overflow-hidden bg-background">
      <AuthHeader />

      <div
        className="absolute inset-0 bg-center bg-cover opacity-55 transition-opacity duration-300 dark:opacity-0"
        style={{ backgroundImage: `url('${$url.cdn("/assets/images/cover/cover-10.webp")}')` }}
      />
      <div
        className="absolute inset-0 bg-center bg-cover opacity-0 transition-opacity duration-300 dark:opacity-40"
        style={{ backgroundImage: `url('${$url.cdn("/assets/images/cover/cover-11.webp")}')` }}
      />
      <div className="absolute inset-0 bg-background/72 backdrop-blur-sm transition-colors duration-300 dark:bg-background/88" />

      <div className="relative z-10 flex flex-1 items-center justify-center px-4 py-24">
        <Suspense>
          <GuestGuard>{children}</GuestGuard>
        </Suspense>
      </div>
    </div>
  )
}
