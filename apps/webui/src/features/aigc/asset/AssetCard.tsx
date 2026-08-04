/**
 * 媒体视图卡片——仅负责展示与调用方动作。
 * @author AaronZZH & Kiro
 */

"use client"

import { Download, Music, Pause, Play, Sparkles, Trash2 } from "lucide-react"
import { useRouter } from "next/navigation"
import { useRef, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger
} from "@/components/ui/context-menu"
import { Model3DPreview } from "@/features/aigc/three/Model3DPreview"
import { downloadFileWithToast } from "@/lib/utils"
import type { MediaType, MediaVO } from "../types"

function getGenerationPath(type: MediaType): string {
  switch (type) {
    case "VIDEO":
      return "/studio/create/video"
    case "AUDIO":
      return "/studio/create/voice"
    case "MUSIC":
      return "/studio/create/music"
    case "MODEL_3D":
      return "/studio/create/tools/3d"
    default:
      return "/studio/create/image"
  }
}

interface AssetCardProps {
  media: MediaVO
  onClick: () => void
  onDelete: () => void
  onPreview?: () => void
  onRegenerate?: () => void
}

export function AssetCard({ media, onClick, onDelete, onPreview }: AssetCardProps) {
  const version = media.currentVersion
  const is3D = media.mediaType === "MODEL_3D"
  const isAudio = media.mediaType === "AUDIO" || media.mediaType === "MUSIC"
  const router = useRouter()
  const [audioPlaying, setAudioPlaying] = useState(false)
  const audioRef = useRef<HTMLAudioElement>(null)

  function handleAudioToggle(event: React.MouseEvent) {
    event.stopPropagation()
    const audio = audioRef.current
    if (!audio) return
    if (audioPlaying) audio.pause()
    else audio.play()
  }

  const actions = (
    <div className="absolute inset-x-0 top-0 flex justify-end gap-1 p-2 opacity-0 transition-opacity group-hover:opacity-100">
      <Button
        variant="secondary"
        size="icon-sm"
        className="bg-black/50 text-white hover:bg-black/70"
        onClick={(event) => {
          event.stopPropagation()
          downloadFileWithToast(version.url, media.name || `media-${media.id}`)
        }}
        aria-label="下载媒体"
      >
        <Download />
      </Button>
      <Button
        variant="secondary"
        size="icon-sm"
        className="bg-black/50 text-white hover:bg-black/70"
        onClick={(event) => {
          event.stopPropagation()
          onDelete()
        }}
        aria-label="移除媒体"
      >
        <Trash2 />
      </Button>
    </div>
  )

  const card = (
    <Card className="group gap-0 overflow-hidden shadow-sm ring-0 transition-all hover:shadow-lg">
      <div className="relative aspect-square overflow-hidden bg-muted">
        {is3D ? (
          <Model3DPreview url={version.url} className="size-full" />
        ) : isAudio ? (
          <button
            type="button"
            className="flex size-full flex-col items-center justify-center gap-2 bg-linear-to-br from-violet-500/15 to-fuchsia-500/15"
            onClick={handleAudioToggle}
          >
            <Music className="text-muted-foreground/60" />
            <div className="flex size-8 items-center justify-center rounded-full bg-black/30 text-white">
              {audioPlaying ? <Pause /> : <Play />}
            </div>
            <audio
              ref={audioRef}
              src={version.url}
              onPlay={() => setAudioPlaying(true)}
              onPause={() => setAudioPlaying(false)}
              onEnded={() => setAudioPlaying(false)}
              className="hidden"
            >
              <track kind="captions" />
            </audio>
          </button>
        ) : (
          <button
            type="button"
            className="block size-full cursor-pointer"
            onClick={onClick}
          >
            {media.mediaType === "VIDEO" ? (
              <video
                src={version.url}
                className="size-full object-cover"
                muted
                playsInline
                preload="metadata"
              />
            ) : (
              // biome-ignore lint/performance/noImgElement: 动态媒体缩略图
              <img
                src={version.thumbnailUrl ?? version.url}
                alt={media.name}
                className="size-full object-cover transition-transform group-hover:scale-105"
              />
            )}
          </button>
        )}
        {actions}
        {media.mediaType !== "IMAGE" && (
          <Badge variant="secondary" className="pointer-events-none absolute bottom-2 left-2">
            {media.mediaType === "VIDEO" ? "视频" : is3D ? "3D" : "音频"}
          </Badge>
        )}
      </div>
      <button type="button" className="p-2 text-left hover:bg-muted/50" onClick={onPreview}>
        <span className="block truncate font-medium text-sm">{media.name}</span>
        <span className="text-muted-foreground text-xs">
          {new Date(media.createTime).toLocaleDateString()}
        </span>
      </button>
    </Card>
  )

  if (media.sourceType !== "GENERATION") return card

  return (
    <ContextMenu>
      <ContextMenuTrigger render={<div />}>{card}</ContextMenuTrigger>
      <ContextMenuContent>
        <ContextMenuItem onClick={() => router.push(getGenerationPath(media.mediaType))}>
          <Sparkles />
          跳转到生成界面
        </ContextMenuItem>
      </ContextMenuContent>
    </ContextMenu>
  )
}
