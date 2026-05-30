/**
 * 分幕时间线——显示视频各幕的时间段和生成状态
 * @author AaronZZH & Kiro
 */

"use client"

import { CheckCircle2, Loader2 } from "lucide-react"
import { ScrollArea } from "@/components/ui/scroll-area"
import { cn } from "@/lib/utils/index"

/** 分幕状态 */
export type SceneStatus = "pending" | "generating" | "completed"

/** 分幕数据 */
export interface VideoScene {
  id: string
  index: number
  startTime: number
  endTime: number
  description: string
  status: SceneStatus
}

interface VideoTimelineProps {
  scenes?: VideoScene[]
  activeSceneId?: string
  onSceneClick?: (scene: VideoScene) => void
}

function SceneStatusIcon({ status }: { status: SceneStatus }) {
  switch (status) {
    case "generating":
      return <Loader2 className="size-3.5 animate-spin text-primary" />
    case "completed":
      return <CheckCircle2 className="size-3.5 text-green-500" />
    default:
      return <div className="size-3.5 rounded-full border border-border/50" />
  }
}

export function VideoTimeline({
  scenes = [],
  activeSceneId,
  onSceneClick
}: VideoTimelineProps) {
  return (
    <div className="flex h-full flex-col">
      <div className="border-border/50 border-b px-4 py-3">
        <h2 className="font-semibold text-foreground text-sm">分幕时间线</h2>
      </div>
      <ScrollArea className="flex-1">
        <div className="flex flex-col gap-1 p-3">
          {scenes.length > 0 ? (
            scenes.map((scene) => (
              <button
                key={scene.id}
                type="button"
                onClick={() => onSceneClick?.(scene)}
                className={cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2.5 text-left transition-colors hover:bg-accent/50",
                  activeSceneId === scene.id && "bg-accent/70"
                )}
              >
                <SceneStatusIcon status={scene.status} />
                <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="font-medium text-foreground text-xs">
                    第{scene.index}幕 ({scene.startTime}-{scene.endTime}s)
                  </span>
                  <span className="truncate text-muted-foreground text-xs">{scene.description}</span>
                </div>
              </button>
            ))
          ) : (
            <p className="py-4 text-center text-muted-foreground text-xs">暂无分幕</p>
          )}
        </div>
      </ScrollArea>
    </div>
  )
}
