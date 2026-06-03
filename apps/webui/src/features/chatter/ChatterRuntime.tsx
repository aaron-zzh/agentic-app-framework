/**
 * ChatterRuntime——统一 AG-UI runtime
 * - target.type=ai/kiro：useAgUiRuntime 对接 /agui/runs
 * - target.type=user：LivechatProvider（WebSocket IM）
 * agent 引用变化时热更新，不重新挂载
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { HttpAgent } from "@ag-ui/client"
import { AssistantRuntimeProvider, type ThreadMessage } from "@assistant-ui/react"
import { type UseAgUiThreadListAdapter, useAgUiRuntime } from "@assistant-ui/react-ag-ui"
import { type ReactNode, useCallback, useMemo } from "react"
import { toast } from "sonner"
import { LivechatProvider } from "@/features/livechat/LivechatProvider"
import { buildApiUrl } from "@/lib/api/config"
import { chatApi } from "@/lib/api/rest/ai/chat"
import { useChatterStore } from "@/lib/store/chatter-store"
import type { ChatterTarget } from "./types"

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
  sessionId?: string // user 类型时必须传入
  children: ReactNode
}

/**
 * 统一 runtime Provider
 * - AI/Kiro → AgUiRuntime（/agui/runs）
 * - user    → IMRuntime（WebSocket）
 */
export function ChatterRuntime({ target, persist, sessionId, children }: ChatterRuntimeProps) {
  const currentPageId = useChatterStore((s) => s.currentPageId)
  const configs = useChatterStore((s) => s.configs)
  const pageConfig = currentPageId ? configs[currentPageId] : undefined

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

  // AI / Kiro 走 AgUiRuntime
  return (
    <AgUiChatterRuntime
      target={target}
      persist={persist}
      pageConfig={pageConfig}
      currentPageId={currentPageId ?? undefined}
    >
      {children}
    </AgUiChatterRuntime>
  )
}

function AgUiChatterRuntime({
  target,
  persist: _persist,
  pageConfig,
  currentPageId,
  children
}: {
  target: ChatterTarget
  persist?: boolean
  pageConfig: { preset?: string; agentRole?: string } | undefined
  currentPageId: string | undefined
  children: ReactNode
}) {
  const agent = useMemo(() => {
    const awarenessContext = JSON.stringify({
      pageId: currentPageId,
      preset: pageConfig?.preset,
      agentRole: target.agentRole ?? pageConfig?.agentRole
    })
    return new HttpAgent({
      url: buildAguiUrl(target),
      initialState: { awarenessContext }
    })
  }, [target, pageConfig, currentPageId])

  const onError = useCallback((error: Error) => {
    // biome-ignore lint/suspicious/noConsole: 错误日志需要记录到控制台供调试
    console.error("[Chatter] 对话错误:", error)
    toast.error(classifyError(error))
  }, [])

  const threadList: UseAgUiThreadListAdapter = useMemo(
    () => ({
      onSwitchToNewThread: async () => {
        if (target.type !== "kiro") {
          await chatApi.createSession({ type: "ai" })
        }
      },
      onSwitchToThread: async (threadId: string) => {
        try {
          const history = await chatApi.getMessages(threadId)
          const messages = history.map((msg) => ({
            id: msg.id,
            role: msg.role === "user" ? ("user" as const) : ("assistant" as const),
            content: [{ type: "text" as const, text: msg.content }],
            createdAt: new Date(msg.createdAt),
            ...(msg.role !== "user" && {
              status: { type: "complete" as const, reason: "stop" as const }
            }),
            ...(msg.role === "user" && { attachments: [] }),
            metadata: { custom: {} }
          })) as ThreadMessage[]
          return { messages }
        } catch {
          return { messages: [] }
        }
      }
    }),
    [target.type]
  )

  const runtime = useAgUiRuntime({ agent, onError, adapters: { threadList } })

  return <AssistantRuntimeProvider runtime={runtime}>{children}</AssistantRuntimeProvider>
}

function classifyError(error: Error): string {
  const msg = error.message.toLowerCase()
  if (msg.includes("network") || msg.includes("fetch") || msg.includes("failed to fetch")) {
    return "网络连接异常，请检查网络后重试"
  }
  if (msg.includes("429") || msg.includes("rate limit")) {
    return "请求配额超限，请稍后再试"
  }
  if (msg.includes("500") || msg.includes("internal")) {
    return "服务异常，请稍后再试"
  }
  return "对话出现错误，请重试"
}
