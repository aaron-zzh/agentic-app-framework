/**
 * ChatLayout——对话面板布局
 * 左侧对话列表（可折叠）+ 中间消息流 + 底部输入区
 * 支持 Drawer 模式嵌入任意页面右侧面板
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Thread, ThreadList } from "@assistant-ui/react"
import { PanelLeftClose, PanelLeftOpen } from "lucide-react"
import { useCallback, useState } from "react"
import { Button } from "@/components/ui/button"
import {
  ResizableHandle,
  ResizablePanel,
  ResizablePanelGroup
} from "@/components/ui/resizable"
import {
  Sheet,
  SheetContent,
  SheetTrigger
} from "@/components/ui/sheet"
import { useResponsive } from "@/lib/hooks/use-responsive"

interface ChatLayoutProps {
  /** Drawer 模式：嵌入页面右侧面板，隐藏侧边栏 */
  drawer?: boolean
}

/**
 * 对话面板布局组件
 * 桌面端：ResizablePanelGroup 左右分割（ThreadList + Thread）
 * 移动端：ThreadList 收入 Sheet 抽屉
 */
export function ChatLayout({ drawer = false }: ChatLayoutProps) {
  const { isMobile } = useResponsive()
  const [sidebarOpen, setSidebarOpen] = useState(!drawer)

  const toggleSidebar = useCallback(() => setSidebarOpen((v) => !v), [])

  // Drawer 模式或移动端：仅显示 Thread，侧边栏通过 Sheet 触发
  if (drawer || isMobile) {
    return (
      <div className="flex h-full flex-col">
        <div className="flex items-center border-b px-2 py-1">
          <Sheet>
            <SheetTrigger render={<Button variant="ghost" size="sm" />}>
              <PanelLeftOpen className="size-4" />
              <span className="sr-only">打开对话列表</span>
            </SheetTrigger>
            <SheetContent side="left" className="w-[280px] p-0">
              <div className="h-full overflow-y-auto pt-8">
                <ThreadList />
              </div>
            </SheetContent>
          </Sheet>
        </div>
        <div className="min-h-0 flex-1">
          <Thread />
        </div>
      </div>
    )
  }

  // 桌面端：可拖拽分割面板
  return (
    <div className="flex h-full flex-col">
      <ResizablePanelGroup direction="horizontal" className="min-h-0 flex-1">
        {sidebarOpen && (
          <>
            <ResizablePanel defaultSize={25} minSize={15} maxSize={40}>
              <div className="flex h-full flex-col">
                <div className="flex items-center justify-end border-b px-2 py-1">
                  <Button variant="ghost" size="sm" onClick={toggleSidebar}>
                    <PanelLeftClose className="size-4" />
                    <span className="sr-only">折叠侧边栏</span>
                  </Button>
                </div>
                <div className="min-h-0 flex-1 overflow-y-auto">
                  <ThreadList />
                </div>
              </div>
            </ResizablePanel>
            <ResizableHandle withHandle />
          </>
        )}
        <ResizablePanel defaultSize={sidebarOpen ? 75 : 100}>
          <div className="flex h-full flex-col">
            {!sidebarOpen && (
              <div className="flex items-center border-b px-2 py-1">
                <Button variant="ghost" size="sm" onClick={toggleSidebar}>
                  <PanelLeftOpen className="size-4" />
                  <span className="sr-only">展开侧边栏</span>
                </Button>
              </div>
            )}
            <div className="min-h-0 flex-1">
              <Thread />
            </div>
          </div>
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  )
}
