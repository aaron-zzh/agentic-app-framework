/**
 * 委托任务 API 客户端——任务查询、控制、公开事件重放与人工审批。
 * @author AaronZZH & Kiro
 */

import { buildSseUrl } from "@/lib/api/config"
import { backendApi } from "../backend-client"
import { restEndpoints } from "../endpoints"

export const DELEGATED_TASK_STATUSES = [
  "PENDING",
  "RUNNING",
  "PAUSED",
  "AWAITING_AUTHORIZATION",
  "AWAITING_CLARIFICATION",
  "COMPLETED",
  "CANCELED",
  "FAILED"
] as const
export type DelegatedTaskStatus = (typeof DELEGATED_TASK_STATUSES)[number]
export type DelegatedTaskSource = "CONVERSATION" | "MANUAL" | "AUTOMATION"
export type DelegatedTaskOwnerKind = "ASSISTANT" | "AGENT" | "HUMAN"

export interface DelegatedTaskVO {
  taskId: string
  conversationId: string
  executionId: string
  sessionId: string
  source: DelegatedTaskSource
  priority: number
  status: DelegatedTaskStatus
  ownerKind: DelegatedTaskOwnerKind
  ownerId: string
  modelCalls: number
  modelTokens: number
  toolCalls: number
  toolUnits: number
  credits: number
  attempts: number
  consecutiveFailures: number
  nextRunAt: string
  leaseUntil: string | null
  checkpoint: Record<string, unknown>
  createdAt: string
  updatedAt: string
}

export const AAF_AI_TASK_EVENT_TYPES = [
  "aaf.task.started",
  "aaf.task.completed",
  "aaf.task.failed",
  "aaf.task.canceled",
  "aaf.task.paused",
  "aaf.task.resumed",
  "aaf.task.rejected",
  "aaf.run.started",
  "aaf.run.completed",
  "aaf.run.failed",
  "aaf.message.started",
  "aaf.message.progress",
  "aaf.message.completed",
  "aaf.model.started",
  "aaf.model.completed",
  "aaf.model.failed",
  "aaf.tool.started",
  "aaf.tool.completed",
  "aaf.tool.failed",
  "aaf.authorization.requested",
  "aaf.authorization.granted",
  "aaf.authorization.denied",
  "aaf.authorization.revoked",
  "aaf.hitl.requested",
  "aaf.hitl.resolved",
  "aaf.clarification.requested",
  "aaf.clarification.updated",
  "aaf.clarification.resolved",
  "aaf.clarification.canceled",
  "aaf.clarification.expired",
  "aaf.iteration.evaluated",
  "aaf.iteration.stopped",
  "aaf.subtask.created",
  "aaf.subtask.started",
  "aaf.subtask.completed",
  "aaf.subtask.failed",
  "aaf.subtask.canceled",
  "aaf.validation.started",
  "aaf.validation.completed",
  "aaf.validation.failed",
  "aaf.recovery.started",
  "aaf.recovery.completed",
  "aaf.ownership.transferred",
  "aaf.task.status_changed",
  "aaf.control_mode.changed",
  "aaf.input.canceled",
  "aaf.input.modified",
  "aaf.input.supplemented",
  "aaf.input.unrelated",
  "aaf.stream.closed"
] as const
export type AafAiTaskEventType = (typeof AAF_AI_TASK_EVENT_TYPES)[number]
export type AafAiTaskEventData = Record<string, string | number | boolean>

export interface AafAiTaskEvent {
  eventId: string
  taskId: string
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

export interface AafAiTaskPublicState {
  taskId: string | null
  executionId: string | null
  runId: string | null
  sessionId: string | null
  status: string
  sequence: number
  eventOffset: number | null
  terminal: boolean
  eventCount: number
}

export interface AafAiTaskSnapshot {
  requestedAfterEventOffset: number
  nextEventOffset: number
  deliveryGuarantee: "AT_LEAST_ONCE"
  state: AafAiTaskPublicState
  events: AafAiTaskEvent[]
}

export interface DelegatedTaskInputRequest {
  inputId: string
  /** 服务端会重新分类判定，不直接信任本字段；取消不走本接口，使用 stop。 */
  kind: "MODIFY" | "SUPPLEMENT" | "UNRELATED"
  /** 原始自然语言输入；MODIFY 据此重新协调规划。 */
  text?: string
  values?: Record<string, string>
}
export interface HumanApprovalDecisionRequest {
  decision: "APPROVED" | "REJECTED"
  reason?: string
}
export interface HumanApprovalVO {
  approvalId: string
  taskId: string
  action: string
  resource: string
  status: "PENDING" | "APPROVED" | "REJECTED"
  decidedAt: string | null
}

export const delegatedTaskKeys = {
  all: ["delegated-tasks"] as const,
  list: (conversationId: string) => ["delegated-tasks", "list", conversationId] as const,
  detail: (taskId: string) => ["delegated-tasks", "detail", taskId] as const,
  events: (taskId: string) => ["delegated-tasks", "events", taskId] as const
}

export const delegatedTaskApi = {
  list: (status?: DelegatedTaskStatus) =>
    backendApi.get<DelegatedTaskVO[]>(restEndpoints.ai.delegatedTaskList, {
      params: status ? { status } : undefined
    }),
  get: (taskId: string) => backendApi.get<DelegatedTaskVO>(restEndpoints.ai.delegatedTask(taskId)),
  listEvents: (taskId: string, afterEventOffset = 0) =>
    backendApi.get<AafAiTaskSnapshot>(restEndpoints.ai.delegatedTaskEvents(taskId), {
      params: { afterEventOffset }
    }),
  stop: (taskId: string, reason: string) =>
    backendApi.post<DelegatedTaskVO>(restEndpoints.ai.delegatedTaskStop(taskId), { reason }),
  takeOver: (taskId: string, reason: string) =>
    backendApi.post<DelegatedTaskVO>(restEndpoints.ai.delegatedTaskTakeOver(taskId), { reason }),
  handBack: (taskId: string) =>
    backendApi.post<DelegatedTaskVO>(restEndpoints.ai.delegatedTaskHandBack(taskId)),
  submitInput: (taskId: string, request: DelegatedTaskInputRequest) =>
    backendApi.post<DelegatedTaskVO>(restEndpoints.ai.delegatedTaskInputs(taskId), request)
}

export const humanApprovalApi = {
  decide: (approvalId: string, request: HumanApprovalDecisionRequest) =>
    backendApi.post<HumanApprovalVO>(restEndpoints.ai.humanApprovalDecision(approvalId), request),
  recover: (approvalId: string) =>
    backendApi.post<boolean>(restEndpoints.ai.humanApprovalRecover(approvalId))
}

export function getDelegatedTaskEventStreamUrl(taskId: string, afterEventOffset = 0): string {
  return `${buildSseUrl(restEndpoints.ai.delegatedTaskEventStream(taskId))}?afterEventOffset=${encodeURIComponent(afterEventOffset)}`
}

export function isAafAiTaskEvent(value: unknown): value is AafAiTaskEvent {
  if (typeof value !== "object" || value === null) return false
  const event = value as Record<string, unknown>
  return (
    typeof event.eventId === "string" &&
    typeof event.taskId === "string" &&
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

export function parseAafAiTaskEvent(data: string): AafAiTaskEvent | null {
  try {
    const value: unknown = JSON.parse(data)
    return isAafAiTaskEvent(value) ? value : null
  } catch {
    return null
  }
}

export function appendAafAiTaskEvent(
  snapshot: AafAiTaskSnapshot,
  event: AafAiTaskEvent
): AafAiTaskSnapshot {
  if (snapshot.events.some((item) => item.eventId === event.eventId)) return snapshot
  const nextEventOffset =
    event.eventOffset === null
      ? snapshot.nextEventOffset
      : Math.max(snapshot.nextEventOffset, event.eventOffset)
  return {
    ...snapshot,
    nextEventOffset,
    events: [...snapshot.events, event].toSorted(
      (left, right) =>
        (left.eventOffset ?? Number.MAX_SAFE_INTEGER) -
        (right.eventOffset ?? Number.MAX_SAFE_INTEGER)
    )
  }
}

export function isDelegatedTaskTerminal(status: DelegatedTaskStatus): boolean {
  return status === "COMPLETED" || status === "CANCELED" || status === "FAILED"
}
