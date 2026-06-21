/**
 * FloatingChatterButton——工作区浮动触发按钮（dialog 模式）
 *
 * 只负责控制 GlobalChatter 的 open 状态，不持有 Chatter 实例。
 * Chatter 由 GlobalChatter 单例统一管理。
 *
 * 支持拖动调整位置：按住按钮拖动 ≥5px 进入拖动模式，松手时不触发 onClick；
 * 位置写入 chatter-store 持久化，弹窗会自动跟随按钮右边缘对齐。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useRef } from "react"
import { LottieIcon } from "@/components/animate/LottieIcon"
import type { ChatterPreset } from "@/features/chatter/types"
import { leadApi } from "@/lib/api/rest/lead/lead"
import { useAuthStore } from "@/lib/store/auth-store"
import { useChatterStore } from "@/lib/store/chatter-store"
import { getOrCreateAnonymousId } from "@/lib/utils/anonymous-id"

const BUTTON_SIZE = 100
const DRAG_THRESHOLD = 5

/** CHAT 节流键：sessionStorage（同 tab 内不重复） */
const CHAT_RECORDED_KEY = "aaf-anonymous-chat-recorded"

interface FloatingChatterButtonProps {
  preset: ChatterPreset
  agentRole?: string
}

export function FloatingChatterButton(props: FloatingChatterButtonProps) {
  const open = useChatterStore((s) => s.open)
  const setOpen = useChatterStore((s) => s.setOpen)
  const setMode = useChatterStore((s) => s.setMode)
  const buttonPos = useChatterStore((s) => s.buttonPos)
  const setButtonPos = useChatterStore((s) => s.setButtonPos)

  const dragRef = useRef<{
    startX: number
    startY: number
    startRight: number
    startBottom: number
  } | null>(null)
  // 当次 pointer 流程是否触发了拖动；若是，紧随其后的 click 应被忽略
  const draggedRef = useRef(false)

  const handlePointerDown = (e: React.PointerEvent<HTMLButtonElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId)
    draggedRef.current = false
    dragRef.current = {
      startX: e.clientX,
      startY: e.clientY,
      startRight: buttonPos.right,
      startBottom: buttonPos.bottom
    }
  }

  const handlePointerMove = (e: React.PointerEvent<HTMLButtonElement>) => {
    const drag = dragRef.current
    if (!drag) return
    const dx = e.clientX - drag.startX
    const dy = e.clientY - drag.startY
    if (!draggedRef.current && Math.hypot(dx, dy) < DRAG_THRESHOLD) return
    draggedRef.current = true
    const vw = window.innerWidth
    const vh = window.innerHeight
    // 视口右下角坐标系：右移鼠标 → right 减小；下移鼠标 → bottom 减小
    const nextRight = clamp(drag.startRight - dx, 0, vw - BUTTON_SIZE)
    const nextBottom = clamp(drag.startBottom - dy, 0, vh - BUTTON_SIZE)
    setButtonPos({ right: nextRight, bottom: nextBottom })
  }

  const handlePointerUp = (e: React.PointerEvent<HTMLButtonElement>) => {
    if (e.currentTarget.hasPointerCapture(e.pointerId)) {
      e.currentTarget.releasePointerCapture(e.pointerId)
    }
    dragRef.current = null
  }

  const handleClick = () => {
    // 拖动结束的 click 视为误触，忽略一次
    if (draggedRef.current) {
      draggedRef.current = false
      return
    }
    // 未登录访客首次点击对话时记一条 CHAT lead（同 tab 不重复，同 24h 不同 tab 不重复）
    recordAnonymousChatLead(props.agentRole)
    setMode("dialog")
    setOpen(!open)
  }

  return (
    <button
      type="button"
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerCancel={handlePointerUp}
      onClick={handleClick}
      style={{ right: buttonPos.right, bottom: buttonPos.bottom }}
      className="fixed z-50 flex size-[100px] cursor-pointer touch-none select-none items-center justify-center text-primary"
      aria-label={open ? "关闭助理" : "打开助理"}
    >
      <span
        data-state={open ? "open" : "closed"}
        className="absolute transition-all data-[state=open]:rotate-90 data-[state=closed]:scale-100 data-[state=open]:scale-0"
      >
        <LottieIcon name="robot" width={100} height={100} loop={false} playOnHover />
      </span>
      <span
        data-state={open ? "open" : "closed"}
        className="absolute transition-all data-[state=closed]:-rotate-90 data-[state=closed]:scale-0 data-[state=open]:scale-100"
      >
        <LottieIcon name="ai-processing" width={100} height={100} loop />
      </span>
    </button>
  )
}

function clamp(v: number, min: number, max: number) {
  return Math.max(min, Math.min(max, v))
}

/**
 * 未登录访客首次点击对话按钮时记一条 CHAT lead，同 tab 内不重复。
 * 已登录用户跳过；调用失败静默（非关键路径）。
 */
function recordAnonymousChatLead(agentRole: string | undefined) {
  if (typeof window === "undefined") return
  if (useAuthStore.getState().isAuthenticated) return
  if (window.sessionStorage.getItem(CHAT_RECORDED_KEY)) return

  leadApi
    .create({
      anonymousId: getOrCreateAnonymousId(),
      channel: "CHAT",
      agentRole: agentRole ?? "customer-service"
    })
    .then(() => {
      window.sessionStorage.setItem(CHAT_RECORDED_KEY, String(Date.now()))
    })
    .catch(() => {
      // 静默失败
    })
}
