/**
 * 看板卡片——可拖拽的记录卡片
 * @author AaronZZH & Kiro
 */

"use client"

import { useDraggable } from "@dnd-kit/core"

import { cn } from "@/lib/utils/cn"

interface KanbanCardProps {
  id: string
  title: string
  description?: string
  /** DragOverlay 中渲染时为 true */
  overlay?: boolean
}

/** 看板卡片 */
export function KanbanCard({ id, title, description, overlay }: KanbanCardProps) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({ id })

  return (
    <div
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      className={cn(
        "cursor-grab rounded-md border bg-background p-3 shadow-sm transition-shadow hover:shadow-md",
        isDragging && "opacity-50",
        overlay && "rotate-2 shadow-lg"
      )}
    >
      <p className="font-medium text-sm leading-tight">{title}</p>
      {description && (
        <p className="mt-1 line-clamp-2 text-muted-foreground text-xs">{description}</p>
      )}
    </div>
  )
}
