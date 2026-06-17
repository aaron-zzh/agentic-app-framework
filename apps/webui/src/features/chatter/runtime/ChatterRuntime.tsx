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
import type { ChatterTarget } from "@/features/chatter/types"
import { LivechatProvider } from "@/features/livechat/LivechatProvider"
import { AgUiChatProvider } from "@/features/livechat/runtime/ag-ui-runtime"
import { buildApiUrl } from "@/lib/api/config"
import { chatApi } from "@/lib/api/rest/ai/chat"
import { useChatterStore } from "@/lib/store/chatter-store"

/** 构建对话端点 URL：kiro 走独立端点，AI 走 /agui/runs */
function buildAguiUrl(target: ChatterTarget): string {
  if (target.type === "kiro") {
    return buildApiUrl("/autodev/kiro/run")
  }
  const agentId = target.agentRole ?? "default"
  return buildApiUrl(`/agui/runs/${agentId}`)
}

interface ChatterRuntimeProps {
  target: ChatterTarget
  persist?: boolean
  sessionId?: string
  modelId?: string
  children: ReactNode
}

/**
 * 统一 runtime Provider
 * - AI/Kiro → AgUiChatProvider（/agui/runs 或 /autodev/kiro/run）
 * - user    → LivechatProvider（WebSocket IM）
 */
export function ChatterRuntime({ target, sessionId, modelId, children }: ChatterRuntimeProps) {
  const currentPageId = useChatterStore((s) => s.currentPageId)
  const configs = useChatterStore((s) => s.configs)
  const pageConfig = currentPageId ? configs[currentPageId] : undefined

  const aguiUrl = useMemo(() => buildAguiUrl(target), [target])

  const initialState = useMemo(
    () => ({
      pageId: currentPageId,
      preset: pageConfig?.preset,
      agentRole: target.agentRole ?? pageConfig?.agentRole,
      ...(modelId ? { modelId } : {})
    }),
    [target.agentRole, pageConfig, currentPageId, modelId]
  )

  const onNewThread = useMemo(
    () =>
      target.type !== "kiro"
        ? async () => {
            await chatApi.createSession({ type: "ai" })
          }
        : undefined,
    [target.type]
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
