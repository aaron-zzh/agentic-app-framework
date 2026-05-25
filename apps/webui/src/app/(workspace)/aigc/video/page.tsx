/**
 * AIGC 视频生成页面
 * @author AaronZZH & Kiro
 */

"use client"

import {
  ResizableHandle,
  ResizablePanel,
  ResizablePanelGroup,
} from "@/components/ui/resizable"
import { VideoStoryboard } from "@/features/aigc/VideoStoryboard"
import { VideoTimeline } from "@/features/aigc/VideoTimeline"
import { VideoPlayer } from "@/features/aigc/VideoPlayer"
import { VideoGenerationChat } from "@/features/aigc/VideoGenerationChat"

export default function AigcVideoPage() {
  return (
    <div className="h-[calc(100vh-var(--layout-header-height))]">
      <ResizablePanelGroup direction="horizontal" className="size-full">
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

        {/* 右栏：对话驱动生成 */}
        <ResizablePanel defaultSize={25} minSize={20} maxSize={35}>
          <VideoGenerationChat />
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  )
}
