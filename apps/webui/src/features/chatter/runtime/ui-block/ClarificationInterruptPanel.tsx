/**
 * ClarificationInterruptPanel——展示待处理的 Clarification interrupt 并驱动提交（AAF-114 #11408 第二版）。
 *
 * 用官方 AG-UI interrupt hook（`useAgUiInterrupts`/`useAgUiSubmitInterruptResponses`）
 * 读取 `reason="input_required"` 的 interrupt，按其 `responseSchema` 渲染 {@link SchemaForm}。
 * 与工具审批 interrupt（`reason="tool_call"`）走同一套协议，但本组件只处理 Clarification 场景，
 * 审批 UI 由既有 {@code ToolConfirmOverlay} 负责，两者不重复渲染同一个 interrupt。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import {
  type AgUiInterrupt,
  useAgUiInterrupts,
  useAgUiSubmitInterruptResponses
} from "@assistant-ui/react-ag-ui"
import { useState } from "react"
import { toast } from "sonner"
import { type JsonSchemaObject, SchemaForm } from "@/features/chatter/runtime/ui-block/SchemaForm"

export function ClarificationInterruptPanel() {
  const submitInterruptResponses = useAgUiSubmitInterruptResponses()
  const [submittingId, setSubmittingId] = useState<string | null>(null)
  const clarificationInterrupts = useAgUiInterrupts().filter((i) => i.reason === "input_required")

  if (clarificationInterrupts.length === 0) return null

  async function handleSubmit(interrupt: AgUiInterrupt, values: Record<string, string>) {
    setSubmittingId(interrupt.id)
    try {
      await submitInterruptResponses([
        { interruptId: interrupt.id, status: "resolved", payload: values }
      ])
    } catch {
      toast.error("提交失败，请重试")
    } finally {
      setSubmittingId(null)
    }
  }

  return (
    <div className="border-t px-3 py-2">
      {clarificationInterrupts.map((interrupt) => (
        <SchemaForm
          key={interrupt.id}
          schema={(interrupt.responseSchema ?? {}) as JsonSchemaObject}
          submitting={submittingId === interrupt.id}
          onSubmit={(values) => handleSubmit(interrupt, values)}
        />
      ))}
    </div>
  )
}
