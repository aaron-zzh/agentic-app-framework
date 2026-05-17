/**
 * MarketingHeader——营销页顶部导航
 * @author AaronZZH & Kiro
 */

"use client"

import Link from "next/link"
import { useTheme } from "next-themes"
import { Brand } from "@/components/brand/Brand"
import { Button } from "@/components/ui/button"
import { paths } from "@/lib/constants/paths"

const navLinks = [
  { label: "产品", href: "/" },
  { label: "定价", href: "/pricing" },
  { label: "模板", href: "/templates" },
  { label: "文档", href: "/docs" }
]

/** 营销页顶部导航 */
export function MarketingHeader() {
  const { theme, setTheme } = useTheme()

  return (
    <header className="sticky top-0 z-50 flex h-[var(--layout-marketing-header-height)] items-center border-b bg-background/95 backdrop-blur">
      <div className="mx-auto flex w-full max-w-[var(--layout-marketing-max-width)] items-center justify-between px-6">
        <Brand size="lg" />

        <nav className="hidden items-center gap-6 md:flex">
          {navLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="text-muted-foreground text-sm transition-colors hover:text-foreground"
            >
              {link.label}
            </Link>
          ))}
        </nav>

        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
            aria-label="切换主题"
          >
            <span className="dark:hidden">🌙</span>
            <span className="hidden dark:inline">☀️</span>
          </Button>
          <Button variant="ghost" size="sm" asChild>
            <Link href={paths.auth.login}>登录</Link>
          </Button>
          <Button size="sm" asChild>
            <Link href={paths.auth.register}>免费开始</Link>
          </Button>
        </div>
      </div>
    </header>
  )
}
