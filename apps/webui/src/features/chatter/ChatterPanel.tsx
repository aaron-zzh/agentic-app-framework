/**
 * ChatterPanel——对话面板（Toolbar + Thread + TaskBoard + Composer）
 * DroppableComposer 包裹输入区，接收拖放附件
 * 当有活跃任务时展示 TaskBoardPanel，收到恢复事件时展示通知
 *
 * @author AaronZZH & Kiro
 */

"use client"

import type { ReactNode } from "react"
import { ChatterComposer } from "./ChatterComposer"
import { ChatterThread } from "./ChatterThread"
import { DroppableComposer } from "./dnd/DroppableComposer"
import { useTaskBoard } from "./hooks/use-task-board"
import { RecoveryNotification } from "./RecoveryNotification"
import { TaskBoardPanel } from "./TaskBoardPanel"
import type { ChatterDropItem } from "./types"

interface ChatterPanelProps {
  toolbar: ReactNode
  attachments: ChatterDropItem[]
  onAttachmentRemove: (index: number) => void
  onAttachmentAdd: (item: ChatterDropItem) => void
  /** 当前会话 ID，用于订阅 TaskBoard */
  sessionId?: string
}

export function ChatterPanel({
  toolbar,
  attachments,
  onAttachmentRemove,
  onAttachmentAdd,
  sessionId
}: ChatterPanelProps) {
  const { tasks, progress, isLoading, recovered, dismissRecovery } = useTaskBoard(sessionId)

  return (
    <div className="flex h-full flex-col">
      {toolbar}
      {/* 断点恢复通知 */}
      {recovered && (
        <div className="px-3 pt-2">
          <RecoveryNotification taskCount={recovered.taskCount} onDismiss={dismissRecovery} />
        </div>
      )}
      <ChatterThread />
      {/* 子任务进度面板（有活跃任务时展示） */}
      <TaskBoardPanel tasks={tasks} progress={progress} isLoading={isLoading} />
      {/* DroppableComposer 只包裹输入区，接收拖放附件 */}
      <DroppableComposer onDrop={onAttachmentAdd}>
        <ChatterComposer attachments={attachments} onAttachmentRemove={onAttachmentRemove} />
      </DroppableComposer>
    </div>
  )
}
