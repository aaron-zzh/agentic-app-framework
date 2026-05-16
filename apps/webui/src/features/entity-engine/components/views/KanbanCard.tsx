/**
 * 看板卡片——可排序的记录卡片
 * @author AaronZZH & Kiro
 */

"use client"

import { useSortable } from "@dnd-kit/sortable"
import { CSS } from "@dnd-kit/utilities"

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
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id
  })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition
  }

  return (
    <div
      ref={setNodeRef}
      style={style}
      {...attributes}
      {...listeners}
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
