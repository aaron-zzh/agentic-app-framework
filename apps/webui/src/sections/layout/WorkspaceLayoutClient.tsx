/**
 * WorkspaceLayoutClient——工作区布局客户端部分
 * 包含 GlobalChatter（全局对话弹窗，配置由当前页面决定）
 *
 * @author AaronZZH & Kiro
 */

"use client"

import type { ReactNode } from "react"
import { Chatter } from "@/features/chatter"
import { AppHeader } from "@/sections/layout/AppHeader"
import { AppSidebar } from "@/sections/layout/AppSidebar"
import { useChatterStore } from "@/stores/chatter-store"

interface WorkspaceLayoutClientProps {
  children: ReactNode
}

export function WorkspaceLayoutClient({ children }: WorkspaceLayoutClientProps) {
  const open = useChatterStore((s) => s.open)
  const setOpen = useChatterStore((s) => s.setOpen)
  const currentPageId = useChatterStore((s) => s.currentPageId)
  const getConfig = useChatterStore((s) => s.getConfig)

  const config = currentPageId ? getConfig(currentPageId) : { preset: "ai" as const, open: false }

  return (
    <div className="flex h-screen overflow-hidden">
      {/* 桌面端固定侧边栏 */}
      <div className="hidden md:flex">
        <AppSidebar />
      </div>
      <div className="flex flex-1 flex-col overflow-hidden">
        <AppHeader />
        <main className="flex flex-1 flex-col overflow-hidden">{children}</main>
      </div>

      {/* 全局 Chatter：dialog 模式，配置由当前页面通过 useChatterConfig 注入 */}
      <Chatter
        preset={config.preset}
        agentRole={config.agentRole}
        layout="dialog"
        open={open}
        onOpenChange={setOpen}
      />
    </div>
  )
}
