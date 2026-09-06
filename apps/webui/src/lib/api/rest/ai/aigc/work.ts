/**
 * AIGC Work 与 Publication API。
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PageResult } from "../../../types"
import { backendApi } from "../../backend-client"
import { invalidateAigcProject } from "./project"

export type AigcWorkStatus = "COLLECTED" | "PUBLISHED" | "ARCHIVED"
export type AigcWorkVisibility = "PRIVATE" | "WORKSPACE" | "PUBLIC"
export type AigcPublicationStatus = "PENDING" | "SCHEDULED" | "PUBLISHING" | "SUCCEEDED" | "FAILED" | "CANCELED"

export interface AigcWork {
  id: number
  version: number
  projectId: number
  deliverableSetObjectId: number
  manifestObjectVersionId: number
  title: string
  coverMediaVersionId?: number
  status: AigcWorkStatus
  visibility: AigcWorkVisibility
  userId: number
  createTime: string
  updateTime: string
}

export interface AigcWorkView {
  id: number
  projectId: number
  deliverableSetObjectId: number
  manifestObjectVersionId: number
  status: AigcWorkStatus
  version: number
}

export interface AigcWorkPublication {
  id: number
  version: number
  workId: number
  channelSpecVersionId: number
  channelCode: string
  externalId?: string
  externalUrl?: string
  status: AigcPublicationStatus
  retryOfPublicationId?: number
  retryCount: number
  failureCode?: string
  failureMessage?: string
  scheduledTime?: string
  publishedTime?: string
  canceledTime?: string
  cancelReason?: string
  responsePayload?: Record<string, unknown>
  createTime: string
  updateTime: string
}

export interface AigcPublicationView {
  id: number
  version: number
  workId: number
  channelSpecVersionId: number
  channelCode: string
  status: AigcPublicationStatus
  retryOfPublicationId?: number
  retryCount: number
  externalId?: string
  externalUrl?: string
  failureCode?: string
  failureMessage?: string
}

export interface AigcWorkParams {
  pageNo?: number
  pageSize?: number
  projectId?: number
  status?: AigcWorkStatus
  visibility?: AigcWorkVisibility
}

export interface AigcWorkCollectInput {
  projectId: number
  deliverableSetObjectId: number
  manifestObjectVersionId: number
  expectedProjectVersion: number
  coverMediaVersionId?: number
  visibility?: AigcWorkVisibility
  idempotencyKey: string
}
export interface AigcWorkPublishInput { workId: number; expectedProjectVersion: number; expectedWorkVersion: number; channelSpecVersionId: number; scheduledAt?: string; idempotencyKey: string }
export interface AigcPublicationCancelInput { workId: number; publicationId: number; expectedProjectVersion: number; expectedWorkVersion: number; expectedPublicationVersion: number; reason: string; idempotencyKey: string }
export interface AigcPublicationRetryInput { workId: number; publicationId: number; expectedProjectVersion: number; expectedWorkVersion: number; expectedPublicationVersion: number; scheduledAt?: string; idempotencyKey: string }
export interface AigcWorkArchiveInput { workId: number; expectedProjectVersion: number; expectedWorkVersion: number; reason: string; idempotencyKey: string }

export const aigcWorkApi = {
  works: (params: AigcWorkParams = {}) => backendApi.get<PageResult<AigcWork>>("/aigc/works", { params: { pageNo: 1, pageSize: 100, ...params } }),
  collect: (data: AigcWorkCollectInput) => backendApi.post<AigcWorkView>("/aigc/works/_collect", data),
  publications: (workId: number) => backendApi.get<AigcWorkPublication[]>(`/aigc/works/${workId}/publications`),
  publish: (input: AigcWorkPublishInput) => backendApi.post<AigcPublicationView>(`/aigc/works/${input.workId}/publications`, {
    expectedProjectVersion: input.expectedProjectVersion,
    expectedWorkVersion: input.expectedWorkVersion,
    channelSpecVersionId: input.channelSpecVersionId,
    scheduledAt: input.scheduledAt,
    idempotencyKey: input.idempotencyKey
  }),
  cancelPublication: (input: AigcPublicationCancelInput) => backendApi.post<AigcPublicationView>(`/aigc/works/${input.workId}/publications/${input.publicationId}/_cancel`, {
    expectedProjectVersion: input.expectedProjectVersion,
    expectedWorkVersion: input.expectedWorkVersion,
    expectedPublicationVersion: input.expectedPublicationVersion,
    reason: input.reason,
    idempotencyKey: input.idempotencyKey
  }),
  retryPublication: (input: AigcPublicationRetryInput) => backendApi.post<AigcPublicationView>(`/aigc/works/${input.workId}/publications/${input.publicationId}/_retry`, {
    expectedProjectVersion: input.expectedProjectVersion,
    expectedWorkVersion: input.expectedWorkVersion,
    expectedPublicationVersion: input.expectedPublicationVersion,
    scheduledAt: input.scheduledAt,
    idempotencyKey: input.idempotencyKey
  }),
  archive: (input: AigcWorkArchiveInput) => backendApi.post<AigcWorkView>(`/aigc/works/${input.workId}/_archive`, {
    expectedProjectVersion: input.expectedProjectVersion,
    expectedWorkVersion: input.expectedWorkVersion,
    idempotencyKey: input.idempotencyKey,
    reason: input.reason
  })
}

export const aigcWorkKeys = {
  all: ["aigc.work"] as const,
  list: (params: AigcWorkParams) => ["aigc.work", "list", params] as const,
  publications: (workId: number) => ["aigc.work", "publications", workId] as const
}

export function useAigcWorks(params: AigcWorkParams = {}) { return useQuery({ queryKey: aigcWorkKeys.list(params), queryFn: () => aigcWorkApi.works(params) }) }
export function useAigcWorkPublications(workId: number) { return useQuery({ queryKey: aigcWorkKeys.publications(workId), queryFn: () => aigcWorkApi.publications(workId), enabled: workId > 0 }) }
function invalidateWork(queryClient: ReturnType<typeof useQueryClient>, projectId?: number, workId?: number) {
  queryClient.invalidateQueries({ queryKey: aigcWorkKeys.all })
  queryClient.invalidateQueries({ queryKey: ["aigc.project", "summary"] })
  queryClient.invalidateQueries({ queryKey: ["aigc.project", "completion-evidence"] })
  if (workId !== undefined) queryClient.invalidateQueries({ queryKey: aigcWorkKeys.publications(workId) })
  if (projectId !== undefined) return invalidateAigcProject(queryClient, projectId)
}
export function useCollectAigcWork() {
  const queryClient = useQueryClient()
  return useMutation({ mutationFn: aigcWorkApi.collect, onSuccess: (work) => invalidateWork(queryClient, work.projectId, work.id) })
}
export function usePublishAigcWork() {
  const queryClient = useQueryClient()
  return useMutation({ mutationFn: aigcWorkApi.publish, onSuccess: (_value, input) => invalidateWork(queryClient, undefined, input.workId) })
}
export function useCancelAigcPublication() {
  const queryClient = useQueryClient()
  return useMutation({ mutationFn: aigcWorkApi.cancelPublication, onSuccess: (_value, input) => invalidateWork(queryClient, undefined, input.workId) })
}
export function useRetryAigcPublication() {
  const queryClient = useQueryClient()
  return useMutation({ mutationFn: aigcWorkApi.retryPublication, onSuccess: (_value, input) => invalidateWork(queryClient, undefined, input.workId) })
}
export function useArchiveAigcWork() {
  const queryClient = useQueryClient()
  return useMutation({ mutationFn: aigcWorkApi.archive, onSuccess: (work) => invalidateWork(queryClient, work.projectId, work.id) })
}
