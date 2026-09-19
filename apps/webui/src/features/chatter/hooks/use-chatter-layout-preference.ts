/**
 * useChatterLayoutPreference——页面声明 Chatter 布局偏好
 *
 * 在需要嵌入模式的页面顶层调用；null 恢复默认，drawer 不映射到全局 mode。
 * 离开时自动恢复默认（dialog 浮动）。
 *
 * @example
 * ```tsx
 * aigc/page.tsx 或其 View 组件
 * useChatterLayoutPreference("panel")
 * ```
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect } from "react"
import { useChatterStore } from "@/lib/store/chatter-store"
import type { ChatterLayout } from "../types"

export function useChatterLayoutPreference(layout: ChatterLayout | null): void {
  const setLayoutOverride = useChatterStore((s) => s.setLayoutOverride)
  const setMode = useChatterStore((s) => s.setMode)
  const setOpen = useChatterStore((s) => s.setOpen)

  useEffect(() => {
    setLayoutOverride(layout)

    if (layout === "dialog" || layout === "panel" || layout === "page") {
      setMode(layout)
    }

    if (layout === "panel" || layout === "page") {
      setOpen(true)
    } else if (layout === null) {
      setMode("dialog")
      setOpen(false)
    }

    return () => {
      setLayoutOverride(null)
      if (layout === "panel" || layout === "page") {
        setMode("dialog")
        setOpen(false)
      }
    }
  }, [layout, setLayoutOverride, setMode, setOpen])
}
