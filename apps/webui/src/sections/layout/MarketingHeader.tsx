/**
 * MarketingHeader——营销页顶部导航
 * @author AaronZZH & Kiro
 */

"use client"

import Link from "next/link"
import { Brand } from "@/components/brand/Brand"
import { Button } from "@/components/ui/button"
import { paths } from "@/lib/constants/paths"
import { useScrollOffset } from "@/lib/hooks/use-scroll-offset"
import { useAuthStore } from "@/lib/store/auth-store"
import { cn } from "@/lib/utils/cn"
import { ThemeToggle } from "./HeaderActions"

const navLinks = [
  { label: "产品", href: "/" },
  { label: "定价", href: "/#pricing" },
  { label: "组件", href: "/components" },
  { label: "示例", href: "/examples" }
]

/** 营销页顶部导航 */
export function MarketingHeader() {
  const isOffset = useScrollOffset()
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)

  return (
    <header
      className={cn(
        "sticky top-0 z-50 flex h-(--layout-marketing-header-height) items-center transition-all duration-200",
        isOffset ? "bg-background/80 shadow-sm backdrop-blur-md" : "bg-background/95 backdrop-blur"
      )}
    >
      <div className="mx-auto flex w-full max-w-(--layout-marketing-max-width) items-center justify-between px-6">
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
          <ThemeToggle />
          {isAuthenticated ? (
            <Button size="sm" nativeButton={false} render={<Link href={paths.studio.welcome} />}>
              进入工作区
            </Button>
          ) : (
            <>
              <Button
                variant="ghost"
                size="sm"
                nativeButton={false}
                render={<Link href={paths.auth.login} />}
              >
                登录
              </Button>
              <Button size="sm" nativeButton={false} render={<Link href={paths.auth.register} />}>
                免费开始
              </Button>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
