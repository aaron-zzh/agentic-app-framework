/**
 * 故事板面板——左栏，展示关键元素列表，数据来自 store
 * @author AaronZZH & Kiro
 */

"use client"

import { useDroppable } from "@dnd-kit/core"
import { MoreHorizontal, Plus } from "lucide-react"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { cn } from "@/lib/utils/index"
import { useAigcStore } from "./store"
import type { MediaAssetVO } from "./types"

function ElementCard({ asset }: { asset: MediaAssetVO }) {
  const removeStoryboardAsset = useAigcStore((s) => s.removeStoryboardAsset)
  const addReferenceAsset = useAigcStore((s) => s.addReferenceAsset)

  return (
    <div className="group flex flex-col gap-2 rounded-lg border border-border/50 bg-card/50 p-3 transition-colors hover:bg-accent/50">
      <div className="font-medium text-foreground text-sm">{asset.name}</div>
      {/* 描述文字区（generationParams 中存 description，降级显示空） */}
      {(() => {
        try {
          const params = asset.generationParams ? JSON.parse(asset.generationParams) : null
          return params?.description ? (
            <p className="line-clamp-3 text-muted-foreground text-xs">{params.description}</p>
          ) : null
        } catch {
          return null
        }
      })()}
      {/* 底部：缩略图 badge + 添加到参考按钮 */}
      <div className="flex items-center gap-2">
        <div className="flex items-center gap-1 rounded-md border border-border/50 bg-muted/50 px-1.5 py-1">
          {/* biome-ignore lint/performance/noImgElement: 动态缩略图 */}
          <img
            src={asset.thumbnailUrl ?? asset.url}
            alt={asset.name}
            className="size-10 rounded object-cover"
          />
          <span className="max-w-[60px] truncate text-muted-foreground text-xs">
            图片 {asset.name.slice(0, 8)}...
          </span>
        </div>
        <Button
          variant="ghost"
          size="sm"
          className="size-7 rounded-full p-0"
          title="添加到参考"
          onClick={() => addReferenceAsset(asset)}
        >
          <Plus className="size-3.5" />
        </Button>
      </div>
    </div>
  )
}

export function StoryboardPanel() {
  const storyboardAssets = useAigcStore((s) => s.storyboardAssets)
  const { isOver, setNodeRef } = useDroppable({ id: "storyboard-drop-zone" })

  return (
    <div className="flex h-full flex-col">
      {/* 标题栏 */}
      <div className="flex items-center justify-between border-border/50 border-b px-4 py-3">
        <h2 className="font-semibold text-foreground text-sm">故事板</h2>
        <Button variant="ghost" size="sm" className="size-7 p-0">
          <MoreHorizontal className="size-4" />
        </Button>
      </div>

      {/* 关键元素列表（可接受拖放） */}
      <ScrollArea className="flex-1">
        <div
          ref={setNodeRef}
          className={cn(
            "flex min-h-full flex-col gap-2 p-3 transition-colors",
            isOver && "bg-primary/5"
          )}
        >
          <span className="mb-1 block font-medium text-muted-foreground text-xs">— 关键元素 —</span>
          {storyboardAssets.length > 0 ? (
            storyboardAssets.map((asset) => <ElementCard key={asset.id} asset={asset} />)
          ) : (
            <p className="py-4 text-center text-muted-foreground text-xs">
              从文件区拖入素材添加元素
            </p>
          )}
        </div>
      </ScrollArea>
    </div>
  )
}
