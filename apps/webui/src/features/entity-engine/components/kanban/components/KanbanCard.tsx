/**
 * 看板卡片——可排序的记录卡片，支持选中、条件样式、模板
 * @author AaronZZH & Kiro
 */

"use client"

import { useSortable } from "@dnd-kit/sortable"
import { CSS } from "@dnd-kit/utilities"
import { useMemo } from "react"

import type { EntityDef } from "@/lib/types/entity/entity"
import { cn } from "@/lib/utils/cn"
import { KanbanCardTemplate } from "./KanbanCardTemplate"

interface KanbanCardProps {
  id: string
  title: string
  description?: string
  record?: Record<string, unknown>
  entity?: EntityDef
  /** DragOverlay 中渲染时为 true */
  overlay?: boolean
  /** 是否选中（批量拖拽） */
  selected?: boolean
  /** 选中回调 */
  onSelect?: (id: string, multi: boolean) => void
}

/** 看板卡片 */
export function KanbanCard({
  id,
  title,
  description,
  record,
  entity,
  overlay,
  selected,
  onSelect
}: KanbanCardProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id
  })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition
  }

  const cardTemplate = entity?.kanbanView?.cardTemplate

  // 条件样式计算
  const conditionalStyle = useMemo(() => {
    if (!cardTemplate?.conditionalStyles || !record) return undefined
    for (const rule of cardTemplate.conditionalStyles) {
      if (String(record[rule.field]) === rule.value) {
        return rule
      }
    }
    return undefined
  }, [cardTemplate?.conditionalStyles, record])

  function handleClick(e: React.MouseEvent) {
    if (onSelect) {
      e.stopPropagation()
      onSelect(id, e.ctrlKey || e.metaKey)
    }
  }

  return (
    <div
      ref={setNodeRef}
      style={{
        ...style,
        borderLeftColor: conditionalStyle?.borderColor ?? conditionalStyle?.color ?? undefined,
        borderLeftWidth: conditionalStyle ? "3px" : undefined
      }}
      {...attributes}
      {...listeners}
      onClick={handleClick}
      onKeyDown={undefined}
      className={cn(
        "cursor-grab rounded-md border bg-background p-3 shadow-sm transition-shadow hover:shadow-md",
        isDragging && "opacity-50",
        overlay && "rotate-2 shadow-lg",
        selected && "ring-2 ring-primary"
      )}
    >
      {/* 使用卡片模板渲染或默认渲染 */}
      {cardTemplate && record && entity ? (
        <KanbanCardTemplate record={record} entity={entity} template={cardTemplate} />
      ) : (
        <>
          <p className="font-medium text-sm leading-tight">{title}</p>
          {description && (
            <p className="mt-1 line-clamp-2 text-muted-foreground text-xs">{description}</p>
          )}
        </>
      )}
    </div>
  )
}
