/**
 * AIGC 核心布局——三栏 resizable 面板
 * 左栏故事板 + 中栏预览/文件区 + 右栏对话
 * @author AaronZZH & Kiro
 */

"use client"

import { DndContext, type DragEndEvent } from "@dnd-kit/core"
import { Sparkles } from "lucide-react"
import { Button } from "@/components/ui/button"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { GenerationHistory } from "./GenerationHistory"
import { GenerationPanel } from "./GenerationPanel"
import { PreviewPanel } from "./PreviewPanel"
import { StoryboardPanel } from "./StoryboardPanel"
import { useAigcStore } from "./store"
import type { MediaAsset } from "./types"

/** 右栏对话面板 */
function ChatPanel() {
  return (
    <div className="flex h-full flex-col">
      <div className="border-border/50 border-b px-4 py-3">
        <h2 className="font-semibold text-foreground text-sm">AI 对话</h2>
      </div>
      <div className="flex flex-1 flex-col justify-end p-4">
        <p className="mb-4 text-center text-muted-foreground text-xs">
          描述你想生成的内容，AI 将为你创作
        </p>
        <div className="flex items-center gap-2 rounded-lg border border-border/50 bg-background px-3 py-2">
          <input
            type="text"
            placeholder="描述你想生成的图片或视频..."
            className="flex-1 bg-transparent text-foreground text-sm outline-none placeholder:text-muted-foreground"
          />
          <Button
            size="sm"
            className="h-7 bg-gradient-to-r from-violet-500 to-fuchsia-500 px-3 text-white text-xs"
          >
            发送
          </Button>
        </div>
      </div>
      {/* 折叠的生成历史面板 */}
      <GenerationHistory />
    </div>
  )
}

export function AigcLayout() {
  const addReferenceAsset = useAigcStore((s) => s.addReferenceAsset)
  const generationPanelOpen = useAigcStore((s) => s.generationPanelOpen)
  const setGenerationPanelOpen = useAigcStore((s) => s.setGenerationPanelOpen)

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event
    if (over?.id === "generation-drop-zone" && active.data.current) {
      addReferenceAsset(active.data.current as MediaAsset)
    }
  }

  return (
    <DndContext onDragEnd={handleDragEnd}>
      <div className="relative flex h-full flex-col bg-background">
        {/* 三栏布局 */}
        <ResizablePanelGroup direction="horizontal" className="flex-1">
          {/* 左栏：故事板 */}
          <ResizablePanel defaultSize={20} minSize={15} maxSize={30}>
            <StoryboardPanel />
          </ResizablePanel>

          <ResizableHandle withHandle />

          {/* 中栏：预览 + 文件区 */}
          <ResizablePanel defaultSize={55} minSize={40}>
            <PreviewPanel />
          </ResizablePanel>

          <ResizableHandle withHandle />

          {/* 右栏：对话 */}
          <ResizablePanel defaultSize={25} minSize={20} maxSize={35}>
            <ChatPanel />
          </ResizablePanel>
        </ResizablePanelGroup>

        {/* 生成按钮（面板关闭时显示） */}
        {!generationPanelOpen && (
          <div className="absolute bottom-4 left-1/2 -translate-x-1/2">
            <Button
              onClick={() => setGenerationPanelOpen(true)}
              className="bg-gradient-to-r from-violet-500 to-fuchsia-500 text-white shadow-lg hover:from-violet-600 hover:to-fuchsia-600"
            >
              <Sparkles className="mr-2 size-4" />
              生成图像
            </Button>
          </div>
        )}

        {/* 生成面板（从底部弹起） */}
        <GenerationPanel />
      </div>
    </DndContext>
  )
}
