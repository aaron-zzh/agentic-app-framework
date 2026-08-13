/**
 * WorkspaceContent——工作区布局
 *
 * Chatter 布局策略：
 * - 默认 dialog（浮动按钮，右下角，不占页面宽度）
 * - 页面通过 useChatterLayoutPreference("panel") 声明后切换为右侧嵌入 panel
 *
 * @author AaronZZH & Kiro
 */

"use client"

import type { ReactNode } from "react"
import { useCallback, useEffect, useRef, useState } from "react"
import type { PanelImperativeHandle } from "react-resizable-panels"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { FloatingChatter } from "@/features/chatter/layout/FloatingChatter"
import { GlobalDndContext } from "@/features/dnd/GlobalDndContext"
import { EntityMetadataGate } from "@/features/entity-engine/components/EntityMetadataGate"
import { setBackendScope } from "@/lib/api/rest/backend-client"
import { useChatterStore } from "@/lib/store/chatter-store"
import { AppHeader } from "./AppHeader"
import { AppSidebar } from "./AppSidebar"

interface WorkspaceContentProps {
  children: ReactNode
}

/** 工作区客户端壳体，在实体元数据成功注册前阻塞所有注册表消费者。 */
export function WorkspaceContent({ children }: WorkspaceContentProps) {
  const open = useChatterStore((s) => s.open)
  const layoutOverride = useChatterStore((s) => s.layoutOverride)
  const mainPanelRef = useRef<PanelImperativeHandle>(null)
  const [scopeReady, setScopeReady] = useState(false)

  // 中后台管理场景：查询视角为 all，行级权限决定可见范围（管理员可查看全部），
  // 区别于 studio 个人工作台场景默认的 own（见 backend-client.ts 查询视角说明）
  useEffect(() => {
    setBackendScope("all")
    setScopeReady(true)
  }, [])

  // layoutOverride 优先，未声明时默认 dialog（浮动）
  const isPanelMode = (layoutOverride ?? "dialog") === "panel"
  // page 模式：页面自己完全管理 Chatter，WorkspaceContent 不渲染任何 Chatter UI
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
    <EntityMetadataGate enabled={scopeReady}>
      <GlobalDndContext>
        <div className="flex h-screen overflow-hidden">
          {/* 桌面端固定侧边栏 */}
          <div className="hidden md:flex">
            <AppSidebar />
          </div>

          <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
            <AppHeader />

            {isPageMode ? (
              <main className="flex flex-1 flex-col overflow-y-auto">{children}</main>
            ) : isPanelMode ? (
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
                      {/* GlobalChatter 通过 Portal 渲染到此 slot */}
                      <div id="chatter-panel-slot" className="h-full" />
                    </ResizablePanel>
                  </>
                )}
              </ResizablePanelGroup>
            ) : (
              <main className="flex flex-1 flex-col overflow-y-auto">{children}</main>
            )}
          </div>
        </div>

        {/* 单例浮动 AI 助理（按钮 + GlobalChatter），按钮仅在 dialog 模式渲染 */}
        <FloatingChatter availableModes={["panel", "page"]} />
      </GlobalDndContext>
    </EntityMetadataGate>
  )
}
