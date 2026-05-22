/**
 * Chatter——统一对话组件
 * 组合 Runtime + Layout + Panel + DnD，通过 preset/layout 控制行为和布局
 *
 * @author AaronZZH & Kiro
 *
 * @example
 * ```tsx
 * // AI 助手面板
 * <Chatter preset="ai" layout="panel" />
 *
 * // Kiro Agent 弹窗
 * <Chatter preset="kiro" layout="dialog" open onOpenChange={setOpen} />
 *
 * // 用户聊天抽屉
 * <Chatter preset="livechat" layout="drawer" targetUserId="user-123" />
 * ```
 */

"use client"

import { DndContext, type DragEndEvent } from "@dnd-kit/core"
import { useCallback, useState } from "react"
import { ChatterLayout } from "./ChatterLayout"
import { ChatterPanel } from "./ChatterPanel"
import { ChatterRuntime } from "./ChatterRuntime"
import { ChatterToolbar } from "./ChatterToolbar"
import type { ChatterDropItem, ChatterProps, ChatterTarget } from "./types"

/** 根据 preset 和 props 生成初始 target */
function presetToTarget(props: ChatterProps): ChatterTarget {
  switch (props.preset) {
    case "kiro":
      return { type: "kiro", agentRole: props.agentRole }
    case "livechat":
      return { type: "user", userId: props.targetUserId }
    default:
      return { type: "ai", agentRole: props.agentRole }
  }
}

export function Chatter(props: ChatterProps) {
  const { preset, layout, persist, open, onOpenChange, toolbar, onDrop } = props
  const [target, setTarget] = useState<ChatterTarget>(() => presetToTarget(props))
  const [attachments, setAttachments] = useState<ChatterDropItem[]>([])

  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      const { over, active } = event
      if (!over || over.id !== "chatter-composer-drop") return
      const item = active.data.current as ChatterDropItem | undefined
      if (!item) return
      setAttachments((prev) => [...prev, item])
      onDrop?.(item)
    },
    [onDrop]
  )

  const handleAttachmentRemove = useCallback((index: number) => {
    setAttachments((prev) => prev.filter((_, i) => i !== index))
  }, [])

  const handleNewSession = useCallback(() => {
    // 新建会话：清空附件，后续可扩展 sessionId 管理
    setAttachments([])
  }, [])

  return (
    <DndContext onDragEnd={handleDragEnd}>
      <ChatterRuntime target={target} persist={persist}>
        <ChatterLayout layout={layout} open={open} onOpenChange={onOpenChange}>
          <ChatterPanel
            toolbar={
              <ChatterToolbar
                preset={preset}
                target={target}
                onTargetChange={setTarget}
                onNewSession={handleNewSession}
                toolbar={toolbar}
              />
            }
            attachments={attachments}
            onAttachmentRemove={handleAttachmentRemove}
            onAttachmentAdd={(item) => setAttachments((prev) => [...prev, item])}
          />
        </ChatterLayout>
      </ChatterRuntime>
    </DndContext>
  )
}
