/**
 * Studio 驾驶舱布局
 *
 * 客户端布局：侧栏 + 顶栏 + 多 tab Bar + 主区 + 助理浮球
 * 详见 docs/design/apps/webui/user-studio-mvp.md
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
import { ThemeProvider, useTheme } from "next-themes"
import { Suspense, useCallback, useEffect, useRef, useState } from "react"
import type { PanelImperativeHandle } from "react-resizable-panels"
import { MotionLazy } from "@/components/animate"
import { CommandPalette } from "@/components/common/CommandPalette"
import { SplashScreen } from "@/components/common/SplashScreen"
import { TopProgressBar } from "@/components/common/TopProgressBar"
import { Button } from "@/components/ui/button"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { FloatingChatter } from "@/features/chatter/layout/FloatingChatter"
import { EntityMetadataGate } from "@/features/entity-engine/components/EntityMetadataGate"
import { SlotDevTrigger, SlotDock } from "@/features/studio/slots"
import { setBackendScope } from "@/lib/api/rest/backend-client"
import { organizationApi } from "@/lib/api/rest/user"
import { commandRegistry, useCommandPalette } from "@/lib/hooks/use-command-palette"
import { useChatterStore } from "@/lib/store/chatter-store"
import { useOrgStore } from "@/lib/store/org-store"
import { StudioRouteSync } from "./StudioRouteSync"
import { StudioSidebar } from "./StudioSidebar"
import { StudioTopbar } from "./StudioTopbar"

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
      window.location.href = "/studio/create?mode=image"
    }
  },
  {
    id: "studio-create-video",
    label: "文生视频",
    group: "创作",
    action: () => {
      window.location.href = "/studio/create?mode=video"
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
    id: "studio-create-viral",
    label: "爆款仿写",
    group: "创作",
    action: () => {
      window.location.href = "/studio/create/viral"
    }
  },
  {
    id: "studio-create-matting",
    label: "抠图",
    group: "创作",
    action: () => {
      window.location.href = "/studio/create/matting"
    }
  },
  {
    id: "studio-create-voice",
    label: "配音",
    group: "创作",
    action: () => {
      window.location.href = "/studio/create?mode=voice"
    }
  },
  {
    id: "studio-create-music",
    label: "音乐",
    group: "创作",
    action: () => {
      window.location.href = "/studio/create?mode=music"
    }
  },
  {
    id: "studio-create-tools",
    label: "工具箱",
    group: "创作",
    action: () => {
      window.location.href = "/studio/create/tools"
    }
  },
  {
    id: "studio-tools-draw",
    label: "无限画布",
    group: "工具箱",
    action: () => {
      window.location.href = "/studio/create/draw"
    }
  },
  {
    id: "studio-tools-ocr",
    label: "图片文字提取",
    group: "工具箱",
    action: () => {
      window.location.href = "/studio/create/tools/ocr"
    }
  },
  {
    id: "studio-tools-weather",
    label: "实时天气",
    group: "工具箱",
    action: () => {
      window.location.href = "/studio/create/tools/weather"
    }
  },
  {
    id: "studio-tools-qrcode",
    label: "二维码生成",
    group: "工具箱",
    action: () => {
      window.location.href = "/studio/create/tools/qrcode"
    }
  },
  {
    id: "studio-tools-todo",
    label: "待办清单",
    group: "工具箱",
    action: () => {
      window.location.href = "/studio/create/tools/todo"
    }
  },
  {
    id: "studio-tools-meeting",
    label: "会议记录",
    group: "工具箱",
    action: () => {
      window.location.href = "/studio/create/tools/meeting"
    }
  },
  {
    id: "studio-tools-hot",
    label: "热点跟踪",
    group: "工具箱",
    action: () => {
      window.location.href = "/studio/create/tools/hot"
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
    id: "studio-projects-templates",
    label: "模板库",
    group: "项目",
    action: () => {
      window.location.href = "/studio/templates"
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
    id: "studio-assets-materials",
    label: "素材库",
    group: "作品",
    action: () => {
      window.location.href = "/studio/assets/materials"
    }
  },
  {
    id: "studio-assets-prompts",
    label: "提示词库",
    group: "作品",
    action: () => {
      window.location.href = "/studio/assets/prompts"
    }
  },
  {
    id: "studio-assets-history",
    label: "任务历史",
    group: "作品",
    action: () => {
      window.location.href = "/studio/assets/history"
    }
  },
  {
    id: "studio-knowledge-docs",
    label: "文档管理",
    group: "知识",
    action: () => {
      window.location.href = "/studio/knowledge/docs"
    }
  },
  {
    id: "studio-knowledge-bases",
    label: "知识库",
    group: "知识",
    action: () => {
      window.location.href = "/studio/knowledge"
    }
  },
  {
    id: "studio-knowledge-favorites",
    label: "我的收藏",
    group: "知识",
    action: () => {
      window.location.href = "/studio/knowledge/favorites"
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
  },
  {
    id: "studio-invite",
    label: "邀请好友",
    group: "我",
    action: () => {
      window.location.href = "/studio/me/invite"
    }
  },
  {
    id: "studio-outfits",
    label: "装扮中心",
    group: "我",
    action: () => {
      window.location.href = "/studio/me/outfits"
    }
  },
  {
    id: "studio-account",
    label: "账号设置",
    group: "我",
    action: () => {
      window.location.href = "/studio/me/account"
    }
  },
  {
    id: "studio-settings",
    label: "系统设置",
    group: "我",
    action: () => {
      window.location.href = "/studio/me/settings"
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
        {/* <StudioTabBar /> */}
        {/* page 模式：GlobalChatter portal 到此 slot，覆盖 main 区 */}
        <div className="relative min-h-0 flex-1">
          <div id="chatter-page-slot" className="absolute inset-0 z-10 bg-background" />
          {mainContent}
        </div>
      </div>
    )
  }

  if (isPanelMode) {
    return (
      <div className="relative flex min-w-0 flex-1 flex-col overflow-hidden">
        <StudioTopbar />
        {/* <StudioTabBar /> */}
        <ResizablePanelGroup orientation="horizontal" className="min-h-0 flex-1 overflow-hidden">
          <ResizablePanel panelRef={mainPanelRef} defaultSize={open ? "65%" : "100%"} minSize="30%">
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
      {/* <StudioTabBar /> */}
      {mainContent}
    </div>
  )
}

export function StudioLayout({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient()
  const setScope = useOrgStore((state) => state.setScope)
  const [scopeReady, setScopeReady] = useState(false)
  const [contextError, setContextError] = useState(false)
  const [contextAttempt, setContextAttempt] = useState(0)

  // Studio 固定使用当前用户的个人默认组织与默认工作区，查询视角为 own。
  // 上下文就绪前不渲染任何 API 消费组件，避免首批请求缺少组织/工作区 Header。
  useEffect(() => {
    // 该值只作为显式重试触发键；递增后必须重新加载默认上下文。
    void contextAttempt

    let cancelled = false
    setBackendScope("own")
    setScopeReady(false)
    setContextError(false)

    organizationApi
      .defaultContext()
      .then(async (context) => {
        if (cancelled) return
        setScope({
          kind: "workspace",
          orgId: context.orgId,
          workspaceId: context.workspaceId
        })
        await queryClient.invalidateQueries()
        if (!cancelled) setScopeReady(true)
      })
      .catch(() => {
        if (!cancelled) setContextError(true)
      })

    return () => {
      cancelled = true
    }
  }, [contextAttempt, queryClient, setScope])

  const content = !scopeReady ? (
    contextError ? (
      <div className="flex h-screen items-center justify-center bg-background p-6">
        <div className="max-w-md space-y-3 text-center">
          <h1 className="font-semibold text-lg">默认工作区不可用</h1>
          <p className="text-muted-foreground text-sm">
            无法加载当前用户的默认组织与工作区，请重试。
          </p>
          <Button type="button" onClick={() => setContextAttempt((value) => value + 1)}>
            重试
          </Button>
        </div>
      </div>
    ) : (
      <SplashScreen />
    )
  ) : (
    <>
      <StudioRouteSync />

      {/* ⌘K 全局命令面板（M9） */}
      <StudioCommandPalette />

      <div className="relative flex h-screen w-full overflow-hidden bg-background">
        <StudioSidebar />
        <MotionLazy>
          <EntityMetadataGate>
            <StudioContent>{children}</StudioContent>
          </EntityMetadataGate>
        </MotionLazy>
      </div>

      {/* panel/page slot 已在 StudioContent 内提供 */}
      <FloatingChatter availableModes={["panel", "page"]} />
      {/* 演示触发器：开发期模拟后端 WS 推送，生产环境移除 */}
      {process.env.NODE_ENV === "development" && <SlotDevTrigger />}
    </>
  )

  return (
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

      {content}
    </ThemeProvider>
  )
}
