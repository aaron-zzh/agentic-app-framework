/**
 * KiroAgentProvider——Kiro Agent 对话 runtime
 * 基于 AG-UI 协议，端点固定为 /api/autodev/kiro/run
 * 通过 URL 参数传递 agentRole
 *
 * @author AaronZZH & Kiro
 */
"use client"

import { HttpAgent } from "@ag-ui/client"
import { AssistantRuntimeProvider } from "@assistant-ui/react"
import { useAgUiRuntime } from "@assistant-ui/react-ag-ui"
import { type ReactNode, useCallback, useMemo } from "react"
import { toast } from "sonner"

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? ""

interface KiroAgentProviderProps {
  children: ReactNode
  /** Agent 角色（如 developer-webui、architect 等） */
  agentRole?: string
}

/**
 * Kiro Agent 对话 Provider
 * 包裹子组件，提供 Kiro Agent 对话 runtime
 */
export function KiroAgentProvider({ children, agentRole }: KiroAgentProviderProps) {
  const url = useMemo(() => {
    const base = `${BASE_URL}/api/autodev/kiro/run`
    return agentRole ? `${base}?agentRole=${encodeURIComponent(agentRole)}` : base
  }, [agentRole])

  const agent = useMemo(() => new HttpAgent({ url }), [url])

  const onError = useCallback((error: Error) => {
    // biome-ignore lint/suspicious/noConsole: 错误日志需要记录到控制台供调试
    console.error("[Kiro Agent] 对话错误:", error)
    toast.error("Kiro Agent 通信异常，请重试")
  }, [])

  const runtime = useAgUiRuntime({ agent, onError })

  return <AssistantRuntimeProvider runtime={runtime}>{children}</AssistantRuntimeProvider>
}
