/**
 * 文件区素材网格——支持 dnd-kit 拖拽到生成面板，接入真实 API
 * @author AaronZZH & Kiro
 */

"use client"

import { useSemanticDraggable } from "@/features/chatter/dnd/useSemanticDraggable"
import { Skeleton } from "@/components/ui/skeleton"
import { useMediaAssets } from "@/lib/queries/use-media-assets"
import { cn } from "@/lib/utils/index"
import { useAigcStore } from "./store"
import type { MediaAsset } from "./types"

function DraggableAssetCard({ asset }: { asset: MediaAsset }) {
  const { ref, listeners, attributes, isDragging } = useSemanticDraggable({
    id: `asset-${asset.id}`,
    item: {
      type: "image",
      id: asset.id,
      title: asset.name,
      url: asset.url,
      thumbnailUrl: asset.thumbnail
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
        <img src={asset.thumbnail} alt={asset.name} className="size-full object-cover" />
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

export function FileGrid() {
  const { data, isLoading } = useMediaAssets({ page: 0, pageSize: 20 })

  if (isLoading) {
    return <FileGridSkeleton />
  }

  if (!data?.list || data.list.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center gap-2 p-8 text-center">
        <p className="text-muted-foreground text-sm">暂无素材</p>
        <p className="text-muted-foreground text-xs">生成或上传素材后将在此展示</p>
      </div>
    )
  }

  return (
    <div className="grid grid-cols-3 gap-2 p-3">
      {data.list.map((asset) => (
        <DraggableAssetCard key={asset.id} asset={asset} />
      ))}
    </div>
  )
}
