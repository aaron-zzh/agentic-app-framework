/**
 * AIGC 动作与 ExecutionRun API。
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PageResult } from "../../../types"
import { backendApi } from "../../backend-client"
import { aigcProjectKeys, invalidateAigcProject } from "./project"

export type AigcExecutionStatus =
  | "PENDING_BIND"
  | "PENDING"
  | "RUNNING"
  | "PARTIALLY_SUCCEEDED"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELED"
export type AigcExecutionTargetType = "AGENT" | "TOOL" | "WORKFLOW" | "UNRESOLVED"
export type AigcExecutionRunKind = "ACTIVITY" | "ORCHESTRATION"

export interface AigcActionOption {
  actionKey: string
  label: string
  targetType: AigcExecutionTargetType
  confirmationRequired: boolean
  estimatedCredits?: number
  applicableObjectTypes: string[]
}

export interface AigcActionCommand {
  objectId?: number
  actionKey: string
  prompt?: string
  requestedModelId?: string
  actionArguments?: Record<string, unknown>
  attachmentMediaVersionIds?: number[]
  selectedProjectObjectIds?: number[]
  expectedGraphRevision?: number
  confirmed?: boolean
  idempotencyKey: string
}

export interface AigcExecutionRun {
  id: number
  projectId: number
  objectId?: number
  parentExecutionRunId?: number
  rootExecutionRunId?: number
  retryOfExecutionRunId?: number
  runKind: AigcExecutionRunKind
  workflowNodeKey?: string
  executionSubmissionId: number
  executionReservationId: number
  targetGraphRevision: number
  frozenProjectObjectIds: number[]
  effectiveInput: Record<string, unknown>
  bindingVersionId?: number
  actionKey: string
  targetType: AigcExecutionTargetType
  targetRef?: string
  status: AigcExecutionStatus
  generationMode?: string
  roleProfileCode?: string
  selectedModelVersion?: string
  outputPayload?: Record<string, unknown>
  taskIds: number[]
  costCredits?: number
  retryCount: number
  errorMessage?: string
  startTime?: string
  endTime?: string
  createTime: string
}

export interface AigcExecutionRunView {
  id: number
  projectId: number
  projectObjectId?: number
  parentExecutionRunId?: number
  rootExecutionRunId?: number
  retryOfExecutionRunId?: number
  runKind: string
  workflowNodeKey?: string
  executionSubmissionId: number
  executionReservationId: number
  targetGraphRevision: number
  frozenProjectObjectIds: number[]
  effectiveInput: Record<string, unknown>
  bindingVersionId?: number
  actionKey: string
  targetType: string
  targetRef?: string
  status: AigcExecutionStatus
  taskIds: number[]
  candidateObjectVersionIds: number[]
  candidateMediaVersionIds: number[]
  runtimeTraceId?: string
  runtimeRunId?: string
  output?: string
  creditCost?: number
}

export interface AigcExecutionRunTreeView {
  root: AigcExecutionRunView
  descendants: AigcExecutionRunView[]
}

export interface AigcExecutionRunParams {
  pageNo?: number
  pageSize?: number
  projectId?: number
  objectId?: number
  status?: AigcExecutionStatus
  rootOnly?: boolean
}

export const aigcExecutionApi = {
  actions: (projectId: number) =>
    backendApi.get<AigcActionOption[]>(`/aigc/projects/${projectId}/actions`),
  execute: (projectId: number, data: AigcActionCommand) =>
    backendApi.post<AigcExecutionRunView>(`/aigc/projects/${projectId}/actions`, data, {
      showError: false
    }),
  runs: (params: AigcExecutionRunParams = {}) =>
    backendApi.get<PageResult<AigcExecutionRun>>("/aigc/execution-runs", {
      params: { pageNo: 1, pageSize: 20, ...params }
    }),
  tree: (id: number) => backendApi.get<AigcExecutionRunTreeView>(`/aigc/execution-runs/${id}/tree`),
  cancel: (id: number, reason?: string) =>
    backendApi.post<AigcExecutionRunView>(`/aigc/execution-runs/${id}/_cancel`, undefined, {
      params: { reason }
    }),
  retry: (id: number, idempotencyKey: string) =>
    backendApi.post<AigcExecutionRunView>(`/aigc/execution-runs/${id}/_retry`, undefined, {
      params: { idempotencyKey }
    })
}

export const aigcExecutionKeys = {
  all: ["aigc.execution"] as const,
  actions: (projectId: number) => ["aigc.execution", "actions", projectId] as const,
  runs: (params: AigcExecutionRunParams) => ["aigc.execution", "runs", params] as const,
  tree: (rootRunId: number) => ["aigc.execution", "tree", rootRunId] as const
}

export function useAigcProjectActions(projectId: number | null) {
  return useQuery({
    queryKey: aigcExecutionKeys.actions(projectId ?? 0),
    queryFn: () => aigcExecutionApi.actions(projectId as number),
    enabled: projectId !== null
  })
}

export function useAigcExecutionRuns(params: AigcExecutionRunParams = {}, enabled = true) {
  return useQuery({
    queryKey: aigcExecutionKeys.runs(params),
    queryFn: () => aigcExecutionApi.runs(params),
    enabled
  })
}

/** 仅在用户展开单个 root 时加载完整 RunTree，避免列表首屏 1+N。 */
export function useAigcExecutionRunTree(rootRunId: number, enabled = true) {
  return useQuery({
    queryKey: aigcExecutionKeys.tree(rootRunId),
    queryFn: () => aigcExecutionApi.tree(rootRunId),
    enabled
  })
}

export function invalidateAigcExecution(
  queryClient: ReturnType<typeof useQueryClient>,
  projectId: number,
  objectId?: number
) {
  queryClient.invalidateQueries({ queryKey: aigcExecutionKeys.all })
  queryClient.invalidateQueries({ queryKey: aigcProjectKeys.mediaRefs(projectId) })
  if (objectId !== undefined)
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.versions(projectId, objectId) })
  return invalidateAigcProject(queryClient, projectId)
}

export function useRunAigcAction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ projectId, data }: { projectId: number; data: AigcActionCommand }) =>
      aigcExecutionApi.execute(projectId, data),
    onSuccess: (run) => invalidateAigcExecution(queryClient, run.projectId, run.projectObjectId)
  })
}
export function useCancelAigcExecutionRun() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, reason }: { id: number; reason?: string }) =>
      aigcExecutionApi.cancel(id, reason),
    onSuccess: (run) => invalidateAigcExecution(queryClient, run.projectId, run.projectObjectId)
  })
}
export function useRetryAigcExecutionRun() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => aigcExecutionApi.retry(id, crypto.randomUUID()),
    onSuccess: (run) => invalidateAigcExecution(queryClient, run.projectId, run.projectObjectId)
  })
}
