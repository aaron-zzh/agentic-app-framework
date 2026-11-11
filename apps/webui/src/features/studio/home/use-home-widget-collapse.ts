/**
 * Studio 首页面板折叠状态：读取与切换本地布局偏好中的折叠面板 key。
 *
 * @example
 * const { collapsed, toggle } = useHomeWidgetCollapse("credits")
 * <HomeWidgetCard collapsible collapsed={collapsed} onToggle={toggle} />
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback } from "react"
import { useStudioHomeLayoutStore } from "@/lib/store/studio-home-layout-store"
import type { HomeWidgetKey } from "./home-widgets"

interface HomeWidgetCollapse {
  collapsed: boolean
  toggle: () => void
}

export function useHomeWidgetCollapse(key: HomeWidgetKey): HomeWidgetCollapse {
  const collapsed = useStudioHomeLayoutStore((state) => state.collapsedKeys.includes(key))
  const setWidgetCollapsed = useStudioHomeLayoutStore((state) => state.setWidgetCollapsed)
  const toggle = useCallback(
    () => setWidgetCollapsed(key, !collapsed),
    [collapsed, key, setWidgetCollapsed]
  )
  return { collapsed, toggle }
}
