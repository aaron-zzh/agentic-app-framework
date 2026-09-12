/**
 * ToolConfirmOverlay——通过 AG-UI structured interrupt 处理工具授权。
 * @author AaronZZH & Kiro
 */

"use client"

import { useAssistantRuntime, useAuiState } from "@assistant-ui/react"
import type { AgUiAssistantRuntime, AgUiInterrupt } from "@assistant-ui/react-ag-ui"
import { useState } from "react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"

function ToolConfirmPanel({
  interrupt,
  loading,
  onDecision
}: {
  interrupt: AgUiInterrupt
  loading: boolean
  onDecision: (approved: boolean) => void
}) {
  return (
    <div className="mx-3 mb-2 rounded-lg border bg-muted/30 p-3">
      <p className="mb-2 font-medium text-sm">
        {interrupt.message ?? "AI 请求执行受控操作，需要您确认："}
      </p>
      <div className="mb-3 flex flex-wrap gap-2">
        <Badge variant="secondary">受控工具操作</Badge>
        {interrupt.toolCallId ? <Badge variant="outline">{interrupt.toolCallId}</Badge> : null}
      </div>
      <div className="flex gap-2">
        <Button size="sm" disabled={loading} onClick={() => onDecision(true)}>
          确认执行
        </Button>
        <Button size="sm" variant="outline" disabled={loading} onClick={() => onDecision(false)}>
          拒绝
        </Button>
      </div>
    </div>
  )
}

export function ToolConfirmOverlay() {
  const runtime = useAssistantRuntime() as AgUiAssistantRuntime
  useAuiState((state) => state.thread.isRunning)
  const [submittingId, setSubmittingId] = useState<string | null>(null)
  const interrupts = runtime
    .unstable_getPendingInterrupts()
    .filter((interrupt) => interrupt.reason === "tool_call")

  async function handleDecision(interrupt: AgUiInterrupt, approved: boolean) {
    setSubmittingId(interrupt.id)
    try {
      await runtime.unstable_submitInterruptResponses([
        { interruptId: interrupt.id, status: "resolved", payload: { approved } }
      ])
    } catch {
      toast.error("提交授权决定失败，请重试")
    } finally {
      setSubmittingId(null)
    }
  }

  if (interrupts.length === 0) return null
  return (
    <>
      {interrupts.map((interrupt) => (
        <ToolConfirmPanel
          key={interrupt.id}
          interrupt={interrupt}
          loading={submittingId === interrupt.id}
          onDecision={(approved) => handleDecision(interrupt, approved)}
        />
      ))}
    </>
  )
}
