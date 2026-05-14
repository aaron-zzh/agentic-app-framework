/**
 * 看板列——作为 droppable 容器接收拖拽卡片
 * @author AaronZZH & Kiro
 */

"use client"

import { useDroppable } from "@dnd-kit/core"
import type { ReactNode } from "react"

import { cn } from "@/lib/utils/cn"

interface KanbanColumnProps {
  id: string
  label: string
  color?: string
  count: number
  children: ReactNode
}

/** 看板列 */
export function KanbanColumn({ id, label, color, count, children }: KanbanColumnProps) {
  const { setNodeRef, isOver } = useDroppable({ id })

  return (
    <div
      ref={setNodeRef}
      className={cn(
        "flex w-64 shrink-0 flex-col rounded-lg bg-muted/50 p-2",
        isOver && "ring-2 ring-primary/50"
      )}
    >
      <div className="mb-2 flex items-center gap-2 px-1">
        {color && <span className="size-2.5 rounded-full" style={{ backgroundColor: color }} />}
        <span className="font-medium text-sm">{label}</span>
        <span className="text-muted-foreground text-xs">({count})</span>
      </div>
      <div className="flex flex-1 flex-col gap-2">{children}</div>
    </div>
  )
}
