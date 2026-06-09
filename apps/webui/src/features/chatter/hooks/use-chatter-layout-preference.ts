/**
 * useChatterLayoutPreference——页面声明 Chatter 布局偏好
 *
 * 在需要嵌入模式的页面顶层调用，离开时自动恢复默认（dialog 浮动）。
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

export function useChatterLayoutPreference(layout: ChatterLayout): void {
  const setLayoutOverride = useChatterStore((s) => s.setLayoutOverride)
  const setOpen = useChatterStore((s) => s.setOpen)

  useEffect(() => {
    setLayoutOverride(layout)
    // panel 模式进入时自动展开
    if (layout === "panel") setOpen(true)
    return () => {
      setLayoutOverride(null)
      // 离开 panel/page 页面时关闭，避免回到浮动模式时 dialog 自动弹出
      if (layout === "panel" || layout === "page") setOpen(false)
    }
  }, [layout, setLayoutOverride, setOpen])
}
