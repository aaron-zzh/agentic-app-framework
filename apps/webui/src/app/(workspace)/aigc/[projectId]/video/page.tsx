/**
 * 视频生成工作台
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { VideoEditPanel } from "@/features/aigc/video/VideoEditPanel"
import { VideoGenerationChat } from "@/features/aigc/video/VideoGenerationChat"
import { VideoPlayer } from "@/features/aigc/video/VideoPlayer"
import { VideoStoryboard } from "@/features/aigc/video/VideoStoryboard"
import { VideoTimeline } from "@/features/aigc/video/VideoTimeline"

export default function AigcVideoPage() {
  const [mode, setMode] = useState<"generate" | "edit">("generate")

  return (
    <div className="flex h-full flex-col">
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

      <ResizablePanelGroup orientation="horizontal" className="flex-1">
        <ResizablePanel defaultSize="20%" minSize="15%" maxSize="30%">
          <ResizablePanelGroup orientation="vertical">
            <ResizablePanel defaultSize="50%">
              <VideoStoryboard />
            </ResizablePanel>
            <ResizableHandle withHandle />
            <ResizablePanel defaultSize="50%">
              <VideoTimeline />
            </ResizablePanel>
          </ResizablePanelGroup>
        </ResizablePanel>

        <ResizableHandle withHandle />

        <ResizablePanel defaultSize="55%" minSize="40%">
          <div className="flex h-full items-center justify-center p-6">
            <div className="w-full max-w-3xl">
              <VideoPlayer />
            </div>
          </div>
        </ResizablePanel>

        <ResizableHandle withHandle />

        <ResizablePanel defaultSize="25%" minSize="20%" maxSize="35%">
          {mode === "generate" ? <VideoGenerationChat /> : <VideoEditPanel />}
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  )
}
