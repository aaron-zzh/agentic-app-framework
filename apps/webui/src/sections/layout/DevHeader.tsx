/**
 * DevHeader——开发调试页顶栏
 * Brand + 导航菜单 + 主题切换 + 登录状态
 * @author AaronZZH & Kiro
 */

"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { useTheme } from "next-themes"

import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { cn } from "@/lib/utils/cn"

const devPages = [
  { label: "组件", href: "/components" },
  { label: "工作区", href: "/workspace" }
]

/** 主题切换按钮 */
function ThemeToggle() {
  const { theme, setTheme } = useTheme()
  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
      aria-label="切换主题"
      title={theme === "dark" ? "切换为亮色" : "切换为暗色"}
    >
      <span className="dark:hidden">🌙</span>
      <span className="hidden dark:inline">☀️</span>
    </Button>
  )
}

export function DevHeader() {
  const pathname = usePathname()

  return (
    <header className="sticky top-0 z-50 flex h-12 items-center border-b bg-background/95 backdrop-blur">
      <div className="flex w-full items-center gap-4 px-4">
        {/* Brand */}
        <Link href="/" className="flex items-center gap-2 font-bold text-base">
          {/* biome-ignore lint/performance/noImgElement: logo 无需 next/image 优化 */}
          <img src="/logo.png" alt="AAF logo" className="size-6" />
          AAF
        </Link>
        <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-muted-foreground text-xs">
          dev
        </span>

        <Separator orientation="vertical" className="h-4" />

        {/* 导航 */}
        <nav className="flex items-center gap-1">
          {devPages.map((p) => {
            const active = pathname.startsWith(p.href)
            return (
              <Link
                key={p.href}
                href={p.href}
                className={cn(
                  "px-2 py-1 text-sm transition-colors",
                  active
                    ? "font-medium text-foreground underline underline-offset-4"
                    : "text-muted-foreground hover:text-foreground"
                )}
              >
                {p.label}
              </Link>
            )
          })}
        </nav>

        <div className="flex-1" />

        {/* 主题切换 */}
        <ThemeToggle />

        {/* 登录状态占位 */}
        <Button variant="outline" size="sm" asChild>
          <Link href="/login">登录</Link>
        </Button>
      </div>
    </header>
  )
}
