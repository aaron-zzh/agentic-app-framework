/**
 * AppSidebar——侧边栏导航
 * @author AaronZZH & Kiro
 *
 * 支持：展开态（完整菜单） + 折叠态（仅图标）
 * 菜单数据优先从后端 API 获取，失败时 fallback 到本地静态配置
 */

"use client"

import { isExternalLink } from "@aaf/core"
import { useBoolean } from "@aaf/hooks"
import {
  BarChart3,
  BookOpen,
  Bot,
  Box,
  CheckSquare,
  ChevronDown,
  CreditCard,
  FileText,
  FolderOpen,
  GitBranch,
  GitPullRequest,
  Globe,
  Image,
  LayoutDashboard,
  type LucideIcon,
  Mail,
  MessageSquare,
  Package,
  ScrollText,
  Settings,
  Shield,
  ShoppingCart,
  Sparkles,
  Trash2,
  Users,
  Video,
  Workflow
} from "lucide-react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { useMemo } from "react"
import { Brand } from "@/components/brand/Brand"
import { Skeleton } from "@/components/ui/skeleton"
import { useLicenseStatus } from "@/lib/queries/use-license-status"
import { useUserMenus } from "@/lib/queries/use-menus"
import { useUIStore } from "@/lib/store/ui-store"
import { cn } from "@/lib/utils/cn"
import { LicensePlanBadge } from "./LicensePlanBadge"
import {
  buildNavConfig,
  buildNavFromApi,
  buildOfficialNavConfig,
  type NavGroup,
  type NavItem
} from "./nav-config"

/** 图标名 → lucide 组件映射（后端返回的 icon 字段对应此表） */
const ICON_MAP: Record<string, LucideIcon> = {
  "layout-dashboard": LayoutDashboard,
  "file-text": FileText,
  users: Users,
  "check-square": CheckSquare,
  settings: Settings,
  "trash-2": Trash2,
  "book-open": BookOpen,
  bot: Bot,
  "credit-card": CreditCard,
  "folder-open": FolderOpen,
  "git-branch": GitBranch,
  globe: Globe,
  mail: Mail,
  "message-square": MessageSquare,
  package: Package,
  shield: Shield,
  "shopping-cart": ShoppingCart,
  workflow: Workflow,
  // AI 创作
  sparkles: Sparkles,
  video: Video,
  box: Box,
  image: Image,
  // 开发工具
  "scroll-text": ScrollText,
  "git-pull-request": GitPullRequest,
  "bar-chart-3": BarChart3
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
  const { data: menus, isLoading, isError } = useUserMenus()
  const { data: license } = useLicenseStatus()

  const navConfig = useMemo(() => {
    const appendOfficial = (groups: NavGroup[]) =>
      license?.owner ? [...groups, buildOfficialNavConfig()] : groups
    if (menus) return appendOfficial(buildNavFromApi(menus))
    if (isError) return appendOfficial(buildNavConfig())
    return null
  }, [menus, isError, license?.owner])

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
        {isLoading && !navConfig ? (
          <NavSkeleton collapsed={!sidebarOpen} />
        ) : (
          (navConfig ?? buildNavConfig()).map((group) => (
            <NavGroupSection
              key={group.subheader}
              group={group}
              pathname={pathname}
              collapsed={!sidebarOpen}
            />
          ))
        )}
      </nav>

      <LicensePlanBadge collapsed={!sidebarOpen} />
    </aside>
  )
}

/** 菜单加载骨架屏 */
function NavSkeleton({ collapsed }: { collapsed: boolean }) {
  return (
    <div className="space-y-4 p-2">
      {["g1", "g2", "g3", "g4"].map((gKey) => (
        <div key={gKey} className="space-y-2">
          {!collapsed && <Skeleton className="h-3 w-16" />}
          {["a", "b", "c"].map((iKey) => (
            <Skeleton key={`${gKey}-${iKey}`} className={cn("h-7", collapsed ? "w-8" : "w-full")} />
          ))}
        </div>
      ))}
    </div>
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
