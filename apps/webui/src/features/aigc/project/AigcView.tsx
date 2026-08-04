/**
 * AIGC 图像生成视图——左栏素材区 + 中栏预览/素材区
 * 右栏对话由外层 WorkspaceLayout Copilot 面板统一提供
 * @author AaronZZH & Kiro
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
import { useParams, useRouter } from "next/navigation"
import { useEffect } from "react"
import { toast } from "sonner"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { useAigcProject } from "@/lib/api/rest/ai"
import { useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"
// import { useChatterLayoutPreference } from "@/features/chatter"
import { CopywritingPanel } from "../copywriting/CopywritingPanel"
import { StoryboardPanel } from "../copywriting/StoryboardPanel"
import { GenerationPanel } from "../generation/GenerationPanel"
import { PreviewPanel } from "../preview/PreviewPanel"
import { useAigcStore } from "../store"

export function AigcView({ projectId: projectIdProp }: { projectId?: number } = {}) {
  const router = useRouter()
  const params = useParams()
  const projectId = projectIdProp ?? (params?.projectId ? Number(params.projectId) : null)
  const { data: project, isError } = useAigcProject(projectId)

  useEffect(() => {
    if (isError && !projectIdProp) router.replace("/aigc")
  }, [isError, projectIdProp, router])
  // aigc 页面需要嵌入式对话面板
  // useChatterLayoutPreference("panel")

  const storyboardPanelOpen = useAigcStore((s) => s.storyboardPanelOpen)
  const queryClient = useQueryClient()
  const removePendingTask = useAigcStore((s) => s.removePendingTask)
  const addPendingTask = useAigcStore((s) => s.addPendingTask)
  const failPendingTask = useAigcStore((s) => s.failPendingTask)

  // 订阅 AIGC 任务事件，完成后刷新素材列表
  useAigcTaskStream({
    onCompleted: (task) => {
      if (!task.outputMediaId || !task.outputUrl) return
      queryClient.invalidateQueries({ queryKey: ["aigc", "media"] })
      setTimeout(() => removePendingTask(task.id), 1500)
    },
    onFailed: (task) => {
      // SSE 可能比 onSuccess 更早到达，兜底确保 pendingTask 存在
      addPendingTask({ id: task.id, prompt: task.prompt ?? "", type: task.type })
      failPendingTask(task.id, task.errorMsg ?? "生成失败")
      toast.error(task.errorMsg ?? "生成失败")
      // 失败卡片保留，等用户点击重试或手动关闭
    },
    onReconnect: () => {
      // SSE 重连后补查断连期间可能丢失的媒体结果
      queryClient.invalidateQueries({ queryKey: ["aigc", "media"] })
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

        {/* 中栏：预览 + 素材区 */}
        <ResizablePanel defaultSize="78%" minSize="40%">
          <div className="relative h-full">
            <PreviewPanel orientation={storyboardPanelOpen ? "vertical" : "horizontal"} />

            {/* 生成面板（从底部弹起） */}
            <GenerationPanel
              projectPrompt={
                project?.prompt?.trim()
                  ? { label: project.name ?? "项目提示词", content: project.prompt }
                  : null
              }
            />

            {/* 文案生成面板（从底部弹起） */}
            <CopywritingPanel projectId={projectId ?? undefined} />
          </div>
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  )
}
