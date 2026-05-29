/**
 * 参考引用行——展示已引用素材的缩略图 + 名称 badge + 描述
 * @author AaronZZH & Kiro
 */

"use client"

import { X } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { useAigcStore } from "./store"
import type { MediaAsset } from "./types"

export function ReferenceRow() {
  const referenceAssets = useAigcStore((s) => s.referenceAssets)
  const removeReferenceAsset = useAigcStore((s) => s.removeReferenceAsset)

  if (referenceAssets.length === 0) return null

  return (
    <div className="flex flex-wrap items-center gap-1.5 text-muted-foreground text-sm">
      <span>参考</span>
      {referenceAssets.map((asset) => (
        <ReferenceTag key={asset.id} asset={asset} onRemove={removeReferenceAsset} />
      ))}
    </div>
  )
}

function ReferenceTag({ asset, onRemove }: { asset: MediaAsset; onRemove: (id: string) => void }) {
  return (
    <Badge variant="secondary" className="gap-1 py-0.5 pr-1.5 pl-0.5">
      {/* biome-ignore lint/performance/noImgElement: 动态素材缩略图 */}
      <img src={asset.thumbnail} alt={asset.name} className="size-4 rounded-sm object-cover" />
      <span className="max-w-[80px] truncate text-xs">@{asset.name}</span>
      <button
        type="button"
        onClick={() => onRemove(asset.id)}
        className="ml-0.5 rounded-full p-0.5 hover:bg-muted-foreground/20"
      >
        <X className="size-3" />
      </button>
    </Badge>
  )
}
