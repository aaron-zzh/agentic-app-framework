/**
 * 看板列——可排序列容器 + 卡片排序上下文
 * @author AaronZZH & Kiro
 */

"use client"

import { useDroppable } from "@dnd-kit/core"
import { SortableContext, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable"
import { CSS } from "@dnd-kit/utilities"
import type { ReactNode } from "react"

import { cn } from "@/lib/utils/cn"

interface KanbanColumnProps {
  id: string
  label: string
  color?: string
  count: number
  /** 列内卡片 ID 列表（用于 SortableContext） */
  itemIds: string[]
  children: ReactNode
}

/** 看板列（可拖拽排序） */
export function KanbanColumn({ id, label, color, count, itemIds, children }: KanbanColumnProps) {
  const { setNodeRef: setDropRef, isOver } = useDroppable({ id })
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id
  })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition
  }

  return (
    <div
      ref={(node) => {
        setNodeRef(node)
        setDropRef(node)
      }}
      style={style}
      className={cn(
        "flex w-64 shrink-0 flex-col rounded-lg bg-muted/50 p-2",
        isOver && "ring-2 ring-primary/50",
        isDragging && "opacity-50"
      )}
    >
      <div className="mb-2 flex cursor-grab items-center gap-2 px-1" {...attributes} {...listeners}>
        {color && <span className="size-2.5 rounded-full" style={{ backgroundColor: color }} />}
        <span className="font-medium text-sm">{label}</span>
        <span className="text-muted-foreground text-xs">({count})</span>
      </div>
      <SortableContext items={itemIds} strategy={verticalListSortingStrategy}>
        <div className="flex flex-1 flex-col gap-2">{children}</div>
      </SortableContext>
    </div>
  )
}
