/**
 * ClarificationInterruptPanel——展示待处理的 Clarification interrupt 并驱动提交（AAF-114 #11408 第二版）。
 *
 * 用官方 AG-UI interrupt API（`unstable_getPendingInterrupts`/`unstable_submitInterruptResponses`）
 * 读取 `reason="input_required"` 的 interrupt，按其 `responseSchema` 渲染 {@link SchemaForm}。
 * 与工具审批 interrupt（`reason="tool_call"`）走同一套协议，但本组件只处理 Clarification 场景，
 * 审批 UI 由既有 {@code ToolConfirmOverlay} 负责，两者不重复渲染同一个 interrupt。
 *
 * `useAssistantRuntime()` 已被官方标记 deprecated，但目前是唯一能拿到 `AgUiAssistantRuntime`
 * 扩展方法（`unstable_*`）的公开方式——`useAui()` 的细粒度 API 未暴露这两个 AG-UI 专属扩展，
 * 官方文档示例本身也是在 `useAgUiRuntime` 调用点直接使用 `runtime` 变量，没有展示子组件内的替代访问方式。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useAssistantRuntime, useAuiState } from "@assistant-ui/react"
import type { AgUiAssistantRuntime, AgUiInterrupt } from "@assistant-ui/react-ag-ui"
import { useState } from "react"
import { toast } from "sonner"
import { type JsonSchemaObject, SchemaForm } from "@/features/chatter/runtime/ui-block/SchemaForm"

export function ClarificationInterruptPanel() {
  const runtime = useAssistantRuntime() as AgUiAssistantRuntime
  // interrupt 发生时 run 从 running 转为暂停等待；订阅 isRunning 触发本组件在该切换时重新渲染
  // （unstable_getPendingInterrupts 本身不是 store selector，不会自动触发订阅更新）。
  useAuiState((s) => s.thread.isRunning)
  const [submittingId, setSubmittingId] = useState<string | null>(null)

  const pending = runtime.unstable_getPendingInterrupts()
  const clarificationInterrupts = pending.filter((i) => i.reason === "input_required")

  if (clarificationInterrupts.length === 0) return null

  async function handleSubmit(interrupt: AgUiInterrupt, values: Record<string, string>) {
    setSubmittingId(interrupt.id)
    try {
      await runtime.unstable_submitInterruptResponses([
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
