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
import type { MediaAssetVO } from "../types"

interface DraggableAssetCardProps {
  asset: MediaAssetVO
  /** 所属组 ID，未分组时为 undefined；拖拽数据会携带此字段供 drop handler 使用 */
  groupId?: number
}

export function DraggableAssetCard({ asset, groupId }: DraggableAssetCardProps) {
  const { ref, listeners, attributes, isDragging } = useSemanticDraggable({
    id: `asset-${asset.id}`,
    item: {
      type: "image",
      id: String(asset.id),
      title: asset.name,
      url: asset.url,
      thumbnailUrl: asset.thumbnailUrl ?? asset.url,
      // 携带当前分组信息，drop handler 可据此判断是否需要变更分组
      groupId
    }
  })
  const setPreviewAsset = useAigcStore((s) => s.setPreviewAsset)
  const previewAsset = useAigcStore((s) => s.previewAsset)
  const isSelected = previewAsset?.id === asset.id

  const audioRef = useRef<HTMLAudioElement>(null)
  const [playing, setPlaying] = useState(false)
  const isAudio = asset.type === "AUDIO"

  const togglePlay = () => {
    const el = audioRef.current
    if (!el) return
    if (el.paused) el.play()
    else el.pause()
  }

  const handlePointerUp = () => {
    if (!isDragging) setPreviewAsset(asset)
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
        if (e.key === "Enter") setPreviewAsset(asset)
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
                src={asset.url}
                onPlay={() => setPlaying(true)}
                onPause={() => setPlaying(false)}
                onEnded={() => setPlaying(false)}
              />
            </div>
          ) : (
            // biome-ignore lint/performance/noImgElement: 动态素材缩略图
            <img
              src={asset.thumbnailUrl ?? asset.url}
              alt={asset.name}
              className="size-full object-cover"
            />
          )}
        </div>
      </div>
      <div className="px-2 py-1.5">
        <span className="block truncate text-foreground text-xs">{asset.name}</span>
      </div>
    </div>
  )
}
