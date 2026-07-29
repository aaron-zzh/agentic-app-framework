/**
 * 委托任务 API 客户端——任务查询、控制、执行事件与人工审批。
 * @author AaronZZH & Kiro
 *
 * @example
 * ```ts
 * const tasks = await delegatedTaskApi.list()
 * const events = await delegatedTaskApi.listEvents(tasks[0].taskId)
 * ```
 */

import { buildSseUrl } from "@/lib/api/config"
import { backendApi } from "../backend-client"
import { restEndpoints } from "../endpoints"

export const DELEGATED_TASK_STATUSES = [
  "PENDING",
  "RUNNING",
  "PAUSED",
  "AWAITING_AUTHORIZATION",
  "AWAITING_INPUT",
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

export const DELEGATED_TASK_EVENT_TYPES = [
  "EXECUTION_STARTED",
  "EXECUTION_COMPLETED",
  "EXECUTION_FAILED",
  "EXECUTION_CANCELED",
  "EXECUTION_PAUSED",
  "EXECUTION_RESUMED",
  "COMMAND_REJECTED",
  "RUN_STARTED",
  "RUN_COMPLETED",
  "RUN_FAILED",
  "MESSAGE_STARTED",
  "MESSAGE_DELTA",
  "MESSAGE_COMPLETED",
  "MODEL_CALL_STARTED",
  "MODEL_CALL_COMPLETED",
  "MODEL_CALL_FAILED",
  "TOOL_CALL_STARTED",
  "TOOL_CALL_COMPLETED",
  "TOOL_CALL_FAILED",
  "AUTHORIZATION_REQUESTED",
  "AUTHORIZATION_GRANTED",
  "AUTHORIZATION_DENIED",
  "AUTHORIZATION_REVOKED",
  "APPROVAL_REQUESTED",
  "APPROVAL_RESOLVED",
  "SUBTASK_CREATED",
  "SUBTASK_STARTED",
  "SUBTASK_COMPLETED",
  "SUBTASK_FAILED",
  "SUBTASK_CANCELED",
  "VALIDATION_STARTED",
  "VALIDATION_COMPLETED",
  "VALIDATION_FAILED",
  "RECOVERY_STARTED",
  "RECOVERY_COMPLETED",
  "OWNERSHIP_TRANSFERRED",
  "TASK_STATUS_CHANGED",
  "CONTROL_MODE_CHANGED",
  "INPUT_CANCELED",
  "INPUT_MODIFIED",
  "INPUT_SUPPLEMENTED",
  "INPUT_UNRELATED"
] as const

export type DelegatedTaskEventType = (typeof DELEGATED_TASK_EVENT_TYPES)[number]
export type ExecutionEventStatus =
  | "DRAFT"
  | "PLANNING"
  | "AWAITING_AUTHORIZATION"
  | "AWAITING_INPUT"
  | "RUNNING"
  | "VERIFYING"
  | "PAUSED"
  | "COMPLETED"
  | "CANCELED"
  | "FAILED"
  | "REJECTED"
  | "RECOVERING"
export type ExecutionControlMode = "READ_ONLY" | "COLLABORATIVE" | "DELEGATED" | "AUTOMATED"
export type ExecutionOwnerType = "SYSTEM" | "HUMAN" | "ASSISTANT" | "AGENT"

export interface DelegatedTaskEventVO {
  eventOffset: number
  eventId: string
  tenantId: string
  conversationId: string
  sessionId: string
  taskId: string
  executionId: string
  runId: string
  parentExecutionId: string | null
  sequence: number
  type: DelegatedTaskEventType
  status: ExecutionEventStatus
  controlMode: ExecutionControlMode
  ownerType: ExecutionOwnerType
  assistantId: string | null
  agentId: string | null
  userId: string | null
  correlationId: string
  causationId: string | null
  idempotencyKey: string | null
  payload: Record<string, unknown>
  createdAt: string
}

export interface DelegatedTaskCreateRequest {
  source: "CONVERSATION" | "MANUAL"
  conversationId: string
  title: string
  description?: string
  priority: number
}

export interface DelegatedTaskInputRequest {
  inputId: string
  kind: "CANCEL" | "MODIFY" | "SUPPLEMENT" | "UNRELATED"
  content?: string
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
  create: (request: DelegatedTaskCreateRequest) =>
    backendApi.post<DelegatedTaskVO>(restEndpoints.ai.delegatedTasks, request),

  list: (status?: DelegatedTaskStatus) =>
    backendApi.get<DelegatedTaskVO[]>(restEndpoints.ai.delegatedTaskList, {
      params: status ? { status } : undefined
    }),

  get: (taskId: string) =>
    backendApi.get<DelegatedTaskVO>(restEndpoints.ai.delegatedTask(taskId)),

  listEvents: (taskId: string) =>
    backendApi.get<DelegatedTaskEventVO[]>(restEndpoints.ai.delegatedTaskEvents(taskId)),

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

export function getDelegatedTaskEventStreamUrl(taskId: string): string {
  return buildSseUrl(restEndpoints.ai.delegatedTaskEventStream(taskId))
}

export function isDelegatedTaskEvent(value: unknown): value is DelegatedTaskEventVO {
  if (typeof value !== "object" || value === null) return false
  const event = value as Record<string, unknown>
  return (
    typeof event.eventOffset === "number" &&
    typeof event.eventId === "string" &&
    typeof event.taskId === "string" &&
    typeof event.type === "string" &&
    typeof event.status === "string" &&
    typeof event.createdAt === "string" &&
    typeof event.payload === "object" &&
    event.payload !== null
  )
}

export function parseDelegatedTaskEvent(data: string): DelegatedTaskEventVO | null {
  try {
    const value: unknown = JSON.parse(data)
    return isDelegatedTaskEvent(value) ? value : null
  } catch {
    return null
  }
}

export function isDelegatedTaskTerminal(status: DelegatedTaskStatus): boolean {
  return status === "COMPLETED" || status === "CANCELED" || status === "FAILED"
}
