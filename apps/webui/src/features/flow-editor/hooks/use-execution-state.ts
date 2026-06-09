/**
 * 运行时状态订阅——通过 SSE 接收流程执行状态和日志
 * @author AaronZZH & Kiro
 */

import { useCallback, useEffect, useState } from "react"
import { buildSseUrl } from "@/lib/api/config"
import { useAuthStore } from "@/lib/store/auth-store"
import type { ExecutionState } from "../types"

/** 节点执行日志条目 */
export interface NodeExecutionLog {
  nodeId: string
  nodeName: string
  input: Record<string, unknown>
  output: Record<string, unknown>
  durationMs: number
  status: "running" | "completed" | "failed"
  error?: string
  timestamp: string
}

/** SSE 事件数据类型 */
interface ExecutionEvent {
  type: "state" | "log"
  state?: ExecutionState
  log?: NodeExecutionLog
}

/** 订阅流程执行状态 */
export function useExecutionState(processInstanceId?: string) {
  const [state, setState] = useState<ExecutionState>({
    status: "idle",
    completedNodes: [],
    failedNodes: []
  })
  const [nodeLogs, setNodeLogs] = useState<NodeExecutionLog[]>([])
  const [nodeTimings, setNodeTimings] = useState<Record<string, number>>({})

  const reset = useCallback(() => {
    setState({ status: "idle", completedNodes: [], failedNodes: [] })
    setNodeLogs([])
    setNodeTimings({})
  }, [])

  useEffect(() => {
    if (!processInstanceId) return

    reset()
    const source = new EventSource(buildSseUrl(`/flows/${processInstanceId}/execution-events`, useAuthStore.getState().accessToken))

    source.onmessage = (event) => {
      const data = JSON.parse(event.data) as ExecutionEvent

      if (data.type === "state" && data.state) {
        setState(data.state)
        if (data.state.nodeTimings) {
          setNodeTimings(data.state.nodeTimings)
        }
      }

      if (data.type === "log" && data.log) {
        setNodeLogs((prev) => [...prev, data.log as NodeExecutionLog])
        const nodeId = data.log.nodeId
        const durationMs = data.log.durationMs
        if (durationMs > 0 && nodeId) {
          setNodeTimings((prev) => ({
            ...prev,
            [nodeId]: durationMs
          }))
        }
      }
    }

    source.onerror = () => {
      source.close()
    }

    return () => source.close()
  }, [processInstanceId, reset])

  return { state, nodeLogs, nodeTimings, reset }
}
