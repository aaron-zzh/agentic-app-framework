/**
 * ChatterPanel——对话面板（Toolbar + Thread + canonical Task + Composer）。
 * assistant-ui threadId 与后端 ConversationId 一致，TaskDetails 按该标识投影。
 * @author AaronZZH & Kiro
 */

"use client"

import { useAuiState } from "@assistant-ui/react"
import type { ReactNode } from "react"
import { ChatterComposer } from "@/features/chatter/composer"
import { DroppableComposer } from "@/features/chatter/dnd/DroppableComposer"
import { useTaskList } from "@/features/chatter/hooks/use-task-list"
import { ClarificationInterruptPanel } from "@/features/chatter/runtime/ui-block/ClarificationInterruptPanel"
import { TaskPanel } from "@/features/chatter/task/TaskPanel"
import { ToolConfirmOverlay } from "@/features/chatter/task/ToolConfirmOverlay"
import { ChatterThread } from "@/features/chatter/thread"
import type {
  ChatterDisplayPreferences,
  ChatterDropItem,
  TaskModelSelection
} from "@/features/chatter/types"

interface ChatterPanelProps {
  toolbar: ReactNode
  attachments: ChatterDropItem[]
  onAttachmentRemove: (index: number) => void
  onAttachmentAdd: (item: ChatterDropItem) => void
  taskModelSelection: TaskModelSelection
  onTaskModelSelectionChange: (selection: TaskModelSelection) => void
  /** 是否为匿名客服模式。 */
  guestMode?: boolean
  /** 是否显示模型选择器（未登录 guest preset 应传 false） */
  showModelSelector?: boolean
  displayPreferences: ChatterDisplayPreferences
  onDisplayPreferencesChange: (preferences: ChatterDisplayPreferences) => void
}

export function ChatterPanel({
  toolbar,
  attachments,
  onAttachmentRemove,
  onAttachmentAdd,
  taskModelSelection,
  onTaskModelSelectionChange,
  guestMode = false,
  showModelSelector,
  displayPreferences,
  onDisplayPreferencesChange
}: ChatterPanelProps) {
  const currentThreadId = useAuiState((state) => state.threads.mainThreadId)
  const conversationId = guestMode || currentThreadId === "main" ? undefined : currentThreadId
  const { tasks, progress, isLoading } = useTaskList(conversationId)

  const composer = (
    <ChatterComposer
      guestMode={guestMode}
      attachments={attachments}
      onAttachmentRemove={onAttachmentRemove}
      onPasteText={guestMode ? undefined : onAttachmentAdd}
      onAfterSend={() => {
        // 发送后从后往前移除 text chip，避免 index 偏移
        for (let i = attachments.length - 1; i >= 0; i--) {
          if (attachments[i].type === "text") onAttachmentRemove(i)
        }
      }}
      taskModelSelection={taskModelSelection}
      onTaskModelSelectionChange={onTaskModelSelectionChange}
      showModelSelector={showModelSelector}
      displayPreferences={displayPreferences}
      onDisplayPreferencesChange={onDisplayPreferencesChange}
    />
  )

  return (
    <div className="flex h-full flex-col">
      {toolbar}
      <ChatterThread guestMode={guestMode} showThinking={displayPreferences.showThinking} />
      {!guestMode && <ClarificationInterruptPanel />}
      {!guestMode && <ToolConfirmOverlay />}
      {!guestMode && displayPreferences.showPlan && (
        <TaskPanel tasks={tasks} progress={progress} isLoading={isLoading} />
      )}
      {guestMode ? (
        composer
      ) : (
        <DroppableComposer onDrop={onAttachmentAdd}>{composer}</DroppableComposer>
      )}
    </div>
  )
}
