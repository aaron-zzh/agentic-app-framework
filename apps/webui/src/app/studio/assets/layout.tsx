/**
 * 资产工作区 Layout——资产生命周期与管理 sub-tab 切换。
 * @author AaronZZH & Kiro
 */

"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { SectionHaze } from "@/components/studio"
import { getWorkspaceConfig } from "@/features/studio/nav-config"
import { cn } from "@/lib/utils/index"

const ASSETS_CONFIG = getWorkspaceConfig("assets")

export default function StudioAssetsLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const activeItem = [...ASSETS_CONFIG.children]
    .sort((left, right) => right.path.length - left.path.length)
    .find((item) => pathname === item.path || pathname.startsWith(`${item.path}/`))

  return (
    <div className="relative flex h-full flex-col">
      <SectionHaze variant="blend" />

      <div className="relative z-10 border-foreground/6 border-b bg-background/30 backdrop-blur">
        <div className="flex items-center justify-center gap-1 overflow-x-auto px-6 py-2">
          {ASSETS_CONFIG.children.map((item) => {
            const isActive = activeItem?.key === item.key
            const Icon = item.icon
            return (
              <Link
                key={item.key}
                href={item.path}
                className={cn(
                  "flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm transition-all",
                  isActive
                    ? "bg-primary/10 font-medium text-primary"
                    : "text-muted-foreground hover:bg-foreground/4 hover:text-foreground"
                )}
              >
                <Icon className="size-3.5" />
                <span>{item.label}</span>
              </Link>
            )
          })}
        </div>
      </div>

      <div className="relative z-0 min-h-0 flex-1 overflow-y-auto">{children}</div>
    </div>
  )
}
