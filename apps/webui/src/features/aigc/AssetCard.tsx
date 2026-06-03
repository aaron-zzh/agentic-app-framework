/**
 * 素材卡片——缩略图 + hover 操作栏 + 底部信息
 * @author AaronZZH & Kiro
 */

"use client"

import { Download, RefreshCw, Trash2 } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import type { MediaAssetVO } from "./types"

/** 安全 JSON 解析，失败返回 null */
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
  return (
    <Card
      className="group cursor-pointer overflow-hidden border-border/50 transition-all hover:border-border hover:shadow-md"
      onClick={onClick}
    >
      {/* 缩略图 */}
      <div className="relative aspect-square overflow-hidden bg-muted">
        {/* biome-ignore lint/performance/noImgElement: 动态素材缩略图 */}
        <img
          src={asset.thumbnailUrl ?? asset.url}
          alt={asset.name}
          className="size-full object-cover transition-transform group-hover:scale-105"
        />

        {/* hover 操作栏 */}
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

        {/* 类型标签 */}
        {asset.type !== "IMAGE" && (
          <Badge variant="secondary" className="absolute bottom-2 left-2 text-[10px]">
            {asset.type === "VIDEO" ? "视频" : asset.type === "MODEL_3D" ? "3D" : "音频"}
          </Badge>
        )}
      </div>

      {/* 底部信息 */}
      <div className="p-2">
        <p className="truncate font-medium text-sm">{asset.name}</p>
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
