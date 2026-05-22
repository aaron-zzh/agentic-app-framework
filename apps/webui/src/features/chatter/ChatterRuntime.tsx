/**
 * ChatterRuntime——统一 AG-UI runtime
 * 基于 useAgUiRuntime，通过 state 传递 target 信息到后端
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
import { chatApi } from "@/lib/api/chat"
import { useChatterStore } from "@/stores/chatter-store"
import type { ChatterTarget } from "./types"

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? ""

/** 根据 target 类型映射到对应端点，通过 query param 传递 target 信息 */
function getEndpointUrl(target: ChatterTarget, persist?: boolean): string {
  const params = new URLSearchParams()
  params.set("targetType", target.type)
  if (target.agentRole) params.set("agentRole", target.agentRole)
  if (target.userId) params.set("targetUserId", target.userId)
  // persist 默认值：kiro=false，其他=true
  const shouldPersist = persist ?? (target.type !== "kiro")
  params.set("persist", String(shouldPersist))

  // 后端统一端点上线后改为 /api/chat/run
  // 目前 fallback 到各自端点
  const base = target.type === "kiro"
    ? `${BASE_URL}/api/autodev/kiro/run`
    : `${BASE_URL}/api/chat/run`

  return `${base}?${params.toString()}`
}

interface ChatterRuntimeProps {
  target: ChatterTarget
  persist?: boolean
  children: ReactNode
}

/**
 * 统一 runtime Provider
 * target/persist 变化时更新 agent url，useAgUiRuntime 热更新不重新挂载
 */
export function ChatterRuntime({ target, persist, children }: ChatterRuntimeProps) {
  // 从 store 读取当前页面感知信息，自动注入到每次请求的 state
  const currentPageId = useChatterStore((s) => s.currentPageId)
  const configs = useChatterStore((s) => s.configs)
  const pageConfig = currentPageId ? configs[currentPageId] : undefined

  const agent = useMemo(() => {
    // awareness 上下文：页面 ID + preset + agentRole，序列化为 JSON 字符串
    const awarenessContext = JSON.stringify({
      pageId: currentPageId,
      preset: pageConfig?.preset,
      agentRole: target.agentRole ?? pageConfig?.agentRole,
    })
    return new HttpAgent({
      url: getEndpointUrl(target, persist),
      // initialState 会被合并到每次 run 请求的 state 字段，后端从 state.awarenessContext 读取
      initialState: { awarenessContext },
    })
  }, [target, persist, currentPageId, pageConfig])

  const onError = useCallback((error: Error) => {
    // biome-ignore lint/suspicious/noConsole: 错误日志需要记录到控制台供调试
    console.error("[Chatter] 对话错误:", error)
    toast.error(classifyError(error))
  }, [])

  // 线程列表适配器：支持切换会话时加载历史消息（UI 渲染用，发送时后端自己从 DB 加载）
  const threadList: UseAgUiThreadListAdapter = useMemo(
    () => ({
      onSwitchToNewThread: async () => {
        // kiro 模式不需要创建会话
        if (target.type !== "kiro") {
          await chatApi.createSession({ type: target.type === "ai" ? "ai" : "livechat" })
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
            ...(msg.role !== "user" && { status: { type: "complete" as const, reason: "stop" as const } }),
            ...(msg.role === "user" && { attachments: [] }),
            metadata: { custom: {} },
          })) as ThreadMessage[]
          return { messages }
        } catch {
          return { messages: [] }
        }
      },
    }),
    [target.type]
  )

  const runtime = useAgUiRuntime({ agent, onError, adapters: { threadList } })

  return <AssistantRuntimeProvider runtime={runtime}>{children}</AssistantRuntimeProvider>
}

/** 根据错误信息分类，返回用户友好的提示 */
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
