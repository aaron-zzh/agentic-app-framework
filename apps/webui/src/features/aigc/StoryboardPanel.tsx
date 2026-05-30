/**
 * 故事板面板——左栏，展示关键元素列表
 * @author AaronZZH & Kiro
 */

"use client"

import { MoreHorizontal } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import type { StoryElement } from "./types"

interface StoryboardPanelProps {
  elements?: StoryElement[]
}

function ElementCard({ element }: { element: StoryElement }) {
  return (
    <div className="group flex gap-3 rounded-lg border border-border/50 bg-card/50 p-3 transition-colors hover:bg-accent/50">
      <div className="size-16 shrink-0 overflow-hidden rounded-md bg-muted">
        {/* biome-ignore lint/performance/noImgElement: 占位缩略图 */}
        <img src={element.thumbnail} alt={element.name} className="size-full object-cover" />
      </div>
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="font-medium text-foreground text-sm">{element.name}</span>
        <p className="line-clamp-2 text-muted-foreground text-xs">{element.description}</p>
        <div className="flex flex-wrap gap-1">
          {element.tags.map((tag) => (
            <Badge key={tag} variant="secondary" className="px-1.5 py-0 text-[10px]">
              {tag}
            </Badge>
          ))}
        </div>
      </div>
    </div>
  )
}

export function StoryboardPanel({ elements = [] }: StoryboardPanelProps) {
  return (
    <div className="flex h-full flex-col">
      {/* 标题栏 */}
      <div className="flex items-center justify-between border-border/50 border-b px-4 py-3">
        <h2 className="font-semibold text-foreground text-sm">故事板</h2>
        <Button variant="ghost" size="sm" className="size-7 p-0">
          <MoreHorizontal className="size-4" />
        </Button>
      </div>

      {/* 关键元素列表 */}
      <ScrollArea className="flex-1">
        <div className="p-3">
          <span className="mb-2 block font-medium text-muted-foreground text-xs">关键元素</span>
          {elements.length > 0 ? (
            <div className="flex flex-col gap-2">
              {elements.map((el) => (
                <ElementCard key={el.id} element={el} />
              ))}
            </div>
          ) : (
            <p className="py-4 text-center text-muted-foreground text-xs">暂无元素</p>
          )}
        </div>
      </ScrollArea>
    </div>
  )
}
