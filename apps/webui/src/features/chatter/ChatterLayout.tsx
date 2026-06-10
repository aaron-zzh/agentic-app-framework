/**
 * ChatterLayout——根据 layout prop 选择容器
 *
 * panel：直接渲染（由父组件放入 ResizablePanel）
 * dialog：可拖拽 + 可 resize 浮窗，标题栏含嵌入/全屏切换按钮
 * drawer：右侧抽屉
 * page：全屏（h-screen，用于独立路由页面）
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Maximize2, PanelRight, X } from "lucide-react"
import { useRouter } from "next/navigation"
import { type ReactNode, useCallback, useRef, useState } from "react"
import { Sheet, SheetContent } from "@/components/ui/sheet"
import type { ChatterLayout as LayoutType } from "./types"

interface ChatterLayoutProps {
  layout: LayoutType
  open?: boolean
  onOpenChange?: (open: boolean) => void
  onLayoutChange?: (layout: LayoutType) => void
  title?: string
  dialogWidth?: number
  dialogHeight?: number
  children: ReactNode
}

/** 可拖拽 + 可 resize 浮窗（dialog 布局） */
function DraggableDialog({
  open,
  onOpenChange,
  onLayoutChange,
  title,
  dialogWidth = 380,
  dialogHeight = 560,
  children
}: {
  open?: boolean
  onOpenChange?: (open: boolean) => void
  onLayoutChange?: (layout: LayoutType) => void
  title?: string
  dialogWidth?: number
  dialogHeight?: number
  children: ReactNode
}) {
  const [pos, setPos] = useState({ x: 0, y: 0 })
  const [size, setSize] = useState({ w: dialogWidth, h: dialogHeight })
  const dragStart = useRef<{ mx: number; my: number; px: number; py: number } | null>(null)
  const resizeStart = useRef<{ mx: number; my: number; w: number; h: number } | null>(null)
  const router = useRouter()

  // 标题栏拖拽移动
  const handleDragDown = useCallback(
    (e: React.PointerEvent<HTMLDivElement>) => {
      if ((e.target as HTMLElement).closest("button")) return
      e.currentTarget.setPointerCapture(e.pointerId)
      dragStart.current = { mx: e.clientX, my: e.clientY, px: pos.x, py: pos.y }
    },
    [pos]
  )

  const handleDragMove = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    if (!dragStart.current) return
    setPos({
      x: dragStart.current.px + e.clientX - dragStart.current.mx,
      y: dragStart.current.py + e.clientY - dragStart.current.my
    })
  }, [])

  const handleDragUp = useCallback(() => {
    dragStart.current = null
  }, [])

  // 右下角 resize 手柄
  const handleResizeDown = useCallback(
    (e: React.PointerEvent<HTMLDivElement>) => {
      e.stopPropagation()
      e.currentTarget.setPointerCapture(e.pointerId)
      resizeStart.current = { mx: e.clientX, my: e.clientY, w: size.w, h: size.h }
    },
    [size]
  )

  const handleResizeMove = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    if (!resizeStart.current) return
    setSize({
      w: Math.max(280, resizeStart.current.w + e.clientX - resizeStart.current.mx),
      h: Math.max(300, resizeStart.current.h + e.clientY - resizeStart.current.my)
    })
  }, [])

  const handleResizeUp = useCallback(() => {
    resizeStart.current = null
  }, [])

  if (!open) return null

  return (
    <div
      className="fixed right-24 bottom-24 z-50 flex flex-col overflow-hidden rounded-xl shadow-xl outline-hidden [background:linear-gradient(135deg,color-mix(in_oklch,var(--color-violet-500)_6%,transparent),transparent_50%,color-mix(in_oklch,var(--color-indigo-500)_6%,transparent)),var(--color-popover)]"
      style={{ width: size.w, height: size.h, transform: `translate(${pos.x}px, ${pos.y}px)` }}
    >
      {/* 标题栏：拖拽 + 操作按钮 */}
      <div
        className="flex cursor-grab select-none items-center justify-between border-white/10 border-b px-3 py-2 active:cursor-grabbing"
        onPointerDown={handleDragDown}
        onPointerMove={handleDragMove}
        onPointerUp={handleDragUp}
      >
        <span className="text-muted-foreground text-xs">{title}</span>
        <div className="flex items-center gap-0.5">
          {/* 切换为嵌入 panel */}
          {onLayoutChange && (
            <button
              type="button"
              onClick={() => onLayoutChange("panel")}
              className="rounded p-0.5 hover:bg-muted"
              aria-label="嵌入侧边"
            >
              <PanelRight className="size-3.5" />
            </button>
          )}
          {/* 切换为全屏 */}
          {onLayoutChange && (
            <button
              type="button"
              onClick={() => {
                onOpenChange?.(false)
                router.push("/ai/chat")
              }}
              className="rounded p-0.5 hover:bg-muted"
              aria-label="全屏"
            >
              <Maximize2 className="size-3.5" />
            </button>
          )}
          <button
            type="button"
            onClick={() => onOpenChange?.(false)}
            className="rounded p-0.5 hover:bg-muted"
            aria-label="关闭"
          >
            <X className="size-4" />
          </button>
        </div>
      </div>

      <div className="min-h-0 flex-1">{children}</div>

      {/* 右下角 resize 手柄 */}
      <div
        className="absolute right-0 bottom-0 size-4 cursor-se-resize opacity-40 hover:opacity-80"
        onPointerDown={handleResizeDown}
        onPointerMove={handleResizeMove}
        onPointerUp={handleResizeUp}
      >
        <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
          <line
            x1="14"
            y1="4"
            x2="4"
            y2="14"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
          />
          <line
            x1="14"
            y1="8"
            x2="8"
            y2="14"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
          />
          <line
            x1="14"
            y1="12"
            x2="12"
            y2="14"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
          />
        </svg>
      </div>
    </div>
  )
}

/**
 * 布局容器选择器
 */
export function ChatterLayout({
  layout,
  open,
  onOpenChange,
  onLayoutChange,
  title,
  dialogWidth,
  dialogHeight,
  children
}: ChatterLayoutProps) {
  if (layout === "dialog") {
    return (
      <DraggableDialog
        open={open}
        onOpenChange={onOpenChange}
        onLayoutChange={onLayoutChange}
        title={title}
        dialogWidth={dialogWidth}
        dialogHeight={dialogHeight}
      >
        {children}
      </DraggableDialog>
    )
  }

  if (layout === "drawer") {
    return (
      <Sheet open={open} onOpenChange={onOpenChange}>
        <SheetContent side="right" className="flex w-[400px] flex-col p-0 sm:max-w-[400px]">
          {children}
        </SheetContent>
      </Sheet>
    )
  }

  if (layout === "page") {
    return <div className="flex h-screen w-full flex-col">{children}</div>
  }

  // panel：直接渲染，由父组件放入 ResizablePanel
  return <div className="flex h-full flex-col">{children}</div>
}
