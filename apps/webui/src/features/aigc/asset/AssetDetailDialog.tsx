/**
 * 素材详情弹窗——大图预览 + 元数据 + 变体列表
 * @author AaronZZH & Kiro
 */

"use client"

import { Badge } from "@/components/ui/badge"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { useMediaAssetDetail, useMediaAssetVariants } from "@/lib/queries/use-media-assets"

function safeJsonParse<T>(str: string): T | null {
  try {
    return JSON.parse(str) as T
  } catch {
    return null
  }
}

interface AssetDetailDialogProps {
  assetId: number | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function AssetDetailDialog({ assetId, open, onOpenChange }: AssetDetailDialogProps) {
  const { data: asset } = useMediaAssetDetail(assetId)
  const { data: variants } = useMediaAssetVariants(assetId)

  if (!asset) return null

  const params = asset.generationParams
    ? safeJsonParse<{ model?: string; prompt?: string }>(asset.generationParams)
    : null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>{asset.name}</DialogTitle>
        </DialogHeader>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div className="overflow-hidden rounded-lg bg-muted">
            {/* biome-ignore lint/performance/noImgElement: 素材详情大图 */}
            <img src={asset.url} alt={asset.name} className="size-full object-contain" />
          </div>
          <div className="flex flex-col gap-3">
            <div className="grid grid-cols-2 gap-2 text-sm">
              {asset.width && asset.height && (
                <div>
                  <span className="text-muted-foreground">尺寸</span>
                  <p>
                    {asset.width} × {asset.height}
                  </p>
                </div>
              )}
              {params?.model && (
                <div>
                  <span className="text-muted-foreground">模型</span>
                  <p>{params.model}</p>
                </div>
              )}
              <div>
                <span className="text-muted-foreground">创建时间</span>
                <p>{new Date(asset.createTime).toLocaleString()}</p>
              </div>
              {asset.size && (
                <div>
                  <span className="text-muted-foreground">文件大小</span>
                  <p>{(asset.size / 1024 / 1024).toFixed(2)} MB</p>
                </div>
              )}
            </div>
            {params?.prompt && (
              <div>
                <span className="text-muted-foreground text-sm">Prompt</span>
                <p className="mt-1 rounded-md bg-muted p-2 text-xs">{params.prompt}</p>
              </div>
            )}
            {asset.tags && (
              <div className="flex flex-wrap gap-1">
                {asset.tags.split(",").map((tag) => (
                  <Badge key={tag} variant="outline" className="text-xs">
                    {tag.trim()}
                  </Badge>
                ))}
              </div>
            )}
            {variants && variants.length > 0 && (
              <div>
                <span className="text-muted-foreground text-sm">变体 ({variants.length})</span>
                <div className="mt-1 grid grid-cols-4 gap-1">
                  {variants.map((v) => (
                    <div key={v.id} className="overflow-hidden rounded-md bg-muted">
                      {/* biome-ignore lint/performance/noImgElement: 变体缩略图 */}
                      <img
                        src={v.thumbnailUrl ?? v.url}
                        alt={v.name}
                        className="aspect-square size-full object-cover"
                      />
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
