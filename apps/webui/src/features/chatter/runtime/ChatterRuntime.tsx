/**
 * ChatterRuntime——统一 runtime 分发
 * - target.type=ai/guest/kiro：AgUiChatProvider（AG-UI SSE 协议）
 * - target.type=user：LivechatProvider（WebSocket IM）
 *
 * AgUi 实现统一由 livechat/runtime/ag-ui-runtime 提供，无重复逻辑
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { type ReactNode, useMemo } from "react"
import type {
  ChatterDisplayPreferences,
  ChatterTarget,
  TaskModelSelection
} from "@/features/chatter/types"
import { LivechatProvider } from "@/features/livechat/LivechatProvider"
import { AgUiChatProvider } from "@/features/livechat/runtime/ag-ui-runtime"
import { buildApiUrl } from "@/lib/api/config"
import { useChatterStore } from "@/lib/store/chatter-store"
import {
  buildChatterForwardedProps,
  buildChatterState,
  resolveChatterAguiPath
} from "./chatter-runtime-state"

/** 构建对话端点 URL。 */
function buildAguiUrl(target: ChatterTarget): string {
  return buildApiUrl(resolveChatterAguiPath(target))
}

interface ChatterRuntimeProps {
  target: ChatterTarget
  persist?: boolean
  sessionId?: string
  taskModelSelection?: TaskModelSelection
  displayPreferences: ChatterDisplayPreferences
  children: ReactNode
}

/**
 * 统一 runtime Provider
 * - AI/Kiro → AgUiChatProvider（/agui/runs 或 /autodev/kiro/run）
 * - user    → LivechatProvider（WebSocket IM）
 *
 * 匿名访客使用 guest 专用公开 session/history/run 端点，线程由 HttpOnly cookie 绑定；
 * 登录 AI/Kiro 继续使用原有会话与 AG-UI 链路。
 */
export function ChatterRuntime({
  target,
  sessionId,
  taskModelSelection,
  displayPreferences,
  children
}: ChatterRuntimeProps) {
  const currentPageId = useChatterStore((s) => s.currentPageId)
  const configs = useChatterStore((s) => s.configs)
  const pageConfig = currentPageId ? configs[currentPageId] : undefined
  const aguiUrl = useMemo(() => buildAguiUrl(target), [target])

  const initialState = useMemo(
    () => buildChatterState({ currentPageId, pageConfig }),
    [pageConfig, currentPageId]
  )

  const forwardedProps = useMemo(
    () => buildChatterForwardedProps({ target, taskModelSelection }),
    [target, taskModelSelection]
  )

  // user 类型走 IM WebSocket
  if (target.type === "user" && target.userId && sessionId) {
    return (
      <LivechatProvider
        config={{
          type: "im",
          userId: target.userId,
          sessionId,
          sessionType: "im"
        }}
      >
        {children}
      </LivechatProvider>
    )
  }

  // AI / Guest / Kiro 走统一 AgUiChatProvider；模式 key 保证认证切换时线程完全重建。
  const guestMode = target.type === "guest"
  return (
    <AgUiChatProvider
      key={guestMode ? "guest" : "authenticated"}
      url={aguiUrl}
      initialState={guestMode ? undefined : initialState}
      forwardedProps={guestMode ? undefined : forwardedProps}
      showThinking={guestMode ? false : displayPreferences.showThinking}
      guestMode={guestMode}
    >
      {children}
    </AgUiChatProvider>
  )
}
