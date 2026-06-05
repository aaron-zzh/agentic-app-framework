/**
 * WorkspaceLayoutClient——工作区布局客户端部分
 * Chatter 以 GitHub Copilot 风格内嵌在右侧：
 * - 点击 header ChatterToggle → 右侧 panel 滑入/收起
 * - 不遮挡内容（非 dialog，无遮罩）
 * - 使用 ResizablePanelGroup 支持拖拽调整宽度
 *
 * @author AaronZZH & Kiro
 */

"use client"

import type { ReactNode } from "react"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { Chatter } from "@/features/chatter"
import { useChatterStore } from "@/lib/store/chatter-store"
import { AppHeader } from "@/sections/layout/AppHeader"
import { AppSidebar } from "@/sections/layout/AppSidebar"

interface WorkspaceLayoutClientProps {
  children: ReactNode
}

export function WorkspaceLayoutClient({ children }: WorkspaceLayoutClientProps) {
  const open = useChatterStore((s) => s.open)
  const currentPageId = useChatterStore((s) => s.currentPageId)
  const getConfig = useChatterStore((s) => s.getConfig)

  const config = currentPageId
    ? getConfig(currentPageId)
    : { preset: "ai" as const, open: false, agentRole: "default-generalist" }

  return (
    <div className="flex h-screen overflow-hidden">
      {/* 桌面端固定侧边栏 */}
      <div className="hidden md:flex">
        <AppSidebar />
      </div>

      <div className="flex flex-1 flex-col overflow-hidden">
        <AppHeader />

        {/* 主内容 + 右侧 Copilot 面板 */}
        <ResizablePanelGroup direction="horizontal" className="flex-1 overflow-hidden">
          <ResizablePanel defaultSize={100} minSize={40}>
            <main className="flex h-full flex-col overflow-hidden">{children}</main>
          </ResizablePanel>

          {/* Copilot 侧边面板：open 时展开，closed 时隐藏（无 ResizableHandle） */}
          {open && (
            <>
              <ResizableHandle withHandle />
              <ResizablePanel defaultSize={28} minSize={20} maxSize={50} className="border-l">
                <Chatter preset={config.preset} agentRole={config.agentRole} layout="panel" />
              </ResizablePanel>
            </>
          )}
        </ResizablePanelGroup>
      </div>
    </div>
  )
}
