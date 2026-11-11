/**
 * Canonical Task API 客户端——唯一映射 Task/Plan/Node/Execution/Dispatch 安全投影。
 * @author AaronZZH & Kiro
 */

import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useEventSubscription } from "@/lib/realtime/use-event-subscription"
import { backendApi } from "../backend-client"
import { restEndpoints } from "../endpoints"

export const TASK_STATUSES = [
  "DRAFT",
  "PLANNING",
  "READY",
  "RUNNING",
  "VERIFYING",
  "AWAITING_AUTHORIZATION",
  "AWAITING_CLARIFICATION",
  "PAUSING",
  "PAUSED",
  "CANCELING",
  "COMPLETED",
  "CANCELED",
  "FAILED"
] as const
export type TaskStatus = (typeof TASK_STATUSES)[number]
export type TaskSource = "CONVERSATION" | "MANUAL" | "AUTOMATION" | "PROMOTION"
export type TaskOwnerKind = "SYSTEM" | "ASSISTANT" | "AGENT" | "HUMAN"
export type TaskControlMode = "READ_ONLY" | "COLLABORATIVE" | "DELEGATED" | "AUTOMATED"
export type TaskPlanStatus = "DRAFT" | "FROZEN" | "SUPERSEDED"
export type TaskNodeKind = "COORDINATOR" | "EXECUTOR" | "EVALUATOR" | "AGGREGATOR"
export type TaskNodeStatus =
  | "PENDING"
  | "READY"
  | "CLAIMED"
  | "RUNNING"
  | "AWAITING_AUTHORIZATION"
  | "AWAITING_CLARIFICATION"
  | "PAUSED"
  | "VERIFYING"
  | "COMPLETED"
  | "RETRYABLE"
  | "FAILED"
  | "CANCELED"
  | "BLOCKED"
export type TaskExecutionScope = "TASK_ROOT" | "TASK_NODE"
export type ExecutorPlanStatus =
  | "DRAFT"
  | "PLANNING"
  | "SUBMITTED"
  | "REVIEW_REQUIRED"
  | "APPROVED"
  | "EXECUTING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED"
  | "REJECTED"
export type ExecutorPlanStepStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED"
export type TaskExecutionStatus =
  | "CREATED"
  | "READY"
  | "DISPATCHED"
  | "RUNNING"
  | "AWAITING_AUTHORIZATION"
  | "AWAITING_CLARIFICATION"
  | "PAUSED"
  | "COMPLETED"
  | "FAILED"
  | "CANCELED"
  | "SUPERSEDED"
export type TaskDispatchStatus = "PENDING" | "CLAIMED" | "DONE" | "CANCELED"

export interface TaskDispatchDetails {
  status: TaskDispatchStatus
  nextRunAt: string
  deliveryAttempts: number
  updatedAt: string
}

export interface TaskExecutionDetails {
  executionId: string
  nodeId: string | null
  scope: TaskExecutionScope
  attemptNo: number
  status: TaskExecutionStatus
  ownerKind: TaskOwnerKind
  consecutiveFailures: number
  latestDispatch: TaskDispatchDetails | null
  createdAt: string
  updatedAt: string
}

export interface ExecutorPlanStepDetails {
  ordinal: number
  title: string
  status: ExecutorPlanStepStatus
  result: string | null
  failure: string | null
}

export interface ExecutorPlanDetails {
  revision: number
  status: ExecutorPlanStatus
  goal: string
  steps: ExecutorPlanStepDetails[]
}

export interface TaskNodeDetails {
  nodeId: string
  kind: TaskNodeKind
  /**
   * 节点执行角色的稳定业务码。后端 TaskDetails.NodeDetails 当前未投影该字段（AAF-127 #12712 待后端补齐），
   * 缺省时首页看板的角色运行节点数为 0。
   */
  roleKey?: string | null
  description: string
  dependsOn: string[]
  status: TaskNodeStatus
  attempts: number
  maxAttempts: number
  result: string | null
  failure: string | null
  executorPlan: ExecutorPlanDetails | null
}

export interface TaskPlanDetails {
  planId: string
  revision: number
  status: TaskPlanStatus
  maxParallelism: number
  failurePolicy: "FAIL_TASK" | "PAUSE_TASK"
  aggregationKind: string
  nodes: TaskNodeDetails[]
}

export interface TaskRootResultDetails {
  result: string
  planRevision: number
  sourceNodeId: string
  committedAt: string
}

export interface TaskDetails {
  taskId: string
  conversationId: string
  originExecutionId: string | null
  source: TaskSource
  priority: number
  status: TaskStatus
  controlMode: TaskControlMode
  ownerKind: TaskOwnerKind
  goal: string | null
  plan: TaskPlanDetails | null
  rootResult: TaskRootResultDetails | null
  executions: TaskExecutionDetails[]
  createdAt: string
  updatedAt: string
  /** 等待外部结果且需人工决策时的阻断原因；为空表示无需人工介入。终态任务恒为空。 */
  decisionBlockers: string[]
}

export const taskKeys = {
  all: ["assistant-tasks"] as const,
  active: ["assistant-tasks", "active"] as const,
  list: (conversationId: string) => [...taskKeys.all, "list", conversationId] as const,
  detail: (taskId: string) => [...taskKeys.all, "detail", taskId] as const
}

export const taskApi = {
  list: (conversationId: string) =>
    backendApi.get<TaskDetails[]>(restEndpoints.ai.tasks, {
      params: { conversationId }
    }),
  /** 当前用户跨会话的未终结 Task（最多 50 条，按更新时间倒序） */
  active: () => backendApi.get<TaskDetails[]>(restEndpoints.ai.activeTasks),
  get: (taskId: string) => backendApi.get<TaskDetails>(restEndpoints.ai.task(taskId)),
  pause: (taskId: string, reason: string) =>
    backendApi.post<TaskDetails>(restEndpoints.ai.taskPause(taskId), { reason }),
  resume: (taskId: string) => backendApi.post<TaskDetails>(restEndpoints.ai.taskResume(taskId), {}),
  takeOver: (taskId: string, reason: string) =>
    backendApi.post<TaskDetails>(restEndpoints.ai.taskTakeOver(taskId), { reason }),
  handBack: (taskId: string) =>
    backendApi.post<TaskDetails>(restEndpoints.ai.taskHandBack(taskId), {}),
  cancel: (taskId: string, reason: string) =>
    backendApi.post<TaskDetails>(restEndpoints.ai.taskCancel(taskId), { reason })
}

export function isTaskTerminal(status: TaskStatus): boolean {
  return status === "COMPLETED" || status === "CANCELED" || status === "FAILED"
}

const TASK_STATUS_EVENT_TYPE = "task.status.changed"
const TRANSITIONING_POLL_MS = 3_000
const ACTIVE_POLL_MS = 10_000

/** 查询 canonical Task 详情；状态事件即时失效查询，低频轮询仅作断线兜底。 */
export function useTaskDetails(taskId: string | null) {
  const queryClient = useQueryClient()

  useEventSubscription(TASK_STATUS_EVENT_TYPE, (event) => {
    if (
      taskId === null ||
      event.type !== TASK_STATUS_EVENT_TYPE ||
      event.payload.taskId !== taskId
    ) {
      return
    }
    void queryClient.invalidateQueries({ queryKey: taskKeys.detail(taskId), exact: true })
  })

  return useQuery({
    queryKey: taskKeys.detail(taskId ?? ""),
    queryFn: () => taskApi.get(taskId as string),
    enabled: taskId !== null,
    refetchInterval: (query) => {
      const task = query.state.data
      if (!task || isTaskTerminal(task.status) || task.status === "PAUSED") return false
      return task.status === "PAUSING" || task.status === "CANCELING"
        ? TRANSITIONING_POLL_MS
        : ACTIVE_POLL_MS
    }
  })
}

const TASK_EVENT_PREFIX = "task."

/**
 * 查询当前用户跨会话的未终结 Task，供首页看板展示运行中任务与角色节点数。
 * 任何 `task.` 前缀事件只作失效通知，不读取 payload 写入本地状态；存在活跃任务时低频轮询兜底断线。
 */
export function useActiveTasks() {
  const queryClient = useQueryClient()

  useEventSubscription(TASK_EVENT_PREFIX, () => {
    void queryClient.invalidateQueries({ queryKey: taskKeys.active, exact: true })
  })

  return useQuery({
    queryKey: taskKeys.active,
    queryFn: taskApi.active,
    refetchInterval: (query) => ((query.state.data?.length ?? 0) > 0 ? ACTIVE_POLL_MS : false)
  })
}
