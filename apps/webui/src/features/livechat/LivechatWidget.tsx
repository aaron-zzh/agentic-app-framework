/**
 * LivechatWidget——公开页面右下角客服浮窗
 * 访客点击后展开 AI 客服对话（livechat preset），使用 drawer 布局
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { MessageCircle, X } from "lucide-react"
import { useState } from "react"
import { Chatter } from "@/features/chatter"

export function LivechatWidget() {
  const [open, setOpen] = useState(false)

  return (
    <>
      {/* 浮动触发按钮 */}
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="fixed right-6 bottom-6 z-50 flex size-14 items-center justify-center rounded-full bg-primary shadow-lg ring-4 ring-primary/20 transition-all hover:scale-105 hover:shadow-xl focus:outline-none"
        aria-label="联系客服"
      >
        <MessageCircle className="size-6 text-primary-foreground" />
      </button>

      {/* Chatter drawer，livechat preset */}
      <Chatter preset="livechat" layout="drawer" open={open} onOpenChange={setOpen} />
    </>
  )
}
