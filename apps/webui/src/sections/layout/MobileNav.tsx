/**
 * 移动端导航——Sheet 侧边栏 + 导航菜单
 * @author AaronZZH & Kiro
 */

"use client"

import { Menu } from "lucide-react"
import { usePathname } from "next/navigation"
import { Brand } from "@/components/brand/Brand"
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet"
import { cn } from "@/lib/utils/cn"
import { buildNavConfig } from "@/sections/layout/nav-config"

/** 侧边栏切换按钮（移动端弹出 Sheet） */
export function MobileNav() {
  return (
    <Sheet>
      <SheetTrigger
        className="inline-flex rounded-md p-1.5 text-muted-foreground hover:bg-accent md:hidden"
        aria-label="打开菜单"
        render={<button type="button" />}
      >
        <Menu className="size-5" />
      </SheetTrigger>
      <SheetContent
        side="left"
        showCloseButton={false}
        className="w-[var(--layout-sidebar-width)] p-0"
      >
        <MobileSidebarContent />
      </SheetContent>
    </Sheet>
  )
}

/** 移动端侧边栏内容（复用导航配置） */
function MobileSidebarContent() {
  const pathname = usePathname()
  const navConfig = buildNavConfig()

  return (
    <nav className="flex h-full flex-col overflow-y-auto py-4">
      <div className="mb-4 px-4">
        <Brand href="/dashboard" />
      </div>
      {navConfig.map(
        (group: {
          subheader: string
          items: Array<{ path: string; title: string; icon?: string }>
        }) => (
          <div key={group.subheader} className="mb-3 px-2">
            <p className="mb-1 px-2 font-medium text-muted-foreground text-xs uppercase">
              {group.subheader}
            </p>
            {group.items.map((item: { path: string; title: string; icon?: string }) => (
              <a
                key={item.path}
                href={item.path}
                className={cn(
                  "flex items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-accent",
                  pathname.startsWith(item.path) && "bg-primary/10 font-medium text-primary"
                )}
              >
                {item.title}
              </a>
            ))}
          </div>
        )
      )}
    </nav>
  )
}
