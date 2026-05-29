/**
 * 看板列——可排序列容器 + 卡片排序上下文 + WIP 限制指示
 * @author AaronZZH & Kiro
 */

"use client"

import { useDroppable } from "@dnd-kit/core"
import { SortableContext, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable"
import { CSS } from "@dnd-kit/utilities"
import type { ReactNode } from "react"
import type { WipLimitMode } from "@/lib/types/entity/views"
import { cn } from "@/lib/utils/cn"

interface KanbanColumnProps {
  id: string
  label: string
  color?: string
  count: number
  /** 列内卡片 ID 列表（用于 SortableContext） */
  itemIds: string[]
  children: ReactNode
  /** WIP 限制数 */
  wipLimit?: number
  /** 是否超限 */
  isOverLimit?: boolean
  /** WIP 限制模式 */
  wipLimitMode?: WipLimitMode
}

/** 看板列（可拖拽排序 + WIP 限制） */
export function KanbanColumn({
  id,
  label,
  color,
  count,
  itemIds,
  children,
  wipLimit,
  isOverLimit,
  wipLimitMode
}: KanbanColumnProps) {
  const { setNodeRef: setDropRef, isOver } = useDroppable({ id })
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id
  })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition
  }

  // 硬限制超限时禁止拖入的视觉反馈
  const isBlocked = isOverLimit && wipLimitMode === "hard"

  return (
    <div
      ref={(node) => {
        setNodeRef(node)
        setDropRef(node)
      }}
      style={style}
      className={cn(
        "flex w-64 shrink-0 flex-col rounded-lg bg-muted/50 p-2",
        isOver && !isBlocked && "ring-2 ring-primary/50",
        isOver && isBlocked && "ring-2 ring-destructive/50",
        isDragging && "opacity-50"
      )}
    >
      {/* 列标题 */}
      <div className="mb-2 flex cursor-grab items-center gap-2 px-1" {...attributes} {...listeners}>
        {color && <span className="size-2.5 rounded-full" style={{ backgroundColor: color }} />}
        <span className={cn("font-medium text-sm", isOverLimit && "text-destructive")}>
          {label}
        </span>
        {/* WIP 状态指示器 */}
        <span
          className={cn(
            "text-xs",
            isOverLimit ? "font-semibold text-destructive" : "text-muted-foreground"
          )}
        >
          {wipLimit ? `${count}/${wipLimit}` : `(${count})`}
        </span>
        {isOverLimit && (
          <span className="text-destructive text-xs" title="超出 WIP 限制">
            ⚠
          </span>
        )}
      </div>
      <SortableContext items={itemIds} strategy={verticalListSortingStrategy}>
        <div className="flex flex-1 flex-col gap-2">{children}</div>
      </SortableContext>
    </div>
  )
}
