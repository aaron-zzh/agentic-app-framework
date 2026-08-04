/**
 * Studio 首屏「最近生成」素材网格
 *
 * 展示最新 15 条 AI 生成素材，图片/视频缩略图，音频直接播放
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronRight, ImageIcon, Music, Pause, Play, Video as VideoIcon } from "lucide-react"
import Image from "next/image"
import Link from "next/link"
import { useRef, useState } from "react"
import Video from "yet-another-react-lightbox/plugins/video"
import { Lightbox, useLightbox } from "@/components/lightbox"
import { GlassCard } from "@/components/studio"
import { Skeleton } from "@/components/ui/skeleton"
import type { AigcMedia } from "@/features/aigc/types"
import { useMediaList } from "@/lib/api/rest/media"

function AssetThumb({
  asset,
  onOpenLightbox
}: {
  asset: AigcMedia
  onOpenLightbox: (url: string) => void
}) {
  const [playing, setPlaying] = useState(false)
  const audioRef = useRef<HTMLAudioElement>(null)
  const version = asset.currentVersion
  const isAudio = asset.mediaType === "AUDIO" || asset.mediaType === "MUSIC"
  const isVideo = asset.mediaType === "VIDEO"
  const src = version.thumbnailUrl ?? version.url

  if (isAudio) {
    return (
      <button
        type="button"
        className="group/thumb relative flex aspect-square w-full items-center justify-center overflow-hidden rounded-lg border border-foreground/8 bg-gradient-to-br from-violet-500/15 to-fuchsia-500/15"
        onClick={() => {
          const audio = audioRef.current
          if (!audio) return
          playing ? audio.pause() : audio.play()
        }}
      >
        <Music className="size-6 text-muted-foreground/50" />
        <div className="absolute flex size-7 items-center justify-center rounded-full bg-black/30 text-white opacity-0 transition-opacity group-hover/thumb:opacity-100">
          {playing ? <Pause className="size-3.5" /> : <Play className="size-3.5" />}
        </div>
        {/* biome-ignore lint/a11y/useMediaCaption: 生成音频无字幕轨 */}
        <audio
          ref={audioRef}
          src={version.url}
          onPlay={() => setPlaying(true)}
          onPause={() => setPlaying(false)}
          onEnded={() => setPlaying(false)}
          className="hidden"
        />
      </button>
    )
  }

  return (
    <button
      type="button"
      onClick={() => src && onOpenLightbox(version.url)}
      className="group/thumb relative block aspect-square w-full overflow-hidden rounded-lg border border-foreground/8 bg-foreground/[0.04] focus-visible:outline-none"
    >
      {isVideo ? (
        version.thumbnailUrl ? (
          // biome-ignore lint/performance/noImgElement: video 缩略图
          <img
            src={version.thumbnailUrl}
            alt={asset.name}
            className="size-full object-cover transition-transform duration-300 group-hover/thumb:scale-105"
          />
        ) : version.url ? (
          // 无缩略图时用 video 元素静帧预览
          <video
            src={version.url}
            muted
            preload="metadata"
            className="size-full object-cover transition-transform duration-300 group-hover/thumb:scale-105"
          />
        ) : (
          <div className="flex h-full flex-col items-center justify-center gap-1 text-foreground/30">
            <VideoIcon className="size-6" />
          </div>
        )
      ) : src ? (
        <Image
          src={src}
          alt={asset.name}
          width={112}
          height={112}
          className="size-full object-cover transition-transform duration-300 group-hover/thumb:scale-105"
        />
      ) : (
        <div className="flex h-full items-center justify-center text-foreground/20">
          <ImageIcon className="size-6" />
        </div>
      )}
      {isVideo && (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
          <div className="flex size-7 items-center justify-center rounded-full bg-black/40 text-white">
            <Play className="size-3.5 translate-x-0.5" />
          </div>
        </div>
      )}
    </button>
  )
}

export function HomeRecentAssets() {
  const { data, isLoading } = useMediaList({
    pageNo: 1,
    pageSize: 15,
    sortField: "createTime",
    sortOrder: "desc"
  })
  const assets = data?.list ?? []

  const imageSlides = assets
    .filter((media) => media.mediaType === "IMAGE" || media.mediaType === "VIDEO")
    .map((media) =>
      media.mediaType === "VIDEO"
        ? {
            type: "video" as const,
            sources: [
              { src: media.currentVersion.url, type: media.currentVersion.mimeType ?? "video/mp4" }
            ]
          }
        : { src: media.currentVersion.url }
    )

  const { open, index, onOpen, onClose } = useLightbox(imageSlides)

  function handleOpenLightbox(url: string) {
    onOpen(url)
  }

  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="font-semibold text-base">最近生成</h2>
          <p className="pt-1 text-muted-foreground text-xs">AI 生成的图像、视频与音频素材</p>
        </div>
        <Link
          href="/studio/assets/materials"
          className="flex items-center gap-0.5 text-muted-foreground text-sm hover:text-foreground"
        >
          更多
          <ChevronRight className="size-3.5" />
        </Link>
      </div>

      {isLoading ? (
        <div className="flex gap-3 overflow-x-auto pb-1">
          {Array.from({ length: 15 }).map((_, i) => (
            <Skeleton key={i} className="size-24 shrink-0 rounded-lg sm:size-28" />
          ))}
        </div>
      ) : assets.length === 0 ? (
        <GlassCard className="flex min-h-[120px] items-center justify-center text-muted-foreground text-sm">
          还没有生成记录，去创作一张吧 ✨
        </GlassCard>
      ) : (
        <div className="flex gap-3 overflow-x-auto pb-1">
          {assets.map((asset) => (
            <div key={asset.id} className="w-24 shrink-0 sm:w-28">
              <AssetThumb asset={asset} onOpenLightbox={handleOpenLightbox} />
            </div>
          ))}
        </div>
      )}

      <Lightbox open={open} index={index} slides={imageSlides} close={onClose} plugins={[Video]} />
    </section>
  )
}
