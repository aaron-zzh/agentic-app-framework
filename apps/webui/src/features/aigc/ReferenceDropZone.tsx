/**
 * ReferenceDropZone——生成面板参考素材拖放区
 * @author AaronZZH & Kiro
 */

"use client"

import { useDroppable } from "@dnd-kit/core"
import { Plus, Upload, X } from "lucide-react"
import { cn } from "@/lib/utils/index"
import { useAigcStore } from "./store"

export function ReferenceDropZone() {
  const { isOver, setNodeRef } = useDroppable({ id: "generation-drop-zone" })
  const referenceAssets = useAigcStore((s) => s.referenceAssets)
  const removeReferenceAsset = useAigcStore((s) => s.removeReferenceAsset)

  return (
    <div
      ref={setNodeRef}
      className={cn(
        "flex min-h-[96px] flex-col gap-2 rounded-lg border border-border/50 border-dashed bg-muted/30 p-3 transition-colors",
        isOver && "border-primary bg-primary/5"
      )}
    >
      {/* 缩略图网格 */}
      {referenceAssets.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {referenceAssets.map((asset) => (
            <div key={asset.id} className="group relative size-14 overflow-hidden rounded-md bg-muted">
              {/* biome-ignore lint/performance/noImgElement: 动态参考素材缩略图 */}
              <img
                src={asset.thumbnailUrl ?? undefined}
                alt={asset.name}
                className="size-full object-cover"
              />
              <button
                type="button"
                onClick={() => removeReferenceAsset(asset.id)}
                className="absolute -top-1 -right-1 hidden size-4 items-center justify-center rounded-full bg-destructive text-destructive-foreground group-hover:flex"
              >
                <X className="size-3" />
              </button>
            </div>
          ))}
        </div>
      )}

      {/* 底部：提示文字 + 计数 + 上传按钮 */}
      <div className="mt-auto flex items-center gap-2">
        {referenceAssets.length === 0 && (
          <span className="flex flex-1 items-center gap-1.5 text-muted-foreground text-xs">
            <Upload className="size-3.5 shrink-0" />
            拖拽素材到此处作为参考
          </span>
        )}
        <div className="ml-auto flex shrink-0 items-center gap-2">
          <span className="text-muted-foreground text-xs">{referenceAssets.length}/16</span>
          <button
            type="button"
            className="flex size-7 items-center justify-center rounded-lg border border-border/60 bg-background text-muted-foreground transition-colors hover:bg-muted"
          >
            <Plus className="size-3.5" />
          </button>
        </div>
      </div>
    </div>
  )
}
