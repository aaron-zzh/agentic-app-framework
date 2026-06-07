/**
 * ChatterLayout——根据 layout prop 选择容器
 *
 * panel：直接渲染（由父组件放入 ResizablePanel）
 * dialog：可拖拽浮窗（绝对定位，右下角初始位置，拖拽标题栏移动）
 * drawer：右侧抽屉
 * page：全屏（h-screen，用于独立路由页面）
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { X } from "lucide-react"
import { type ReactNode, useCallback, useRef, useState } from "react"
import { Sheet, SheetContent } from "@/components/ui/sheet"
import type { ChatterLayout as LayoutType } from "./types"

interface ChatterLayoutProps {
  layout: LayoutType
  open?: boolean
  onOpenChange?: (open: boolean) => void
  children: ReactNode
}

/** 可拖拽浮窗（dialog 布局） */
function DraggableDialog({
  open,
  onOpenChange,
  children
}: {
  open?: boolean
  onOpenChange?: (open: boolean) => void
  children: ReactNode
}) {
  // 初始位置：右下角偏移
  const [pos, setPos] = useState({ x: 0, y: 0 })
  const dragStart = useRef<{ mx: number; my: number; px: number; py: number } | null>(null)

  const handlePointerDown = useCallback(
    (e: React.PointerEvent<HTMLDivElement>) => {
      e.currentTarget.setPointerCapture(e.pointerId)
      dragStart.current = { mx: e.clientX, my: e.clientY, px: pos.x, py: pos.y }
    },
    [pos]
  )

  const handlePointerMove = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    if (!dragStart.current) return
    setPos({
      x: dragStart.current.px + e.clientX - dragStart.current.mx,
      y: dragStart.current.py + e.clientY - dragStart.current.my
    })
  }, [])

  const handlePointerUp = useCallback(() => {
    dragStart.current = null
  }, [])

  if (!open) return null

  return (
    <div
      className="fixed right-24 bottom-24 z-50 flex h-[560px] w-[380px] flex-col overflow-hidden rounded-xl border bg-background shadow-xl"
      style={{ transform: `translate(${pos.x}px, ${pos.y}px)` }}
    >
      {/* 拖拽把手（标题栏） */}
      <div
        className="flex cursor-grab select-none items-center justify-between border-b bg-muted/40 px-3 py-2 active:cursor-grabbing"
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
      >
        <span className="text-muted-foreground text-xs">AI 助理</span>
        <button
          type="button"
          onClick={() => onOpenChange?.(false)}
          className="rounded p-0.5 hover:bg-muted"
          aria-label="关闭"
        >
          <X className="size-4" />
        </button>
      </div>
      <div className="min-h-0 flex-1">{children}</div>
    </div>
  )
}

/**
 * 布局容器选择器
 */
export function ChatterLayout({ layout, open, onOpenChange, children }: ChatterLayoutProps) {
  if (layout === "dialog") {
    return (
      <DraggableDialog open={open} onOpenChange={onOpenChange}>
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
