/**
 * 个人中心统一 Layout——左侧固定侧边导航 + 右侧内容区
 * 参考 minimal-ui account-layout（顶部 tab → 侧边栏）
 * @author AaronZZH
 */

"use client"

import {
  Bell,
  Building2,
  CreditCard,
  Globe,
  Key,
  Link2,
  LogOut,
  Shield,
  Ticket,
  User
} from "lucide-react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { useAuthStore } from "@/lib/store/auth-store"
import { cn } from "@/lib/utils/cn"
import { LicensePlanBadge } from "@/sections/layout/LicensePlanBadge"

// ─── 导航配置（对标参考：General / Billing / Notifications / Social Links / Security）

const NAV_GROUPS: {
  label?: string
  items: { label: string; href: string; icon: React.ComponentType<{ className?: string }> }[]
}[] = [
  {
    items: [
      { label: "编辑资料", href: "/settings/profile", icon: User },
      { label: "通知设置", href: "/settings/notifications", icon: Bell },
      { label: "社交链接", href: "/settings/socials", icon: Link2 },
      { label: "安全设置", href: "/settings/security", icon: Shield }
    ]
  },
  {
    label: "计费",
    items: [
      { label: "积分详情", href: "/settings/credits", icon: CreditCard },
      { label: "价格套餐", href: "/settings/pricing", icon: Ticket }
    ]
  },
  {
    label: "管理",
    items: [
      { label: "API 密钥", href: "/settings/api-keys", icon: Key },
      { label: "组织管理", href: "/settings/organization", icon: Building2 },
      { label: "授权委托", href: "/settings/delegation", icon: Globe }
    ]
  }
]

const FOOTER_LINKS = [
  { label: "使用条款", href: "/terms" },
  { label: "隐私政策", href: "/privacy" }
]

// ─── 侧边栏 ───────────────────────────────────────────────────────────────────

function SettingsSidebar() {
  const pathname = usePathname()
  const router = useRouter()
  const user = useAuthStore((s) => s.user)
  const clearAuth = useAuthStore((s) => s.clearAuth)

  const handleLogout = () => {
    clearAuth()
    router.push("/login")
  }

  return (
    <aside className="flex h-full w-56 shrink-0 flex-col gap-1 py-6 pr-4">
      {/* 用户信息 */}
      <div className="mb-4 flex items-center gap-3 px-2">
        <Avatar className="size-9">
          <AvatarFallback>{user?.nickname?.charAt(0) ?? "U"}</AvatarFallback>
        </Avatar>
        <p className="truncate text-muted-foreground text-sm">{user?.email ?? user?.nickname}</p>
      </div>

      {/* 导航分组 */}
      <nav className="flex-1 space-y-1 overflow-y-auto">
        {NAV_GROUPS.map((group, gi) => (
          <div key={gi} className={gi > 0 ? "pt-3" : ""}>
            {group.label && (
              <p className="mb-1 px-3 font-semibold text-muted-foreground/60 text-xs uppercase tracking-wider">
                {group.label}
              </p>
            )}
            {group.items.map(({ label, href, icon: Icon }) => {
              const active = pathname === href || pathname.startsWith(`${href}/`)
              return (
                <Link
                  key={href}
                  href={href}
                  className={cn(
                    "flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors",
                    active
                      ? "bg-accent font-medium text-accent-foreground"
                      : "text-muted-foreground hover:bg-accent/50 hover:text-foreground"
                  )}
                >
                  <Icon className="size-4 shrink-0" />
                  {label}
                </Link>
              )
            })}
            {gi < NAV_GROUPS.length - 1 && <Separator className="mt-3" />}
          </div>
        ))}
      </nav>

      {/* 底部链接 + 退出 */}
      <div className="mt-4 space-y-1 border-t pt-4">
        {FOOTER_LINKS.map(({ label, href }) => (
          <Link
            key={href}
            href={href}
            className="block px-3 py-1.5 text-muted-foreground text-xs hover:text-foreground"
          >
            {label}
          </Link>
        ))}
        <Button
          variant="ghost"
          size="sm"
          className="mt-1 w-full justify-start gap-3 text-muted-foreground"
          onClick={handleLogout}
        >
          <LogOut className="size-4" />
          退出登录
        </Button>

        {/* 框架版本 / 授权——置于退出登录之后 */}
        <div className="pt-2">
          <LicensePlanBadge collapsed={false} />
        </div>
      </div>
    </aside>
  )
}

// ─── Layout ───────────────────────────────────────────────────────────────────

export default function SettingsLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-full">
      <SettingsSidebar />
      <Separator orientation="vertical" className="h-auto" />
      <main className="flex-1 overflow-auto">{children}</main>
    </div>
  )
}
