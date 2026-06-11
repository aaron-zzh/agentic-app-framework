/**
 * 视频生成对话面板——右栏，对话驱动视频生成 + 素材状态卡片
 * @author AaronZZH & Kiro
 */

"use client"

import { CheckCircle2, Film, Loader2 } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import type { SceneStatus, VideoScene } from "./VideoTimeline"

/** 媒体素材状态 */
export interface MediaAssetStatus {
  id: string
  name: string
  thumbnail: string
  duration: string
  status: SceneStatus
}

interface VideoGenerationChatProps {
  scenes?: VideoScene[]
  assets?: MediaAssetStatus[]
}

function AssetStatusCard({ asset }: { asset: MediaAssetStatus }) {
  return (
    <div className="flex items-center gap-2 rounded-md border border-border/50 bg-card/50 p-2">
      <div className="relative size-10 shrink-0 overflow-hidden rounded bg-muted">
        {/* biome-ignore lint/performance/noImgElement: 动态素材缩略图 */}
        <img src={asset.thumbnail} alt={asset.name} className="size-full object-cover" />
        {asset.status === "generating" && (
          <div className="absolute inset-0 flex items-center justify-center bg-black/40">
            <Loader2 className="size-4 animate-spin text-white" />
          </div>
        )}
      </div>
      <div className="flex min-w-0 flex-1 flex-col">
        <span className="truncate font-medium text-foreground text-xs">{asset.name}</span>
        <span className="text-[10px] text-muted-foreground">{asset.duration}</span>
      </div>
      {asset.status === "completed" && (
        <CheckCircle2 className="size-3.5 shrink-0 text-green-500" />
      )}
      {asset.status === "generating" && (
        <Loader2 className="size-3.5 shrink-0 animate-spin text-primary" />
      )}
    </div>
  )
}

function SceneDescription({ scene }: { scene: VideoScene }) {
  return (
    <div className="flex items-start gap-2 rounded-md bg-muted/30 px-3 py-2">
      <Film className="mt-0.5 size-3.5 shrink-0 text-muted-foreground" />
      <div className="flex flex-col gap-0.5">
        <span className="font-medium text-foreground text-xs">
          第{scene.index}幕 ({scene.startTime}-{scene.endTime}s)
        </span>
        <span className="text-muted-foreground text-xs">{scene.description}</span>
      </div>
      <Badge
        variant={scene.status === "completed" ? "default" : "secondary"}
        className="ml-auto shrink-0 text-[10px]"
      >
        {scene.status === "completed"
          ? "已完成"
          : scene.status === "generating"
            ? "生成中"
            : "待生成"}
      </Badge>
    </div>
  )
}

export function VideoGenerationChat({ scenes = [], assets = [] }: VideoGenerationChatProps) {
  return (
    <div className="flex h-full flex-col">
      <div className="border-border/50 border-b px-4 py-3">
        <h2 className="font-semibold text-foreground text-sm">视频生成</h2>
      </div>

      <ScrollArea className="flex-1">
        <div className="flex flex-col gap-4 p-4">
          <section>
            <span className="mb-2 block font-medium text-muted-foreground text-xs">
              Media Assets
            </span>
            {assets.length > 0 ? (
              <div className="flex flex-col gap-1.5">
                {assets.map((asset) => (
                  <AssetStatusCard key={asset.id} asset={asset} />
                ))}
              </div>
            ) : (
              <p className="py-2 text-center text-muted-foreground text-xs">暂无素材</p>
            )}
          </section>

          <Separator />

          <section>
            <span className="mb-2 block font-medium text-muted-foreground text-xs">分幕描述</span>
            {scenes.length > 0 ? (
              <div className="flex flex-col gap-1.5">
                {scenes.map((scene) => (
                  <SceneDescription key={scene.id} scene={scene} />
                ))}
              </div>
            ) : (
              <p className="py-2 text-center text-muted-foreground text-xs">暂无分幕</p>
            )}
          </section>

          <Separator />

          <div className="flex items-center gap-2 rounded-md border border-border/50 bg-background px-3 py-2">
            <input
              type="text"
              placeholder="输入评论，编辑当前镜头并生成新画面..."
              className="flex-1 bg-transparent text-foreground text-xs outline-none placeholder:text-muted-foreground"
            />
            <Button variant="ghost" size="sm" className="h-6 px-2 text-xs">
              手动编辑
            </Button>
            <Button size="sm" className="h-6 px-2 text-xs">
              重新生成
            </Button>
          </div>
        </div>
      </ScrollArea>
    </div>
  )
}
