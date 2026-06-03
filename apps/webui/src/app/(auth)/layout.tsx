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
    <div className="relative flex min-h-screen flex-col overflow-hidden">
      {/* Header */}
      <AuthHeader />

      {/* 背景图 */}
      <div
        className="absolute inset-0 bg-center bg-cover"
        style={{ backgroundImage: "url('/assets/images/cover/cover-10.webp')" }}
      />
      {/* 背景模糊遮罩 */}
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" />

      {/* 卡片 */}
      <div className="relative z-10 flex flex-1 items-center justify-center">
        <div className="w-full max-w-[420px] rounded-2xl bg-white p-8 shadow-2xl dark:bg-zinc-900">
          <Suspense>
            <GuestGuard>{children}</GuestGuard>
          </Suspense>
        </div>
      </div>
    </div>
  )
}
