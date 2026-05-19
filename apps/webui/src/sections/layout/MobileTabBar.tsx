/**
 * MobileTabBar——底部 Tab 导航（仅手机端显示）
 * @author AaronZZH & Kiro
 *
 * 断点 <768px 时显示，替代侧边栏导航。
 * 使用 Tailwind `md:hidden` 控制可见性（CSS 优先，无 JS 判断）。
 */

"use client"

import { CheckSquare, LayoutDashboard, Menu, Search, Settings } from "lucide-react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { useUIStore } from "@/lib/store/ui-store"
import { cn } from "@/lib/utils/cn"
import { paths } from "@/lib/constants/paths"

interface TabItem {
  label: string
  icon: React.ReactNode
  path: string
  /** 是否精确匹配（默认 startsWith） */
  exact?: boolean
}

const TABS: TabItem[] = [
  { label: "工作台", icon: <LayoutDashboard className="size-5" />, path: paths.workspace.dashboard, exact: true },
  { label: "待办", icon: <CheckSquare className="size-5" />, path: paths.workspace.todos },
  { label: "搜索", icon: <Search className="size-5" />, path: "#search" },
  { label: "设置", icon: <Settings className="size-5" />, path: paths.workspace.settings }
]

/** 底部 Tab 导航 */
export function MobileTabBar() {
  const pathname = usePathname()
  const { toggleSidebar } = useUIStore()

  return (
    <nav className="fixed inset-x-0 bottom-0 z-50 flex h-14 items-center justify-around border-t bg-background md:hidden">
      {TABS.map((tab) => {
        if (tab.path === "#search") {
          return (
            <button
              key={tab.path}
              type="button"
              onClick={toggleSidebar}
              className="flex flex-col items-center gap-0.5 text-muted-foreground"
            >
              <Menu className="size-5" />
              <span className="text-[10px]">菜单</span>
            </button>
          )
        }

        const isActive = tab.exact ? pathname === tab.path : pathname.startsWith(tab.path)

        return (
          <Link
            key={tab.path}
            href={tab.path}
            className={cn(
              "flex flex-col items-center gap-0.5",
              isActive ? "text-primary" : "text-muted-foreground"
            )}
          >
            {tab.icon}
            <span className="text-[10px]">{tab.label}</span>
          </Link>
        )
      })}
    </nav>
  )
}
