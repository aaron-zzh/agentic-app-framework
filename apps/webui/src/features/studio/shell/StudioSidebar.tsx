/**
 * Studio Sidebar——五度空间侧栏
 *
 * 顶级 5 工作区图标 + 子菜单（hover 展开 / 折叠态仅图标）
 * 点击：调用 useStudioShell.openTab 打开/切到对应 tab，并 router.push 实际路由
 */

"use client"

import { ChevronDown, Gift, MessageCircle, Settings } from "lucide-react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { useMemo } from "react"
import { Brand } from "@/components/brand/Brand"
import { useWechatQrImage } from "@/lib/queries/use-system-config"
import { cn } from "@/lib/utils/index"
import { STUDIO_NAV, type StudioWorkspaceConfig } from "../nav-config"
import { useStudioShell } from "./store"

interface SidebarItemProps {
  config: StudioWorkspaceConfig
  active: boolean
  collapsed: boolean
  onOpen: () => void
}

function WorkspaceItem({ config, active, collapsed, onOpen }: SidebarItemProps) {
  const Icon = config.icon

  return (
    <button
      type="button"
      onClick={onOpen}
      data-active={active}
      className={cn(
        "group/ws flex w-full rounded-lg text-left transition-all duration-200",
        collapsed
          ? "flex-col items-center gap-1 px-1 py-2"
          : "flex-row items-center gap-3 px-3 py-2.5",
        "hover:bg-foreground/[0.04]",
        active &&
          "bg-foreground/[0.06] text-foreground shadow-[inset_0_0_0_1px_rgb(255_255_255_/_0.05)]",
        !active && "text-muted-foreground hover:text-foreground"
      )}
    >
      <span
        className={cn(
          "flex shrink-0 items-center justify-center rounded-lg transition-all duration-200",
          collapsed ? "size-8" : "size-9",
          active
            ? "bg-primary/15 text-primary shadow-[0_0_12px_-2px_var(--color-primary)]"
            : "bg-foreground/[0.04] text-foreground/60 group-hover/ws:bg-foreground/[0.08]"
        )}
      >
        <Icon className="size-5" />
      </span>
      {collapsed ? (
        <span className="w-full truncate text-center text-[10px] leading-tight transition-opacity duration-200">
          {config.label}
        </span>
      ) : (
        <span className="truncate font-medium text-sm transition-opacity duration-200">
          {config.label}
        </span>
      )}
    </button>
  )
}

export function StudioSidebar() {
  const pathname = usePathname()
  const router = useRouter()
  const { sidebarCollapsed, toggleSidebar, openTab } = useStudioShell()

  // 当前 active workspace（从 URL 反查）
  const activeWorkspace = useMemo(() => {
    const segments = pathname.split("/").filter(Boolean)
    if (segments[0] !== "studio") return null
    const seg = segments[1]
    if (seg === "templates") return "projects"
    // 工具箱路径归 tools workspace
    if (seg === "create" && (segments[2] === "tools" || segments[2] === "draw")) return "tools"
    return STUDIO_NAV.find((w) => w.workspace === seg)?.workspace ?? null
  }, [pathname])

  const handleOpen = (config: StudioWorkspaceConfig) => {
    const defaultChild = config.children.find((c) => c.default) ?? config.children[0]
    const url = defaultChild?.path ?? config.path
    openTab({
      workspace: config.workspace,
      url,
      title: config.label
    })
    router.push(url)
  }

  const { data: wechatQrData } = useWechatQrImage()
  const wechatQrUrl = wechatQrData || "https://picsum.photos/720"

  return (
    <aside
      data-collapsed={sidebarCollapsed}
      className={cn(
        "relative flex h-screen flex-col border-foreground/[0.06] border-r bg-card/80 transition-[width]",
        sidebarCollapsed ? "w-16" : "w-[180px]"
      )}
    >
      {/* 边缘折叠按钮 */}
      <button
        type="button"
        onClick={toggleSidebar}
        className="absolute top-[calc(4rem/2)] -right-3 z-10 flex size-6 -translate-y-1/2 items-center justify-center rounded-full border border-foreground/[0.08] bg-background text-muted-foreground shadow-sm hover:text-foreground"
        aria-label={sidebarCollapsed ? "展开侧边栏" : "收起侧边栏"}
      >
        <ChevronDown
          className={cn(
            "size-3.5 transition-transform",
            sidebarCollapsed ? "rotate-90" : "-rotate-90"
          )}
        />
      </button>

      {/* 品牌区 */}
      <div className="flex h-16 items-center px-4">
        <Brand collapsed={sidebarCollapsed} size="sm" href="/" className="text-foreground" />
      </div>

      {/* 工作区列表 */}
      <nav className="flex flex-1 flex-col gap-1 px-2">
        {STUDIO_NAV.map((config) => (
          <WorkspaceItem
            key={config.workspace}
            config={config}
            active={activeWorkspace === config.workspace}
            collapsed={sidebarCollapsed}
            onOpen={() => handleOpen(config)}
          />
        ))}
      </nav>

      {/* 底部邀请按钮 */}
      <div className="border-foreground/[0.06] border-t p-2">
        {sidebarCollapsed ? (
          <div className="relative flex justify-center">
            {/* 占位保持高度 */}
            <div className="size-10 shrink-0" />
            {/* 绝对定位，hover 时向右展开，不影响 sidebar 宽度 */}
            <Link
              href="/studio/me/invite"
              className={cn(
                "group/invite absolute inset-y-0 left-0 z-50 flex items-center gap-2 rounded-lg",
                "bg-card font-medium text-amber-400 text-xs",
                "overflow-hidden whitespace-nowrap",
                "w-10 transition-[width] duration-200 hover:w-36",
                "hover:border hover:border-amber-400/20 hover:px-3 hover:shadow-md",
                "px-3"
              )}
            >
              <Gift className="size-4 shrink-0" />
              <span className="opacity-0 transition-opacity delay-100 duration-150 group-hover/invite:opacity-100">
                邀请赚积分 🎁
              </span>
            </Link>
          </div>
        ) : (
          <Link
            href="/studio/me/invite"
            className="flex w-full items-center gap-2 rounded-lg px-3 py-2 font-medium text-amber-400 text-xs transition-colors hover:bg-amber-400/10"
          >
            <Gift className="size-4 shrink-0" />
            <span className="flex-1 truncate">邀请赚积分 🎁</span>
          </Link>
        )}
        {/* 微信客服 */}
        {sidebarCollapsed ? (
          <div className="group/wechat relative mt-1 flex justify-center">
            <div className="size-10 shrink-0" />
            <div className="absolute inset-y-0 left-0 z-50 flex w-10 cursor-pointer items-center gap-2 overflow-hidden whitespace-nowrap rounded-lg bg-card px-3 font-medium text-muted-foreground text-xs transition-[width] duration-200 group-hover/wechat:w-24 group-hover/wechat:border group-hover/wechat:border-foreground/10 group-hover/wechat:text-foreground group-hover/wechat:shadow-md">
              <MessageCircle className="size-4 shrink-0" />
              <span className="opacity-0 transition-opacity delay-100 duration-150 group-hover/wechat:opacity-100">
                客服
              </span>
            </div>
            {/* 二维码浮层在 overflow-hidden 外，不被裁剪 */}
            <div className="pointer-events-none absolute bottom-0 left-16 z-50 ml-1 opacity-0 transition-opacity duration-200 group-hover/wechat:opacity-100">
              <div className="w-36 rounded-lg border bg-background p-2 shadow-lg">
                {/* biome-ignore lint/performance/noImgElement: 微信客服二维码 */}
                <img src={wechatQrUrl} alt="微信客服" className="w-full rounded" />
                <p className="mt-1 text-center text-[10px] text-muted-foreground">扫码联系客服</p>
              </div>
            </div>
          </div>
        ) : (
          <div className="group/wechat relative">
            <div className="flex w-full cursor-pointer items-center gap-2 rounded-lg px-3 py-2 font-medium text-muted-foreground text-xs transition-colors hover:bg-foreground/[0.04] hover:text-foreground">
              <MessageCircle className="size-4 shrink-0" />
              <span className="flex-1 truncate">微信客服</span>
            </div>
            <div className="pointer-events-none absolute bottom-full left-0 z-50 mb-2 opacity-0 transition-opacity duration-200 group-hover/wechat:opacity-100">
              <div className="rounded-lg border bg-background p-2 shadow-lg">
                {/* biome-ignore lint/performance/noImgElement: 微信客服二维码 */}
                <img src={wechatQrUrl} alt="微信客服" className="size-32 rounded" />
                <p className="mt-1 text-center text-[10px] text-muted-foreground">扫码联系客服</p>
              </div>
            </div>
          </div>
        )}
        {/* 设置按钮 */}
        {sidebarCollapsed ? (
          <div className="relative mt-1 flex justify-center">
            <div className="size-10 shrink-0" />
            <Link
              href="/studio/me"
              className={cn(
                "group/settings absolute inset-y-0 left-0 z-50 flex items-center gap-2 rounded-lg",
                "bg-card font-medium text-muted-foreground text-xs",
                "overflow-hidden whitespace-nowrap",
                "w-10 transition-[width] duration-200 hover:w-24",
                "px-3 hover:border hover:border-foreground/10 hover:text-foreground hover:shadow-md"
              )}
            >
              <Settings className="size-4 shrink-0" />
              <span className="opacity-0 transition-opacity delay-100 duration-150 group-hover/settings:opacity-100">
                设置
              </span>
            </Link>
          </div>
        ) : (
          <Link
            href="/studio/me"
            className="flex w-full items-center gap-2 rounded-lg px-3 py-2 font-medium text-muted-foreground text-xs transition-colors hover:bg-foreground/[0.04] hover:text-foreground"
          >
            <Settings className="size-4 shrink-0" />
            <span className="flex-1 truncate">设置</span>
          </Link>
        )}
      </div>
    </aside>
  )
}
