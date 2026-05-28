/**
 * 对话中的媒体预览卡片——图片/视频内联预览 + 操作按钮
 * @author AaronZZH & Kiro
 */

"use client"

import { Download, Loader2, Maximize2, Play, RefreshCw } from "lucide-react"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils/index"
import { StyleAdjustDialog } from "./StyleAdjustDialog"

type MediaType = "image" | "video"
type MediaStatus = "generating" | "completed"

interface MediaPreviewCardProps {
  type: MediaType
  thumbnail: string
  alt?: string
  status?: MediaStatus
  duration?: string
  onExpand?: () => void
  onRegenerate?: () => void
  onSave?: () => void
}

export function MediaPreviewCard({
  type,
  thumbnail,
  alt = "",
  status = "completed",
  duration,
  onExpand,
  onRegenerate,
  onSave
}: MediaPreviewCardProps) {
  return (
    <div className="group overflow-hidden rounded-xl border border-border/50 bg-card/80 transition-all hover:border-border">
      {/* 缩略图区域 */}
      <div className="relative aspect-video overflow-hidden bg-muted">
        {/* biome-ignore lint/performance/noImgElement: 动态媒体缩略图 */}
        <img src={thumbnail} alt={alt} className="size-full object-cover" />

        {/* 视频播放图标 */}
        {type === "video" && status === "completed" && (
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="flex size-10 items-center justify-center rounded-full bg-black/50 backdrop-blur-sm">
              <Play className="size-5 text-white" fill="white" />
            </div>
          </div>
        )}

        {/* 生成中遮罩 */}
        {status === "generating" && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-black/50 backdrop-blur-sm">
            <Loader2 className="size-6 animate-spin text-white" />
            <span className="text-white/80 text-xs">生成中...</span>
          </div>
        )}

        {/* 时长标签 */}
        {type === "video" && duration && (
          <span className="absolute right-2 bottom-2 rounded bg-black/60 px-1.5 py-0.5 text-[10px] text-white">
            {duration}
          </span>
        )}

        {/* 放大按钮（hover 显示） */}
        {type === "image" && status === "completed" && (
          <Button
            variant="ghost"
            size="sm"
            className={cn(
              "absolute top-2 right-2 size-7 p-0 opacity-0 transition-opacity",
              "bg-black/40 text-white hover:bg-black/60 group-hover:opacity-100"
            )}
            onClick={onExpand}
          >
            <Maximize2 className="size-3.5" />
          </Button>
        )}
      </div>

      {/* 操作栏 */}
      <div className="flex items-center justify-end gap-1 px-2 py-1.5">
        {type === "image" && status === "completed" && <StyleAdjustDialog initialPrompt={alt} />}
        {type === "image" && status === "completed" && (
          <Button
            variant="ghost"
            size="sm"
            className="h-6 px-2 text-muted-foreground text-xs hover:text-foreground"
            onClick={onRegenerate}
          >
            <RefreshCw className="mr-1 size-3" />
            换一张
          </Button>
        )}
        {status === "completed" && (
          <Button
            variant="ghost"
            size="sm"
            className="h-6 px-2 text-muted-foreground text-xs hover:text-foreground"
            onClick={onSave}
          >
            <Download className="mr-1 size-3" />
            保存
          </Button>
        )}
      </div>
    </div>
  )
}
