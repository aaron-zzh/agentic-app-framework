/**
 * ChatterRuntime——统一 runtime 分发
 * - target.type=ai/kiro：AgUiChatProvider（AG-UI SSE 协议）
 * - target.type=user：LivechatProvider（WebSocket IM）
 *
 * AgUi 实现统一由 livechat/runtime/ag-ui-runtime 提供，无重复逻辑
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { type ReactNode, useMemo } from "react"
import type { ChatterTarget, TaskModelSelection } from "@/features/chatter/types"
import { LivechatProvider } from "@/features/livechat/LivechatProvider"
import { AgUiChatProvider } from "@/features/livechat/runtime/ag-ui-runtime"
import { buildApiUrl } from "@/lib/api/config"
import { chatApi } from "@/lib/api/rest/ai"
import { useAuthStore } from "@/lib/store/auth-store"
import { useChatterStore } from "@/lib/store/chatter-store"
import { buildChatterInitialState, resolveChatterAguiPath } from "./chatter-runtime-state"

/** 构建对话端点 URL。 */
function buildAguiUrl(target: ChatterTarget, isAuthenticated: boolean): string {
  return buildApiUrl(resolveChatterAguiPath(target, isAuthenticated))
}

interface ChatterRuntimeProps {
  target: ChatterTarget
  persist?: boolean
  sessionId?: string
  taskModelSelection?: TaskModelSelection
  children: ReactNode
}

/**
 * 统一 runtime Provider
 * - AI/Kiro → AgUiChatProvider（/agui/runs 或 /autodev/kiro/run）
 * - user    → LivechatProvider（WebSocket IM）
 *
 * 匿名访客（未登录）当前不记录对话历史：每次刷新/重开都是新 thread；
 * AG-UI 链路 /agui/runs 端点已在公开白名单，对话本身可正常进行。
 */
export function ChatterRuntime({
  target,
  sessionId,
  taskModelSelection,
  children
}: ChatterRuntimeProps) {
  const currentPageId = useChatterStore((s) => s.currentPageId)
  const configs = useChatterStore((s) => s.configs)
  const pageConfig = currentPageId ? configs[currentPageId] : undefined
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)

  const aguiUrl = useMemo(() => buildAguiUrl(target, isAuthenticated), [target, isAuthenticated])

  const initialState = useMemo(
    () =>
      buildChatterInitialState({
        target,
        currentPageId,
        pageConfig,
        isAuthenticated,
        taskModelSelection
      }),
    [target, pageConfig, currentPageId, isAuthenticated, taskModelSelection]
  )

  // onNewThread 行为：
  // - 已登录 + AI：调 chatApi.createSession 持久化新会话
  // - 未登录 + AI：no-op（不调 sessions API 避免 401，runtime 自管 threadId）
  // - kiro：不需要 session
  const onNewThread = useMemo(
    () =>
      target.type === "kiro"
        ? undefined
        : isAuthenticated
          ? async () => {
              await chatApi.createSession({ type: "ai" })
            }
          : async () => {
              /* 匿名访客新建 thread 不持久化到后端 */
            },
    [isAuthenticated, target.type]
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

  // AI / Kiro 走统一 AgUiChatProvider
  return (
    <AgUiChatProvider url={aguiUrl} initialState={initialState} onNewThread={onNewThread}>
      {children}
    </AgUiChatProvider>
  )
}
