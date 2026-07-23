/**
 * 实体定义列表——左侧导航面板
 * @author AaronZZH & Kiro
 */

"use client"

import { Badge } from "@/components/ui/badge"
import { ScrollArea } from "@/components/ui/scroll-area"
import type { EntityDefRecord } from "@/lib/api/rest/entity/entity-def"
import { cn } from "@/lib/utils/cn"

interface EntityDefListProps {
  items: EntityDefRecord[]
  selectedId: number | undefined
  onSelect: (item: EntityDefRecord) => void
}

/** 实体列表导航 */
export function EntityDefList({ items, selectedId, onSelect }: EntityDefListProps) {
  return (
    <div className="flex h-full flex-col border-r">
      <div className="flex items-center justify-between border-b px-3 py-2">
        <span className="font-medium text-sm">实体列表</span>
      </div>
      <ScrollArea className="flex-1">
        <div className="flex flex-col gap-0.5 p-1">
          {items.map((item) => (
            <button
              key={item.id}
              type="button"
              className={cn(
                "flex items-center justify-between rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-accent",
                selectedId === item.id && "bg-accent"
              )}
              onClick={() => onSelect(item)}
            >
              <span className="truncate">{item.slug}</span>
              <div className="flex items-center gap-1">
                {item.builtin && (
                  <Badge variant="secondary" className="text-xs">
                    内置
                  </Badge>
                )}
                {!item.enabled && (
                  <Badge variant="destructive" className="text-xs">
                    禁用
                  </Badge>
                )}
              </div>
            </button>
          ))}
          {items.length === 0 && (
            <p className="px-3 py-4 text-center text-muted-foreground text-sm">暂无实体定义</p>
          )}
        </div>
      </ScrollArea>
    </div>
  )
}
