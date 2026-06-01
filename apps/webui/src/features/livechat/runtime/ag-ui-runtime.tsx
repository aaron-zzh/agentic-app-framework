/**
 * AG-UI Runtime Provider——AI 助理对话的 runtime 配置
 * 使用 @ag-ui/client HttpAgent 对接后端 AG-UI SSE 端点，
 * 通过 useAgUiRuntime 将事件流转为 assistant-ui 可消费的 runtime
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { HttpAgent } from "@ag-ui/client"
import { AssistantRuntimeProvider, type ThreadMessage } from "@assistant-ui/react"
import { type UseAgUiThreadListAdapter, useAgUiRuntime } from "@assistant-ui/react-ag-ui"
import { type ReactNode, useCallback, useEffect, useMemo } from "react"
import { toast } from "sonner"
import { buildApiUrl } from "@/lib/api/config"
import { chatApi } from "@/lib/api/rest/ai/chat"
import { useAgentRunStore } from "./agent-run-store"

const AGENT_URL = buildApiUrl("/chat/agent/run")

interface AgUiChatProviderProps {
  children: ReactNode
}

/**
 * AG-UI 对话 Provider
 * 包裹子组件，提供 AI 助理对话 runtime（SSE 流式通信）
 */
export function AgUiChatProvider({ children }: AgUiChatProviderProps) {
  const agent = useMemo(() => new HttpAgent({ url: AGENT_URL }), [])

  // 订阅 AG-UI 事件流，把运行状态/工具调用/AAF 专有 CUSTOM 事件写入运行状态 store
  useEffect(() => {
    const run = useAgentRunStore.getState()
    const sub = agent.subscribe({
      onRunStartedEvent: () => run.startRun(),
      onRunFinishedEvent: () => run.finishRun(),
      onRunErrorEvent: ({ event }) => run.errorRun(event.message),
      onToolCallStartEvent: ({ event }) => run.startTool(event.toolCallName),
      onToolCallEndEvent: () => run.endTool(),
      onCustomEvent: ({ event }) => {
        if (event.name !== "agent-run") return
        const value = event.value as { type?: string; title?: string; message?: string } | undefined
        run.pushEntry({
          type: value?.type ?? "CUSTOM",
          title: value?.title,
          message: value?.message,
          timestamp: Date.now()
        })
      }
    })
    return () => sub.unsubscribe()
  }, [agent])

  /** 错误回调：记录日志 + toast 提示用户 */
  const onError = useCallback((error: Error) => {
    // biome-ignore lint/suspicious/noConsole: 错误日志需要记录到控制台供调试
    console.error("[AG-UI] 对话错误:", error)

    const message = classifyError(error)
    toast.error(message)
  }, [])

  /** 线程列表适配器：新建/切换会话 */
  const threadList: UseAgUiThreadListAdapter = useMemo(
    () => ({
      onSwitchToNewThread: async () => {
        await chatApi.createSession({ type: "ai" })
      },
      onSwitchToThread: async (threadId: string) => {
        const history = await chatApi.getMessages(threadId)
        // 后端消息转为 assistant-ui ThreadMessage 格式
        // 使用 content 字符串形式，runtime 内部会自动转为 TextPart
        const messages = history.map((msg) => ({
          id: msg.id,
          role: msg.role === "user" ? ("user" as const) : ("assistant" as const),
          content: [{ type: "text" as const, text: msg.content }],
          createdAt: new Date(msg.createdAt),
          // assistant 消息需要 status 字段
          ...(msg.role !== "user" && {
            status: { type: "complete" as const, reason: "stop" as const }
          }),
          // user 消息需要 attachments 字段
          ...(msg.role === "user" && { attachments: [] }),
          metadata: { custom: {} }
        })) as ThreadMessage[]
        return { messages }
      }
    }),
    []
  )

  const runtime = useAgUiRuntime({
    agent,
    onError,
    adapters: { threadList }
  })

  return <AssistantRuntimeProvider runtime={runtime}>{children}</AssistantRuntimeProvider>
}

/** 根据错误信息分类，返回用户友好的提示 */
function classifyError(error: Error): string {
  const msg = error.message.toLowerCase()

  if (msg.includes("network") || msg.includes("fetch") || msg.includes("failed to fetch")) {
    return "网络连接异常，请检查网络后重试"
  }
  if (msg.includes("quota") || msg.includes("429") || msg.includes("rate limit")) {
    return "请求配额超限，请稍后再试"
  }
  if (msg.includes("model") || msg.includes("500") || msg.includes("internal")) {
    return "模型服务异常，请稍后再试"
  }
  return "对话出现错误，请重试"
}
