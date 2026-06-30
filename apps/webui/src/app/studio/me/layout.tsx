/**
 * Studio 个人中心 Layout——左侧导航 + 右侧内容区
 * 参考 (workspace)/settings/layout.tsx
 */

"use client"

import {
  Bell,
  CheckSquare,
  CreditCard,
  Gift,
  LayoutDashboard,
  LogOut,
  Ticket,
  User,
  Zap
} from "lucide-react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { useAuth } from "@/lib/auth/use-auth"
import { $url } from "@/lib/utils"
import { cn } from "@/lib/utils/index"

const NAV_GROUPS: {
  label?: string
  items: { label: string; href: string; icon: React.ComponentType<{ className?: string }> }[]
}[] = [
  {
    items: [
      { label: "账号资料", href: "/studio/me/account", icon: User },
      { label: "成长任务", href: "/studio/me/tasks", icon: CheckSquare },
      { label: "通知设置", href: "/studio/me/settings", icon: Bell }
    ]
  },
  {
    label: "积分与套餐",
    items: [
      { label: "AI模型", href: "/studio/me/pricing", icon: Zap },
      { label: "积分详情", href: "/studio/me/credits", icon: CreditCard },
      { label: "会员套餐", href: "/studio/me/membership", icon: Ticket },
      { label: "邀请赚积分", href: "/studio/me/invite", icon: Gift }
    ]
  }
]

function StudioMeSidebar() {
  const pathname = usePathname()
  const router = useRouter()
  const { user, logout, isAdmin } = useAuth()

  const handleLogout = async () => {
    await logout()
    router.push("/login")
  }

  return (
    <aside className="flex h-full w-52 shrink-0 flex-col gap-1 px-4 py-6">
      {/* 用户信息 */}
      <div className="mb-4 flex items-center gap-3 px-2">
        <Avatar className="size-9">
          <AvatarImage src={user?.avatar || $url.cdn("/assets/avatar/avatar.png")} />
          <AvatarFallback>{user?.nickname?.charAt(0) ?? "U"}</AvatarFallback>
        </Avatar>
        <div className="min-w-0">
          <p className="truncate font-medium text-sm">{user?.nickname ?? user?.username}</p>
          <p className="truncate text-muted-foreground text-xs">{user?.email}</p>
        </div>
      </div>

      {/* 管理员：跳转工作台后台 */}
      {isAdmin && (
        <Link
          href="/dashboard"
          className="mb-3 flex items-center gap-2 rounded-lg border border-amber-500/20 bg-gradient-to-r from-amber-500/10 to-orange-500/10 px-3 py-2 font-medium text-amber-500 text-xs transition-colors hover:from-amber-500/15 hover:to-orange-500/15"
        >
          <LayoutDashboard className="size-3.5 shrink-0" />
          <span className="flex-1">进入工作台</span>
        </Link>
      )}

      {/* 导航 */}
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

      {/* 退出 */}
      <div className="mt-4 border-t pt-4">
        <Button
          variant="ghost"
          size="sm"
          className="w-full justify-start gap-3 text-muted-foreground"
          onClick={handleLogout}
        >
          <LogOut className="size-4" />
          退出登录
        </Button>
      </div>
    </aside>
  )
}

export default function StudioMeLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-full">
      <StudioMeSidebar />
      <Separator orientation="vertical" className="h-auto" />
      <main className="flex-1 overflow-auto">{children}</main>
    </div>
  )
}
