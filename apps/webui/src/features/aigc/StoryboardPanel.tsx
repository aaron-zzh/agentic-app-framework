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

/** 示例数据（TODO: 移至 Storybook stories 或 __fixtures__/，组件 props 改为必填） */
const MOCK_ELEMENTS: StoryElement[] = [
  {
    id: "1",
    name: "主角形象",
    description: "一位身穿深蓝色长袍的年轻法师，手持发光法杖",
    thumbnail: "/placeholder.svg",
    tags: ["角色", "主角"]
  },
  {
    id: "2",
    name: "魔法森林",
    description: "古老的森林中弥漫着蓝紫色的魔法光芒，巨大的蘑菇散发荧光",
    thumbnail: "/placeholder.svg",
    tags: ["场景", "森林"]
  },
  {
    id: "3",
    name: "水晶龙",
    description: "通体由透明水晶构成的巨龙，折射出彩虹般的光芒",
    thumbnail: "/placeholder.svg",
    tags: ["角色", "龙"]
  }
]

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

export function StoryboardPanel() {
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
          <div className="flex flex-col gap-2">
            {MOCK_ELEMENTS.map((el) => (
              <ElementCard key={el.id} element={el} />
            ))}
          </div>
        </div>
      </ScrollArea>
    </div>
  )
}
