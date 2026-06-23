/**
 * KiroAgentProvider——Kiro Agent 对话 runtime
 * 基于 AG-UI 协议，端点固定为 /api/autodev/kiro/run
 * 通过 URL 参数传递 agentRole
 *
 * @author AaronZZH & Kiro
 */
"use client"

import { type AbstractAgent, HttpAgent } from "@ag-ui/client"
import { AssistantRuntimeProvider } from "@assistant-ui/react"
import { useAgUiRuntime } from "@assistant-ui/react-ag-ui"
import { type ReactNode, useCallback, useMemo } from "react"
import { toast } from "sonner"
import { buildApiUrl } from "@/lib/api/config"

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
    const base = buildApiUrl("/autodev/kiro/run")
    return agentRole ? `${base}?agentRole=${encodeURIComponent(agentRole)}` : base
  }, [agentRole])

  const agent = useMemo(() => {
    const a = new HttpAgent({ url })
    if (!("pendingInterrupts" in a)) {
      Object.defineProperty(a, "pendingInterrupts", { value: [], writable: true })
    }
    return a
  }, [url])

  const onError = useCallback((_error: Error) => {
    // console.error("[Kiro Agent] 对话错误:", _error)
    toast.error("Kiro Agent 通信异常，请重试")
  }, [])

  // HttpAgent@0.0.53 缺少 pendingInterrupts，已通过 Object.defineProperty 补全
  const runtime = useAgUiRuntime({ agent: agent as unknown as AbstractAgent, onError })

  return <AssistantRuntimeProvider runtime={runtime}>{children}</AssistantRuntimeProvider>
}
