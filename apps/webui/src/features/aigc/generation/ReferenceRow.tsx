/**
 * 参考引用行——以自然语言句子形式展示引用素材
 * @author AaronZZH & Kiro
 */

"use client"

import { X } from "lucide-react"
import { useAigcStore } from "../store"
import type { MediaAssetVO } from "../types"

/** 素材 badge */
function AssetBadge({ asset, onRemove }: { asset: MediaAssetVO; onRemove: (id: number) => void }) {
  return (
    <span className="inline-flex shrink-0 cursor-default items-center gap-1 rounded-md bg-muted px-1.5 py-0.5 align-middle text-foreground text-sm">
      {/* biome-ignore lint/performance/noImgElement: 动态素材缩略图 */}
      <img
        src={asset.thumbnailUrl ?? asset.url}
        alt={asset.name}
        className="size-5 rounded-sm object-cover"
      />
      <span className="max-w-[120px] truncate text-xs">{asset.name}</span>
      <button
        type="button"
        onClick={() => onRemove(asset.id)}
        className="ml-0.5 rounded-full p-0.5 hover:bg-muted-foreground/20"
      >
        <X className="size-3" />
      </button>
    </span>
  )
}

export function ReferenceRow() {
  const referenceAssets = useAigcStore((s) => s.referenceAssets)
  const removeReferenceAsset = useAigcStore((s) => s.removeReferenceAsset)

  if (referenceAssets.length === 0) return null

  const getDesc = (asset: MediaAssetVO, idx: number) => {
    try {
      const params = asset.generationParams ? JSON.parse(asset.generationParams) : null
      if (params?.referenceDesc) return params.referenceDesc
    } catch {
      /* empty */
    }
    return idx === 0 ? "中的人物衣着发型及场景，调整" : "中的人物"
  }

  return (
    <div className="flex flex-wrap items-baseline gap-x-1.5 gap-y-1 text-muted-foreground text-sm leading-loose">
      <span className="shrink-0">参考</span>
      {referenceAssets.map((asset, idx) => (
        <span key={asset.id} className="inline-flex flex-wrap items-baseline gap-x-1.5">
          <AssetBadge asset={asset} onRemove={removeReferenceAsset} />
          <span className="text-foreground/70 text-sm">{getDesc(asset, idx)}</span>
        </span>
      ))}
    </div>
  )
}
