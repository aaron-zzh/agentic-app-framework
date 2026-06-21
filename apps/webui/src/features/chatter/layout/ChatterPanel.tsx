/**
 * ChatterPanel——对话面板（Toolbar + Thread + TaskBoard + Composer）
 * DroppableComposer 包裹输入区，接收拖放附件
 * 当有活跃任务时展示 TaskBoardPanel，收到恢复事件时展示通知
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { AuiIf, useVoiceState } from "@assistant-ui/react"
import type { ReactNode } from "react"
import { deriveVoiceOrbState, VoiceControl, VoiceOrb } from "@/components/voice"
import { ChatterComposer } from "@/features/chatter/composer"
import { DroppableComposer } from "@/features/chatter/dnd/DroppableComposer"
import { useTaskBoard } from "@/features/chatter/hooks/use-task-board"
import { RecoveryNotification } from "@/features/chatter/task/RecoveryNotification"
import { TaskBoardPanel } from "@/features/chatter/task/TaskBoardPanel"
import { ToolConfirmOverlay } from "@/features/chatter/task/ToolConfirmOverlay"
import { ChatterThread } from "@/features/chatter/thread"
import type { ChatterDropItem } from "@/features/chatter/types"
import type { AiModelVO } from "@/lib/api/rest/ai/ai-model"

interface ChatterPanelProps {
  toolbar: ReactNode
  attachments: ChatterDropItem[]
  onAttachmentRemove: (index: number) => void
  onAttachmentAdd: (item: ChatterDropItem) => void
  sessionId?: string
  modelId?: string
  onModelChange?: (modelId: string, model: AiModelVO) => void
  /** 是否显示模型选择器（未登录 guest preset 应传 false） */
  showModelSelector?: boolean
}

export function ChatterPanel({
  toolbar,
  attachments,
  onAttachmentRemove,
  onAttachmentAdd,
  sessionId,
  modelId,
  onModelChange,
  showModelSelector
}: ChatterPanelProps) {
  const { tasks, progress, isLoading, recovered, dismissRecovery } = useTaskBoard(sessionId)

  return (
    <div className="flex h-full flex-col">
      {toolbar}
      {/* 通话中显示语音控制区（Orb 动画 + 控制栏） */}
      <AuiIf condition={(s) => s.thread.voice != null && s.thread.voice.status.type !== "ended"}>
        <div className="flex flex-col items-center gap-2 border-b py-4">
          <ActiveVoiceOrb />
          <VoiceControl className="border-none py-0" />
        </div>
      </AuiIf>
      {recovered && (
        <div className="px-3 pt-2">
          <RecoveryNotification taskCount={recovered.taskCount} onDismiss={dismissRecovery} />
        </div>
      )}
      <ChatterThread />
      <ToolConfirmOverlay />
      <TaskBoardPanel tasks={tasks} progress={progress} isLoading={isLoading} />
      <DroppableComposer onDrop={onAttachmentAdd}>
        <ChatterComposer
          attachments={attachments}
          onAttachmentRemove={onAttachmentRemove}
          modelId={modelId}
          onModelChange={onModelChange}
          showModelSelector={showModelSelector}
        />
      </DroppableComposer>
    </div>
  )
}

/** 读取 voice 状态并传给 VoiceOrb */
function ActiveVoiceOrb() {
  const voiceState = useVoiceState()
  const state = deriveVoiceOrbState(voiceState)
  return <VoiceOrb state={state} className="size-20" />
}
