/**
 * 素材卡片——缩略图 + hover 操作栏 + 底部信息
 * @author AaronZZH & Kiro
 */

"use client"

import { Download, RefreshCw, Trash2 } from "lucide-react"
import Link from "next/link"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Model3DPreview } from "@/features/aigc/three/Model3DPreview"
import type { MediaAssetVO } from "../types"

function safeJsonParse<T>(str: string): T | null {
  try {
    return JSON.parse(str) as T
  } catch {
    return null
  }
}

interface AssetCardProps {
  asset: MediaAssetVO
  onClick: () => void
  onDelete: () => void
  onRegenerate: () => void
}

export function AssetCard({ asset, onClick, onDelete, onRegenerate }: AssetCardProps) {
  const is3D = asset.type === "MODEL_3D"

  return (
    <Card
      className="group cursor-pointer overflow-hidden shadow-sm ring-0 transition-all hover:shadow-lg"
      onClick={is3D ? undefined : onClick}
    >
      <div className="relative aspect-square overflow-hidden bg-muted">
        {is3D ? (
          <Model3DPreview url={asset.url} className="size-full" />
        ) : (
          // biome-ignore lint/performance/noImgElement: 动态素材缩略图
          <img
            src={asset.thumbnailUrl ?? asset.url}
            alt={asset.name}
            className="size-full object-cover transition-transform group-hover:scale-105"
          />
        )}
        <div className="absolute inset-x-0 top-0 flex justify-end gap-1 p-2 opacity-0 transition-opacity group-hover:opacity-100">
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
              onRegenerate()
            }}
          >
            <RefreshCw className="size-3.5" />
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
      </div>
      <div className="p-2">
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
          {asset.generationParams
            ? (safeJsonParse<{ model?: string }>(asset.generationParams)?.model ?? "")
            : ""}
          {asset.generationParams ? " · " : ""}
          {new Date(asset.createTime).toLocaleDateString()}
        </p>
      </div>
    </Card>
  )
}
