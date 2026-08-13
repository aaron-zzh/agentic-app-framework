/**
 * 创作功能分区 Layout——创作能力 sub-tab 切换
 *
 * 顶部 sub-tab Bar：创作台 / 文案 / 爆款 / 抠图。
 * 工具箱虽复用 /studio/create 路由树，但属于独立一级功能分区，不显示创作子导航。
 * 详见 docs/design/apps/webui/user-studio-mvp.md B 创作功能分区
 *
 * @author AaronZZH & Kiro
 */

"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { SectionHaze } from "@/components/studio"
import { getSectionConfig } from "@/features/studio/nav-config"
import { cn } from "@/lib/utils/index"

const CREATE_CONFIG = getSectionConfig("create")

export default function StudioCreateLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const isToolsSection =
    pathname.startsWith("/studio/create/tools") || pathname.startsWith("/studio/create/draw")
  const activeItem = [...CREATE_CONFIG.children]
    .sort((left, right) => right.path.length - left.path.length)
    .find((item) => pathname === item.path || pathname.startsWith(`${item.path}/`))

  return (
    <div className="relative flex h-full flex-col">
      <SectionHaze variant="violet" />

      {!isToolsSection ? (
        <div className="relative z-10 border-foreground/6 border-b bg-background/30 backdrop-blur">
          <div className="flex items-center justify-center gap-1 overflow-x-auto px-6 py-2">
            {CREATE_CONFIG.children.map((item) => {
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
                  {item.badge ? (
                    <span className="ml-0.5 inline-flex items-center justify-center rounded-full bg-rose-500/20 px-1.5 py-0 text-[10px] text-rose-300">
                      {item.badge}
                    </span>
                  ) : null}
                </Link>
              )
            })}
          </div>
        </div>
      ) : null}

      <div className="relative z-0 min-h-0 flex-1 overflow-y-auto">{children}</div>
    </div>
  )
}
