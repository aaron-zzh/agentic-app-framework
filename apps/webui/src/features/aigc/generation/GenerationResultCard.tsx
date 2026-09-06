/**
 * 统一生成结果卡片。
 *
 * 展示持久化后的 Media，并允许用户将成功结果幂等保存为 Asset。
 * @author AaronZZH & Kiro
 */

"use client"

import {
  Box,
  Check,
  CheckCircle2,
  Loader2,
  Music,
  PanelTopOpen,
  Save,
  Video,
  Wand2,
  XCircle
} from "lucide-react"
import { useRouter } from "next/navigation"
import { useState } from "react"
import { toast } from "sonner"
import VideoPlugin from "yet-another-react-lightbox/plugins/video"
import { PendingOverlay } from "@/components/animate/PendingOverlay"
import { Lightbox, useLightbox } from "@/components/lightbox"
import { GlassCard } from "@/components/studio/GlassCard"
import { GlowButton } from "@/components/studio/GlowButton"
import { useSaveMediaAsAsset } from "@/lib/api/rest/media"
import type { AigcTaskEvent } from "@/lib/api/rest/ai/aigc-task"

interface GenerationResultCardProps {
  tasks: AigcTaskEvent[]
  mediaType: "IMAGE" | "VIDEO" | "AUDIO" | "MUSIC" | "MODEL_3D"
  onRegenerate?: (task: AigcTaskEvent) => void
  regeneratingTaskId?: number | null
}

export function GenerationResultCard({
  tasks,
  mediaType,
  onRegenerate,
  regeneratingTaskId = null
}: GenerationResultCardProps) {
  const [savedMediaIds, setSavedMediaIds] = useState<Set<number>>(() => new Set())
  const router = useRouter()
  const saveAsset = useSaveMediaAsAsset()
  const slides = tasks
    .filter(
      (task) =>
        task.status === "SUCCESS" &&
        task.outputUrl &&
        (mediaType === "IMAGE" || mediaType === "VIDEO")
    )
    .map((task) =>
      mediaType === "VIDEO"
        ? {
            type: "video" as const,
            sources: [{ src: task.outputUrl as string, type: "video/mp4" }]
          }
        : { src: task.outputUrl as string }
    )

  const {
    open: lightboxOpen,
    index: lightboxIndex,
    onOpen: openLightbox,
    onClose: closeLightbox
  } = useLightbox(slides)

  if (tasks.length === 0) return null

  const handleSaveAsset = (task: AigcTaskEvent) => {
    if (!task.outputMediaId || task.isAsset || savedMediaIds.has(task.outputMediaId)) return
    saveAsset.mutate(
      { mediaId: task.outputMediaId },
      {
        onSuccess: (asset) => {
          setSavedMediaIds((current) => new Set(current).add(asset.mediaId))
          toast.success("已保存为资产")
        },
        onError: () => toast.error("保存资产失败，请重试")
      }
    )
  }

  const handleOpenCanvas = (task: AigcTaskEvent) => {
    if (mediaType !== "IMAGE" || task.status !== "SUCCESS" || !task.outputUrl) return
    const params = new URLSearchParams({ imageUrl: task.outputUrl })
    router.push(`/studio/create/draw?${params.toString()}`)
  }

  return (
    <>
      <GlassCard glow="violet" className="overflow-hidden">
        <div className="border-foreground/6 border-b px-4 py-3">
          <p className="font-medium text-sm">生成结果</p>
        </div>
        <div className="divide-y divide-foreground/4">
          {tasks.map((task) => {
            const isSaved =
              task.isAsset ||
              task.assetId !== null ||
              (task.outputMediaId !== null && savedMediaIds.has(task.outputMediaId))
            const isSaving =
              saveAsset.isPending && saveAsset.variables?.mediaId === task.outputMediaId
            const isRegenerating = regeneratingTaskId === task.id

            return (
              <div key={task.id} className="flex items-center gap-3 px-4 py-3">
                <TaskStatusIcon status={task.status} />

                <ResultPreview task={task} mediaType={mediaType} onOpen={openLightbox} />

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
                    tone={isSaved ? "ghost" : "violet"}
                    size="sm"
                    onClick={() => handleSaveAsset(task)}
                    disabled={isSaved || isSaving || task.outputMediaId === null}
                    className="shrink-0 text-xs"
                  >
                    {isSaving ? (
                      <Loader2 className="animate-spin" />
                    ) : isSaved ? (
                      <Check />
                    ) : (
                      <Save />
                    )}
                    {isSaved ? "已保存" : isSaving ? "保存中..." : "保存为资产"}
                  </GlowButton>
                )}

                {mediaType === "IMAGE" && task.status === "SUCCESS" && task.outputUrl && (
                  <GlowButton
                    tone="ghost"
                    size="sm"
                    onClick={() => handleOpenCanvas(task)}
                    className="shrink-0 text-xs"
                  >
                    <PanelTopOpen />
                    在画布中打开
                  </GlowButton>
                )}

                {(task.status === "SUCCESS" || task.status === "FAIL") && onRegenerate && (
                  <GlowButton
                    tone="ghost"
                    size="sm"
                    onClick={() => onRegenerate(task)}
                    disabled={regeneratingTaskId !== null}
                    className="shrink-0 text-xs"
                  >
                    {isRegenerating ? <Loader2 className="animate-spin" /> : <Wand2 />}
                    {isRegenerating ? "提交中..." : "重新生成"}
                  </GlowButton>
                )}
              </div>
            )
          })}
        </div>
      </GlassCard>

      <Lightbox
        open={lightboxOpen}
        index={lightboxIndex}
        slides={slides}
        close={closeLightbox}
        plugins={mediaType === "VIDEO" ? [VideoPlugin] : []}
      />
    </>
  )
}

function ResultPreview({
  task,
  mediaType,
  onOpen
}: {
  task: AigcTaskEvent
  mediaType: GenerationResultCardProps["mediaType"]
  onOpen: (url: string) => void
}) {
  if (task.status === "SUCCESS" && task.outputUrl) {
    if (mediaType === "AUDIO" || mediaType === "MUSIC") {
      return (
        <audio controls src={task.outputUrl} className="h-8 w-48 shrink-0">
          <track kind="captions" />
        </audio>
      )
    }
    if (mediaType === "MODEL_3D") {
      return (
        <a
          href={task.outputUrl}
          target="_blank"
          rel="noreferrer"
          className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-violet-500/10 text-violet-400 hover:bg-violet-500/20"
          aria-label="打开 3D 模型"
        >
          <Box />
        </a>
      )
    }
    return (
      <button
        type="button"
        className="flex size-12 shrink-0 cursor-zoom-in items-center justify-center overflow-hidden rounded-lg bg-foreground/6"
        onClick={() => onOpen(task.outputUrl as string)}
      >
        {mediaType === "VIDEO" ? (
          <Video className="text-violet-400" />
        ) : (
          // biome-ignore lint/performance/noImgElement: 生成缩略图无固定尺寸信息
          <img
            src={task.outputUrl}
            alt={task.prompt ?? "生成结果"}
            className="size-full object-cover"
          />
        )}
      </button>
    )
  }

  if (task.status === "FAIL") {
    return (
      <div className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-foreground/4 text-destructive/30">
        {mediaType === "AUDIO" || mediaType === "MUSIC" ? (
          <Music className="opacity-30" />
        ) : mediaType === "MODEL_3D" ? (
          <Box className="opacity-30" />
        ) : mediaType === "VIDEO" ? (
          "视"
        ) : (
          "图"
        )}
      </div>
    )
  }

  return (
    <div className="relative size-12 shrink-0 overflow-hidden rounded-lg">
      {mediaType === "AUDIO" || mediaType === "MUSIC" ? (
        <div className="flex size-full items-center justify-center bg-linear-to-br from-violet-500/15 to-fuchsia-500/15">
          <Music className="animate-pulse text-violet-400" />
        </div>
      ) : mediaType === "MODEL_3D" ? (
        <div className="flex size-full items-center justify-center bg-violet-500/10">
          <Box className="animate-pulse text-violet-400" />
        </div>
      ) : (
        <PendingOverlay label="" showProgress progressMs={60000} />
      )}
    </div>
  )
}

const STATUS_LABEL: Record<AigcTaskEvent["status"], string> = {
  PREPARED: "已准备",
  SUBMITTING: "提交中",
  NEEDS_RECONCILIATION: "待对账",
  PENDING: "排队中",
  RUNNING: "生成中",
  COMPLETING: "完成中",
  SUCCESS: "已完成",
  FAIL: "生成失败"
}

function TaskStatusIcon({ status }: { status: AigcTaskEvent["status"] }) {
  if (status === "SUCCESS") return <CheckCircle2 className="shrink-0 text-emerald-400" />
  if (status === "FAIL") return <XCircle className="shrink-0 text-destructive" />
  return <Loader2 className="shrink-0 animate-spin text-muted-foreground" />
}
