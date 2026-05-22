/**
 * DraggableItem——包装任意元素使其可拖拽
 * 使用 @dnd-kit/core 的 useDraggable，data 为 ChatterDropItem
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useDraggable } from "@dnd-kit/core"
import type { ReactNode } from "react"
import type { ChatterDropItem } from "../types"

interface DraggableItemProps {
  item: ChatterDropItem
  children: ReactNode
}

/**
 * 拖拽包装器
 * 将 children 包装为可拖拽元素，拖拽数据为 ChatterDropItem
 */
export function DraggableItem({ item, children }: DraggableItemProps) {
  const id = `chatter-drag-${item.type}-${item.id ?? item.title ?? "unknown"}`
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id,
    data: item
  })

  return (
    <div ref={setNodeRef} style={{ opacity: isDragging ? 0.5 : 1 }} {...listeners} {...attributes}>
      {children}
    </div>
  )
}
