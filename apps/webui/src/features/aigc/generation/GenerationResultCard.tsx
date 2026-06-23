/**
 * GenerationResultCard——生成结果卡片
 *
 * 在 /studio/create/image 和 /studio/create/video 提交成功后展示。
 * 右上角"→保存到项目"按钮触发 SaveToProjectDialog。
 * M6: PENDING/RUNNING 状态显示"取消"按钮
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { CheckCircle2, FolderInput, Loader2, Music, XCircle } from "lucide-react"
import { useState } from "react"
import { PendingOverlay } from "@/components/animate/PendingOverlay"
import { Lightbox, useLightbox } from "@/components/lightbox"
import { GlassCard } from "@/components/studio/GlassCard"
import { GlowButton } from "@/components/studio/GlowButton"
import type { AigcTaskEvent } from "@/lib/hooks/use-aigc-task-stream"
import type { SaveFromGenerationParams } from "@/lib/queries/use-image-generation"
import { SaveToProjectDialog } from "./SaveToProjectDialog"

interface GenerationResultCardProps {
  tasks: AigcTaskEvent[]
  mediaType: "IMAGE" | "VIDEO" | "AUDIO"
}

export function GenerationResultCard({ tasks, mediaType }: GenerationResultCardProps) {
  const [dialogOpen, setDialogOpen] = useState(false)
  const [selectedAsset, setSelectedAsset] = useState<Omit<
    SaveFromGenerationParams,
    "projectId"
  > | null>(null)

  const imageSlides = tasks
    .filter((t) => t.status === "SUCCESS" && t.ossUrl && mediaType === "IMAGE")
    .map((t) => ({ src: t.ossUrl as string }))

  const {
    open: lightboxOpen,
    index: lightboxIndex,
    onOpen: openLightbox,
    onClose: closeLightbox
  } = useLightbox(imageSlides)

  const handleSaveToProject = (task: AigcTaskEvent) => {
    setSelectedAsset({
      url: task.ossUrl ?? task.resultUrl ?? "",
      name: task.prompt?.slice(0, 40) ?? "生成作品",
      type: mediaType,
      thumbnailUrl: task.ossUrl ?? task.resultUrl ?? undefined
    })
    setDialogOpen(true)
  }

  if (tasks.length === 0) return null

  return (
    <>
      <GlassCard glow="violet" className="overflow-hidden">
        <div className="border-foreground/[0.06] border-b px-4 py-3">
          <p className="font-medium text-sm">生成结果</p>
        </div>
        <div className="divide-y divide-foreground/[0.04]">
          {tasks.map((task) => (
            <div key={task.id} className="flex items-center gap-3 px-4 py-3">
              <TaskStatusIcon status={task.status} />

              {task.status === "SUCCESS" && task.ossUrl ? (
                mediaType === "AUDIO" ? (
                  <audio controls src={task.ossUrl} className="h-8 w-48 shrink-0">
                    <track kind="captions" />
                  </audio>
                ) : (
                  <button
                    type="button"
                    className="size-12 shrink-0 cursor-zoom-in overflow-hidden rounded-lg"
                    onClick={() => openLightbox(task.ossUrl as string)}
                  >
                    {/* biome-ignore lint/performance/noImgElement: 生成缩略图，无尺寸信息无法用 next/image */}
                    <img
                      src={task.ossUrl}
                      alt={task.prompt ?? "生成结果"}
                      className="size-full object-cover"
                    />
                  </button>
                )
              ) : task.status === "FAIL" ? (
                <div className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-foreground/[0.04] text-destructive/30 text-xs">
                  {mediaType === "AUDIO" ? (
                    <Music className="size-5 opacity-30" />
                  ) : mediaType === "VIDEO" ? (
                    "视"
                  ) : (
                    "图"
                  )}
                </div>
              ) : (
                <div className="relative size-12 shrink-0 overflow-hidden rounded-lg">
                  {mediaType === "AUDIO" ? (
                    <div className="flex size-full items-center justify-center bg-gradient-to-br from-violet-500/15 to-fuchsia-500/15">
                      <Music className="size-5 animate-pulse text-violet-400" />
                    </div>
                  ) : (
                    <PendingOverlay label="" showProgress progressMs={60000} />
                  )}
                </div>
              )}

              <div className="min-w-0 flex-1">
                <p className="truncate text-sm">
                  {task.prompt?.slice(0, 50) ?? `任务 #${task.id}`}
                </p>
                <p className="mt-0.5 text-muted-foreground text-xs">
                  {STATUS_LABEL[task.status]}
                  {task.errorMsg && ` · ${task.errorMsg.slice(0, 30)}`}
                </p>
              </div>

              {task.status === "SUCCESS" && (
                <GlowButton
                  tone="ghost"
                  size="sm"
                  onClick={() => handleSaveToProject(task)}
                  className="shrink-0 text-xs"
                >
                  <FolderInput className="size-3.5" />
                  保存到项目
                </GlowButton>
              )}
            </div>
          ))}
        </div>
      </GlassCard>

      {/* Lightbox 图片预览 */}
      <Lightbox
        open={lightboxOpen}
        index={lightboxIndex}
        slides={imageSlides}
        close={closeLightbox}
      />

      {selectedAsset && (
        <SaveToProjectDialog open={dialogOpen} onOpenChange={setDialogOpen} asset={selectedAsset} />
      )}
    </>
  )
}

const STATUS_LABEL: Record<AigcTaskEvent["status"], string> = {
  PENDING: "排队中",
  RUNNING: "生成中",
  SUCCESS: "已完成",
  FAIL: "生成失败"
}

function TaskStatusIcon({ status }: { status: AigcTaskEvent["status"] }) {
  if (status === "SUCCESS") return <CheckCircle2 className="size-4 shrink-0 text-emerald-400" />
  if (status === "FAIL") return <XCircle className="size-4 shrink-0 text-destructive" />
  return <Loader2 className="size-4 shrink-0 animate-spin text-muted-foreground" />
}
