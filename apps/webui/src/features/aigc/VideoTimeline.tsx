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

/** 示例数据 */
const MOCK_SCENES: VideoScene[] = [
  { id: "s1", index: 1, startTime: 0, endTime: 4, description: "写实胶片 POV 城市街道", status: "completed" },
  { id: "s2", index: 2, startTime: 4, endTime: 8, description: "主角登场逆光剪影", status: "generating" },
  { id: "s3", index: 3, startTime: 8, endTime: 12, description: "粒子消散转场", status: "pending" },
  { id: "s4", index: 4, startTime: 12, endTime: 16, description: "抽象空间漫游", status: "pending" },
]

export function VideoTimeline({
  scenes = MOCK_SCENES,
  activeSceneId,
  onSceneClick,
}: VideoTimelineProps) {
  return (
    <div className="flex h-full flex-col">
      <div className="border-b border-border/50 px-4 py-3">
        <h2 className="text-sm font-semibold text-foreground">分幕时间线</h2>
      </div>
      <ScrollArea className="flex-1">
        <div className="flex flex-col gap-1 p-3">
          {scenes.map((scene) => (
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
                <span className="text-xs font-medium text-foreground">
                  第{scene.index}幕 ({scene.startTime}-{scene.endTime}s)
                </span>
                <span className="truncate text-xs text-muted-foreground">
                  {scene.description}
                </span>
              </div>
            </button>
          ))}
        </div>
      </ScrollArea>
    </div>
  )
}
