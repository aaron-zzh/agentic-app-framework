/**
 * TopProgressBar——路由切换时的顶部细线进度条
 * @author AaronZZH & Kiro
 */

"use client"

import { usePathname, useSearchParams } from "next/navigation"
import { useEffect, useState } from "react"

import { cn } from "@/lib/utils/cn"

/** 顶部进度条（路由切换时自动显示） */
export function TopProgressBar() {
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const [loading, setLoading] = useState(false)

  // 路由变化时短暂显示进度条
  // biome-ignore lint/correctness/useExhaustiveDependencies: setLoading 是 stable setter
  useEffect(() => {
    setLoading(true)
    const timer = setTimeout(() => setLoading(false), 300)
    return () => clearTimeout(timer)
  }, [pathname, searchParams])

  return (
    <div
      className={cn(
        "fixed top-0 left-0 z-50 h-0.5 w-full transition-opacity duration-200",
        loading ? "opacity-100" : "opacity-0"
      )}
    >
      <div className="h-full animate-[progress_1s_ease-in-out_infinite] bg-primary" />
    </div>
  )
}
