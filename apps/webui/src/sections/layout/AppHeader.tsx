/**
 * AppHeader——工作区顶栏
 * @author AaronZZH & Kiro
 *
 * 结构：[侧边栏切换] [面包屑] --- [搜索] [通知] [用户菜单]
 */

"use client"

import { m } from "framer-motion"
import { Menu, Moon, Search, Sun, Users, MessageSquare } from "lucide-react"
import { usePathname } from "next/navigation"
import { useTheme } from "next-themes"
import { useState } from "react"
import { AnimateBorder, transitionTap, varHover, varTap } from "@/components/animate"
import { Brand } from "@/components/brand/Brand"
import { CommandPalette } from "@/components/common/CommandPalette"
import { ThemeSettings } from "@/components/common/ThemeSettings"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger
} from "@/components/ui/sheet"
import { useCommandPalette } from "@/lib/hooks/use-command-palette"
import { cn } from "@/lib/utils/cn"
import { ContactsPanel } from "./ContactsPanel"
import { NotificationDrawer } from "./notifications"
import { WorkspaceSwitcher } from "./WorkspaceSwitcher"

export function AppHeader() {
  const { open: cmdOpen, onClose: cmdClose, commands, recentItems, addRecent } = useCommandPalette()
  const [cmdManualOpen, setCmdManualOpen] = useState(false)
  const isCommandOpen = cmdOpen || cmdManualOpen

  return (
    <header className="flex h-[var(--layout-header-height)] shrink-0 items-center gap-2 border-b bg-background px-4">
      <SidebarToggle />
      <WorkspaceSwitcher />
      <Breadcrumb />
      <div className="flex-1" />
      <SearchButton onClick={() => setCmdManualOpen(true)} />
      <ThemeToggle />
      <ChatterToggle />
      <NotificationDrawer />
      <ContactsPanel>
        <button
          type="button"
          className="flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground"
          aria-label="联系人"
        >
          <Users className="size-4" />
        </button>
      </ContactsPanel>
      <SettingsButton />
      <UserAvatar />
      <CommandPalette
        open={isCommandOpen}
        onClose={() => {
          cmdClose()
          setCmdManualOpen(false)
        }}
        commands={commands}
        recentItems={recentItems}
        addRecent={addRecent}
      />
    </header>
  )
}

/** 侧边栏切换（移动端弹出Sheet） */
function SidebarToggle() {
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
        <MobileSidebar />
      </SheetContent>
    </Sheet>
  )
}

/** 移动端侧边栏内容（复用 AppSidebar 的导航数据） */
function MobileSidebar() {
  // 延迟导入避免循环依赖，直接内联渲染导航
  const { buildNavConfig } = require("@/sections/layout/nav-config")
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

/** 面包屑（占位，后续从路由 + entity.label 自动生成） */
function Breadcrumb() {
  return (
    <nav aria-label="breadcrumb" className="hidden text-muted-foreground text-sm sm:block">
      {/* TODO: 根据路由 + entity.label + 记录标题自动生成 */}
    </nav>
  )
}

/** ⌘K 搜索按钮 */
function SearchButton({ onClick }: { onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "hidden items-center gap-2 rounded-md border px-3 py-1.5 text-muted-foreground text-xs transition-colors hover:bg-accent sm:flex"
      )}
    >
      <Search className="size-3.5" />
      <span>搜索...</span>
      <kbd className="rounded bg-muted px-1.5 py-0.5 font-mono text-[10px]">⌘K</kbd>
    </button>
  )
}

/** 明暗主题快捷切换 */
function ThemeToggle() {
  const { theme, setTheme } = useTheme()

  return (
    <button
      type="button"
      className="rounded-md p-1.5 text-muted-foreground hover:bg-accent"
      aria-label="切换主题"
      onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
    >
      <Sun className="size-5 dark:hidden" />
      <Moon className="hidden size-5 dark:block" />
    </button>
  )
}

/** 用户头像菜单（参考 next-ts AccountButton） */
function UserAvatar({ src, displayName }: { src?: string; displayName?: string }) {
  return (
    <m.button
      type="button"
      whileTap={varTap(0.96)}
      whileHover={varHover(1.04)}
      transition={transitionTap()}
      className="inline-flex items-center justify-center border-none bg-transparent p-0"
      aria-label="用户菜单"
    >
      <AnimateBorder rounded="full" borderWidth={1.5} size={40} glowSize={60} duration={8}>
        <Avatar className="!size-[36px] after:hidden">
          <AvatarImage src={src || "/assets/avatar/avatar.png"} alt={displayName || "用户头像"} />
          <AvatarFallback>{displayName?.charAt(0).toUpperCase() || "U"}</AvatarFallback>
        </Avatar>
      </AnimateBorder>
    </m.button>
  )
}

/** 设置按钮（弹出主题设置面板） */
function SettingsButton() {
  return (
    <Sheet>
      <SheetTrigger
        className="rounded-md p-1.5 text-muted-foreground hover:bg-accent"
        aria-label="主题设置"
        render={<button type="button" />}
      >
        <SettingsIcon />
      </SheetTrigger>
      <SheetContent side="right">
        <SheetHeader>
          <SheetTitle>设置</SheetTitle>
          <SheetDescription>自定义主题色、外观模式和布局</SheetDescription>
        </SheetHeader>
        <div className="flex-1 overflow-y-auto px-6 py-4">
          <ThemeSettings />
        </div>
      </SheetContent>
    </Sheet>
  )
}

/** 齿轮图标（外圈缓慢旋转） */
function SettingsIcon() {
  return (
    <svg className="size-5" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        className="origin-center animate-[spin_8s_linear_infinite]"
        fill="currentColor"
        fillRule="evenodd"
        clipRule="evenodd"
        opacity="0.5"
        d="M14.279 2.152C13.909 2 13.439 2 12.5 2s-1.408 0-1.779.152a2.008 2.008 0 0 0-1.09 1.083c-.094.223-.13.484-.145.863a1.615 1.615 0 0 1-.796 1.353a1.64 1.64 0 0 1-1.579.008c-.338-.178-.583-.276-.825-.308a2.026 2.026 0 0 0-1.49.396c-.318.242-.553.646-1.022 1.453c-.47.807-.704 1.21-.757 1.605c-.07.526.074 1.058.4 1.479c.148.192.357.353.68.555c.477.297.783.803.783 1.361c0 .558-.306 1.064-.782 1.36c-.324.203-.533.364-.682.556a1.99 1.99 0 0 0-.399 1.479c.053.394.287.798.757 1.605c.47.807.704 1.21 1.022 1.453c.424.323.96.465 1.49.396c.242-.032.487-.13.825-.308a1.64 1.64 0 0 1 1.58.008c.486.28.774.795.795 1.353c.015.38.051.64.145.863c.204.49.596.88 1.09 1.083c.37.152.84.152 1.779.152s1.409 0 1.779-.152a2.008 2.008 0 0 0 1.09-1.083c.094-.223.13-.483.145-.863c.02-.558.309-1.074.796-1.353a1.64 1.64 0 0 1 1.579-.008c.338.178.583.276.825.308c.53.07 1.066-.073 1.49-.396c.318-.242.553-.646 1.022-1.453c.47-.807.704-1.21.757-1.605a1.99 1.99 0 0 0-.4-1.479c-.148-.192-.357-.353-.68-.555c-.477-.297-.783-.803-.783-1.361c0-.558.306-1.064.782-1.36c.324-.203.533-.364.682-.556a1.99 1.99 0 0 0 .399-1.479c-.053-.394-.287-.798-.757-1.605c-.47-.807-.704-1.21-1.022-1.453a2.026 2.026 0 0 0-1.49-.396c-.242.032-.487.13-.825.308a1.64 1.64 0 0 1-1.58-.008a1.615 1.615 0 0 1-.795-1.353c-.015-.38-.051-.64-.145-.863a2.007 2.007 0 0 0-1.09-1.083"
      />
      <circle cx="12.5" cy="12" r="3" fill="currentColor" />
    </svg>
  )
}


/** Chatter 全局触发按钮（读取 chatter store，直接控制 open 状态） */
function ChatterToggle() {
  // 动态导入避免 store 在 SSR 阶段执行
  const { useChatterStore } = require("@/stores/chatter-store")
  const open = useChatterStore((s: { open: boolean }) => s.open)
  const setOpen = useChatterStore((s: { setOpen: (v: boolean) => void }) => s.setOpen)

  return (
    <button
      type="button"
      className={`flex size-8 items-center justify-center rounded-md hover:bg-accent ${open ? "text-primary" : "text-muted-foreground hover:text-foreground"}`}
      aria-label="打开对话"
      onClick={() => setOpen(!open)}
    >
      <MessageSquare className="size-4" />
    </button>
  )
}
