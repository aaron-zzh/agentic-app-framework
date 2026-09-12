/**
 * AAF Assistant 公开事件安全信封；仅供 AG-UI CUSTOM 事件识别与投影。
 * @author AaronZZH & Kiro
 */

export type AafAiTaskEventType = `aaf.${string}`
export type AafAiTaskEventData = Record<string, string | number | boolean>

export interface AafAiTaskEvent {
  eventId: string
  taskId: string | null
  executionId: string
  runId: string
  sessionId: string
  sequence: number
  eventOffset: number | null
  status: string
  type: AafAiTaskEventType
  version: number
  audience: "END_USER"
  delivery: "CURSOR_AT_LEAST_ONCE" | "LIVE_BEST_EFFORT" | "PROJECTION_ONLY"
  data: AafAiTaskEventData
  createdAt: string
}

export function isAafAiTaskEvent(value: unknown): value is AafAiTaskEvent {
  if (typeof value !== "object" || value === null) return false
  const event = value as Record<string, unknown>
  return (
    typeof event.eventId === "string" &&
    (typeof event.taskId === "string" || event.taskId === null) &&
    typeof event.executionId === "string" &&
    typeof event.sequence === "number" &&
    (typeof event.eventOffset === "number" || event.eventOffset === null) &&
    typeof event.status === "string" &&
    typeof event.type === "string" &&
    event.type.startsWith("aaf.") &&
    typeof event.version === "number" &&
    typeof event.createdAt === "string" &&
    typeof event.data === "object" &&
    event.data !== null &&
    !Array.isArray(event.data)
  )
}
