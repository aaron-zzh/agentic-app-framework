/**
 * /studio/assets/history——生成历史 + 失败重试
 * @author AaronZZH & Kiro
 */

"use client"

import { Image as ImageIcon, Video } from "lucide-react"
import { GlassCard, NeonChip } from "@/components/studio"
import { Skeleton } from "@/components/ui/skeleton"
import { useMediaAssets } from "@/lib/queries/use-media-assets"

export default function StudioAssetsHistoryPage() {
  const { data: page, isLoading } = useMediaAssets({ size: 40 })
  const items = page?.list ?? []

  return (
    <div className="mx-auto max-w-5xl space-y-4 p-6">
      <header>
        <h1 className="font-semibold text-xl">生成历史</h1>
        <p className="mt-1 text-muted-foreground text-sm">所有生成记录，失败任务可重试</p>
      </header>

      {isLoading ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="aspect-square rounded-xl" />
          ))}
        </div>
      ) : items.length === 0 ? (
        <div className="py-20 text-center text-muted-foreground text-sm">暂无生成记录</div>
      ) : (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
          {items.map((item) => (
            <GlassCard key={item.id} glow="none" className="overflow-hidden">
              <div className="aspect-square bg-foreground/[0.04]">
                {item.thumbnailUrl ? (
                  // biome-ignore lint/performance/noImgElement: 缩略图
                  <img src={item.thumbnailUrl} alt={item.name} className="size-full object-cover" />
                ) : (
                  <div className="flex size-full items-center justify-center text-muted-foreground/30">
                    {item.type === "VIDEO" ? (
                      <Video className="size-8" />
                    ) : (
                      <ImageIcon className="size-8" />
                    )}
                  </div>
                )}
              </div>
              <div className="p-2">
                <p className="truncate text-xs">{item.name}</p>
                <NeonChip tone="emerald" size="sm" className="mt-1">
                  完成
                </NeonChip>
              </div>
            </GlassCard>
          ))}
        </div>
      )}
    </div>
  )
}
