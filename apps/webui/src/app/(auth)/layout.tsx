/**
 * 认证布局——全屏背景虚化 + 中央亚克力卡片
 * @author AaronZZH & Kiro
 */

"use client"

import { Suspense } from "react"
import { GuestGuard } from "@/lib/auth/GuestGuard"
import { AuthHeader } from "@/sections/layout/AuthHeader"

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="relative flex min-h-screen flex-col overflow-hidden bg-background">
      {/* Header */}
      <AuthHeader />

      {/* 亮色背景 */}
      <div
        className="absolute inset-0 bg-center bg-cover opacity-55 transition-opacity duration-300 dark:opacity-0"
        style={{ backgroundImage: "url('/assets/images/cover/cover-10.webp')" }}
      />

      {/* 暗色背景 */}
      <div
        className="absolute inset-0 bg-center bg-cover opacity-0 transition-opacity duration-300 dark:opacity-40"
        style={{ backgroundImage: "url('/assets/images/cover/cover-11.webp')" }}
      />

      {/* 主题遮罩 */}
      <div className="absolute inset-0 bg-background/72 backdrop-blur-sm transition-colors duration-300 dark:bg-background/88" />

      {/* 卡片 */}
      <div className="relative z-10 flex flex-1 items-center justify-center px-4 py-24">
        <div className="w-full max-w-[420px] rounded-2xl border bg-background/92 p-8 shadow-2xl shadow-black/10 backdrop-blur-md transition-colors duration-300 dark:border-white/10 dark:bg-background/86 dark:shadow-black/35">
          <Suspense>
            <GuestGuard>{children}</GuestGuard>
          </Suspense>
        </div>
      </div>
    </div>
  )
}
