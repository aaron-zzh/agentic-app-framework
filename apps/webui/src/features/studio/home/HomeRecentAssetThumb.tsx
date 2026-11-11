/**
 * Studio 首页最近生成中的单个缩略图（边长由 size 决定，默认 24px）：悬停时在上方放大预览（约 96px），
 * 点击图片、视频交给宿主打开灯箱，点击音频直接播放或暂停。
 *
 * 放大预览是缩略图内的绝对定位浮层，不走 Portal，全屏投屏时也能显示；
 * 因此宿主容器不能裁剪纵向溢出。
 * @author AaronZZH & Kiro
 */

"use client"

import { ImageIcon, Music, Pause, Play, Video as VideoIcon } from "lucide-react"
import Image from "next/image"
import { useRef, useState } from "react"
import type { AigcMedia } from "@/features/aigc/types"
import { cn } from "@/lib/utils/cn"

interface HomeRecentAssetThumbProps {
  asset: AigcMedia
  /** 缩略图边长（px），默认 24 */
  size?: number
  /** 打开灯箱，参数为素材地址 */
  onOpenLightbox: (url: string) => void
  className?: string
}

/** 图片或视频画面：缩略图优先，视频无缩略图时用 video 静帧 */
function renderVisual(asset: AigcMedia, sizes: number) {
  const version = asset.currentVersion
  if (asset.mediaType === "VIDEO") {
    if (version.thumbnailUrl) {
      // biome-ignore lint/performance/noImgElement: 视频缩略图地址来自对象存储，尺寸不定
      return <img src={version.thumbnailUrl} alt={asset.name} className="size-full object-cover" />
    }
    if (version.url) {
      return <video src={version.url} muted preload="metadata" className="size-full object-cover" />
    }
    return <VideoIcon className="size-3 text-foreground/40" />
  }
  const src = version.thumbnailUrl ?? version.url
  if (!src) return <ImageIcon className="size-3 text-foreground/30" />
  return (
    <Image
      src={src}
      alt={asset.name}
      width={sizes}
      height={sizes}
      className="size-full object-cover"
    />
  )
}

export function HomeRecentAssetThumb({
  asset,
  size = 24,
  onOpenLightbox,
  className
}: HomeRecentAssetThumbProps) {
  const [playing, setPlaying] = useState(false)
  const audioRef = useRef<HTMLAudioElement>(null)
  const version = asset.currentVersion
  const isAudio = asset.mediaType === "AUDIO" || asset.mediaType === "MUSIC"
  const isVideo = asset.mediaType === "VIDEO"

  function handleClick() {
    if (isAudio) {
      const audio = audioRef.current
      if (!audio) return
      if (playing) audio.pause()
      else void audio.play()
      return
    }
    if (version.url) onOpenLightbox(version.url)
  }

  return (
    <div
      className={cn("group/thumb relative shrink-0", className)}
      style={{ width: size, height: size }}
    >
      <button
        type="button"
        aria-label={isAudio ? `${playing ? "暂停" : "播放"}${asset.name}` : `预览${asset.name}`}
        onClick={handleClick}
        className={cn(
          "flex size-full items-center justify-center overflow-hidden rounded border border-foreground/15 outline-none transition-colors hover:border-primary/70 focus-visible:ring-2 focus-visible:ring-primary",
          isAudio
            ? "bg-gradient-to-br from-violet-500/30 to-fuchsia-500/30"
            : "bg-foreground/[0.06]"
        )}
      >
        {isAudio ? (
          playing ? (
            <Pause className="size-3 text-foreground/80" />
          ) : (
            <Music className="size-3 text-foreground/60" />
          )
        ) : (
          renderVisual(asset, size)
        )}
      </button>

      {/* 悬停放大预览 */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute bottom-full left-1/2 z-30 mb-2 flex size-24 -translate-x-1/2 scale-90 flex-col overflow-hidden rounded-lg border border-foreground/15 bg-background/90 opacity-0 shadow-lg backdrop-blur transition duration-150 group-focus-within/thumb:scale-100 group-focus-within/thumb:opacity-100 group-hover/thumb:scale-100 group-hover/thumb:opacity-100"
      >
        {isAudio ? (
          <div className="flex flex-1 flex-col items-center justify-center gap-1 bg-gradient-to-br from-violet-500/20 to-fuchsia-500/20 p-2">
            <Music className="size-6 text-foreground/60" />
            <span className="line-clamp-2 text-center text-[10px] text-foreground/80">
              {asset.name}
            </span>
          </div>
        ) : (
          <div className="relative flex flex-1 items-center justify-center">
            {renderVisual(asset, 96)}
            {isVideo ? (
              <span className="absolute flex size-6 items-center justify-center rounded-full bg-black/45 text-white">
                <Play className="size-3 translate-x-px" />
              </span>
            ) : null}
          </div>
        )}
      </div>

      {isAudio ? (
        // biome-ignore lint/a11y/useMediaCaption: 生成音频无字幕轨
        <audio
          ref={audioRef}
          src={version.url}
          onPlay={() => setPlaying(true)}
          onPause={() => setPlaying(false)}
          onEnded={() => setPlaying(false)}
          className="hidden"
        />
      ) : null}
    </div>
  )
}
