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
import { useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"
import { useAigcProject } from "@/lib/queries/use-aigc-projects"
import { invalidateCreditQueries } from "@/lib/queries/use-credits"
// import { useChatterLayoutPreference } from "@/features/chatter"
import { CopywritingPanel } from "../copywriting/CopywritingPanel"
import { StoryboardPanel } from "../copywriting/StoryboardPanel"
import { GenerationPanel } from "../generation/GenerationPanel"
import { PreviewPanel } from "../preview/PreviewPanel"
import { useAigcStore } from "../store"
import type { MediaAssetType } from "../types"

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
        // 临时占位 asset 的类型需与任务类型对齐，否则音频/视频/3D 会被当作图片渲染
        const assetType: MediaAssetType =
          task.type === "VIDEO"
            ? "VIDEO"
            : task.type === "MODEL_3D"
              ? "MODEL_3D"
              : task.type === "VOICE" || task.type === "MUSIC"
                ? "AUDIO"
                : "IMAGE"
        const tempAsset = {
          id: task.id,
          name: task.prompt ?? "生成素材",
          type: assetType,
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
          updateTime: "",
          groupName: null,
          aiGenerated: true,
          modelName: null,
          providerCode: null
        }
        completePendingTask(task.id, task.ossUrl, tempAsset)
      }
      setTimeout(() => {
        removePendingTask(task.id)
        queryClient.invalidateQueries({ queryKey: ["media-assets"] })
        queryClient.invalidateQueries({ queryKey: ["media-asset-library"] })
        invalidateCreditQueries(queryClient)
      }, 1500)
    },
    onFailed: (task) => {
      // SSE 可能比 onSuccess 更早到达，兜底确保 pendingTask 存在
      addPendingTask({ id: task.id, prompt: task.prompt ?? "", type: task.type })
      failPendingTask(task.id, task.errorMsg ?? "生成失败")
      toast.error(task.errorMsg ?? "生成失败")
      // 失败卡片保留，等用户点击重试或手动关闭
    },
    onReconnect: () => {
      // SSE 断连重连后，补查断连期间可能丢失的任务结果
      queryClient.invalidateQueries({ queryKey: ["media-assets"] })
      queryClient.invalidateQueries({ queryKey: ["media-asset-library"] })
      invalidateCreditQueries(queryClient)
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
            <GenerationPanel />

            {/* 文案生成面板（从底部弹起） */}
            <CopywritingPanel projectId={projectId ?? undefined} />
          </div>
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  )
}
