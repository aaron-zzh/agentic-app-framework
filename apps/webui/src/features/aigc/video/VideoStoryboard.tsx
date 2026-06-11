/**
 * 视频故事板面板——关键元素列表，支持展开/折叠描述
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronDown, ChevronRight, Plus } from "lucide-react"
import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import type { StoryElement } from "../types"

/** 示例数据 */
const MOCK_VIDEO_ELEMENTS: StoryElement[] = [
  {
    id: "v1",
    name: "开场镜头",
    description: "写实胶片 POV 视角，城市街道清晨，阳光透过建筑缝隙洒落",
    thumbnail: "/placeholder.svg",
    tags: ["图片", "Element"]
  },
  {
    id: "v2",
    name: "主角登场",
    description: "人物从远处走来，逆光剪影，背景虚化",
    thumbnail: "/placeholder.svg",
    tags: ["图片", "Character"]
  },
  {
    id: "v3",
    name: "转场特效",
    description: "粒子消散过渡，从实景到抽象空间",
    thumbnail: "/placeholder.svg",
    tags: ["视频", "Transition"]
  }
]

function VideoElementCard({ element }: { element: StoryElement }) {
  const [expanded, setExpanded] = useState(false)

  return (
    <div className="rounded-lg border border-border/50 bg-card/50 transition-colors hover:bg-accent/50">
      <div className="flex items-center gap-3 p-3">
        <div className="size-12 shrink-0 overflow-hidden rounded-md bg-muted">
          {/* biome-ignore lint/performance/noImgElement: 动态缩略图 */}
          <img src={element.thumbnail} alt={element.name} className="size-full object-cover" />
        </div>
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <span className="truncate font-medium text-foreground text-sm">{element.name}</span>
          <div className="flex items-center gap-1">
            {element.tags.map((tag) => (
              <Badge key={tag} variant="secondary" className="px-1.5 py-0 text-[10px]">
                {tag}
              </Badge>
            ))}
          </div>
        </div>
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="sm"
            className="size-6 p-0 text-muted-foreground"
            onClick={() => setExpanded(!expanded)}
          >
            {expanded ? (
              <ChevronDown className="size-3.5" />
            ) : (
              <ChevronRight className="size-3.5" />
            )}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            className="size-6 p-0 text-muted-foreground hover:text-primary"
          >
            <Plus className="size-3.5" />
          </Button>
        </div>
      </div>
      {expanded && (
        <div className="border-border/30 border-t px-3 py-2">
          <p className="text-muted-foreground text-xs">{element.description}</p>
        </div>
      )}
    </div>
  )
}

export function VideoStoryboard() {
  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-between border-border/50 border-b px-4 py-3">
        <h2 className="font-semibold text-foreground text-sm">关键元素</h2>
        <Button
          variant="ghost"
          size="sm"
          className="size-7 p-0 text-muted-foreground hover:text-primary"
        >
          <Plus className="size-4" />
        </Button>
      </div>
      <ScrollArea className="flex-1">
        <div className="flex flex-col gap-2 p-3">
          {MOCK_VIDEO_ELEMENTS.map((el) => (
            <VideoElementCard key={el.id} element={el} />
          ))}
        </div>
      </ScrollArea>
    </div>
  )
}
