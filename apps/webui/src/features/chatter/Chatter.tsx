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

import { useCallback, useEffect, useState } from "react"
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
  ChatterLayout as LayoutType
} from "./types"

/** 根据 preset 和 props 生成初始 target */
function presetToTarget(props: ChatterProps): ChatterTarget {
  switch (props.preset) {
    case "kiro":
      return { type: "kiro", agentRole: props.agentRole }
    case "livechat":
      return { type: "user", userId: props.targetUserId }
    case "guest":
      return { type: "ai", agentRole: "customer-service" }
    default:
      return { type: "ai", agentRole: props.agentRole }
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
    hideToolbar
  } = props
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const effectiveLayout = resolveLayout(layout, preset, isAuthenticated)

  const [mounted, setMounted] = useState(false)
  const [target, setTarget] = useState<ChatterTarget>(() => presetToTarget(props))
  const [isOpen, setIsOpen] = useState(open ?? (effectiveLayout === "dialog" && !isAuthenticated))
  const [attachments, setAttachments] = useState<ChatterDropItem[]>([])
  const [modelId, setModelId] = useState<string>("")

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
    setAttachments((prev) => [...prev, pendingDropItem])
    onDrop?.(pendingDropItem)
    setPendingDropItem(null)
  }, [pendingDropItem, onDrop, setPendingDropItem])

  const handleAttachmentRemove = useCallback((index: number) => {
    setAttachments((prev) => prev.filter((_, i) => i !== index))
  }, [])

  const handleNewSession = useCallback(() => {
    setAttachments([])
  }, [])

  // 避免 SSR 时 isAuthenticated=false 导致 panel 降级为 dialog 产生闪烁
  if (!mounted && (layout === "panel" || layout === "page")) return null

  return (
    <ChatterRuntime target={target} persist={persist} sessionId={props.sessionId} modelId={modelId}>
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
          sessionId={props.sessionId}
          modelId={modelId}
          onModelChange={setModelId}
          showModelSelector={preset !== "guest" && preset !== "livechat"}
        />
      </ChatterLayout>
    </ChatterRuntime>
  )
}
