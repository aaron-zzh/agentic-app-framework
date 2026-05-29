/**
 * 工作流 AG-UI Runtime——通过 SSE 连接后端工作流执行端点
 * 解析 AG-UI 事件，更新 ExecutionState，提供启动/输入/取消方法
 * @author AaronZZH & Kiro
 */

import { useCallback, useRef, useState } from "react"
import type { ExecutionState } from "../types"

const BASE = process.env.NEXT_PUBLIC_API_URL ?? ""

/** 工作流运行消息 */
export interface WorkflowMessage {
  id: string
  role: "system" | "user" | "assistant"
  content: string
  timestamp: number
}

/** AG-UI 事件类型 */
type AgUiEventType =
  | "RUN_STARTED"
  | "RUN_FINISHED"
  | "RUN_ERROR"
  | "TEXT_MESSAGE_START"
  | "TEXT_MESSAGE_CONTENT"
  | "TEXT_MESSAGE_END"
  | "STATE_DELTA"
  | "TOOL_CALL_START"
  | "TOOL_CALL_END"

interface AgUiEvent {
  type: AgUiEventType
  runId?: string
  messageId?: string
  textDelta?: string
  role?: string
  state?: Record<string, unknown>
  toolCallId?: string
  toolCallName?: string
}

/** 工作流运行状态 */
export type WorkflowRunStatus = "idle" | "running" | "waiting_input" | "completed" | "failed"

interface WorkflowRuntimeState {
  runId: string | null
  status: WorkflowRunStatus
  messages: WorkflowMessage[]
  executionState: ExecutionState
  /** 当前等待输入的 toolCallId（用于提交用户输入） */
  pendingToolCallId: string | null
}

/** 工作流 AG-UI Runtime Hook */
export function useWorkflowRuntime() {
  const [state, setState] = useState<WorkflowRuntimeState>({
    runId: null,
    status: "idle",
    messages: [],
    executionState: { status: "idle", completedNodes: [], failedNodes: [] },
    pendingToolCallId: null
  })

  const eventSourceRef = useRef<EventSource | null>(null)
  /** 当前正在拼接的消息 */
  const currentMsgRef = useRef<{ id: string; content: string } | null>(null)

  /** 处理 AG-UI 事件 */
  const handleEvent = useCallback((event: AgUiEvent) => {
    setState((prev) => {
      switch (event.type) {
        case "RUN_STARTED":
          return {
            ...prev,
            runId: event.runId ?? prev.runId,
            status: "running",
            executionState: { ...prev.executionState, status: "running" }
          }

        case "STATE_DELTA": {
          const delta = event.state ?? {}
          const currentNodeId = delta.currentNodeId as string | undefined
          const completedNodes =
            (delta.completedNodes as string[]) ?? prev.executionState.completedNodes
          const failedNodes = (delta.failedNodes as string[]) ?? prev.executionState.failedNodes
          const nodeTimings = delta.nodeTimings as Record<string, number> | undefined
          return {
            ...prev,
            executionState: {
              ...prev.executionState,
              currentNodeId: currentNodeId ?? prev.executionState.currentNodeId,
              completedNodes,
              failedNodes,
              nodeTimings: nodeTimings ?? prev.executionState.nodeTimings
            }
          }
        }

        case "TEXT_MESSAGE_START": {
          const msgId = event.messageId ?? crypto.randomUUID()
          currentMsgRef.current = { id: msgId, content: "" }
          return prev
        }

        case "TEXT_MESSAGE_CONTENT": {
          if (currentMsgRef.current && event.textDelta) {
            currentMsgRef.current.content += event.textDelta
          }
          return prev
        }

        case "TEXT_MESSAGE_END": {
          if (!currentMsgRef.current) return prev
          const msg: WorkflowMessage = {
            id: currentMsgRef.current.id,
            role: (event.role as WorkflowMessage["role"]) ?? "assistant",
            content: currentMsgRef.current.content,
            timestamp: Date.now()
          }
          currentMsgRef.current = null
          return { ...prev, messages: [...prev.messages, msg] }
        }

        case "TOOL_CALL_START": {
          if (event.toolCallName === "user_input") {
            return {
              ...prev,
              status: "waiting_input",
              pendingToolCallId: event.toolCallId ?? null
            }
          }
          return prev
        }

        case "TOOL_CALL_END":
          return prev

        case "RUN_FINISHED":
          return {
            ...prev,
            status: "completed",
            executionState: { ...prev.executionState, status: "completed" }
          }

        case "RUN_ERROR":
          return {
            ...prev,
            status: "failed",
            executionState: { ...prev.executionState, status: "failed" }
          }

        default:
          return prev
      }
    })
  }, [])

  /** 启动工作流 */
  const startWorkflow = useCallback(
    (processKey: string, variables?: Record<string, unknown>) => {
      // 关闭已有连接
      eventSourceRef.current?.close()

      setState({
        runId: null,
        status: "running",
        messages: [],
        executionState: { status: "running", completedNodes: [], failedNodes: [] },
        pendingToolCallId: null
      })

      // 使用 POST + fetch 获取 SSE 流
      const body = JSON.stringify({ processKey, variables: variables ?? {} })

      fetch(`${BASE}/api/workflow/run`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body
      }).then(async (res) => {
        if (!res.ok || !res.body) {
          setState((prev) => ({ ...prev, status: "failed" }))
          return
        }

        const reader = res.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ""

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split("\n")
          buffer = lines.pop() ?? ""

          for (const line of lines) {
            if (line.startsWith("data:")) {
              const data = line.slice(5).trim()
              if (data) {
                try {
                  handleEvent(JSON.parse(data) as AgUiEvent)
                } catch {
                  // 忽略解析错误
                }
              }
            }
          }
        }
      })
    },
    [handleEvent]
  )

  /** 提交用户输入（恢复等待中的流程） */
  const submitInput = useCallback(
    async (input: string) => {
      if (!state.runId) return

      // 添加用户消息到列表
      setState((prev) => ({
        ...prev,
        status: "running",
        pendingToolCallId: null,
        messages: [
          ...prev.messages,
          { id: crypto.randomUUID(), role: "user", content: input, timestamp: Date.now() }
        ]
      }))

      await fetch(`${BASE}/api/workflow/run/${state.runId}/input`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ input })
      })
    },
    [state.runId]
  )

  /** 取消工作流执行 */
  const cancel = useCallback(() => {
    eventSourceRef.current?.close()
    eventSourceRef.current = null
    setState((prev) => ({
      ...prev,
      status: "idle",
      executionState: { ...prev.executionState, status: "idle" }
    }))
  }, [])

  return {
    ...state,
    startWorkflow,
    submitInput,
    cancel
  }
}
