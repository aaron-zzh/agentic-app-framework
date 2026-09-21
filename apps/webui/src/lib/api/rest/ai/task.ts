/**
 * Canonical Task API 客户端——唯一映射 Task/Plan/Node/Execution/Dispatch 安全投影。
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
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
  list: (conversationId: string) => [...taskKeys.all, "list", conversationId] as const,
  detail: (taskId: string) => [...taskKeys.all, "detail", taskId] as const
}

export const taskApi = {
  list: (conversationId: string) =>
    backendApi.get<TaskDetails[]>(restEndpoints.ai.tasks, {
      params: { conversationId }
    }),
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

/** 查询 canonical Task 详情；非终态期间轮询服务端权威投影。 */
export function useTaskDetails(taskId: string | null) {
  return useQuery({
    queryKey: taskKeys.detail(taskId ?? ""),
    queryFn: () => taskApi.get(taskId as string),
    enabled: taskId !== null,
    refetchInterval: (query) => {
      const task = query.state.data
      return task && !isTaskTerminal(task.status) ? 3000 : false
    }
  })
}
