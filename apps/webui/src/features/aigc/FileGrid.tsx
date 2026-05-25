/**
 * 文件区素材网格——支持 dnd-kit 拖拽到生成面板
 * @author AaronZZH & Kiro
 */

"use client"

import { useDraggable } from "@dnd-kit/core"
import { cn } from "@/lib/utils/index"
import type { MediaAsset } from "./types"
import { useAigcStore } from "./store"

/** 示例素材数据 */
const MOCK_ASSETS: MediaAsset[] = [
  { id: "a1", name: "森林场景_v1", url: "", thumbnail: "/placeholder.svg", width: 1152, height: 2048, model: "GPT Image 2" },
  { id: "a2", name: "角色立绘_01", url: "", thumbnail: "/placeholder.svg", width: 1024, height: 1024, model: "GPT Image 2" },
  { id: "a3", name: "水晶龙_概念", url: "", thumbnail: "/placeholder.svg", width: 2048, height: 1152, model: "GPT Image 2" },
  { id: "a4", name: "魔法阵_特效", url: "", thumbnail: "/placeholder.svg", width: 1024, height: 1024, model: "DALL·E 3" },
  { id: "a5", name: "背景_星空", url: "", thumbnail: "/placeholder.svg", width: 1920, height: 1080, model: "GPT Image 2" },
  { id: "a6", name: "道具_法杖", url: "", thumbnail: "/placeholder.svg", width: 512, height: 512, model: "GPT Image 2" },
]

function DraggableAssetCard({ asset }: { asset: MediaAsset }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: asset.id,
    data: asset,
  })
  const setPreviewAsset = useAigcStore((s) => s.setPreviewAsset)

  return (
    <div
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      className={cn(
        "group cursor-grab overflow-hidden rounded-lg border border-border/50 bg-card/50 transition-all hover:border-primary/50",
        isDragging && "opacity-50 ring-2 ring-primary"
      )}
      onClick={() => setPreviewAsset(asset)}
      onKeyDown={(e) => { if (e.key === "Enter") setPreviewAsset(asset) }}
    >
      <div className="aspect-square bg-muted">
        {/* biome-ignore lint/performance/noImgElement: 动态素材缩略图 */}
        <img src={asset.thumbnail} alt={asset.name} className="size-full object-cover" />
      </div>
      <div className="px-2 py-1.5">
        <span className="block truncate text-xs text-foreground">{asset.name}</span>
      </div>
    </div>
  )
}

export function FileGrid() {
  return (
    <div className="grid grid-cols-3 gap-2 p-3">
      {MOCK_ASSETS.map((asset) => (
        <DraggableAssetCard key={asset.id} asset={asset} />
      ))}
    </div>
  )
}
