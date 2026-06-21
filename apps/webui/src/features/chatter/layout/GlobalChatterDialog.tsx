/**
 * GlobalChatterDialog——可 resize 浮窗容器
 *
 * 定位策略：弹窗右下角锚点由外部传入 anchor（来自 buttonPos + size + gap），
 * 弹窗右边缘对齐按钮右边缘、底部位于按钮上方 GAP；
 * 拖动 toolbar 直接驱动 buttonPos（在 GlobalChatter 中实现），
 * 弹窗位置随 anchor 实时变化，无需独立的 transform 偏移层。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { type ReactNode, useCallback, useRef, useState } from "react"

interface GlobalChatterDialogProps {
  open: boolean
  /** 弹窗右下角对齐的视口边距（来自浮动按钮位置 + 间距） */
  anchor: { right: number; bottom: number }
  children: ReactNode
}

export function GlobalChatterDialog({ open, anchor, children }: GlobalChatterDialogProps) {
  const [size, setSize] = useState({ w: 380, h: 560 })
  const resizeStart = useRef<{ mx: number; my: number; w: number; h: number } | null>(null)

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
      className="fixed z-50 flex flex-col overflow-hidden rounded-xl shadow-xl outline-hidden [background:linear-gradient(135deg,color-mix(in_oklch,var(--color-violet-500)_6%,transparent),transparent_50%,color-mix(in_oklch,var(--color-indigo-500)_6%,transparent)),var(--color-popover)]"
      style={{
        right: anchor.right,
        bottom: anchor.bottom,
        width: size.w,
        height: size.h
      }}
    >
      <div className="min-h-0 flex-1 overflow-hidden">{children}</div>

      {/* resize 手柄 */}
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
