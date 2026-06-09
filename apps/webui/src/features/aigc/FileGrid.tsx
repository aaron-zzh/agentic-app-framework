/**
 * 文件区素材网格——支持 dnd-kit 拖拽到生成面板，接入真实 API
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect, useMemo } from "react"
import { Skeleton } from "@/components/ui/skeleton"
import { useSemanticDraggable } from "@/features/chatter/dnd/useSemanticDraggable"
import { useMediaAssets } from "@/lib/queries/use-media-assets"
import { cn } from "@/lib/utils/index"
import { useAigcStore } from "./store"
import type { MediaAssetVO } from "./types"

function DraggableAssetCard({ asset }: { asset: MediaAssetVO }) {
  const { ref, listeners, attributes, isDragging } = useSemanticDraggable({
    id: `asset-${asset.id}`,
    item: {
      type: "image",
      id: String(asset.id),
      title: asset.name,
      url: asset.url,
      thumbnailUrl: asset.thumbnailUrl ?? asset.url
    }
  })
  const setPreviewAsset = useAigcStore((s) => s.setPreviewAsset)

  return (
    // biome-ignore lint/a11y/noStaticElementInteractions: dnd-kit 通过 attributes 注入 role
    <div
      ref={ref}
      {...listeners}
      {...attributes}
      className={cn(
        "group cursor-grab overflow-hidden rounded-lg border border-border/50 bg-card/50 transition-all hover:border-primary/50",
        isDragging && "opacity-50 ring-2 ring-primary"
      )}
      onClick={() => setPreviewAsset(asset)}
      onKeyDown={(e) => {
        if (e.key === "Enter") setPreviewAsset(asset)
      }}
    >
      <div className="aspect-square bg-muted">
        {/* biome-ignore lint/performance/noImgElement: 动态素材缩略图 */}
        <img
          src={asset.thumbnailUrl ?? asset.url}
          alt={asset.name}
          className="size-full object-cover"
        />
      </div>
      <div className="px-2 py-1.5">
        <span className="block truncate text-foreground text-xs">{asset.name}</span>
      </div>
    </div>
  )
}

/** 加载骨架屏 */
function FileGridSkeleton() {
  return (
    <div className="grid grid-cols-3 gap-2 p-3">
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={`skeleton-${i}`} className="overflow-hidden rounded-lg border border-border/50">
          <Skeleton className="aspect-square w-full" />
          <div className="px-2 py-1.5">
            <Skeleton className="h-3 w-3/4" />
          </div>
        </div>
      ))}
    </div>
  )
}

interface FileGridProps {
  filterUnassigned?: boolean
}

const EMPTY_LIST: MediaAssetVO[] = []
const QUERY_PARAMS = { page: 0, pageSize: 20 }

export function FileGrid({ filterUnassigned = false }: FileGridProps) {
  const { data, isLoading } = useMediaAssets(QUERY_PARAMS)
  const storyboardAssets = useAigcStore((s) => s.storyboardAssets)
  const setPreviewList = useAigcStore((s) => s.setPreviewList)
  const pendingTasks = useAigcStore((s) => s.pendingTasks)

  const assignedIds = useMemo(() => new Set(storyboardAssets.map((a) => a.id)), [storyboardAssets])
  const list = data?.list ?? EMPTY_LIST
  const filtered = useMemo(
    () => (filterUnassigned ? list.filter((a) => !assignedIds.has(a.id)) : list),
    [filterUnassigned, list, assignedIds]
  )

  // 同步 previewList，支持导航箭头
  useEffect(() => {
    setPreviewList(filtered)
  }, [filtered, setPreviewList])

  if (isLoading) {
    return <FileGridSkeleton />
  }

  if (filtered.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center gap-2 p-8 text-center">
        <p className="text-muted-foreground text-sm">暂无素材</p>
        <p className="text-muted-foreground text-xs">生成或上传素材后将在此展示</p>
      </div>
    )
  }

  return (
    <div className="grid grid-cols-3 gap-2 p-3">
      {/* 生成中的占位卡片 */}
      {pendingTasks.map((task) => (
        <div
          key={`pending-${task.id}`}
          className="flex aspect-square animate-pulse flex-col items-center justify-center gap-1 rounded-lg border border-border/50 bg-muted/30"
        >
          <div className="size-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          <span className="line-clamp-1 px-1 text-center text-[10px] text-muted-foreground">
            {task.prompt.slice(0, 12)}…
          </span>
        </div>
      ))}
      {filtered.map((asset) => (
        <DraggableAssetCard key={asset.id} asset={asset} />
      ))}
    </div>
  )
}
