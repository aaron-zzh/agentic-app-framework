/**
 * 故事板面板——左栏，展示关键元素列表（文案+多图像+多视频），支持视图设置
 * @author AaronZZH & Kiro
 */

"use client"

import { useDroppable } from "@dnd-kit/core"
import { BookOpen, Film, Image, Play, Plus, Settings2, Text, X } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Slider } from "@/components/ui/slider"
import { cn } from "@/lib/utils/index"
import { ProjectDocPanel } from "../project/ProjectDocPanel"
import { useAigcStore } from "../store"
import type { MediaAssetVO } from "../types"

type DisplayMode = "all" | "text" | "media"

interface StoryboardElement {
  asset: MediaAssetVO
  mediaAssets: MediaAssetVO[]
}

function groupAssets(assets: MediaAssetVO[]): StoryboardElement[] {
  return assets.map((a) => ({ asset: a, mediaAssets: [a] }))
}

function MediaTypeIcon({ type }: { type: MediaAssetVO["type"] }) {
  if (type === "VIDEO") return <Film className="size-3 text-blue-400" />
  if (type === "IMAGE") return <Image className="size-3 text-emerald-400" />
  return <Text className="size-3 text-muted-foreground" />
}

function MediaBadge({
  asset,
  scale,
  onRemove
}: {
  asset: MediaAssetVO
  scale: number
  onRemove?: () => void
}) {
  const thumbSize = Math.round(40 * scale)

  return (
    <div className="group/badge relative flex items-center gap-1 rounded-md border border-border/50 bg-muted/50 px-1.5 py-1">
      <div
        className="relative shrink-0 overflow-hidden rounded"
        style={{ width: thumbSize, height: thumbSize }}
      >
        {/* biome-ignore lint/performance/noImgElement: 动态缩略图 */}
        <img
          src={asset.thumbnailUrl ?? asset.url}
          alt={asset.name}
          className="size-full object-cover"
        />
        {asset.type === "VIDEO" && (
          <div className="absolute inset-0 flex items-center justify-center bg-black/30">
            <Play className="size-2.5 fill-white text-white" />
          </div>
        )}
      </div>
      <div className="flex min-w-0 flex-col">
        <div className="flex items-center gap-0.5">
          <MediaTypeIcon type={asset.type} />
          <span className="max-w-[56px] truncate text-muted-foreground text-xs">
            {asset.name.slice(0, 10)}
          </span>
        </div>
        {asset.duration != null && (
          <span className="text-[10px] text-muted-foreground/60">{asset.duration}s</span>
        )}
      </div>
      {onRemove && (
        <button
          type="button"
          onClick={onRemove}
          className="absolute -top-1 -right-1 hidden size-4 items-center justify-center rounded-full bg-destructive text-destructive-foreground group-hover/badge:flex"
        >
          <X className="size-2.5" />
        </button>
      )}
    </div>
  )
}

function ElementCard({
  element,
  mode,
  scale
}: {
  element: StoryboardElement
  mode: DisplayMode
  scale: number
}) {
  const addReferenceAsset = useAigcStore((s) => s.addReferenceAsset)
  const removeStoryboardAsset = useAigcStore((s) => s.removeStoryboardAsset)

  const { asset, mediaAssets } = element

  let description: string | null = null
  try {
    const params = asset.generationParams ? JSON.parse(asset.generationParams) : null
    description = params?.description ?? null
  } catch {
    // ignore
  }

  const showText = mode === "all" || mode === "text"
  const showMedia = mode === "all" || mode === "media"

  return (
    <div className="group flex flex-col gap-2 rounded-lg border border-border/50 bg-card/50 p-3 transition-colors hover:bg-accent/50">
      <div className="flex items-center justify-between gap-1">
        <span className="min-w-0 truncate font-medium text-foreground text-sm">{asset.name}</span>
        <div className="flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
          <Button
            variant="ghost"
            size="sm"
            className="size-6 p-0"
            title="添加到参考"
            onClick={() => addReferenceAsset(asset)}
          >
            <Plus className="size-3" />
          </Button>
          <Button
            variant="ghost"
            size="sm"
            className="size-6 p-0 hover:text-destructive"
            title="从元素区移除"
            onClick={() => removeStoryboardAsset(asset.id)}
          >
            <X className="size-3" />
          </Button>
        </div>
      </div>
      {showText && description && (
        <p className="line-clamp-3 text-muted-foreground text-xs leading-relaxed">{description}</p>
      )}
      {showMedia && mediaAssets.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {mediaAssets.map((a) => (
            <MediaBadge key={a.id} asset={a} scale={scale} />
          ))}
        </div>
      )}
    </div>
  )
}

function ViewSettingsPopover({
  mode,
  scale,
  onModeChange,
  onScaleChange
}: {
  mode: DisplayMode
  scale: number
  onModeChange: (m: DisplayMode) => void
  onScaleChange: (v: number) => void
}) {
  const modes: { value: DisplayMode; label: string }[] = [
    { value: "all", label: "全部" },
    { value: "text", label: "仅文字" },
    { value: "media", label: "仅素材" }
  ]

  return (
    <Popover>
      <PopoverTrigger
        title="视图设置"
        className="inline-flex size-7 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
      >
        <Settings2 className="size-4" />
      </PopoverTrigger>
      <PopoverContent side="bottom" align="end" className="w-52 p-3">
        <p className="mb-2 font-medium text-foreground text-xs">显示模式</p>
        <div className="mb-4 flex flex-col gap-1">
          {modes.map((m) => (
            <button
              key={m.value}
              type="button"
              onClick={() => onModeChange(m.value)}
              className={cn(
                "rounded-md px-3 py-1.5 text-left text-sm transition-colors",
                mode === m.value
                  ? "bg-primary/20 text-primary"
                  : "text-muted-foreground hover:bg-accent"
              )}
            >
              {m.label}
            </button>
          ))}
        </div>
        <p className="mb-2 font-medium text-foreground text-xs">缩放</p>
        <div className="flex items-center gap-2">
          <span className="w-8 shrink-0 text-muted-foreground text-xs">50%</span>
          <Slider
            min={0.5}
            max={1.5}
            step={0.1}
            value={[scale]}
            onValueChange={(v) => onScaleChange(Array.isArray(v) ? v[0] : v)}
            className="flex-1"
          />
          <span className="w-9 shrink-0 text-right text-muted-foreground text-xs">150%</span>
        </div>
      </PopoverContent>
    </Popover>
  )
}

export function StoryboardPanel() {
  const storyboardAssets = useAigcStore((s) => s.storyboardAssets)
  const setStoryboardPanelOpen = useAigcStore((s) => s.setStoryboardPanelOpen)
  const { isOver, setNodeRef } = useDroppable({ id: "storyboard-drop-zone" })

  const [mode, setMode] = useState<DisplayMode>("all")
  const [scale, setScale] = useState(1)
  const [docPanelOpen, setDocPanelOpen] = useState(false)

  const elements = groupAssets(storyboardAssets)

  return (
    <div className="flex h-full min-w-0 flex-col overflow-hidden">
      <div className="flex items-center justify-between border-border/50 border-b px-4 py-3">
        <h2 className="font-semibold text-foreground text-sm">元素看板</h2>
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="sm"
            className="size-7 p-0 text-muted-foreground hover:text-foreground"
            title="项目规范（Prompt + 文档）"
            onClick={() => setDocPanelOpen(true)}
          >
            <BookOpen className="size-4" />
          </Button>
          <ViewSettingsPopover
            mode={mode}
            scale={scale}
            onModeChange={setMode}
            onScaleChange={setScale}
          />
          <Button
            variant="ghost"
            size="sm"
            className="size-7 p-0 text-muted-foreground hover:text-foreground"
            title="关闭看板"
            onClick={() => setStoryboardPanelOpen(false)}
          >
            <X className="size-4" />
          </Button>
        </div>
      </div>

      <ProjectDocPanel open={docPanelOpen} onOpenChange={setDocPanelOpen} />

      <ScrollArea className="flex-1">
        <div
          ref={setNodeRef}
          className={cn(
            "flex min-h-full flex-col gap-2 p-3 transition-colors",
            isOver && "bg-primary/5"
          )}
        >
          <span className="mb-1 block font-medium text-muted-foreground text-xs">— 关键元素 —</span>
          {elements.length > 0 ? (
            elements.map((el) => (
              <ElementCard key={el.asset.id} element={el} mode={mode} scale={scale} />
            ))
          ) : (
            <div className="py-8 text-center">
              <Film className="mx-auto mb-2 size-8 text-muted-foreground/30" />
              <p className="text-muted-foreground text-xs">从文件区拖入素材添加元素</p>
            </div>
          )}
        </div>
      </ScrollArea>
    </div>
  )
}
