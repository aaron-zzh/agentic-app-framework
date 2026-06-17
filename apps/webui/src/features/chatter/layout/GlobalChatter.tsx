/**
 * GlobalChatter——单例 Chatter，三种模式共享同一 runtime
 *
 * 通过 React Portal 将 ChatterPanel 渲染到对应容器：
 * - mode=dialog：浮窗（固定右下角，可拖拽）
 * - mode=panel：Portal 到 #chatter-panel-slot
 * - mode=page：Portal 到 #chatter-page-slot
 *
 * 挂在 WorkspaceLayout，全应用只初始化一次 runtime。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { createPortal } from "react-dom"
import { ChatterPanel } from "@/features/chatter/layout/ChatterPanel"
import { GlobalChatterDialog } from "@/features/chatter/layout/GlobalChatterDialog"
import { ChatterRuntime } from "@/features/chatter/runtime/ChatterRuntime"
import { ChatterToolbar } from "@/features/chatter/toolbar/ChatterToolbar"
import type { ChatterDropItem, ChatterTarget } from "@/features/chatter/types"
import { useChatterStore } from "@/lib/store/chatter-store"

export function GlobalChatter() {
  const open = useChatterStore((s) => s.open)
  const mode = useChatterStore((s) => s.mode)
  const currentPageId = useChatterStore((s) => s.currentPageId)
  const getConfig = useChatterStore((s) => s.getConfig)
  const pendingDropItem = useChatterStore((s) => s.pendingDropItem)
  const setPendingDropItem = useChatterStore((s) => s.setPendingDropItem)

  const config = currentPageId
    ? getConfig(currentPageId)
    : { preset: "ai" as const, open: false, agentRole: "default-generalist" }

  const [target, setTarget] = useState<ChatterTarget>({ type: "ai", agentRole: config.agentRole })
  const [attachments, setAttachments] = useState<ChatterDropItem[]>([])

  // 消费全局 DnD
  useEffect(() => {
    if (!pendingDropItem) return
    setAttachments((prev) => [...prev, pendingDropItem])
    setPendingDropItem(null)
  }, [pendingDropItem, setPendingDropItem])

  const [panelSlot, setPanelSlot] = useState<Element | null>(null)
  const [pageSlot, setPageSlot] = useState<Element | null>(null)

  useEffect(() => {
    const observe = () => {
      setPanelSlot(document.getElementById("chatter-panel-slot"))
      setPageSlot(document.getElementById("chatter-page-slot"))
    }
    observe()
    const observer = new MutationObserver(observe)
    observer.observe(document.body, { childList: true, subtree: true })
    return () => observer.disconnect()
  }, [])

  // dialog 模式的拖拽状态（由 GlobalChatter 管理，注入到 ChatterToolbar header）
  const [pos, setPos] = useState({ x: 0, y: 0 })
  const dragStart = useRef<{ mx: number; my: number; px: number; py: number } | null>(null)

  const handleDragDown = useCallback(
    (e: React.PointerEvent<HTMLElement>) => {
      if ((e.target as HTMLElement).closest("button,select,[role=combobox]")) return
      e.currentTarget.setPointerCapture(e.pointerId)
      dragStart.current = { mx: e.clientX, my: e.clientY, px: pos.x, py: pos.y }
    },
    [pos]
  )
  const handleDragMove = useCallback((e: React.PointerEvent<HTMLElement>) => {
    if (!dragStart.current) return
    setPos({
      x: dragStart.current.px + e.clientX - dragStart.current.mx,
      y: dragStart.current.py + e.clientY - dragStart.current.my
    })
  }, [])
  const handleDragUp = useCallback(() => {
    dragStart.current = null
  }, [])

  const dialogDragProps =
    mode === "dialog"
      ? {
          onPointerDown: handleDragDown,
          onPointerMove: handleDragMove,
          onPointerUp: handleDragUp,
          className: "cursor-grab active:cursor-grabbing select-none"
        }
      : undefined

  const toolbar = (
    <ChatterToolbar
      preset={config.preset}
      target={target}
      onTargetChange={setTarget}
      onNewSession={() => setAttachments([])}
      dragProps={dialogDragProps}
    />
  )

  const panel = (
    <ChatterPanel
      toolbar={toolbar}
      attachments={attachments}
      onAttachmentRemove={(i) => setAttachments((prev) => prev.filter((_, idx) => idx !== i))}
      onAttachmentAdd={(item) => setAttachments((prev) => [...prev, item])}
    />
  )

  return (
    <ChatterRuntime target={target}>
      {mode === "dialog" && (
        <GlobalChatterDialog open={open} pos={pos}>
          {panel}
        </GlobalChatterDialog>
      )}

      {mode === "panel" &&
        open &&
        panelSlot &&
        createPortal(<div className="flex h-full flex-col">{panel}</div>, panelSlot)}

      {mode === "page" &&
        pageSlot &&
        createPortal(<div className="flex h-full flex-col">{panel}</div>, pageSlot)}
    </ChatterRuntime>
  )
}
