/**
 * AIGC 动作与执行记录 API。
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PageResult } from "../../../types"
import { backendApi } from "../../backend-client"
import { aigcProjectKeys } from "./project"

export type AigcExecutionStatus = "pending" | "running" | "succeeded" | "failed" | "canceled"
export type AigcExecutionTargetType = "agent" | "tool" | "workflow" | "unresolved"

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
  attachmentMediaVersionIds?: number[]
  confirmed?: boolean
  idempotencyKey: string
}

export interface AigcExecutionRun {
  id: number
  projectId: number
  objectId?: number
  parentRunId?: number
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
  actionKey: string
  targetType: AigcExecutionTargetType
  targetRef?: string
  status: AigcExecutionStatus
  taskIds: number[]
  candidateObjectVersionIds: number[]
  candidateMediaVersionIds: number[]
  creditCost?: number
}

export interface AigcExecutionRunParams {
  pageNo?: number
  pageSize?: number
  projectId?: number
  objectId?: number
  status?: AigcExecutionStatus
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
      params: { pageNo: 1, pageSize: 200, ...params }
    }),
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
  runs: (params: AigcExecutionRunParams) => ["aigc.execution", "runs", params] as const
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

function invalidateExecution(
  queryClient: ReturnType<typeof useQueryClient>,
  projectId: number,
  objectId?: number
) {
  queryClient.invalidateQueries({ queryKey: aigcExecutionKeys.all })
  queryClient.invalidateQueries({ queryKey: aigcProjectKeys.graph(projectId) })
  queryClient.invalidateQueries({ queryKey: aigcProjectKeys.summary(projectId) })
  if (objectId !== undefined) {
    queryClient.invalidateQueries({ queryKey: aigcProjectKeys.versions(projectId, objectId) })
  }
}

export function useRunAigcAction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ projectId, data }: { projectId: number; data: AigcActionCommand }) =>
      aigcExecutionApi.execute(projectId, data),
    onSuccess: (run) => invalidateExecution(queryClient, run.projectId, run.projectObjectId)
  })
}

export function useCancelAigcExecutionRun() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, reason }: { id: number; reason?: string }) =>
      aigcExecutionApi.cancel(id, reason),
    onSuccess: (run) => invalidateExecution(queryClient, run.projectId, run.projectObjectId)
  })
}

export function useRetryAigcExecutionRun() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => aigcExecutionApi.retry(id, crypto.randomUUID()),
    onSuccess: (run) => invalidateExecution(queryClient, run.projectId, run.projectObjectId)
  })
}
