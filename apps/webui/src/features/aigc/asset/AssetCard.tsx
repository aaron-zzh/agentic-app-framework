/**
 * 素材卡片——缩略图 + hover 操作栏 + 底部信息
 * @author AaronZZH & Kiro
 */

"use client"

import { Download, FolderOpen, Music, Pause, Play, Sparkles, Trash2 } from "lucide-react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useRef, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger
} from "@/components/ui/context-menu"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Model3DPreview } from "@/features/aigc/three/Model3DPreview"
import { useMediaCategories, useUpdateMediaAsset } from "@/lib/queries/use-media-assets"
import type { MediaAssetType, MediaAssetVO, MediaCategoryVO } from "../types"

/** 按 type 返回对应生成界面路径 */
function getGenerationPath(type: MediaAssetType): string {
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

/** 扁平化分类树 */
function flattenCategories(
  cats: MediaCategoryVO[],
  depth = 0
): Array<MediaCategoryVO & { depth: number }> {
  return cats.flatMap((c) => [{ ...c, depth }, ...flattenCategories(c.children, depth + 1)])
}

interface AssetCardProps {
  asset: MediaAssetVO
  onClick: () => void
  onDelete: () => void
  onPreview?: () => void
  onRegenerate?: () => void
}

export function AssetCard({ asset, onClick, onDelete, onPreview }: AssetCardProps) {
  const is3D = asset.type === "MODEL_3D"
  const isAudio = asset.type === "AUDIO"
  const router = useRouter()
  const { data: categories } = useMediaCategories()
  const { mutate: updateAsset } = useUpdateMediaAsset()
  const [audioPlaying, setAudioPlaying] = useState(false)
  const audioRef = useRef<HTMLAudioElement>(null)

  const flatCats = categories ? flattenCategories(categories) : []

  function handleAudioToggle(e: React.MouseEvent) {
    e.stopPropagation()
    const audio = audioRef.current
    if (!audio) return
    if (audioPlaying) {
      audio.pause()
    } else {
      audio.play()
    }
  }

  const thumbnailOverlay = (
    <>
      <div className="absolute inset-x-0 top-0 flex justify-end gap-1 p-2 opacity-0 transition-opacity group-hover:opacity-100">
        {/* 设置分类 */}
        <Popover>
          <PopoverTrigger
            render={
              <button
                type="button"
                className={buttonVariants({
                  variant: "secondary",
                  size: "icon",
                  className: "size-7 bg-black/50 text-white hover:bg-black/70"
                })}
                onClick={(e: React.MouseEvent) => e.stopPropagation()}
              />
            }
          >
            <FolderOpen className="size-3.5" />
          </PopoverTrigger>
          <PopoverContent className="w-44 p-1" onClick={(e) => e.stopPropagation()}>
            <ScrollArea className="max-h-52">
              <button
                type="button"
                className="w-full rounded px-2 py-1.5 text-left text-xs hover:bg-muted"
                onClick={() => updateAsset({ id: asset.id, categoryId: null })}
              >
                <span className="text-muted-foreground">无分类</span>
              </button>
              {flatCats.map((cat) => (
                <button
                  key={cat.id}
                  type="button"
                  className={`w-full rounded px-2 py-1.5 text-left text-xs hover:bg-muted ${asset.categoryId === cat.id ? "font-medium text-primary" : ""}`}
                  style={{ paddingLeft: `${8 + cat.depth * 12}px` }}
                  onClick={() => updateAsset({ id: asset.id, categoryId: cat.id })}
                >
                  {cat.name}
                </button>
              ))}
            </ScrollArea>
          </PopoverContent>
        </Popover>
        <Button
          variant="secondary"
          size="icon"
          className="size-7 bg-black/50 text-white hover:bg-black/70"
          onClick={(e) => {
            e.stopPropagation()
            window.open(asset.url, "_blank")
          }}
        >
          <Download className="size-3.5" />
        </Button>
        <Button
          variant="secondary"
          size="icon"
          className="size-7 bg-black/50 text-white hover:bg-black/70"
          onClick={(e) => {
            e.stopPropagation()
            onDelete()
          }}
        >
          <Trash2 className="size-3.5" />
        </Button>
      </div>
      {asset.type !== "IMAGE" && (
        <Badge
          variant="secondary"
          className="pointer-events-none absolute bottom-2 left-2 text-[10px]"
        >
          {asset.type === "VIDEO" ? "视频" : is3D ? "3D" : "音频"}
        </Badge>
      )}
    </>
  )

  const card = (
    <Card
      className="group overflow-hidden shadow-sm ring-0 transition-all hover:shadow-lg"
      onClick={is3D ? undefined : undefined}
    >
      {is3D || isAudio ? (
        <div className="relative -mx-0 -mt-4 aspect-square overflow-hidden bg-muted">
          {is3D ? (
            <Model3DPreview url={asset.url} className="size-full" />
          ) : (
            <button
              type="button"
              className="flex size-full flex-col items-center justify-center gap-2 bg-gradient-to-br from-violet-500/15 to-fuchsia-500/15"
              onClick={handleAudioToggle}
            >
              <Music className="size-8 text-muted-foreground/60" />
              <div className="flex size-8 items-center justify-center rounded-full bg-black/30 text-white">
                {audioPlaying ? <Pause className="size-4" /> : <Play className="size-4" />}
              </div>
              {/* biome-ignore lint/a11y/useMediaCaption: 生成音频无字幕轨 */}
              <audio
                ref={audioRef}
                src={asset.url}
                onPlay={() => setAudioPlaying(true)}
                onPause={() => setAudioPlaying(false)}
                onEnded={() => setAudioPlaying(false)}
                className="hidden"
              />
            </button>
          )}
          {thumbnailOverlay}
        </div>
      ) : (
        // 注意：thumbnailOverlay 内含 Popover/Button 等 base-ui trigger（本身渲染为 <button>），
        // 外层不能再用 <button>，否则 button 嵌套 button 触发 hydration error。
        // 改用 div + role="button" + 键盘事件，保留 a11y。
        // biome-ignore lint/a11y/useSemanticElements: 内部含 thumbnailOverlay（Popover/Button），不能嵌套 <button>
        <div
          role="button"
          tabIndex={0}
          className="relative -mx-0 -mt-4 block aspect-square w-full cursor-pointer overflow-hidden bg-muted"
          onClick={onClick}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === " ") {
              e.preventDefault()
              onClick()
            }
          }}
        >
          {asset.type === "VIDEO" ? (
            <video
              src={asset.url}
              className="size-full object-cover"
              muted
              playsInline
              preload="metadata"
              onMouseEnter={(e) => e.currentTarget.play()}
              onMouseLeave={(e) => {
                e.currentTarget.pause()
                e.currentTarget.currentTime = 0
              }}
            />
          ) : (
            // biome-ignore lint/performance/noImgElement: 动态素材缩略图
            <img
              src={asset.thumbnailUrl ?? asset.url}
              alt={asset.name}
              className="size-full object-cover transition-transform group-hover:scale-105"
            />
          )}
          {thumbnailOverlay}
        </div>
      )}
      {/* 底部信息区：因 is3D 分支内嵌 <Link>（即 <a>），不能用 <button> 外壳；统一用 div + role="button" */}
      {/* biome-ignore lint/a11y/useSemanticElements: is3D 分支内含 <Link>（<a>），button 内嵌 a 违反 HTML 规范 */}
      <div
        role="button"
        tabIndex={0}
        className="w-full cursor-pointer p-2 text-left hover:bg-muted/50"
        onClick={(e) => {
          e.stopPropagation()
          onPreview?.()
        }}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault()
            onPreview?.()
          }
        }}
      >
        {is3D ? (
          <Link
            href={`/aigc/assets/${asset.id}`}
            className="block truncate font-medium text-sm hover:text-primary hover:underline"
            onClick={(e) => e.stopPropagation()}
          >
            {asset.name}
          </Link>
        ) : (
          <p className="truncate font-medium text-sm">{asset.name}</p>
        )}
        <p className="text-[11px] text-muted-foreground">
          {asset.modelName || ""} {new Date(asset.createTime).toLocaleDateString()}
        </p>
      </div>
    </Card>
  )

  if (!asset.aiGenerated) return card

  return (
    <ContextMenu>
      <ContextMenuTrigger render={<div />}>{card}</ContextMenuTrigger>
      <ContextMenuContent>
        <ContextMenuItem onClick={() => router.push(getGenerationPath(asset.type))}>
          <Sparkles className="mr-2 size-3.5" />
          跳转到生成界面
        </ContextMenuItem>
      </ContextMenuContent>
    </ContextMenu>
  )
}
