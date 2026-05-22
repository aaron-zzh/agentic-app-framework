/**
 * ChatterPanel——对话面板（Toolbar + Thread + Composer）
 * DroppableComposer 包裹输入区，接收拖放附件
 *
 * @author AaronZZH & Kiro
 */

"use client"

import type { ReactNode } from "react"
import { ChatterComposer } from "./ChatterComposer"
import { ChatterThread } from "./ChatterThread"
import { DroppableComposer } from "./dnd/DroppableComposer"
import type { ChatterDropItem } from "./types"

interface ChatterPanelProps {
  toolbar: ReactNode
  attachments: ChatterDropItem[]
  onAttachmentRemove: (index: number) => void
  onAttachmentAdd: (item: ChatterDropItem) => void
}

export function ChatterPanel({ toolbar, attachments, onAttachmentRemove, onAttachmentAdd }: ChatterPanelProps) {
  return (
    <div className="flex h-full flex-col">
      {toolbar}
      <ChatterThread />
      {/* DroppableComposer 只包裹输入区，接收拖放附件 */}
      <DroppableComposer onDrop={onAttachmentAdd}>
        <ChatterComposer attachments={attachments} onAttachmentRemove={onAttachmentRemove} />
      </DroppableComposer>
    </div>
  )
}
