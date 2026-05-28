/**
 * AIGC 视频页面——支持「生成」和「编辑」模式切换
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { VideoEditPanel } from "@/features/aigc/VideoEditPanel"
import { VideoGenerationChat } from "@/features/aigc/VideoGenerationChat"
import { VideoPlayer } from "@/features/aigc/VideoPlayer"
import { VideoStoryboard } from "@/features/aigc/VideoStoryboard"
import { VideoTimeline } from "@/features/aigc/VideoTimeline"

export default function AigcVideoPage() {
  const [mode, setMode] = useState<"generate" | "edit">("generate")

  return (
    <div className="flex h-[calc(100vh-var(--layout-header-height))] flex-col">
      {/* 模式切换 Tab */}
      <div className="flex items-center border-border/50 border-b px-4 py-2">
        <Tabs value={mode} onValueChange={(v) => setMode(v as "generate" | "edit")}>
          <TabsList className="h-8">
            <TabsTrigger value="generate" className="text-xs">
              生成
            </TabsTrigger>
            <TabsTrigger value="edit" className="text-xs">
              编辑
            </TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      {/* 主内容区 */}
      <ResizablePanelGroup direction="horizontal" className="flex-1">
        {/* 左栏：故事板 + 时间线 */}
        <ResizablePanel defaultSize={20} minSize={15} maxSize={30}>
          <ResizablePanelGroup direction="vertical">
            <ResizablePanel defaultSize={50}>
              <VideoStoryboard />
            </ResizablePanel>
            <ResizableHandle withHandle />
            <ResizablePanel defaultSize={50}>
              <VideoTimeline />
            </ResizablePanel>
          </ResizablePanelGroup>
        </ResizablePanel>

        <ResizableHandle withHandle />

        {/* 中栏：视频预览 */}
        <ResizablePanel defaultSize={55} minSize={40}>
          <div className="flex h-full flex-col items-center justify-center p-6">
            <div className="w-full max-w-3xl">
              <VideoPlayer />
            </div>
          </div>
        </ResizablePanel>

        <ResizableHandle withHandle />

        {/* 右栏：根据模式切换 */}
        <ResizablePanel defaultSize={25} minSize={20} maxSize={35}>
          {mode === "generate" ? <VideoGenerationChat /> : <VideoEditPanel />}
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  )
}
