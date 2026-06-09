/**
 * AIGC 图像生成视图——左栏素材区 + 中栏预览/文件区
 * 右栏对话由外层 WorkspaceLayout Copilot 面板统一提供
 * @author AaronZZH & Kiro
 */

"use client"

import { DndContext, type DragEndEvent, useSensor, useSensors } from "@dnd-kit/core"
import { useQueryClient } from "@tanstack/react-query"
import { PenLine, Sparkles } from "lucide-react"
import { Button } from "@/components/ui/button"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"
// import { useChatterLayoutPreference } from "@/features/chatter"
import { GenerationPanel } from "./GenerationPanel"
import { PreviewPanel } from "./PreviewPanel"
import { SmartPointerSensor } from "./SmartPointerSensor"
import { StoryboardPanel } from "./StoryboardPanel"
import { useAigcStore } from "./store"
import type { MediaAssetVO } from "./types"

export function AigcView() {
  // aigc 页面需要嵌入式对话面板
  // useChatterLayoutPreference("panel")

  const addReferenceAsset = useAigcStore((s) => s.addReferenceAsset)
  const addStoryboardAsset = useAigcStore((s) => s.addStoryboardAsset)
  const generationPanelOpen = useAigcStore((s) => s.generationPanelOpen)
  const setGenerationPanelOpen = useAigcStore((s) => s.setGenerationPanelOpen)
  const queryClient = useQueryClient()
  const removePendingTask = useAigcStore((s) => s.removePendingTask)

  // 订阅 AIGC 任务事件，完成后刷新素材列表
  useAigcTaskStream({
    onCompleted: (task) => {
      removePendingTask(task.id)
      queryClient.invalidateQueries({ queryKey: ["media-assets"] })
    },
    onFailed: (task) => {
      removePendingTask(task.id)
    }
  })

  const sensors = useSensors(
    useSensor(SmartPointerSensor, { activationConstraint: { distance: 8 } })
  )

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event
    if (!active.data.current) return
    if (over?.id === "generation-drop-zone") {
      addReferenceAsset(active.data.current as MediaAssetVO)
    } else if (over?.id === "storyboard-drop-zone") {
      addStoryboardAsset(active.data.current as MediaAssetVO)
    }
  }

  return (
    <div className="min-h-0 flex-1">
    <DndContext sensors={sensors} onDragEnd={handleDragEnd}>
      <ResizablePanelGroup orientation="horizontal" className="h-full bg-background">
        {/* 左栏：素材区 */}
        <ResizablePanel defaultSize="22%" minSize="15%" maxSize="35%">
          <StoryboardPanel />
        </ResizablePanel>

        <ResizableHandle withHandle />

        {/* 中栏：预览 + 文件区 */}
        <ResizablePanel defaultSize="78%" minSize="40%">
          <div className="relative h-full">
            <PreviewPanel />

            {/* 操作按钮（面板关闭时显示） */}
            {!generationPanelOpen && (
              <div className="absolute bottom-4 left-1/2 z-10 flex -translate-x-1/2 items-center gap-2">
                <Button
                  variant="outline"
                  className="shadow-lg"
                  onClick={() => {/* TODO: 打开文案生成面板 */}}
                >
                  <PenLine className="mr-2 size-4" />
                  生成文案
                </Button>
                <Button
                  onClick={() => setGenerationPanelOpen(true)}
                  className="shadow-lg"
                >
                  <Sparkles className="mr-2 size-4" />
                  生成图像
                </Button>
              </div>
            )}

            {/* 生成面板（从底部弹起） */}
            <GenerationPanel />
          </div>
        </ResizablePanel>
      </ResizablePanelGroup>
    </DndContext>
    </div>
  )
}
