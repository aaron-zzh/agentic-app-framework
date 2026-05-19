/**
 * AppSidebar——侧边栏导航
 * @author AaronZZH & Kiro
 *
 * 支持：展开态（完整菜单） + 折叠态（仅图标）
 * 菜单数据来自 nav-config（本地 + entityRegistry 合并）
 */

"use client"

import { isExternalLink } from "@aaf/core"
import { useBoolean } from "@aaf/hooks"
import {
  CheckSquare,
  ChevronDown,
  FileText,
  LayoutDashboard,
  type LucideIcon,
  Settings,
  Trash2,
  Users
} from "lucide-react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { Brand } from "@/components/brand/Brand"
import { useUIStore } from "@/lib/store/ui-store"
import { cn } from "@/lib/utils/cn"
import { buildNavConfig, type NavGroup, type NavItem } from "./nav-config"

/** 图标名 → lucide 组件映射 */
const ICON_MAP: Record<string, LucideIcon> = {
  "layout-dashboard": LayoutDashboard,
  "file-text": FileText,
  users: Users,
  "check-square": CheckSquare,
  settings: Settings,
  "trash-2": Trash2
}

function NavIcon({ name, className }: { name?: string; className?: string }) {
  const Icon = name ? ICON_MAP[name] : undefined
  if (!Icon) return <span className={cn("size-4 shrink-0", className)} />
  return <Icon className={cn("size-4 shrink-0", className)} />
}

/** 侧边栏 */
export function AppSidebar() {
  const pathname = usePathname()
  const { sidebarOpen, toggleSidebar } = useUIStore()
  const navConfig = buildNavConfig()

  return (
    <aside
      className={cn(
        "relative flex shrink-0 flex-col border-r bg-sidebar transition-[width] duration-200",
        sidebarOpen
          ? "w-[var(--layout-sidebar-width)]"
          : "w-[var(--layout-sidebar-collapsed-width)]"
      )}
    >
      {/* 边缘折叠按钮 */}
      <button
        type="button"
        onClick={toggleSidebar}
        className="absolute top-[calc(var(--layout-header-height)/2)] -right-3 z-10 flex size-6 -translate-y-1/2 items-center justify-center rounded-full border bg-background text-muted-foreground shadow-sm hover:text-foreground"
        aria-label={sidebarOpen ? "收起侧边栏" : "展开侧边栏"}
      >
        <ChevronDown
          className={cn("size-3.5 transition-transform", sidebarOpen ? "-rotate-90" : "rotate-90")}
        />
      </button>

      {/* Brand */}
      <div className="flex h-[var(--layout-header-height)] items-center px-4">
        <Brand collapsed={!sidebarOpen} href="/dashboard" />
      </div>

      {/* 导航 */}
      <nav className="flex-1 overflow-y-auto px-2 py-2">
        {navConfig.map((group) => (
          <NavGroupSection
            key={group.subheader}
            group={group}
            pathname={pathname}
            collapsed={!sidebarOpen}
          />
        ))}
      </nav>
    </aside>
  )
}

/** 导航分组 */
function NavGroupSection({
  group,
  pathname,
  collapsed
}: {
  group: NavGroup
  pathname: string
  collapsed: boolean
}) {
  const { value: open, onToggle: toggleOpen } = useBoolean(true)

  return (
    <div className="mb-3">
      {/* 分组标题 */}
      {collapsed ? (
        <div className="mx-2 my-2 border-t" />
      ) : (
        <button type="button" onClick={toggleOpen} className="flex w-full items-center px-2 py-1">
          <span className="font-medium text-muted-foreground text-xs uppercase tracking-wide">
            {group.subheader}
          </span>
        </button>
      )}

      {(open || collapsed) && (
        <ul className="mt-0.5 space-y-0.5">
          {group.items.map((item) => (
            <NavItemRow key={item.path} item={item} pathname={pathname} collapsed={collapsed} />
          ))}
        </ul>
      )}
    </div>
  )
}

/** 菜单项内容（图标 + 文字 + badge + 展开箭头） */
function NavItemContent({
  item,
  depth,
  collapsed,
  childOpen
}: {
  item: NavItem
  depth: number
  collapsed: boolean
  childOpen: boolean
}) {
  return (
    <>
      {depth === 0 && <NavIcon name={item.icon} />}
      {depth > 0 && !collapsed && (
        <span className="size-1.5 shrink-0 rounded-full bg-current opacity-30" />
      )}
      {!collapsed && <span className="flex-1 truncate text-left">{item.title}</span>}
      {!collapsed && item.badge && (
        <span className="rounded-full bg-primary/10 px-1.5 py-0.5 text-primary text-xs">
          {item.badge}
        </span>
      )}
      {!collapsed && item.children && item.children.length > 0 && (
        <ChevronDown
          className={cn(
            "size-3.5 text-muted-foreground transition-transform",
            childOpen && "rotate-180"
          )}
        />
      )}
    </>
  )
}

/** 单个菜单项（支持递归子菜单） */
function NavItemRow({
  item,
  pathname,
  collapsed,
  depth = 0
}: {
  item: NavItem
  pathname: string
  collapsed: boolean
  depth?: number
}) {
  const { value: childOpen, onToggle: toggleChild } = useBoolean(false)
  const isActive = item.deepMatch ? pathname.startsWith(item.path) : pathname === item.path
  const hasChildren = !collapsed && item.children && item.children.length > 0

  const itemClass = cn(
    "flex w-full items-center gap-2 rounded-md py-1.5 text-sm transition-colors hover:bg-sidebar-accent",
    isActive && "bg-primary/10 font-medium text-primary",
    item.disabled && "pointer-events-none opacity-50",
    collapsed && "justify-center px-2"
  )

  const itemStyle = collapsed ? undefined : { paddingLeft: `${depth * 12 + 8}px`, paddingRight: 8 }
  const content = (
    <NavItemContent item={item} depth={depth} collapsed={collapsed} childOpen={childOpen} />
  )

  return (
    <li>
      {hasChildren ? (
        <button
          type="button"
          onClick={() => toggleChild()}
          title={collapsed ? item.title : undefined}
          className={itemClass}
          style={itemStyle}
        >
          {content}
        </button>
      ) : isExternalLink(item.path) ? (
        <a
          href={item.path}
          target="_blank"
          rel="noopener noreferrer"
          title={collapsed ? item.title : undefined}
          className={itemClass}
          style={itemStyle}
        >
          {content}
        </a>
      ) : (
        <Link
          href={item.path}
          title={collapsed ? item.title : undefined}
          className={itemClass}
          style={itemStyle}
        >
          {content}
        </Link>
      )}

      {/* 子菜单 */}
      {hasChildren && childOpen && (
        <ul className="mt-0.5 space-y-0.5">
          {item.children?.map((child) => (
            <NavItemRow
              key={child.path}
              item={child}
              pathname={pathname}
              collapsed={collapsed}
              depth={depth + 1}
            />
          ))}
        </ul>
      )}
    </li>
  )
}
