/**
 * 预览面板——中栏，上方大图预览 + 下方文件区网格
 * @author AaronZZH & Kiro
 */

"use client"

import { Download, Heart, ThumbsUp, Trash2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { FileGrid } from "./FileGrid"
import { useAigcStore } from "./store"

export function PreviewPanel() {
  const previewAsset = useAigcStore((s) => s.previewAsset)

  return (
    <div className="flex h-full flex-col">
      {/* 预览区 */}
      <div className="relative flex flex-1 items-center justify-center bg-muted/30 p-4">
        {previewAsset ? (
          <>
            {/* biome-ignore lint/performance/noImgElement: 动态预览大图 */}
            <img
              src={previewAsset.thumbnail}
              alt={previewAsset.name}
              className="max-h-full max-w-full rounded-lg object-contain"
            />
            {/* 操作栏 */}
            <div className="absolute top-4 right-4 flex gap-1">
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
            {/* 模型信息 */}
            <div className="absolute bottom-4 left-4 rounded-md bg-background/80 px-2 py-1 text-muted-foreground text-xs backdrop-blur-sm">
              {previewAsset.model}, {previewAsset.resolution ?? "2K"} ({previewAsset.width}×
              {previewAsset.height})
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
        </div>
        <ScrollArea className="h-48">
          <FileGrid />
        </ScrollArea>
      </div>
    </div>
  )
}
