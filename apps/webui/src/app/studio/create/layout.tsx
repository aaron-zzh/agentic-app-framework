/**
 * 创作工作区 Layout——5 sub-tab 切换
 *
 * 顶部 sub-tab Bar：图像 / 视频 / 文案 / 爆款 / 工具箱
 * 用 path 高亮，下划线发光跟随
 * 详见 docs/design/apps/webui/user-studio-mvp.md B 创作工作区
 *
 * @author AaronZZH & Kiro
 */

"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { SectionHaze } from "@/components/studio"
import { getWorkspaceConfig } from "@/features/studio/nav-config"
import { cn } from "@/lib/utils/index"

const CREATE_CONFIG = getWorkspaceConfig("create")

export default function StudioCreateLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()

  return (
    <div className="relative flex h-full flex-col">
      {/* 顶部光雾装饰 */}
      <SectionHaze variant="violet" />

      {/* sub-tab Bar */}
      <div className="relative z-10 border-foreground/6 border-b bg-background/30 backdrop-blur">
        <div className="flex items-center justify-center gap-1 overflow-x-auto px-6 py-2">
          {CREATE_CONFIG.children.map((item) => {
            const isActive = pathname === item.path || pathname.startsWith(`${item.path}/`)
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
                {item.badge && (
                  <span className="ml-0.5 inline-flex items-center justify-center rounded-full bg-rose-500/20 px-1.5 py-0 text-[10px] text-rose-300">
                    {item.badge}
                  </span>
                )}
              </Link>
            )
          })}
        </div>
      </div>

      {/* sub-tab 内容区 */}
      <div className="relative z-0 min-h-0 flex-1 overflow-y-auto">{children}</div>
    </div>
  )
}
