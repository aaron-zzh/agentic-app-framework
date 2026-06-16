/**
 * FloatingChatterButton——工作区浮动 Chatter 触发按钮（dialog 模式）
 *
 * 使用 assistant-ui AssistantModalPrimitive（基于 Radix Popover）
 * Content 自动定位在按钮正上方，带官方动画效果。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { AssistantModalPrimitive } from "@assistant-ui/react"
import { BotMessageSquare, ChevronDown } from "lucide-react"
import { useCallback } from "react"
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

  const handleLayoutChange = useCallback(
    (layout: ChatterLayout) => {
      setLayoutOverride(layout === "dialog" ? null : layout)
      if (layout !== "dialog") setOpen(true)
    },
    [setLayoutOverride, setOpen]
  )

  return (
    <AssistantModalPrimitive.Root open={open} onOpenChange={setOpen}>
      <AssistantModalPrimitive.Anchor className="fixed end-5 bottom-5 z-50 size-12">
        <AssistantModalPrimitive.Trigger
          className="flex size-full cursor-pointer items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg transition-transform hover:scale-110 active:scale-90"
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
        </AssistantModalPrimitive.Trigger>
      </AssistantModalPrimitive.Anchor>

      <AssistantModalPrimitive.Content
        sideOffset={16}
        className="data-[state=closed]:fade-out-0 data-[state=closed]:slide-out-to-bottom-1/2 data-[state=closed]:slide-out-to-right-1/2 data-[state=closed]:zoom-out data-[state=open]:fade-in-0 data-[state=open]:slide-in-from-bottom-1/2 data-[state=open]:slide-in-from-right-1/2 data-[state=open]:zoom-in z-50 h-[560px] w-[380px] overflow-clip overscroll-contain rounded-xl border p-0 shadow-md outline-none [background:linear-gradient(135deg,color-mix(in_oklch,var(--color-violet-500)_6%,transparent),transparent_50%,color-mix(in_oklch,var(--color-indigo-500)_6%,transparent)),var(--color-popover)] data-[state=closed]:animate-out data-[state=open]:animate-in"
      >
        <Chatter
          preset={preset}
          agentRole={agentRole}
          layout="panel"
          onLayoutChange={handleLayoutChange}
        />
      </AssistantModalPrimitive.Content>
    </AssistantModalPrimitive.Root>
  )
}
