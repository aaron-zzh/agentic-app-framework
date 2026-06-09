"use client"

/**
 * FloatingAssistant——公开页面浮动 AI 助理
 * 触发按钮固定右下角，点击弹出 Chatter 对话窗口
 *
 * @author AaronZZH & Kiro
 */

import { BotMessageSquare, ChevronDown } from "lucide-react"
import type { FC, RefObject } from "react"
import { useState } from "react"
import { Chatter } from "@/features/chatter"

export const FloatingAssistant: FC = () => {
  const [open, setOpen] = useState(false)

  return (
    <>
      {/* 触发按钮 */}
      <div className="fixed right-5 bottom-5 z-50 size-12">
        <FloatingButton state={open ? "open" : "closed"} onClick={() => setOpen(!open)} />
      </div>

      {/* 公开页面始终用 guest（AI 客服）preset */}
      <Chatter preset="guest" layout="dialog" open={open} onOpenChange={setOpen} />
    </>
  )
}

type State = "open" | "closed"

const FloatingButton = ({
  state,
  ref,
  ...rest
}: {
  state: State
  ref?: RefObject<HTMLButtonElement | null>
} & React.ButtonHTMLAttributes<HTMLButtonElement>) => (
  <button
    ref={ref}
    type="button"
    aria-label={state === "open" ? "关闭助理" : "打开助理"}
    className="flex size-full items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg transition-transform hover:scale-110 active:scale-90"
    {...rest}
  >
    <BotMessageSquare
      data-state={state}
      className="absolute size-6 transition-all data-[state=open]:rotate-90 data-[state=closed]:scale-100 data-[state=open]:scale-0"
    />
    <ChevronDown
      data-state={state}
      className="absolute size-6 transition-all data-[state=closed]:-rotate-90 data-[state=closed]:scale-0 data-[state=open]:scale-100"
    />
  </button>
)
FloatingButton.displayName = "FloatingButton"
