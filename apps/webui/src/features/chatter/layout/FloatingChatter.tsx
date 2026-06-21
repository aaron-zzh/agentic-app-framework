/**
 * FloatingChatter——浮动 AI 助理（按钮 + GlobalChatter 一体化）
 *
 * 已登录：使用当前页 chatter config（preset/agentRole），dialog/panel/page 三模可切换
 * 未登录：固定 guest preset（AI 客服），仅 dialog 模式，工具栏隐藏
 *
 * 按钮仅在 dialog 模式渲染——panel/page 模式由对应布局接管 chatter 渲染区域。
 *
 * 未登录访客的 lead 记录策略：
 * - 进入页面 → 写一条 channel=VISIT（IP/UA/region 由后端推断），24h 内同访客不重复
 * - 点击对话按钮 → 由 FloatingChatterButton 写一条 channel=CHAT，同 tab 不重复
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect } from "react"
import { FloatingChatterButton } from "@/features/chatter/layout/FloatingChatterButton"
import { GlobalChatter } from "@/features/chatter/layout/GlobalChatter"
import { leadApi } from "@/lib/api/rest/lead/lead"
import { useAuthStore } from "@/lib/store/auth-store"
import { useChatterStore } from "@/lib/store/chatter-store"
import { getOrCreateAnonymousId } from "@/lib/utils/anonymous-id"

interface FloatingChatterProps {
  /**
   * 当前布局支持的非 dialog 模式。
   * - WorkspaceLayout 传 ["panel", "page"]（有 chatter-panel-slot/chatter-page-slot）
   * - MarketingLayout 等公开布局不传，默认仅 dialog
   */
  availableModes?: ("panel" | "page")[]
}

/** VISIT 节流键：localStorage 存上次记录时间戳（同访客 24h 内不重复） */
const VISIT_THROTTLE_KEY = "aaf-anonymous-visit-at"
const VISIT_THROTTLE_MS = 24 * 60 * 60 * 1000

export function FloatingChatter({ availableModes }: FloatingChatterProps = {}) {
  const mode = useChatterStore((s) => s.mode)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const currentPageId = useChatterStore((s) => s.currentPageId)
  const getConfig = useChatterStore((s) => s.getConfig)

  // 与 GlobalChatter 内的 config 推导保持一致
  const config =
    isAuthenticated && currentPageId
      ? getConfig(currentPageId)
      : isAuthenticated
        ? { preset: "ai" as const, agentRole: "default-generalist" }
        : { preset: "guest" as const, agentRole: "customer-service" }

  // 未登录访客挂载时记录一条 VISIT lead；24h 节流避免每次跳页/刷新刷数据
  useEffect(() => {
    if (isAuthenticated) return
    if (typeof window === "undefined") return
    const last = Number(window.localStorage.getItem(VISIT_THROTTLE_KEY) ?? 0)
    if (Date.now() - last < VISIT_THROTTLE_MS) return

    leadApi
      .create({
        anonymousId: getOrCreateAnonymousId(),
        channel: "VISIT"
      })
      .then(() => {
        window.localStorage.setItem(VISIT_THROTTLE_KEY, String(Date.now()))
      })
      .catch(() => {
        // 静默失败：访客记录非关键路径，不阻塞使用
      })
  }, [isAuthenticated])

  return (
    <>
      {mode === "dialog" && (
        <FloatingChatterButton preset={config.preset} agentRole={config.agentRole} />
      )}
      <GlobalChatter availableModes={availableModes} />
    </>
  )
}
