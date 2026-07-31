/**
 * ChatterPanel——对话面板（Toolbar + Thread + DelegatedTask + Composer）。
 * assistant-ui threadId 与后端 ConversationId 一致，任务数据按该标识查询。
 * @author AaronZZH & Kiro
 */

"use client"

import { useAuiState } from "@assistant-ui/react"
import type { ReactNode } from "react"
import { ChatterComposer } from "@/features/chatter/composer"
import { DroppableComposer } from "@/features/chatter/dnd/DroppableComposer"
import { useTaskBoard } from "@/features/chatter/hooks/use-task-board"
import { TaskBoardPanel } from "@/features/chatter/task/TaskBoardPanel"
import { ToolConfirmOverlay } from "@/features/chatter/task/ToolConfirmOverlay"
import { ChatterThread } from "@/features/chatter/thread"
import type { ChatterDropItem, TaskModelSelection } from "@/features/chatter/types"

interface ChatterPanelProps {
  toolbar: ReactNode
  attachments: ChatterDropItem[]
  onAttachmentRemove: (index: number) => void
  onAttachmentAdd: (item: ChatterDropItem) => void
  taskModelSelection: TaskModelSelection
  onTaskModelSelectionChange: (selection: TaskModelSelection) => void
  /** 是否显示模型选择器（未登录 guest preset 应传 false） */
  showModelSelector?: boolean
}

export function ChatterPanel({
  toolbar,
  attachments,
  onAttachmentRemove,
  onAttachmentAdd,
  taskModelSelection,
  onTaskModelSelectionChange,
  showModelSelector
}: ChatterPanelProps) {
  const currentThreadId = useAuiState((state) => state.threads.mainThreadId)
  const conversationId = currentThreadId === "main" ? undefined : currentThreadId
  const { tasks, progress, isLoading } = useTaskBoard(conversationId)

  return (
    <div className="flex h-full flex-col">
      {toolbar}
      <ChatterThread />
      <ToolConfirmOverlay tasks={tasks} />
      <TaskBoardPanel tasks={tasks} progress={progress} isLoading={isLoading} />
      <DroppableComposer onDrop={onAttachmentAdd}>
        <ChatterComposer
          attachments={attachments}
          onAttachmentRemove={onAttachmentRemove}
          onPasteText={onAttachmentAdd}
          onAfterSend={() => {
            // 发送后从后往前移除 text chip，避免 index 偏移
            for (let i = attachments.length - 1; i >= 0; i--) {
              if (attachments[i].type === "text") onAttachmentRemove(i)
            }
          }}
          taskModelSelection={taskModelSelection}
          onTaskModelSelectionChange={onTaskModelSelectionChange}
          showModelSelector={showModelSelector}
        />
      </DroppableComposer>
    </div>
  )
}
