/**
 * ToolConfirmPanel——工具调用确认 UI
 * 当 Agent 因权限检查暂停时，展示工具调用详情和确认/拒绝按钮
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useAssistantRuntime } from "@assistant-ui/react"
import { CheckIcon, XIcon } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { buildApiUrl } from "@/lib/api/config"

interface ToolCall {
  id: string
  name: string
  args?: Record<string, unknown>
}

interface ToolConfirmPanelProps {
  threadId: string
  toolCalls: ToolCall[]
  onConfirmed: () => void
}

export function ToolConfirmPanel({ threadId, toolCalls, onConfirmed }: ToolConfirmPanelProps) {
  const [loading, setLoading] = useState(false)

  const handleConfirm = async (approved: boolean) => {
    setLoading(true)
    try {
      await fetch(buildApiUrl(`/agui/runs/${threadId}/confirm`), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          approved,
          reason: approved ? undefined : "用户拒绝了工具调用",
          toolCalls: toolCalls.map((tc) => ({ id: tc.id, name: tc.name }))
        })
      })
      onConfirmed()
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="mx-3 mb-2 rounded-lg border border-amber-200 bg-amber-50 p-3">
      <p className="mb-2 font-medium text-amber-800 text-sm">AI 请求执行以下操作，需要您确认：</p>
      <div className="mb-3 space-y-1">
        {toolCalls.map((tc) => (
          <div key={tc.id} className="rounded bg-white px-2 py-1 font-mono text-gray-700 text-xs">
            <span className="font-semibold text-amber-700">{tc.name}</span>
            {tc.args && <span className="ml-2 text-gray-500">{JSON.stringify(tc.args)}</span>}
          </div>
        ))}
      </div>
      <div className="flex gap-2">
        <Button
          size="sm"
          variant="default"
          disabled={loading}
          onClick={() => handleConfirm(true)}
          className="gap-1"
        >
          <CheckIcon className="size-3" />
          确认执行
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={loading}
          onClick={() => handleConfirm(false)}
          className="gap-1"
        >
          <XIcon className="size-3" />
          拒绝
        </Button>
      </div>
    </div>
  )
}

/**
 * 监听 requires-action 状态，自动展示确认 UI
 */
export function ToolConfirmOverlay() {
  const runtime = useAssistantRuntime()
  const [confirmed, setConfirmed] = useState(false)

  const thread = runtime.thread.getState()
  const threadId = (runtime.threads.getState() as { threadId?: string }).threadId ?? ""
  const lastMsg = thread.messages.at(-1)

  if (confirmed || !lastMsg || lastMsg.role !== "assistant") return null
  if (!("status" in lastMsg) || (lastMsg.status as { type: string }).type !== "requires-action") {
    return null
  }

  const interrupts = (lastMsg.metadata?.custom as { agui?: { interrupts?: unknown[] } })?.agui
    ?.interrupts
  const toolCalls: ToolCall[] = Array.isArray(interrupts)
    ? interrupts.map((i: unknown) => {
        const interrupt = i as {
          id?: string
          toolCallId?: string
          toolCallName?: string
          args?: Record<string, unknown>
        }
        return {
          id: interrupt.id ?? interrupt.toolCallId ?? "",
          name: interrupt.toolCallName ?? "unknown",
          args: interrupt.args
        }
      })
    : []

  if (toolCalls.length === 0) return null

  return (
    <ToolConfirmPanel
      threadId={threadId}
      toolCalls={toolCalls}
      onConfirmed={() => setConfirmed(true)}
    />
  )
}
