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

import { CheckCircle2, Loader2, Music, Video, Wand2, XCircle } from "lucide-react"
import VideoPlugin from "yet-another-react-lightbox/plugins/video"
import { PendingOverlay } from "@/components/animate/PendingOverlay"
import { Lightbox, useLightbox } from "@/components/lightbox"
import { GlassCard } from "@/components/studio/GlassCard"
import { GlowButton } from "@/components/studio/GlowButton"
import type { AigcTaskEvent } from "@/lib/hooks/use-aigc-task-stream"

interface GenerationResultCardProps {
  tasks: AigcTaskEvent[]
  mediaType: "IMAGE" | "VIDEO" | "AUDIO"
  onRegenerate?: (task: AigcTaskEvent) => void
}

export function GenerationResultCard({
  tasks,
  mediaType,
  onRegenerate
}: GenerationResultCardProps) {
  const slides = tasks
    .filter((t) => t.status === "SUCCESS" && t.ossUrl && mediaType !== "AUDIO")
    .map((t) =>
      mediaType === "VIDEO"
        ? {
            type: "video" as const,
            sources: [{ src: t.ossUrl as string, type: "video/mp4" }]
          }
        : { src: t.ossUrl as string }
    )

  const {
    open: lightboxOpen,
    index: lightboxIndex,
    onOpen: openLightbox,
    onClose: closeLightbox
  } = useLightbox(slides)

  if (tasks.length === 0) return null

  return (
    <>
      <GlassCard glow="violet" className="overflow-hidden">
        <div className="border-foreground/6 border-b px-4 py-3">
          <p className="font-medium text-sm">生成结果</p>
        </div>
        <div className="divide-y divide-foreground/4">
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
                    className="flex size-12 shrink-0 cursor-zoom-in items-center justify-center overflow-hidden rounded-lg bg-foreground/6"
                    onClick={() => openLightbox(task.ossUrl as string)}
                  >
                    {mediaType === "VIDEO" ? (
                      <Video className="size-6 text-violet-400" />
                    ) : (
                      /* biome-ignore lint/performance/noImgElement: 生成缩略图，无尺寸信息无法用 next/image */
                      <img
                        src={task.ossUrl}
                        alt={task.prompt ?? "生成结果"}
                        className="size-full object-cover"
                      />
                    )}
                  </button>
                )
              ) : task.status === "FAIL" ? (
                <div className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-foreground/4 text-destructive/30 text-xs">
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
                    <div className="flex size-full items-center justify-center bg-linear-to-br from-violet-500/15 to-fuchsia-500/15">
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

              {(task.status === "SUCCESS" || task.status === "FAIL") && onRegenerate && (
                <GlowButton
                  tone="ghost"
                  size="sm"
                  onClick={() => onRegenerate(task)}
                  className="shrink-0 text-xs"
                >
                  <Wand2 className="size-3.5" />
                  重新生成
                </GlowButton>
              )}
            </div>
          ))}
        </div>
      </GlassCard>

      {/* Lightbox 图片/视频预览 */}
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
