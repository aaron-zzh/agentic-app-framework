/**
 * Studio 驾驶舱布局
 *
 * 客户端布局：侧栏 + 顶栏 + 多 tab Bar + 主区 + 助理浮球
 * 详见 docs/design/apps/webui/user-studio-mvp.md
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { MotionLazy } from "@/components/animate"
import { ThemeProvider, useTheme } from "next-themes"
import { Suspense, useCallback, useEffect, useRef } from "react"
import type { PanelImperativeHandle } from "react-resizable-panels"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { CommandPalette } from "@/components/common/CommandPalette"
import { TopProgressBar } from "@/components/common/TopProgressBar"
import { FloatingChatter } from "@/features/chatter/layout/FloatingChatter"
import { StudioRouteSync, StudioSidebar, StudioTabBar, StudioTopbar } from "@/features/studio/shell"
import { SlotDevTrigger, SlotDock } from "@/features/studio/slots"
import { AuthProvider } from "@/lib/auth/AuthProvider"
import { commandRegistry, useCommandPalette } from "@/lib/hooks/use-command-palette"
import { useChatterStore } from "@/lib/store/chatter-store"

// Studio 常用命令（模块级注册，避免重复）
commandRegistry.registerAll([
  {
    id: "studio-home",
    label: "驾驶舱首屏",
    group: "首页",
    action: () => {
      window.location.href = "/studio"
    }
  },
  {
    id: "studio-create-image",
    label: "文生图",
    group: "创作",
    action: () => {
      window.location.href = "/studio/create/image"
    }
  },
  {
    id: "studio-create-video",
    label: "文生视频",
    group: "创作",
    action: () => {
      window.location.href = "/studio/create/video"
    }
  },
  {
    id: "studio-create-copy",
    label: "文案智能体",
    group: "创作",
    action: () => {
      window.location.href = "/studio/create/copy"
    }
  },
  {
    id: "studio-create-pipeline",
    label: "工作流",
    group: "创作",
    action: () => {
      window.location.href = "/studio/create/pipeline"
    }
  },
  {
    id: "studio-projects",
    label: "我的项目",
    group: "首页",
    action: () => {
      window.location.href = "/studio/projects"
    }
  },
  {
    id: "studio-assets-works",
    label: "我的作品",
    group: "首页",
    action: () => {
      window.location.href = "/studio/assets/works"
    }
  },
  {
    id: "studio-membership",
    label: "会员套餐",
    group: "我",
    action: () => {
      window.location.href = "/studio/me/membership"
    }
  },
  {
    id: "studio-credits",
    label: "积分充值",
    group: "我",
    action: () => {
      window.location.href = "/studio/me/credits"
    }
  },
  {
    id: "studio-tasks",
    label: "成长任务",
    group: "我",
    action: () => {
      window.location.href = "/studio/me/tasks"
    }
  }
])

/** Studio 专属主题绑定：mount 时默认暗色，用户手动切换后保留选择 */
function StudioThemeBinder() {
  const { setTheme } = useTheme()
  // biome-ignore lint/correctness/useExhaustiveDependencies: 只在 mount 时执行一次
  useEffect(() => {
    const stored = localStorage.getItem("aaf-studio-theme")
    if (!stored) setTheme("dark")
  }, [])
  return null
}

/** 命令面板（需在客户端组件中用 hook） */
function StudioCommandPalette() {
  const { open, onClose, commands, recentItems, addRecent } = useCommandPalette()
  return (
    <CommandPalette
      open={open}
      onClose={onClose}
      commands={commands}
      recentItems={recentItems}
      addRecent={addRecent}
    />
  )
}

/** Studio 内容区：支持 panel（嵌入侧边）/ page（覆盖全屏）/ 默认三态 */
function StudioContent({ children }: { children: React.ReactNode }) {
  const open = useChatterStore((s) => s.open)
  const layoutOverride = useChatterStore((s) => s.layoutOverride)
  const mainPanelRef = useRef<PanelImperativeHandle>(null)

  const isPanelMode = (layoutOverride ?? "dialog") === "panel"
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

  const mainContent = (
    <>
      <main className="relative min-h-0 flex-1 overflow-y-auto">{children}</main>
      <SlotDock />
    </>
  )

  if (isPageMode) {
    return (
      <div className="relative flex min-w-0 flex-1 flex-col overflow-hidden">
        <StudioTopbar />
        <StudioTabBar />
        {/* page 模式：GlobalChatter portal 到此 slot，覆盖 main 区 */}
        <div className="relative min-h-0 flex-1">
          <div id="chatter-page-slot" className="absolute inset-0 z-10" />
          {mainContent}
        </div>
      </div>
    )
  }

  if (isPanelMode) {
    return (
      <div className="relative flex min-w-0 flex-1 flex-col overflow-hidden">
        <StudioTopbar />
        <StudioTabBar />
        <ResizablePanelGroup orientation="horizontal" className="min-h-0 flex-1 overflow-hidden">
          <ResizablePanel
            panelRef={mainPanelRef}
            defaultSize={open ? "65%" : "100%"}
            minSize="30%"
          >
            <div className="flex h-full flex-col overflow-hidden">{mainContent}</div>
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
                {/* GlobalChatter portal 到此 slot */}
                <div id="chatter-panel-slot" className="h-full" />
              </ResizablePanel>
            </>
          )}
        </ResizablePanelGroup>
      </div>
    )
  }

  // dialog 模式（默认）
  return (
    <div className="relative flex min-w-0 flex-1 flex-col">
      <StudioTopbar />
      <StudioTabBar />
      {mainContent}
    </div>
  )
}

export default function StudioLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthProvider>
      {/* Studio 子树独立 ThemeProvider，defaultTheme='dark'，storageKey 与根不同 */}
      <ThemeProvider
        attribute="class"
        defaultTheme="dark"
        enableSystem={false}
        storageKey="aaf-studio-theme"
        disableTransitionOnChange
      >
        <StudioThemeBinder />

        <Suspense>
          <TopProgressBar />
        </Suspense>

        <StudioRouteSync />

        {/* ⌘K 全局命令面板（M9） */}
        <StudioCommandPalette />

        <div className="relative flex h-screen w-full overflow-hidden bg-background">
          <StudioSidebar />
          <MotionLazy>
            <StudioContent>{children}</StudioContent>
          </MotionLazy>
        </div>

        {/* panel/page slot 已在 StudioContent 内提供 */}
        <FloatingChatter availableModes={["panel", "page"]} />
        {/* 演示触发器：开发期模拟后端 WS 推送，生产环境移除 */}
        {process.env.NODE_ENV === "development" && <SlotDevTrigger />}
      </ThemeProvider>
    </AuthProvider>
  )
}
