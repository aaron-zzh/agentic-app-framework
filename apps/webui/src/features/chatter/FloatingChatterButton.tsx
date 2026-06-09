/**
 * FloatingChatterButton——工作区浮动 Chatter 触发按钮（dialog 模式）
 *
 * 样式复用 FloatingAssistant 的圆形按钮，状态接入 chatter-store。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { BotMessageSquare, ChevronDown } from "lucide-react"
import { useCallback } from "react"
import { Chatter } from "@/features/chatter"
import { useChatterStore } from "@/lib/store/chatter-store"
import type { ChatterLayout, ChatterPreset } from "@/features/chatter/types"

interface FloatingChatterButtonProps {
  preset: ChatterPreset
  agentRole?: string
}

export function FloatingChatterButton({ preset, agentRole }: FloatingChatterButtonProps) {
  const open = useChatterStore((s) => s.open)
  const setOpen = useChatterStore((s) => s.setOpen)
  const setLayoutOverride = useChatterStore((s) => s.setLayoutOverride)

  const handleLayoutChange = useCallback(
    (layout: ChatterLayout) => {
      setLayoutOverride(layout === "dialog" ? null : layout)
      // panel/page 模式保持 open
      if (layout !== "dialog") setOpen(true)
    },
    [setLayoutOverride, setOpen]
  )

  return (
    <>
      <div className="fixed right-5 bottom-5 z-50 size-12">
        <button
          type="button"
          aria-label={open ? "关闭助理" : "打开助理"}
          onClick={() => setOpen(!open)}
          className="flex size-full items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg transition-transform hover:scale-110 active:scale-90"
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
