/**
 * DroppableComposer——包装 Composer 接收拖放
 * 使用 @dnd-kit/core 的 useDroppable
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useDroppable } from "@dnd-kit/core"
import type { ReactNode } from "react"
import type { ChatterDropItem } from "../types"

interface DroppableComposerProps {
  onDrop: (item: ChatterDropItem) => void
  children: ReactNode
}

export function DroppableComposer({ children }: DroppableComposerProps) {
  const { isOver, setNodeRef } = useDroppable({ id: "chatter-composer-drop" })

  return (
    <div
      ref={setNodeRef}
      className={isOver ? "rounded-md ring-2 ring-primary/50 transition-all" : "transition-all"}
    >
      {children}
    </div>
  )
}
