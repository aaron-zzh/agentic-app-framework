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

import { Sparkles, X } from "lucide-react"
import { useCallback, useEffect, useRef, useState } from "react"
import { createPortal } from "react-dom"
import { Button } from "@/components/ui/button"
import { ChatterPanel } from "@/features/chatter/layout/ChatterPanel"
import { GlobalChatterDialog } from "@/features/chatter/layout/GlobalChatterDialog"
import { ChatterRuntime } from "@/features/chatter/runtime/ChatterRuntime"
import { ChatterToolbar } from "@/features/chatter/toolbar/ChatterToolbar"
import type { ChatterDropItem, ChatterTarget } from "@/features/chatter/types"
import { useAuthStore } from "@/lib/store/auth-store"
import { useChatterStore } from "@/lib/store/chatter-store"

interface GlobalChatterProps {
  /**
   * 当前布局支持的非 dialog 模式。
   * - WorkspaceLayout 传 ["panel", "page"]（有 chatter-panel-slot/chatter-page-slot）
   * - MarketingLayout 等公开布局不传，默认仅 dialog
   */
  availableModes?: ("panel" | "page")[]
}

export function GlobalChatter({ availableModes = [] }: GlobalChatterProps = {}) {
  const open = useChatterStore((s) => s.open)
  const setOpen = useChatterStore((s) => s.setOpen)
  const mode = useChatterStore((s) => s.mode)
  const currentPageId = useChatterStore((s) => s.currentPageId)
  const getConfig = useChatterStore((s) => s.getConfig)
  const pendingDropItem = useChatterStore((s) => s.pendingDropItem)
  const setPendingDropItem = useChatterStore((s) => s.setPendingDropItem)
  const buttonPos = useChatterStore((s) => s.buttonPos)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)

  // 未登录强制 guest preset（AI 客服），不读 page 级配置；
  // 已登录但无 currentPageId 时用 ai 默认助理
  const config =
    isAuthenticated && currentPageId
      ? getConfig(currentPageId)
      : isAuthenticated
        ? { preset: "ai" as const, open: false, agentRole: "default-generalist" }
        : { preset: "guest" as const, open: false, agentRole: "customer-service" }

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

  // dialog 模式：拖 toolbar 直接驱动 buttonPos，按钮与弹窗保持锚定，不再用独立 pos 偏移
  const setButtonPos = useChatterStore((s) => s.setButtonPos)
  const dragStart = useRef<{ mx: number; my: number; pr: number; pb: number } | null>(null)

  const handleDragDown = useCallback(
    (e: React.PointerEvent<HTMLElement>) => {
      if ((e.target as HTMLElement).closest("button,select,[role=combobox]")) return
      e.currentTarget.setPointerCapture(e.pointerId)
      dragStart.current = {
        mx: e.clientX,
        my: e.clientY,
        pr: buttonPos.right,
        pb: buttonPos.bottom
      }
    },
    [buttonPos]
  )
  const handleDragMove = useCallback(
    (e: React.PointerEvent<HTMLElement>) => {
      const s = dragStart.current
      if (!s) return
      const dx = e.clientX - s.mx
      const dy = e.clientY - s.my
      const vw = window.innerWidth
      const vh = window.innerHeight
      // 鼠标右下移 → buttonPos.right/bottom 减小（按钮向右下移）
      setButtonPos({
        right: clamp(s.pr - dx, 0, vw - 100),
        bottom: clamp(s.pb - dy, 0, vh - 100)
      })
    },
    [setButtonPos]
  )
  const handleDragUp = useCallback((e: React.PointerEvent<HTMLElement>) => {
    if (e.currentTarget.hasPointerCapture(e.pointerId)) {
      e.currentTarget.releasePointerCapture(e.pointerId)
    }
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

  // guest preset（未登录 AI 客服）渲染最简 header：助理名 + 关闭按钮，保留 dialog 拖动
  const toolbar =
    config.preset === "guest" ? (
      <div
        {...dialogDragProps}
        className={`flex items-center gap-2 overflow-hidden border-b px-3 py-2${
          dialogDragProps?.className ? ` ${dialogDragProps.className}` : ""
        }`}
      >
        <Sparkles className="size-3.5 shrink-0 text-primary" />
        <span className="flex-1 truncate font-medium text-sm">AI 客服</span>
        <Button variant="ghost" size="icon-sm" aria-label="关闭" onClick={() => setOpen(false)}>
          <X className="size-3.5" />
        </Button>
      </div>
    ) : (
      <ChatterToolbar
        preset={config.preset}
        target={target}
        onTargetChange={setTarget}
        onNewSession={() => setAttachments([])}
        dragProps={dialogDragProps}
        availableModes={availableModes}
        hideRoleSwitch
      />
    )

  const panel = (
    <ChatterPanel
      toolbar={toolbar}
      attachments={attachments}
      onAttachmentRemove={(i) => setAttachments((prev) => prev.filter((_, idx) => idx !== i))}
      onAttachmentAdd={(item) => setAttachments((prev) => [...prev, item])}
      showModelSelector={config.preset !== "guest"}
    />
  )

  return (
    <ChatterRuntime target={target}>
      {mode === "dialog" && (
        <GlobalChatterDialog
          open={open}
          anchor={{
            // 弹窗右边对齐按钮右边、底部位于按钮上方 8px
            right: buttonPos.right,
            bottom: buttonPos.bottom + 100 + 8
          }}
        >
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

function clamp(v: number, min: number, max: number) {
  return Math.max(min, Math.min(max, v))
}
