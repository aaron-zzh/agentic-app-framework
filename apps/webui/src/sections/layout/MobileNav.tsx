/**
 * 移动端导航——Sheet 侧边栏 + 导航菜单
 * @author AaronZZH & Kiro
 */

"use client"

import { Menu } from "lucide-react"
import { usePathname } from "next/navigation"
import { useMemo } from "react"
import { Brand } from "@/components/brand/Brand"
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet"
import { useLicenseStatus } from "@/lib/queries/use-license-status"
import { useUserMenus } from "@/lib/queries/use-menus"
import { cn } from "@/lib/utils/cn"
import {
  buildNavConfig,
  buildNavFromApi,
  buildOfficialNavConfig,
  type NavGroup,
  type NavItem
} from "@/sections/layout/nav-config"

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
  const { data: menus } = useUserMenus()
  const { data: license } = useLicenseStatus()
  const navConfig = useMemo(() => {
    const appendOfficial = (groups: NavGroup[]) =>
      license?.owner ? [...groups, buildOfficialNavConfig()] : groups
    if (menus) return appendOfficial(buildNavFromApi(menus))
    return appendOfficial(buildNavConfig())
  }, [menus, license?.owner])

  return (
    <nav className="flex h-full flex-col overflow-y-auto py-4">
      <div className="mb-4 px-4">
        <Brand href="/dashboard" />
      </div>
      {navConfig.map((group: NavGroup) => (
        <div key={group.subheader} className="mb-3 px-2">
          <p className="mb-1 px-2 font-medium text-muted-foreground text-xs uppercase">
            {group.subheader}
          </p>
          {group.items.map((item: NavItem) => (
            <MobileNavItem key={item.path} item={item} pathname={pathname} />
          ))}
        </div>
      ))}
    </nav>
  )
}

function MobileNavItem({
  item,
  pathname,
  depth = 0
}: {
  item: NavItem
  pathname: string
  depth?: number
}) {
  return (
    <>
      <a
        href={item.path}
        className={cn(
          "flex items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-accent",
          pathname.startsWith(item.path) && "bg-primary/10 font-medium text-primary"
        )}
        style={{ paddingLeft: `${depth * 12 + 8}px` }}
      >
        {item.title}
      </a>
      {item.children?.map((child) => (
        <MobileNavItem key={child.path} item={child} pathname={pathname} depth={depth + 1} />
      ))}
    </>
  )
}
