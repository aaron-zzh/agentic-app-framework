/**
 * FloatingChatterButton——工作区浮动触发按钮（dialog 模式）
 *
 * 只负责控制 GlobalChatter 的 open 状态，不持有 Chatter 实例。
 * Chatter 由 GlobalChatter 单例统一管理。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { BotMessageSquare, ChevronDown } from "lucide-react"
import type { ChatterPreset } from "@/features/chatter/types"
import { useChatterStore } from "@/lib/store/chatter-store"

interface FloatingChatterButtonProps {
  preset: ChatterPreset
  agentRole?: string
}

export function FloatingChatterButton(_props: FloatingChatterButtonProps) {
  const open = useChatterStore((s) => s.open)
  const setOpen = useChatterStore((s) => s.setOpen)
  const setMode = useChatterStore((s) => s.setMode)

  const handleClick = () => {
    setMode("dialog")
    setOpen(!open)
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      className="fixed end-5 bottom-5 z-50 flex size-12 cursor-pointer items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg transition-transform hover:scale-110 active:scale-90"
      aria-label={open ? "关闭助理" : "打开助理"}
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
  )
}
