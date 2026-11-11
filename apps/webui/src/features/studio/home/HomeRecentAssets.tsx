/**
 * Studio 首页「最近生成」（高 60px，位于创作输入下方）：一行垂直居中，左侧标题，中间最多 15 个 36px 缩略图（或加载、空、失败提示），右侧"查看全部"进入素材库。
 *
 * 悬停缩略图在上方放大预览，点击图片、视频在灯箱中预览，点击音频直接播放。
 * 数据来自 useMediaList，生成完成的实时事件会刷新它。
 * 缩略图区不裁剪纵向溢出（放大预览向上浮出），窄屏下多余的缩略图直接隐藏。
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronRight } from "lucide-react"
import Link from "next/link"
import Video from "yet-another-react-lightbox/plugins/video"
import { Lightbox, useLightbox } from "@/components/lightbox"
import { useMediaList } from "@/lib/api/rest/media"
import { cn } from "@/lib/utils/cn"
import { HomeRecentAssetThumb } from "./HomeRecentAssetThumb"

/** 最多展示条数 */
const RECENT_LIMIT = 15

/** 缩略图边长（px），与骨架 size-9 一致 */
const THUMB_SIZE = 36

/** 窄屏下只显示前 8 个，避免溢出（不能用横向滚动，否则会裁掉向上浮出的预览） */
const NARROW_HIDDEN_CLASS = "max-md:nth-[n+9]:hidden"

interface HomeRecentAssetsProps {
  className?: string
}

export function HomeRecentAssets({ className }: HomeRecentAssetsProps) {
  const { data, isLoading, isError, refetch } = useMediaList({
    pageNo: 1,
    pageSize: RECENT_LIMIT,
    sortField: "createTime",
    sortOrder: "desc"
  })
  const assets = data?.list ?? []

  const slides = assets
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

  const { open, index, onOpen, onClose } = useLightbox(slides)

  return (
    <section
      aria-label="最近生成"
      className={cn("flex h-[60px] min-w-0 items-center gap-3 px-4", className)}
    >
      <h2 className="shrink-0 font-medium text-muted-foreground text-xs tracking-wide">最近生成</h2>
      <div className="flex min-w-0 flex-1 items-center gap-1.5">
        {isLoading ? (
          Array.from({ length: RECENT_LIMIT }, (_, i) => (
            <span
              key={`recent-asset-${i}`}
              className={cn(
                "size-9 shrink-0 animate-pulse rounded bg-foreground/10",
                NARROW_HIDDEN_CLASS
              )}
            />
          ))
        ) : isError ? (
          <span className="truncate text-muted-foreground text-xs">
            加载失败，
            <button
              type="button"
              className="text-foreground underline-offset-2 hover:underline"
              onClick={() => void refetch()}
            >
              重试
            </button>
          </span>
        ) : assets.length === 0 ? (
          <span className="truncate text-muted-foreground text-xs">
            还没有生成记录，在创作区生成第一个作品吧
          </span>
        ) : (
          assets.map((asset) => (
            <HomeRecentAssetThumb
              key={asset.id}
              asset={asset}
              size={THUMB_SIZE}
              onOpenLightbox={onOpen}
              className={NARROW_HIDDEN_CLASS}
            />
          ))
        )}
      </div>
      <Link
        href="/studio/assets/materials"
        className="flex shrink-0 items-center gap-0.5 text-muted-foreground text-xs hover:text-foreground"
      >
        查看全部
        <ChevronRight className="size-3.5" />
      </Link>

      <Lightbox open={open} index={index} slides={slides} close={onClose} plugins={[Video]} />
    </section>
  )
}
