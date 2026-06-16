/**
 * AG-UI Runtime Provider——AI 助理对话的 runtime 配置
 * 使用 @ag-ui/client HttpAgent 对接后端 AG-UI SSE 端点，
 * 通过 useAgUiRuntime 将事件流转为 assistant-ui 可消费的 runtime
 *
 * 支持自定义端点 url 和 initialState，供 ChatterRuntime 复用
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
import { useAuthStore } from "@/lib/store/auth-store"
import { OmniVoiceAdapter } from "@/lib/voice/omni-voice-adapter"
import { useAgentRunStore } from "./agent-run-store"

const DEFAULT_AGENT_URL = buildApiUrl("/agui/runs")

interface AgUiChatProviderProps {
  children: ReactNode
  /** 自定义端点 URL，默认 /agui/runs */
  url?: string
  /** Agent 初始状态（用于传递页面感知上下文） */
  initialState?: Record<string, unknown>
  /** 初始线程 ID，传入时自动切换到该线程（用于匿名访客恢复历史） */
  initialThreadId?: string
  /** 新建会话回调（默认调用 chatApi.createSession） */
  onNewThread?: () => Promise<void>
}

/**
 * AG-UI 对话 Provider
 * 包裹子组件，提供 AI 助理对话 runtime（SSE 流式通信）
 */
export function AgUiChatProvider({
  children,
  url,
  initialState,
  initialThreadId,
  onNewThread
}: AgUiChatProviderProps) {
  // 将 initialState 序列化为稳定字符串，避免每次渲染对象引用不同导致 agent 重建
  const initialStateKey = JSON.stringify(initialState)

  // biome-ignore lint/correctness/useExhaustiveDependencies: initialState 通过 initialStateKey 跟踪
  const agent = useMemo(
    () => new HttpAgent({ url: url ?? DEFAULT_AGENT_URL, initialState }),
    [url, initialStateKey]
  )

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
        if (event.name === "suggestions") {
          run.setSuggestions(event.value as { prompt: string; label?: string }[])
          return
        }
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

  const onError = useCallback((error: Error) => {
    toast.error(classifyError(error))
  }, [])

  const threadList: UseAgUiThreadListAdapter = useMemo(
    () => ({
      onSwitchToNewThread: async () => {
        if (onNewThread) {
          await onNewThread()
        } else {
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
    [onNewThread]
  )

  const voiceAdapter = useMemo(
    () => new OmniVoiceAdapter({ getToken: () => useAuthStore.getState().accessToken }),
    []
  )

  // @ts-expect-error: @ag-ui/client 版本与 @assistant-ui/react-ag-ui 期望的 AbstractAgent 类型不匹配（pendingInterrupts），升级依赖后可移除
  const runtime = useAgUiRuntime({ agent, onError, adapters: { threadList, voice: voiceAdapter } })

  // 初始线程恢复：挂载后切换到指定 threadId（匿名访客历史恢复）
  useEffect(() => {
    if (initialThreadId) {
      runtime.threads.switchToThread(initialThreadId)
    }
    // 仅挂载时执行一次
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [runtime.threads.switchToThread, initialThreadId])

  return <AssistantRuntimeProvider runtime={runtime}>{children}</AssistantRuntimeProvider>
}

/** 根据错误信息分类，返回用户友好的提示 */
export function classifyError(error: Error): string {
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
