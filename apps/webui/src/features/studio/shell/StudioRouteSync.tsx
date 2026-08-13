/**
 * Studio 路由变化 → 自动同步 tab 状态
 *
 * 监听 pathname 变化，自动调用 openTab 同步当前 active section。
 * 这是"客户端 tab 状态"与"实际路由"的桥接器，挂在 layout 顶部。
 */

"use client"

import { usePathname } from "next/navigation"
import { useEffect } from "react"
import { getSectionConfig, resolveSectionFromPath } from "../nav-config"
import { useStudioShell } from "./store"

export function StudioRouteSync() {
  const pathname = usePathname()
  const openTab = useStudioShell((s) => s.openTab)

  useEffect(() => {
    const section = resolveSectionFromPath(pathname)
    if (!section) return

    // 项目工作台：每个 id 独立 tab
    const projectMatch = pathname.match(/^\/studio\/projects\/(\d+)/)
    const uniqueKey = projectMatch ? `id-${projectMatch[1]}` : undefined

    const cfg = getSectionConfig(section)
    const title = projectMatch ? `项目 #${projectMatch[1]}` : cfg.label

    openTab({
      section,
      url: pathname,
      title,
      uniqueKey
    })
  }, [pathname, openTab])

  return null
}
