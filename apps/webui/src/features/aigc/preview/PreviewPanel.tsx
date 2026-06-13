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
  Layers,
  MoreHorizontal,
  Music,
  PanelLeft,
  PenLine,
  RefreshCw,
  Sparkles,
  ThumbsUp,
  Trash2,
  Video,
  X
} from "lucide-react"
import { useParams } from "next/navigation"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger
} from "@/components/ui/context-menu"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { ScrollArea } from "@/components/ui/scroll-area"
import { FileGrid } from "../asset/FileGrid"
import { useAigcStore } from "../store"
import { ImageViewer } from "./ImageViewer"

function FileAreaHeader({ onClose }: { onClose?: () => void }) {
  // const fileFilterUnassigned = useAigcStore((s) => s.fileFilterUnassigned)
  // const toggleFileFilter = useAigcStore((s) => s.toggleFileFilter)
  const fileTypeFilter = useAigcStore((s) => s.fileTypeFilter)
  const setFileTypeFilter = useAigcStore((s) => s.setFileTypeFilter)
  const fileZoom = useAigcStore((s) => s.fileZoom)
  const setFileZoom = useAigcStore((s) => s.setFileZoom)
  const setGenerationPanelOpen = useAigcStore((s) => s.setGenerationPanelOpen)
  const setCopywritingPanelOpen = useAigcStore((s) => s.setCopywritingPanelOpen)

  return (
    <div className="flex shrink-0 items-center justify-between border-border/50 border-b px-3 py-2">
      <span className="font-medium text-muted-foreground text-xs">素材区</span>
      <div className="flex items-center gap-1">
        {/* 生成下拉按钮 */}
        <DropdownMenu>
          <DropdownMenuTrigger className="group/button inline-flex h-6 shrink-0 select-none items-center justify-center gap-1 rounded-lg border border-transparent bg-primary px-2 font-medium text-primary-foreground text-xs outline-none transition-all hover:opacity-90 focus-visible:ring-2">
            <Sparkles className="size-3" />
            生成
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-36">
            <DropdownMenuItem onClick={() => setGenerationPanelOpen(true)}>
              <Sparkles className="mr-2 size-3.5" />
              生成图像
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => setCopywritingPanelOpen(true)}>
              <PenLine className="mr-2 size-3.5" />
              生成文案
            </DropdownMenuItem>
            <DropdownMenuItem disabled>
              <Video className="mr-2 size-3.5" />
              生成视频
            </DropdownMenuItem>
            <DropdownMenuItem disabled>
              <Music className="mr-2 size-3.5" />
              生成音乐
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
        {/* <span className="text-muted-foreground text-xs">只展示未分配</span>
        <Switch
          checked={fileFilterUnassigned}
          onCheckedChange={toggleFileFilter}
          className="scale-75"
        /> */}
        <Popover>
          <PopoverTrigger className="inline-flex size-6 items-center justify-center rounded-md text-muted-foreground hover:bg-accent">
            <MoreHorizontal className="size-3.5" />
          </PopoverTrigger>
          <PopoverContent className="w-56 p-3" align="end">
            <div className="flex flex-col gap-3">
              <button
                type="button"
                tabIndex={0}
                className="flex w-full cursor-pointer items-center justify-start gap-2 rounded-md border border-border px-3 py-1.5 text-xs hover:bg-muted"
              >
                + 新建素材组
              </button>
              <div>
                <p className="mb-1.5 text-muted-foreground text-xs">筛选</p>
                <div className="flex gap-1">
                  {(["ALL", "IMAGE", "VIDEO", "AUDIO"] as const).map((t) => (
                    <button
                      key={t}
                      type="button"
                      onClick={() => setFileTypeFilter(t)}
                      className={`cursor-pointer rounded px-2 py-1 text-xs transition-colors ${
                        fileTypeFilter === t
                          ? "bg-primary text-primary-foreground"
                          : "bg-muted text-muted-foreground hover:bg-muted/80"
                      }`}
                    >
                      {t === "ALL"
                        ? "全部"
                        : t === "IMAGE"
                          ? "图片"
                          : t === "VIDEO"
                            ? "视频"
                            : "音频"}
                    </button>
                  ))}
                </div>
              </div>
              <div>
                <p className="mb-1.5 text-muted-foreground text-xs">缩放</p>
                <div className="flex items-center gap-2 overflow-hidden">
                  <span className="shrink-0 text-[10px] text-muted-foreground">50%</span>
                  <input
                    type="range"
                    min={50}
                    max={150}
                    step={10}
                    value={fileZoom}
                    onChange={(e) => setFileZoom(Number(e.target.value))}
                    className="min-w-0 flex-1 accent-primary"
                  />
                  <span className="shrink-0 text-[10px] text-muted-foreground">150%</span>
                </div>
              </div>
            </div>
          </PopoverContent>
        </Popover>
        {onClose && (
          <button
            type="button"
            onClick={onClose}
            className="inline-flex size-6 cursor-pointer items-center justify-center rounded-md text-muted-foreground hover:bg-accent"
          >
            <X className="size-3.5" />
          </button>
        )}
      </div>
    </div>
  )
}

export function PreviewPanel({
  orientation = "vertical"
}: {
  orientation?: "horizontal" | "vertical"
}) {
  const previewAsset = useAigcStore((s) => s.previewAsset)
  const previewList = useAigcStore((s) => s.previewList)
  const navigatePreview = useAigcStore((s) => s.navigatePreview)
  const fileFilterUnassigned = useAigcStore((s) => s.fileFilterUnassigned)
  const fileAreaOpen = useAigcStore((s) => s.fileAreaOpen)
  const setFileAreaOpen = useAigcStore((s) => s.setFileAreaOpen)
  const storyboardPanelOpen = useAigcStore((s) => s.storyboardPanelOpen)
  const setStoryboardPanelOpen = useAigcStore((s) => s.setStoryboardPanelOpen)
  const params = useParams()
  const projectId = params.projectId ? Number(params.projectId) : null

  const currentIdx = previewAsset ? previewList.findIndex((a) => a.id === previewAsset.id) : -1
  const hasPrev = currentIdx > 0
  const hasNext = currentIdx >= 0 && currentIdx < previewList.length - 1

  const [naturalSize, setNaturalSize] = useState<{ w: number; h: number } | null>(null)

  // 编辑弹窗状态
  const [_editOpen, _setEditOpen] = useState(false)
  const [_editPrompt, _setEditPrompt] = useState("")

  function getPrompt() {
    try {
      const p = previewAsset?.generationParams ? JSON.parse(previewAsset.generationParams) : {}
      return p.prompt ?? previewAsset?.name ?? ""
    } catch {
      return previewAsset?.name ?? ""
    }
  }

  return (
    <ResizablePanelGroup orientation={orientation} className="h-full min-w-0">
      {/* 水平模式（元素看板关闭）：文件区在左，预览区在右 */}
      {orientation === "horizontal" && fileAreaOpen && (
        <>
          <ResizablePanel defaultSize="35%" minSize="20%">
            <div className="flex h-full flex-col overflow-hidden">
              <FileAreaHeader onClose={() => setFileAreaOpen(false)} />
              <ScrollArea className="min-h-0 flex-1">
                <FileGrid filterUnassigned={fileFilterUnassigned} projectId={projectId} />
              </ScrollArea>
            </div>
          </ResizablePanel>
          <ResizableHandle withHandle />
        </>
      )}

      {/* 预览区 */}
      <ResizablePanel defaultSize="50%" minSize="30%">
        <div className="flex h-full flex-col">
          <div className="flex shrink-0 items-center border-border/50 border-b px-4 py-2">
            {!storyboardPanelOpen && (
              <Button
                variant="ghost"
                size="sm"
                className="mr-1 size-6 p-0 text-muted-foreground"
                onClick={() => setStoryboardPanelOpen(true)}
                title="展开元素看板"
              >
                <PanelLeft className="size-3.5" />
              </Button>
            )}
            <span className="font-medium text-muted-foreground text-xs">预览</span>
            {previewAsset && (
              <span className="ml-2 truncate text-foreground text-xs">{previewAsset.name}</span>
            )}
            {!fileAreaOpen && (
              <Button
                variant="ghost"
                size="sm"
                className="ml-auto size-6 p-0 text-muted-foreground"
                onClick={() => setFileAreaOpen(true)}
                title="展开文件区"
              >
                <Layers className="size-3.5" />
              </Button>
            )}
          </div>
          <div className="relative flex flex-1 items-center justify-center overflow-hidden bg-muted/30 p-1">
            {previewAsset ? (
              <>
                {/* 图片区域 + 右键菜单 */}
                <ContextMenu>
                  <ContextMenuTrigger className="h-full w-full">
                    {previewAsset.type === "AUDIO" ? (
                      <div className="flex h-full w-full flex-col items-center justify-center gap-4 p-6">
                        <Music className="size-16 text-muted-foreground/60" />
                        <span className="max-w-full truncate text-muted-foreground text-sm">
                          {previewAsset.name}
                        </span>
                        {/* biome-ignore lint/a11y/useMediaCaption: 生成音频无字幕轨 */}
                        <audio controls src={previewAsset.url ?? ""} className="w-full max-w-md" />
                      </div>
                    ) : previewAsset.type === "VIDEO" ? (
                      // biome-ignore lint/a11y/useMediaCaption: 生成视频无字幕轨
                      <video
                        controls
                        src={previewAsset.url ?? ""}
                        className="h-full w-full object-contain"
                      />
                    ) : (
                      <ImageViewer
                        src={previewAsset.thumbnailUrl ?? previewAsset.url ?? ""}
                        alt={previewAsset.name}
                        className="h-full w-full"
                        onLoad={(e) => {
                          const img = e.currentTarget
                          setNaturalSize({ w: img.naturalWidth, h: img.naturalHeight })
                        }}
                      />
                    )}
                  </ContextMenuTrigger>
                  <ContextMenuContent>
                    <ContextMenuItem
                      onClick={() => {
                        useAigcStore.getState().addReferenceAsset(previewAsset)
                        useAigcStore.getState().setGenerationPanelOpen(true)
                      }}
                    >
                      <PenLine className="mr-2 size-3.5" />
                      AI 编辑
                    </ContextMenuItem>
                    <ContextMenuItem
                      onClick={() => {
                        useAigcStore.getState().setPrompt(getPrompt())
                        useAigcStore.getState().setGenerationPanelOpen(true)
                      }}
                    >
                      <RefreshCw className="mr-2 size-3.5" />
                      重新生成
                    </ContextMenuItem>
                  </ContextMenuContent>
                </ContextMenu>
                <div className="absolute inset-x-4 top-2 flex items-center justify-between">
                  {/* 左：模型 + 尺寸信息 */}
                  <div className="flex items-center gap-1.5 rounded-md bg-black/40 px-2 py-1 backdrop-blur-sm">
                    {(() => {
                      let model = previewAsset.modelName ?? ""
                      let sizePreset = ""
                      try {
                        const p = previewAsset.generationParams
                          ? JSON.parse(previewAsset.generationParams)
                          : {}
                        if (!model) model = p.model ?? ""
                        sizePreset = p.sizePreset ?? ""
                      } catch {}
                      const w = naturalSize?.w
                      const h = naturalSize?.h
                      const sizeStr = w && h ? `${w}×${h}` : ""
                      return (
                        <>
                          {model && <span className="text-white/80 text-xs">{model}</span>}
                          {sizePreset && (
                            <span className="text-white/60 text-xs">{sizePreset}</span>
                          )}
                          {sizeStr && <span className="text-white/60 text-xs">{sizeStr}</span>}
                        </>
                      )
                    })()}
                  </div>
                  {/* 右：操作按钮 */}
                  <div className="flex gap-1">
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
                      onClick={async () => {
                        if (!previewAsset?.url) return
                        try {
                          const res = await fetch(previewAsset.url)
                          const blob = await res.blob()
                          const blobUrl = URL.createObjectURL(blob)
                          const a = document.createElement("a")
                          a.href = blobUrl
                          a.download = previewAsset.name || "image"
                          a.click()
                          URL.revokeObjectURL(blobUrl)
                        } catch {
                          // 跨域 fetch 失败时降级直接打开
                          window.open(previewAsset.url, "_blank")
                        }
                      }}
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
                </div>
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
              </>
            ) : (
              <p className="text-muted-foreground text-sm">选择素材以预览</p>
            )}
          </div>
        </div>
      </ResizablePanel>

      {/* 文件区（垂直模式） */}
      {orientation === "vertical" && fileAreaOpen && (
        <>
          <ResizableHandle withHandle />
          <ResizablePanel defaultSize="50%" minSize="15%">
            <div className="flex h-full flex-col overflow-hidden">
              <FileAreaHeader onClose={() => setFileAreaOpen(false)} />
              <ScrollArea className="min-h-0 flex-1">
                <FileGrid filterUnassigned={fileFilterUnassigned} projectId={projectId} />
              </ScrollArea>
            </div>
          </ResizablePanel>
        </>
      )}
    </ResizablePanelGroup>
  )
}
