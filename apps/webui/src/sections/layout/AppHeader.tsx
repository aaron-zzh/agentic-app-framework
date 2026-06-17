/**
 * AppHeader——工作区顶栏
 * @author AaronZZH & Kiro
 *
 * 结构：[侧边栏切换] [日历] [联系人] [搜索] [工作区] [面包屑] --- [主题] [对话] [通知] [设置] [头像]
 */

"use client"

import { Calendar, Search } from "lucide-react"
import { useState } from "react"
import { CommandPalette } from "@/components/common/CommandPalette"
import { useCommandPalette } from "@/lib/hooks/use-command-palette"
import { cn } from "@/lib/utils/cn"
import { HeaderActions } from "./HeaderActions"
import { MobileNav } from "./MobileNav"
import { WorkspaceSwitcher } from "./WorkspaceSwitcher"

export function AppHeader() {
  const { open: cmdOpen, onClose: cmdClose, commands, recentItems, addRecent } = useCommandPalette()
  const [cmdManualOpen, setCmdManualOpen] = useState(false)
  const isCommandOpen = cmdOpen || cmdManualOpen

  return (
    <header className="flex h-[var(--layout-header-height)] shrink-0 items-center gap-2 border-b bg-background px-4">
      {/* 左侧：侧边栏切换 + 功能图标组 */}
      <MobileNav />
      <WorkspaceSwitcher />
      <CalendarButton />
      <SearchButton onClick={() => setCmdManualOpen(true)} />

      <Breadcrumb />
      <div className="flex-1" />

      {/* 右侧：工具图标组 */}
      <HeaderActions />
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

/** 日历快捷入口 */
function CalendarButton() {
  return (
    <a
      href="/module/calendar"
      className="flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground"
      aria-label="日历"
    >
      <Calendar className="size-4" />
    </a>
  )
}
