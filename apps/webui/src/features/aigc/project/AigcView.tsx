/**
 * AIGC 图像生成视图——左栏素材区 + 中栏预览/文件区
 * 右栏对话由外层 WorkspaceLayout Copilot 面板统一提供
 * @author AaronZZH & Kiro
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
import { PenLine, Sparkles } from "lucide-react"
import { useParams, useRouter } from "next/navigation"
import { useEffect } from "react"
import { Button } from "@/components/ui/button"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"
import { useAigcProject } from "@/lib/queries/use-aigc-projects"
// import { useChatterLayoutPreference } from "@/features/chatter"
import { CopywritingPanel } from "../copywriting/CopywritingPanel"
import { StoryboardPanel } from "../copywriting/StoryboardPanel"
import { GenerationPanel } from "../generation/GenerationPanel"
import { PreviewPanel } from "../preview/PreviewPanel"
import { useAigcStore } from "../store"

export function AigcView() {
  const router = useRouter()
  const params = useParams()
  const projectId = params?.projectId ? Number(params.projectId) : null
  const { data: project, isError } = useAigcProject(projectId)

  useEffect(() => {
    if (isError) router.replace("/aigc")
  }, [isError, router])
  // aigc 页面需要嵌入式对话面板
  // useChatterLayoutPreference("panel")

  const _addReferenceAsset = useAigcStore((s) => s.addReferenceAsset)
  const _addStoryboardAsset = useAigcStore((s) => s.addStoryboardAsset)
  const setProjectPromptTag = useAigcStore((s) => s.setProjectPromptTag)

  // 项目加载后把项目提示词同步到 store，供 GenerationPanel 使用
  useEffect(() => {
    if (!project) return
    setProjectPromptTag(
      project.prompt?.trim()
        ? { label: project.name ?? "项目提示词", content: project.prompt }
        : null
    )
    return () => setProjectPromptTag(null)
  }, [project, setProjectPromptTag])
  const generationPanelOpen = useAigcStore((s) => s.generationPanelOpen)
  const setGenerationPanelOpen = useAigcStore((s) => s.setGenerationPanelOpen)
  const copywritingPanelOpen = useAigcStore((s) => s.copywritingPanelOpen)
  const setCopywritingPanelOpen = useAigcStore((s) => s.setCopywritingPanelOpen)
  const storyboardPanelOpen = useAigcStore((s) => s.storyboardPanelOpen)
  const queryClient = useQueryClient()
  const removePendingTask = useAigcStore((s) => s.removePendingTask)
  const addPendingTask = useAigcStore((s) => s.addPendingTask)
  const completePendingTask = useAigcStore((s) => s.completePendingTask)
  const failPendingTask = useAigcStore((s) => s.failPendingTask)

  // 订阅 AIGC 任务事件，完成后刷新素材列表
  useAigcTaskStream({
    onCompleted: (task) => {
      if (task.ossUrl) {
        const tempAsset = {
          id: task.id,
          name: task.prompt ?? "生成图片",
          type: "IMAGE" as const,
          url: task.ossUrl,
          thumbnailUrl: task.ossUrl,
          size: null,
          width: null,
          height: null,
          duration: null,
          generationParams: null,
          tags: null,
          categoryId: null,
          groupId: null,
          userId: 0,
          version: 0,
          createTime: "",
          updateTime: ""
        }
        completePendingTask(task.id, task.ossUrl, tempAsset)
      }
      setTimeout(() => {
        removePendingTask(task.id)
        queryClient.invalidateQueries({ queryKey: ["media-assets"] })
      }, 1500)
    },
    onFailed: (task) => {
      // SSE 可能比 onSuccess 更早到达，兜底确保 pendingTask 存在
      addPendingTask({ id: task.id, prompt: task.prompt ?? "", type: task.type })
      failPendingTask(task.id, task.errorMsg ?? "生成失败")
      setTimeout(() => removePendingTask(task.id), 3000)
    }
  })

  return (
    <div className="min-h-0 flex-1">
      <ResizablePanelGroup orientation="horizontal" className="h-full bg-background">
        {/* 左栏：元素看板（可关闭） */}
        {storyboardPanelOpen && (
          <>
            <ResizablePanel defaultSize="22%" minSize="15%" maxSize="35%">
              <StoryboardPanel />
            </ResizablePanel>
            <ResizableHandle withHandle />
          </>
        )}

        {/* 中栏：预览 + 文件区 */}
        <ResizablePanel defaultSize="78%" minSize="40%">
          <div className="relative h-full">
            <PreviewPanel orientation={storyboardPanelOpen ? "vertical" : "horizontal"} />

            {/* 操作按钮（面板关闭时显示） */}
            {!generationPanelOpen && !copywritingPanelOpen && (
              <div className="absolute bottom-4 left-1/2 z-10 flex -translate-x-1/2 items-center gap-2">
                <Button
                  variant="outline"
                  className="shadow-lg"
                  onClick={() => setCopywritingPanelOpen(true)}
                >
                  <PenLine className="mr-2 size-4" />
                  生成文案
                </Button>
                <Button onClick={() => setGenerationPanelOpen(true)} className="shadow-lg">
                  <Sparkles className="mr-2 size-4" />
                  生成图像
                </Button>
              </div>
            )}

            {/* 生成面板（从底部弹起） */}
            <GenerationPanel />

            {/* 文案生成面板（从底部弹起） */}
            <CopywritingPanel />
          </div>
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  )
}
