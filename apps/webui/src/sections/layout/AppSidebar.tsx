/**
 * AppSidebar——从 entityRegistry 自动生成侧边栏菜单
 * @author AaronZZH & Kiro
 */

"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { useUIStore } from "@/lib/store/ui-store"
import { cn } from "@/lib/utils/cn"

import { buildNavConfig } from "./nav-config"

/** 侧边栏 */
export function AppSidebar() {
  const pathname = usePathname()
  const sidebarOpen = useUIStore((s) => s.sidebarOpen)
  const navConfig = buildNavConfig()

  if (!sidebarOpen) return null

  return (
    <aside className="flex w-[var(--layout-sidebar-width)] shrink-0 flex-col border-r bg-sidebar">
      <div className="flex h-[var(--layout-header-height)] items-center px-4">
        <span className="font-bold text-lg text-sidebar-foreground">AAF</span>
      </div>
      <nav className="flex-1 overflow-y-auto px-2 py-2">
        {navConfig.map((group) => (
          <div key={group.group} className="mb-4">
            <p className="mb-1 px-2 font-medium text-muted-foreground text-xs uppercase">
              {group.label}
            </p>
            {group.items.map((item) => (
              <Link
                key={item.path}
                href={item.path}
                className={cn(
                  "flex items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-colors hover:bg-sidebar-accent",
                  pathname.startsWith(item.path) &&
                    "bg-sidebar-accent font-medium text-sidebar-accent-foreground"
                )}
              >
                <span>{item.title}</span>
              </Link>
            ))}
          </div>
        ))}
      </nav>
    </aside>
  )
}
