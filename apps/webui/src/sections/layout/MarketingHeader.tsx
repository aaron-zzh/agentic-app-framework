/**
 * MarketingHeader——营销页顶部导航
 * @author AaronZZH & Kiro
 */

import Link from "next/link"

import { paths } from "@/lib/constants/paths"

const navLinks = [
  { label: "产品", href: "/" },
  { label: "定价", href: "/pricing" },
  { label: "模板", href: "/templates" },
  { label: "文档", href: "/docs" }
]

/** 营销页顶部导航 */
export function MarketingHeader() {
  return (
    <header className="sticky top-0 z-50 flex h-[var(--layout-marketing-header-height)] items-center border-b bg-background/95 backdrop-blur">
      <div className="mx-auto flex w-full max-w-[var(--layout-marketing-max-width)] items-center justify-between px-6">
        <Link href="/" className="text-xl font-bold">
          AAF
        </Link>

        <nav className="hidden items-center gap-6 md:flex">
          {navLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="text-sm text-muted-foreground transition-colors hover:text-foreground"
            >
              {link.label}
            </Link>
          ))}
        </nav>

        <div className="flex items-center gap-3">
          <Link
            href={paths.auth.login}
            className="text-sm text-muted-foreground hover:text-foreground"
          >
            登录
          </Link>
          <Link
            href={paths.auth.register}
            className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
          >
            免费开始
          </Link>
        </div>
      </div>
    </header>
  )
}
