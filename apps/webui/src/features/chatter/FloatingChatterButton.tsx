/**
 * FloatingChatterButton——工作区浮动 Chatter 触发按钮（dialog 模式）
 *
 * 样式复用 FloatingAssistant 的圆形按钮，状态接入 chatter-store。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { BotMessageSquare, ChevronDown } from "lucide-react"
import { useCallback, useRef, useState } from "react"
import { Chatter } from "@/features/chatter"
import type { ChatterLayout, ChatterPreset } from "@/features/chatter/types"
import { useChatterStore } from "@/lib/store/chatter-store"

interface FloatingChatterButtonProps {
  preset: ChatterPreset
  agentRole?: string
}

export function FloatingChatterButton({ preset, agentRole }: FloatingChatterButtonProps) {
  const open = useChatterStore((s) => s.open)
  const setOpen = useChatterStore((s) => s.setOpen)
  const setLayoutOverride = useChatterStore((s) => s.setLayoutOverride)

  // 拖拽位置：相对于视口右下角的偏移（right/bottom），初始值对应 right-5 bottom-5
  const [pos, setPos] = useState({ right: 20, bottom: 20 })
  const dragRef = useRef<{
    startX: number
    startY: number
    startRight: number
    startBottom: number
  } | null>(null)
  const didDrag = useRef(false)

  function handlePointerDown(e: React.PointerEvent<HTMLButtonElement>) {
    e.currentTarget.setPointerCapture(e.pointerId)
    didDrag.current = false
    dragRef.current = {
      startX: e.clientX,
      startY: e.clientY,
      startRight: pos.right,
      startBottom: pos.bottom
    }
  }

  function handlePointerMove(e: React.PointerEvent<HTMLButtonElement>) {
    if (!dragRef.current) return
    const dx = e.clientX - dragRef.current.startX
    const dy = e.clientY - dragRef.current.startY
    if (Math.abs(dx) > 4 || Math.abs(dy) > 4) didDrag.current = true
    const newRight = Math.max(8, Math.min(window.innerWidth - 56, dragRef.current.startRight - dx))
    const newBottom = Math.max(
      8,
      Math.min(window.innerHeight - 56, dragRef.current.startBottom - dy)
    )
    setPos({ right: newRight, bottom: newBottom })
  }

  function handlePointerUp() {
    dragRef.current = null
  }

  function handleClick() {
    if (didDrag.current) return // 拖拽结束不触发 click
    setOpen(!open)
  }

  const handleLayoutChange = useCallback(
    (layout: ChatterLayout) => {
      setLayoutOverride(layout === "dialog" ? null : layout)
      if (layout !== "dialog") setOpen(true)
    },
    [setLayoutOverride, setOpen]
  )

  return (
    <>
      <div className="fixed z-50 size-12" style={{ right: pos.right, bottom: pos.bottom }}>
        <button
          type="button"
          aria-label={open ? "关闭助理" : "打开助理"}
          onClick={handleClick}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          className="flex size-full cursor-grab items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg transition-transform hover:scale-110 active:scale-90 active:cursor-grabbing"
        >
          <BotMessageSquare
            data-state={open ? "open" : "closed"}
            className="absolute size-6 transition-all data-[state=open]:rotate-90 data-[state=closed]:scale-100 data-[state=open]:scale-0"
          />
          <ChevronDown
            data-state={open ? "open" : "closed"}
            className="absolute size-6 transition-all data-[state=closed]:-rotate-90 data-[state=closed]:scale-0 data-[state=open]:scale-100"
          />
        </button>
      </div>

      <Chatter
        preset={preset}
        agentRole={agentRole}
        layout="dialog"
        open={open}
        onOpenChange={setOpen}
        onLayoutChange={handleLayoutChange}
      />
    </>
  )
}
