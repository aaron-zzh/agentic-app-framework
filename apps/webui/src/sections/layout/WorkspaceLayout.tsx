/**
 * WorkspaceLayout——工作区布局
 *
 * Chatter 布局策略：
 * - 默认 dialog（浮动按钮，右下角，不占页面宽度）
 * - 页面通过 useChatterLayoutPreference("panel") 声明后切换为右侧嵌入 panel
 *
 * @author AaronZZH & Kiro
 */

"use client"

import type { ReactNode } from "react"
import { useCallback, useEffect, useRef } from "react"
import type { PanelImperativeHandle } from "react-resizable-panels"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { Chatter } from "@/features/chatter"
import { FloatingChatterButton } from "@/features/chatter/FloatingChatterButton"
import { useChatterStore } from "@/lib/store/chatter-store"
import { AppHeader } from "@/sections/layout/AppHeader"
import { AppSidebar } from "@/sections/layout/AppSidebar"

interface WorkspaceLayoutProps {
  children: ReactNode
}

export function WorkspaceLayout({ children }: WorkspaceLayoutProps) {
  const open = useChatterStore((s) => s.open)
  const currentPageId = useChatterStore((s) => s.currentPageId)
  const getConfig = useChatterStore((s) => s.getConfig)
  const layoutOverride = useChatterStore((s) => s.layoutOverride)
  const mainPanelRef = useRef<PanelImperativeHandle>(null)

  const config = currentPageId
    ? getConfig(currentPageId)
    : { preset: "ai" as const, open: false, agentRole: "default-generalist" }

  // layoutOverride 优先，未声明时默认 dialog（浮动）
  const isPanelMode = (layoutOverride ?? "dialog") === "panel"
  // page 模式：页面自己完全管理 Chatter，WorkspaceLayout 不渲染任何 Chatter UI
  const isPageMode = layoutOverride === "page"

  // panel 模式下 open 变化时调整主面板宽度
  useEffect(() => {
    if (isPanelMode) {
      mainPanelRef.current?.resize(open ? 65 : 100)
    }
  }, [open, isPanelMode])

  const chatPanelCallback = useCallback((handle: PanelImperativeHandle | null) => {
    if (handle) setTimeout(() => handle.resize(35), 0)
  }, [])

  return (
    <div className="flex h-screen overflow-hidden">
      {/* 桌面端固定侧边栏 */}
      <div className="hidden md:flex">
        <AppSidebar />
      </div>

      <div className="flex flex-1 flex-col overflow-hidden">
        <AppHeader />

        {isPageMode ? (
          /* page 模式：页面完全接管，不渲染任何 Chatter UI */
          <main className="flex flex-1 flex-col overflow-hidden">{children}</main>
        ) : isPanelMode ? (
          /* 嵌入模式：ResizablePanel */
          <ResizablePanelGroup orientation="horizontal" className="flex-1 overflow-hidden">
            <ResizablePanel
              panelRef={mainPanelRef}
              defaultSize={open ? "65%" : "100%"}
              minSize="30%"
            >
              <main className="flex h-full flex-col overflow-hidden">{children}</main>
            </ResizablePanel>
            {open && (
              <>
                <ResizableHandle withHandle />
                <ResizablePanel
                  panelRef={chatPanelCallback}
                  defaultSize="35%"
                  minSize="23%"
                  maxSize="50%"
                  className="border-l"
                >
                  <Chatter preset={config.preset} agentRole={config.agentRole} layout="panel" />
                </ResizablePanel>
              </>
            )}
          </ResizablePanelGroup>
        ) : (
          /* 浮动模式：主内容全宽，浮动按钮 + dialog */
          <main className="flex flex-1 flex-col overflow-hidden">
            {children}
            <FloatingChatterButton preset={config.preset} agentRole={config.agentRole} />
          </main>
        )}
      </div>
    </div>
  )
}
