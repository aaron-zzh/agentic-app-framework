/**
 * TopProgressBar——路由切换时的顶部进度条（仅超时才显示）
 * @author AaronZZH & Kiro
 *
 * 快速切换（<200ms）不显示，避免闪烁。超时后短暂显示再消失。
 */

"use client"

import { usePathname, useSearchParams } from "next/navigation"
import { useEffect, useRef, useState } from "react"

import { cn } from "@/lib/utils/cn"

/** 延迟阈值（ms），低于此时间不显示进度条 */
const DELAY = 200
/** 显示持续时间（ms） */
const DURATION = 500

export function TopProgressBar() {
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const [visible, setVisible] = useState(false)
  const delayRef = useRef<ReturnType<typeof setTimeout>>(null)
  const hideRef = useRef<ReturnType<typeof setTimeout>>(null)

  // biome-ignore lint/correctness/useExhaustiveDependencies: pathname/searchParams 变化触发
  useEffect(() => {
    // 清除上一轮 timer
    if (delayRef.current) clearTimeout(delayRef.current)
    if (hideRef.current) clearTimeout(hideRef.current)

    // 延迟显示
    delayRef.current = setTimeout(() => {
      setVisible(true)
      // 显示一段时间后自动隐藏
      hideRef.current = setTimeout(() => setVisible(false), DURATION)
    }, DELAY)

    // 路由切换完成（新页面渲染）→ 立即隐藏
    return () => {
      if (delayRef.current) clearTimeout(delayRef.current)
      if (hideRef.current) clearTimeout(hideRef.current)
      setVisible(false)
    }
  }, [pathname, searchParams])

  return (
    <div
      className={cn(
        "fixed top-0 left-0 z-[9999] h-0.5 w-full transition-opacity duration-150",
        visible ? "opacity-100" : "opacity-0"
      )}
    >
      <div className="h-full animate-[progress_2s_ease-out_forwards] bg-primary shadow-[0_0_4px] shadow-primary/50" />
    </div>
  )
}
