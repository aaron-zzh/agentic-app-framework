/**
 * AppHeader——顶栏（侧边栏切换 + 面包屑 + 命令面板入口 + 用户菜单）
 * @author AaronZZH & Kiro
 */

"use client"

import { useUIStore } from "@/lib/store/ui-store"

/** 顶栏 */
export function AppHeader() {
  const toggleSidebar = useUIStore((s) => s.toggleSidebar)

  return (
    <header className="flex h-[var(--layout-header-height)] shrink-0 items-center gap-4 border-b bg-background px-4">
      {/* 侧边栏切换 */}
      <button
        type="button"
        onClick={toggleSidebar}
        className="rounded-md p-1.5 text-muted-foreground hover:bg-accent"
        aria-label="Toggle sidebar"
      >
        <svg className="size-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M4 6h16M4 12h16M4 18h16"
          />
        </svg>
      </button>

      {/* 面包屑占位 */}
      <div className="flex-1" />

      {/* ⌘K 命令面板入口 */}
      <button
        type="button"
        className="flex items-center gap-2 rounded-md border px-3 py-1.5 text-muted-foreground text-xs hover:bg-accent"
      >
        <span>搜索...</span>
        <kbd className="rounded bg-muted px-1.5 py-0.5 font-mono text-[10px]">⌘K</kbd>
      </button>

      {/* 用户菜单占位 */}
      <div className="size-8 rounded-full bg-muted" />
    </header>
  )
}
