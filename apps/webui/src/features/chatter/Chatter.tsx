/**
 * Chatter——统一对话组件
 * 组合 Runtime + Layout + Panel + DnD，通过 preset/layout 控制行为和布局
 *
 * 未登录时 panel/page 布局自动降级为 dialog（livechat preset 除外，访客也可对话）
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

import { useCallback, useEffect, useMemo, useState } from "react"
import { useAuthStore } from "@/lib/store/auth-store"
import { useChatterStore } from "@/lib/store/chatter-store"
import { ChatterLayout } from "./layout/ChatterLayout"
import { ChatterPanel } from "./layout/ChatterPanel"
import { ChatterRuntime } from "./runtime/ChatterRuntime"
import { ChatterToolbar } from "./toolbar/ChatterToolbar"
import type {
  ChatterDropItem,
  ChatterProps,
  ChatterTarget,
  ChatterLayout as LayoutType,
  TaskModelSelection
} from "./types"
import { DEFAULT_CHATTER_DISPLAY_PREFERENCES, DEFAULT_TASK_MODEL_SELECTION } from "./types"

/** 根据 preset、props 与认证态生成 target。 */
function presetToTarget(
  preset: ChatterProps["preset"],
  isAuthenticated: boolean,
  agentRole?: string,
  agentSkill?: string,
  targetUserId?: string
): ChatterTarget {
  switch (preset) {
    case "kiro":
      return { type: "kiro", agentRole }
    case "livechat":
      return { type: "user", userId: targetUserId }
    case "guest":
      return isAuthenticated ? { type: "ai" } : { type: "guest" }
    default:
      return { type: "ai", agentRole, agentSkill }
  }
}

/**
 * 未登录时 panel/page 降级为 dialog
 * livechat preset 不降级（访客也可使用客服对话）
 */
function resolveLayout(
  layout: LayoutType,
  preset: ChatterProps["preset"],
  isAuthenticated: boolean
): LayoutType {
  if (isAuthenticated) return layout
  if (preset === "livechat" || preset === "guest") return layout
  if (layout === "panel" || layout === "page") return "dialog"
  return layout
}

export function Chatter(props: ChatterProps) {
  const {
    preset,
    layout,
    persist,
    open,
    onOpenChange,
    onLayoutChange,
    toolbar,
    onDrop,
    hideToolbar,
    agentRole,
    agentSkill,
    targetUserId
  } = props
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const effectiveLayout = resolveLayout(layout, preset, isAuthenticated)

  const [mounted, setMounted] = useState(false)
  const [target, setTarget] = useState<ChatterTarget>(() =>
    presetToTarget(preset, isAuthenticated, agentRole, agentSkill, targetUserId)
  )
  const [isOpen, setIsOpen] = useState(open ?? (effectiveLayout === "dialog" && !isAuthenticated))
  const [attachments, setAttachments] = useState<ChatterDropItem[]>([])
  const [taskModelSelection, setTaskModelSelection] = useState<TaskModelSelection>(
    DEFAULT_TASK_MODEL_SELECTION
  )
  const [displayPreferences, setDisplayPreferences] = useState(DEFAULT_CHATTER_DISPLAY_PREFERENCES)

  // 认证变化必须回到当前 preset 的合法 target；Provider 会按模式 key 重建线程。
  useEffect(() => {
    setTarget(presetToTarget(preset, isAuthenticated, agentRole, agentSkill, targetUserId))
    setAttachments([])
  }, [isAuthenticated, preset, agentRole, agentSkill, targetUserId])

  useEffect(() => {
    setMounted(true)
  }, [])

  // 外部受控 open prop 变化时同步内部状态
  useEffect(() => {
    if (open !== undefined) setIsOpen(open)
  }, [open])
  const handleOpenChange = useCallback(
    (v: boolean) => {
      setIsOpen(v)
      onOpenChange?.(v)
    },
    [onOpenChange]
  )

  const pendingDropItem = useChatterStore((s) => s.pendingDropItem)
  const setPendingDropItem = useChatterStore((s) => s.setPendingDropItem)

  // 消费全局 DnD 落下的附件
  useEffect(() => {
    if (!pendingDropItem) return
    if (preset === "guest" && !isAuthenticated) {
      setPendingDropItem(null)
      return
    }
    setAttachments((prev) => [...prev, pendingDropItem])
    onDrop?.(pendingDropItem)
    setPendingDropItem(null)
  }, [pendingDropItem, preset, isAuthenticated, onDrop, setPendingDropItem])

  const handleAttachmentRemove = useCallback((index: number) => {
    setAttachments((prev) => prev.filter((_, i) => i !== index))
  }, [])

  const handleNewSession = useCallback(() => {
    setAttachments([])
  }, [])

  const runtimeTarget = useMemo(() => {
    if (preset !== "guest") return target
    if (!isAuthenticated) return { type: "guest" } satisfies ChatterTarget
    return target.type === "guest" ? ({ type: "ai" } satisfies ChatterTarget) : target
  }, [isAuthenticated, preset, target])
  const taskModelSelectionEnabled =
    preset === "ai" && runtimeTarget.type === "ai" && isAuthenticated

  // 避免 SSR 时 isAuthenticated=false 导致 panel 降级为 dialog 产生闪烁
  if (!mounted && (layout === "panel" || layout === "page")) return null

  return (
    <ChatterRuntime
      target={runtimeTarget}
      persist={persist}
      sessionId={props.sessionId}
      taskModelSelection={taskModelSelectionEnabled ? taskModelSelection : undefined}
      displayPreferences={displayPreferences}
    >
      <ChatterLayout
        layout={effectiveLayout}
        open={open ?? isOpen}
        onOpenChange={handleOpenChange}
        onLayoutChange={onLayoutChange}
        dialogWidth={props.dialogWidth}
        dialogHeight={props.dialogHeight}
        dialogAnchor={props.dialogAnchor}
        title={preset === "guest" ? "AI 客服" : preset === "livechat" ? "客服" : "AI 助理"}
      >
        <ChatterPanel
          toolbar={
            hideToolbar || preset === "livechat" || preset === "guest" ? null : (
              <ChatterToolbar
                preset={preset}
                target={target}
                onTargetChange={setTarget}
                onNewSession={handleNewSession}
                toolbar={toolbar}
              />
            )
          }
          attachments={attachments}
          onAttachmentRemove={handleAttachmentRemove}
          onAttachmentAdd={(item) => setAttachments((prev) => [...prev, item])}
          taskModelSelection={taskModelSelection}
          onTaskModelSelectionChange={setTaskModelSelection}
          guestMode={runtimeTarget.type === "guest"}
          showModelSelector={taskModelSelectionEnabled}
          displayPreferences={displayPreferences}
          onDisplayPreferencesChange={setDisplayPreferences}
        />
      </ChatterLayout>
    </ChatterRuntime>
  )
}
