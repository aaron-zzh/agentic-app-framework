/**
 * 可拖拽素材卡片——支持拖到生成面板 / 拖到其他素材组
 * @author AaronZZH & Kiro
 */

"use client"

import { Music, Pause, Play } from "lucide-react"
import { useRef, useState } from "react"
import { useSemanticDraggable } from "@/features/chatter/dnd/useSemanticDraggable"
import { cn } from "@/lib/utils/index"
import { useAigcStore } from "../store"
import type { AigcMedia } from "../types"

interface DraggableAssetCardProps {
  media: AigcMedia
}

export function DraggableAssetCard({ media }: DraggableAssetCardProps) {
  const version = media.currentVersion
  const { ref, listeners, attributes, isDragging } = useSemanticDraggable({
    id: `media-${media.id}`,
    item: {
      type: media.mediaType === "VIDEO" ? "video" : "image",
      id: media.id,
      title: media.name,
      url: version.url,
      thumbnailUrl: version.thumbnailUrl ?? version.url,
      semantics: { componentName: "StudioMedia", entity: "aigc.media" }
    }
  })
  const setPreviewMediaId = useAigcStore((state) => state.setPreviewMediaId)
  const previewMediaId = useAigcStore((state) => state.previewMediaId)
  const isSelected = previewMediaId === media.id

  const audioRef = useRef<HTMLAudioElement>(null)
  const [playing, setPlaying] = useState(false)
  const isAudio = media.mediaType === "AUDIO" || media.mediaType === "MUSIC"

  const togglePlay = () => {
    const el = audioRef.current
    if (!el) return
    if (el.paused) el.play()
    else el.pause()
  }

  const handlePointerUp = () => {
    if (!isDragging) setPreviewMediaId(media.id)
  }

  return (
    // biome-ignore lint/a11y/noStaticElementInteractions: dnd-kit 通过 attributes 注入 role
    <div
      ref={ref}
      {...listeners}
      {...attributes}
      className={cn(
        "group cursor-grab rounded-[6px] bg-card/50 transition-all",
        isDragging && "opacity-50",
        isSelected && "relative z-10"
      )}
      onPointerUp={handlePointerUp}
      onKeyDown={(e) => {
        if (e.key === "Enter") setPreviewMediaId(media.id)
      }}
    >
      <div
        className={cn(
          "overflow-hidden rounded-[6px] outline outline-1 outline-transparent transition-[outline-color]",
          isSelected ? "outline-primary" : "hover:outline-primary/40"
        )}
      >
        <div className="aspect-square bg-muted">
          {isAudio ? (
            <div className="relative flex size-full items-center justify-center bg-gradient-to-br from-violet-500/15 to-fuchsia-500/15">
              <Music className="size-8 text-muted-foreground/50" />
              <button
                type="button"
                onPointerDown={(e) => e.stopPropagation()}
                onPointerUp={(e) => {
                  e.stopPropagation()
                  togglePlay()
                }}
                className="absolute inset-0 m-auto flex size-10 items-center justify-center rounded-full bg-black/50 text-white opacity-0 transition-opacity hover:bg-black/70 group-hover:opacity-100"
                aria-label={playing ? "暂停" : "播放"}
              >
                {playing ? <Pause className="size-5" /> : <Play className="size-5" />}
              </button>
              {/* biome-ignore lint/a11y/useMediaCaption: 生成音频无字幕轨 */}
              <audio
                ref={audioRef}
                src={version.url}
                onPlay={() => setPlaying(true)}
                onPause={() => setPlaying(false)}
                onEnded={() => setPlaying(false)}
              />
            </div>
          ) : (
            // biome-ignore lint/performance/noImgElement: 动态素材缩略图
            <img
              src={version.thumbnailUrl ?? version.url}
              alt={media.name}
              className="size-full object-cover"
            />
          )}
        </div>
      </div>
      <div className="px-2 py-1.5">
        <span className="block truncate text-foreground text-xs">{media.name}</span>
      </div>
    </div>
  )
}
