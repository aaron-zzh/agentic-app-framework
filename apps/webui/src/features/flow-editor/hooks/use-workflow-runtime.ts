/**
 * 工作流 AG-UI Runtime——通过 SSE 连接后端工作流执行端点
 * 解析 AG-UI 事件，更新 ExecutionState，提供启动/输入/取消方法
 * @author AaronZZH & Kiro
 */

import { useCallback, useEffect, useRef, useState } from "react"
import { buildApiUrl } from "@/lib/api/config"
import type { ExecutionState } from "../types"

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

async function readEventStream(
  response: Response,
  onEvent: (event: AgUiEvent) => void,
  signal: AbortSignal
): Promise<boolean> {
  if (!response.ok || !response.body) throw new Error(`工作流连接失败: ${response.status}`)
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ""
  let terminal = false

  while (!signal.aborted) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split(/\r?\n/)
    buffer = lines.pop() ?? ""
    for (const line of lines) {
      if (!line.startsWith("data:")) continue
      const data = line.slice(5).trim()
      if (!data) continue
      const event = JSON.parse(data) as AgUiEvent
      onEvent(event)
      if (event.type === "RUN_FINISHED" || event.type === "RUN_ERROR") terminal = true
    }
  }
  return terminal
}

function waitForReconnect(signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = window.setTimeout(resolve, 1000)
    signal.addEventListener(
      "abort",
      () => {
        window.clearTimeout(timer)
        resolve()
      },
      { once: true }
    )
  })
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

  const abortControllerRef = useRef<AbortController | null>(null)
  const runIdRef = useRef<string | null>(null)
  /** 当前正在拼接的消息 */
  const currentMsgRef = useRef<{ id: string; content: string } | null>(null)

  useEffect(
    () => () => {
      abortControllerRef.current?.abort()
    },
    []
  )

  /** 处理 AG-UI 事件 */
  const handleEvent = useCallback((event: AgUiEvent) => {
    if (event.runId) runIdRef.current = event.runId
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
          const activeNodes = (delta.activeNodes as string[]) ?? []
          const completedNodes =
            (delta.completedNodes as string[]) ?? prev.executionState.completedNodes
          const failedNodes = (delta.failedNodes as string[]) ?? prev.executionState.failedNodes
          const nodeTimings = delta.nodeTimings as Record<string, number> | undefined
          // activeNodes 中第一个节点视为当前节点
          const currentNodeId = activeNodes[0] ?? prev.executionState.currentNodeId
          return {
            ...prev,
            executionState: {
              ...prev.executionState,
              currentNodeId,
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

  /** 启动工作流。 */
  const startWorkflow = useCallback(
    (flowId: string | number, variables?: Record<string, unknown>, debug = false) => {
      abortControllerRef.current?.abort()
      const controller = new AbortController()
      abortControllerRef.current = controller
      runIdRef.current = null

      setState({
        runId: null,
        status: "running",
        messages: [],
        executionState: { status: "running", completedNodes: [], failedNodes: [] },
        pendingToolCallId: null
      })

      const run = async () => {
        try {
          const response = await fetch(buildApiUrl("/workflow/run"), {
            method: "POST",
            credentials: "include",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ flowId: Number(flowId), debug, variables: variables ?? {} }),
            signal: controller.signal
          })
          let terminal = await readEventStream(response, handleEvent, controller.signal)
          while (!terminal && !controller.signal.aborted && runIdRef.current) {
            await waitForReconnect(controller.signal)
            if (controller.signal.aborted || !runIdRef.current) break
            const resumed = await fetch(buildApiUrl(`/workflow/run/${runIdRef.current}/events`), {
              credentials: "include",
              signal: controller.signal
            })
            terminal = await readEventStream(resumed, handleEvent, controller.signal)
          }
        } catch (error) {
          if (error instanceof DOMException && error.name === "AbortError") return
          setState((prev) => ({
            ...prev,
            status: "failed",
            executionState: { ...prev.executionState, status: "failed" }
          }))
        }
      }
      void run()
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

      const response = await fetch(buildApiUrl(`/workflow/run/${state.runId}/input`), {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ input })
      })
      if (!response.ok) throw new Error("提交工作流输入失败")
    },
    [state.runId]
  )

  /** 取消工作流执行 */
  const cancel = useCallback(() => {
    abortControllerRef.current?.abort()
    abortControllerRef.current = null
    runIdRef.current = null
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
