/**
 * Studio 首页中央画布标签页切换：助理 / 项目与任务 / 工作流。当前标签页记入首页布局偏好。
 * @author AaronZZH & Kiro
 */

"use client"

import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  isStudioHomeCanvasTab,
  STUDIO_HOME_CANVAS_TABS,
  type StudioHomeCanvasTab,
  useStudioHomeLayoutStore
} from "@/lib/store/studio-home-layout-store"
import { cn } from "@/lib/utils/cn"

const TAB_LABELS: Record<StudioHomeCanvasTab, string> = {
  assistant: "助理",
  project: "项目",
  workflow: "工作流"
}

interface HomeCanvasTabsProps {
  className?: string
}

export function HomeCanvasTabs({ className }: HomeCanvasTabsProps) {
  const canvasTab = useStudioHomeLayoutStore((state) => state.canvasTab)
  const setCanvasTab = useStudioHomeLayoutStore((state) => state.setCanvasTab)

  return (
    <Tabs
      value={canvasTab}
      onValueChange={(value: unknown) => {
        if (isStudioHomeCanvasTab(value)) setCanvasTab(value)
      }}
      className={cn("gap-0", className)}
    >
      <TabsList
        aria-label="中央画布视图"
        className="border border-foreground/10 bg-background/50 backdrop-blur-md"
      >
        {STUDIO_HOME_CANVAS_TABS.map((tab) => (
          <TabsTrigger key={tab} value={tab} className="px-3">
            {TAB_LABELS[tab]}
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  )
}
