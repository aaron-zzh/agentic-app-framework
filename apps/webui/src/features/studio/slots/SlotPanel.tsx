/**
 * 单个面板包装——标题栏 + 关闭按钮 + 可拖拽 resize
 * @author AaronZZH & Kiro
 */

"use client"

import { X } from "lucide-react"
import { useCallback, useEffect, useRef, useState } from "react"
import { cn } from "@/lib/utils"
import { SLOT_REGISTRY } from "./registry"
import { type SlotInstance, useSlotStore } from "./store"

const TONE_RING: Record<string, string> = {
  violet: "ring-violet-400/20",
  cyan: "ring-cyan-400/20",
  emerald: "ring-emerald-400/20",
  amber: "ring-amber-400/20",
  rose: "ring-rose-400/20"
}

const TONE_GLOW: Record<string, string> = {
  violet: "from-violet-500/15 to-transparent",
  cyan: "from-cyan-500/15 to-transparent",
  emerald: "from-emerald-500/15 to-transparent",
  amber: "from-amber-500/15 to-transparent",
  rose: "from-rose-500/15 to-transparent"
}

export function SlotPanel({ slot }: { slot: SlotInstance }) {
  const def = SLOT_REGISTRY[slot.panelType]
  const close = useSlotStore((s) => s.closeSlot)
  const resize = useSlotStore((s) => s.resizeSlot)

  const [width, setWidth] = useState(slot.width ?? def.defaultWidth)
  const startXRef = useRef<number | null>(null)
  const startWRef = useRef<number>(width)

  const handleMouseDown = useCallback(
    (e: React.MouseEvent) => {
      startXRef.current = e.clientX
      startWRef.current = width
      e.preventDefault()
    },
    [width]
  )

  useEffect(() => {
    function handleMove(e: MouseEvent) {
      if (startXRef.current === null) return
      const delta = startXRef.current - e.clientX // 左拖增宽
      const next = Math.max(def.minWidth, Math.min(def.maxWidth, startWRef.current + delta))
      setWidth(next)
    }
    function handleUp() {
      if (startXRef.current !== null) {
        startXRef.current = null
        resize(slot.id, width)
      }
    }
    window.addEventListener("mousemove", handleMove)
    window.addEventListener("mouseup", handleUp)
    return () => {
      window.removeEventListener("mousemove", handleMove)
      window.removeEventListener("mouseup", handleUp)
    }
  }, [def.maxWidth, def.minWidth, resize, slot.id, width])

  const Icon = def.icon
  const Component = def.component

  return (
    <div
      className={cn(
        "group relative shrink-0 overflow-hidden rounded-xl ring-1 ring-inset",
        "bg-foreground/[0.025] backdrop-blur-md",
        TONE_RING[def.tone]
      )}
      style={{ width: `${width}px` }}
    >
      {/* 顶部光晕 */}
      <div
        className={cn(
          "pointer-events-none absolute inset-x-0 top-0 h-16 bg-gradient-to-b opacity-60",
          TONE_GLOW[def.tone]
        )}
      />

      {/* 标题栏 */}
      <div className="relative flex items-center justify-between border-foreground/[0.06] border-b px-3 py-2">
        <div className="flex items-center gap-1.5">
          <Icon className="size-3.5 opacity-80" />
          <span className="font-medium text-xs">{def.title}</span>
        </div>
        <button
          type="button"
          onClick={() => close(slot.id)}
          className="rounded p-1 text-muted-foreground/60 transition-colors hover:bg-foreground/[0.06] hover:text-foreground"
          aria-label="关闭面板"
        >
          <X className="size-3.5" />
        </button>
      </div>

      {/* 内容区 */}
      <div className="relative h-[260px] overflow-y-auto p-3">
        <Component payload={slot.payload} />
      </div>

      {/* 左侧拖拽条 */}
      <button
        type="button"
        onMouseDown={handleMouseDown}
        aria-label="拖动调整宽度"
        className="absolute inset-y-0 left-0 w-1 cursor-col-resize bg-transparent hover:bg-foreground/10"
      />
    </div>
  )
}
