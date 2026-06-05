/**
 * 预览面板——中栏，上方大图预览 + 元素导航 + 下方文件区网格
 * @author AaronZZH & Kiro
 */

"use client"

import {
  ChevronDown,
  ChevronUp,
  Download,
  Heart,
  MoreHorizontal,
  ThumbsUp,
  Trash2
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { Switch } from "@/components/ui/switch"
import { FileGrid } from "./FileGrid"
import { useAigcStore } from "./store"

export function PreviewPanel() {
  const previewAsset = useAigcStore((s) => s.previewAsset)
  const previewList = useAigcStore((s) => s.previewList)
  const navigatePreview = useAigcStore((s) => s.navigatePreview)
  const fileFilterUnassigned = useAigcStore((s) => s.fileFilterUnassigned)
  const toggleFileFilter = useAigcStore((s) => s.toggleFileFilter)

  const currentIdx = previewAsset ? previewList.findIndex((a) => a.id === previewAsset.id) : -1
  const hasPrev = currentIdx > 0
  const hasNext = currentIdx >= 0 && currentIdx < previewList.length - 1

  return (
    <div className="flex h-full flex-col">
      {/* 预览区 */}
      <div className="relative flex flex-1 items-center justify-center bg-muted/30 p-4">
        {previewAsset ? (
          <>
            {/* 标题栏 */}
            <div className="absolute top-2 left-4 text-muted-foreground text-xs">
              预览 {previewAsset.name}
            </div>

            {/* biome-ignore lint/performance/noImgElement: 动态预览大图 */}
            <img
              src={previewAsset.thumbnailUrl ?? undefined}
              alt={previewAsset.name}
              className="max-h-full max-w-full rounded-lg object-contain"
            />

            {/* 操作栏 */}
            <div className="absolute top-2 right-4 flex gap-1">
              <Button
                variant="ghost"
                size="sm"
                className="size-8 p-0 text-muted-foreground hover:text-foreground"
              >
                <Heart className="size-4" />
              </Button>
              <Button
                variant="ghost"
                size="sm"
                className="size-8 p-0 text-muted-foreground hover:text-foreground"
              >
                <Download className="size-4" />
              </Button>
              <Button
                variant="ghost"
                size="sm"
                className="size-8 p-0 text-muted-foreground hover:text-foreground"
              >
                <ThumbsUp className="size-4" />
              </Button>
              <Button
                variant="ghost"
                size="sm"
                className="size-8 p-0 text-muted-foreground hover:text-destructive"
              >
                <Trash2 className="size-4" />
              </Button>
            </div>

            {/* 元素导航箭头（垂直居中，左侧） */}
            {previewList.length > 1 && (
              <div className="absolute top-1/2 left-3 flex -translate-y-1/2 flex-col items-center gap-1">
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={!hasPrev}
                  onClick={() => navigatePreview(-1)}
                  className="size-8 rounded-full p-0 text-muted-foreground hover:text-foreground disabled:opacity-30"
                >
                  <ChevronUp className="size-4" />
                </Button>
                <span className="text-[10px] text-muted-foreground">元素</span>
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={!hasNext}
                  onClick={() => navigatePreview(1)}
                  className="size-8 rounded-full p-0 text-muted-foreground hover:text-foreground disabled:opacity-30"
                >
                  <ChevronDown className="size-4" />
                </Button>
              </div>
            )}

            {/* 模型信息 */}
            <div className="absolute bottom-4 left-4 rounded-md bg-background/80 px-2 py-1 text-muted-foreground text-xs backdrop-blur-sm">
              {(() => {
                try {
                  const p = previewAsset.generationParams
                    ? JSON.parse(previewAsset.generationParams)
                    : null
                  return `${p?.model ?? ""} ${p?.resolution ?? "2K"} (${previewAsset.width}×${previewAsset.height})`
                } catch {
                  return `2K (${previewAsset.width}×${previewAsset.height})`
                }
              })()}
            </div>
          </>
        ) : (
          <p className="text-muted-foreground text-sm">选择素材以预览</p>
        )}
      </div>

      <Separator />

      {/* 文件区 */}
      <div className="shrink-0">
        <div className="flex items-center justify-between border-border/50 border-b px-4 py-2">
          <span className="font-medium text-muted-foreground text-xs">文件区</span>
          <div className="flex items-center gap-2">
            <span className="text-muted-foreground text-xs">只展示未分配素材</span>
            <Switch
              checked={fileFilterUnassigned}
              onCheckedChange={toggleFileFilter}
              className="scale-75"
            />
            <Button variant="ghost" size="sm" className="size-6 p-0 text-muted-foreground">
              <MoreHorizontal className="size-3.5" />
            </Button>
          </div>
        </div>
        <ScrollArea className="h-48">
          <FileGrid filterUnassigned={fileFilterUnassigned} />
        </ScrollArea>
      </div>
    </div>
  )
}
