/**
 * 统一语义拖放 hook
 * 任何组件调用此 hook 即获得拖放到对话框的能力
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useDraggable } from "@dnd-kit/core"
import { useMemo } from "react"

import type { ChatterDropItem } from "../types"

interface UseSemanticDraggableOptions {
  /** 唯一标识 */
  id: string
  /** 拖放数据 */
  item: ChatterDropItem
  /** 是否禁用 */
  disabled?: boolean
}

function truncate(str: string, max: number): string {
  return str.length > max ? `${str.slice(0, max)}…` : str
}

/**
 * 语义拖放 hook
 * 返回 ref/listeners/attributes 绑定到目标元素即可拖放到对话框
 */
export function useSemanticDraggable({ id, item, disabled = false }: UseSemanticDraggableOptions) {
  const enrichedItem = useMemo<ChatterDropItem>(
    () => ({
      ...item,
      summary: item.summary ?? truncate(item.title ?? item.content ?? "", 100)
    }),
    [item]
  )

  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: `semantic-drag-${id}`,
    data: enrichedItem,
    disabled
  })

  return { ref: setNodeRef, listeners, attributes, isDragging }
}
