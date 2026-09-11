/**
 * FloatingChatter——浮动 AI 助理（按钮 + GlobalChatter 一体化）
 *
 * 已登录：使用当前页 chatter config（preset/agentRole），dialog/panel/page 三模可切换
 * 未登录：固定 guest preset（AI 客服），仅 dialog 模式，工具栏隐藏
 *
 * 按钮仅在 dialog 模式渲染——panel/page 模式由对应布局接管 chatter 渲染区域。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect, useState } from "react"
import { FloatingChatterButton } from "@/features/chatter/layout/FloatingChatterButton"
import { GlobalChatter } from "@/features/chatter/layout/GlobalChatter"
import { useAuthStore } from "@/lib/store/auth-store"
import { useChatterStore } from "@/lib/store/chatter-store"

interface FloatingChatterProps {
  /**
   * 当前布局支持的非 dialog 模式。
   * - WorkspaceLayout 传 ["panel", "page"]（有 chatter-panel-slot/chatter-page-slot）
   * - MarketingLayout 等公开布局不传，默认仅 dialog
   */
  availableModes?: ("panel" | "page")[]
}

export function FloatingChatter({ availableModes }: FloatingChatterProps = {}) {
  const mode = useChatterStore((s) => s.mode)
  const open = useChatterStore((s) => s.open)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const currentPageId = useChatterStore((s) => s.currentPageId)
  const getConfig = useChatterStore((s) => s.getConfig)
  const [guestActivated, setGuestActivated] = useState(false)

  // 匿名客服首次打开时才初始化；本页关闭后保持 runtime，避免再次打开重复加载。
  useEffect(() => {
    if (isAuthenticated) {
      setGuestActivated(false)
    } else if (open) {
      setGuestActivated(true)
    }
  }, [isAuthenticated, open])

  // 与 GlobalChatter 内的 config 推导保持一致
  const config =
    isAuthenticated && currentPageId
      ? getConfig(currentPageId)
      : isAuthenticated
        ? { preset: "ai" as const, agentRole: undefined }
        : { preset: "guest" as const, agentRole: "system.role.customer-service" }

  const shouldRenderChatter = isAuthenticated || open || guestActivated

  return (
    <>
      {mode === "dialog" && <FloatingChatterButton preset={config.preset} />}
      {shouldRenderChatter && <GlobalChatter availableModes={availableModes} />}
    </>
  )
}
