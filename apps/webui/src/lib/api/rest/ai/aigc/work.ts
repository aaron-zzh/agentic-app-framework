/**
 * AIGC 作品收录与 Publication API。
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PageResult } from "../../../types"
import { backendApi } from "../../backend-client"
import { aigcProjectKeys } from "./project"

export type AigcWorkStatus = "collected" | "published" | "archived"
export type AigcWorkVisibility = "PRIVATE" | "WORKSPACE" | "PUBLIC"
export type AigcPublicationStatus =
  | "pending"
  | "scheduled"
  | "publishing"
  | "published"
  | "failed"
  | "canceled"

export interface AigcWork {
  id: number
  version: number
  projectId: number
  deliverableObjectId: number
  adoptedObjectVersionId: number
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
  deliverableObjectId: number
  adoptedObjectVersionId: number
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
  scheduledTime?: string
  publishedTime?: string
  responsePayload?: Record<string, unknown>
  createTime: string
  updateTime: string
}

export interface AigcPublicationView {
  id: number
  workId: number
  channelSpecVersionId: number
  channelCode: string
  status: AigcPublicationStatus
  externalId?: string
  externalUrl?: string
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
  deliverableObjectId: number
  adoptedObjectVersionId: number
  coverMediaVersionId?: number
  visibility?: AigcWorkVisibility
}

export interface AigcWorkPublishInput {
  workId: number
  channelSpecVersionId: number
  scheduledAt?: string
}

export interface AigcPublicationResultInput {
  workId: number
  publicationId: number
  status: AigcPublicationStatus
  externalId?: string
  externalUrl?: string
  responseJson?: string
}

export const aigcWorkApi = {
  works: (params: AigcWorkParams = {}) =>
    backendApi.get<PageResult<AigcWork>>("/aigc/works", {
      params: { pageNo: 1, pageSize: 100, ...params }
    }),
  collect: (data: AigcWorkCollectInput) =>
    backendApi.post<AigcWorkView>("/aigc/works/_collect", data),
  publications: (workId: number) =>
    backendApi.get<AigcWorkPublication[]>(`/aigc/works/${workId}/publications`),
  publish: (input: AigcWorkPublishInput) =>
    backendApi.post<AigcPublicationView>(`/aigc/works/${input.workId}/publications`, {
      channelSpecVersionId: input.channelSpecVersionId,
      scheduledAt: input.scheduledAt,
      idempotencyKey: crypto.randomUUID()
    }),
  updatePublication: (input: AigcPublicationResultInput) =>
    backendApi.patch<AigcPublicationView>(
      `/aigc/works/${input.workId}/publications/${input.publicationId}/result`,
      {
        status: input.status,
        externalId: input.externalId,
        externalUrl: input.externalUrl,
        responseJson: input.responseJson
      }
    ),
  archive: (workId: number, expectedVersion: number) =>
    backendApi.post<AigcWorkView>(`/aigc/works/${workId}/_archive`, undefined, {
      params: { expectedVersion }
    })
}

export const aigcWorkKeys = {
  all: ["aigc.work"] as const,
  list: (params: AigcWorkParams) => ["aigc.work", "list", params] as const,
  publications: (workId: number) => ["aigc.work", "publications", workId] as const
}

export function useAigcWorks(params: AigcWorkParams = {}) {
  return useQuery({ queryKey: aigcWorkKeys.list(params), queryFn: () => aigcWorkApi.works(params) })
}

export function useAigcWorkPublications(workId: number) {
  return useQuery({
    queryKey: aigcWorkKeys.publications(workId),
    queryFn: () => aigcWorkApi.publications(workId)
  })
}

export function useCollectAigcWork() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcWorkApi.collect,
    onSuccess: (work) => {
      queryClient.invalidateQueries({ queryKey: aigcWorkKeys.all })
      queryClient.invalidateQueries({ queryKey: aigcProjectKeys.detail(work.projectId) })
      queryClient.invalidateQueries({ queryKey: aigcProjectKeys.summary(work.projectId) })
    }
  })
}

export function usePublishAigcWork() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcWorkApi.publish,
    onSuccess: (_publication, input) => {
      queryClient.invalidateQueries({ queryKey: aigcWorkKeys.publications(input.workId) })
      queryClient.invalidateQueries({ queryKey: aigcWorkKeys.all })
    }
  })
}

export function useUpdateAigcPublication() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcWorkApi.updatePublication,
    onSuccess: (_publication, input) => {
      queryClient.invalidateQueries({ queryKey: aigcWorkKeys.publications(input.workId) })
      queryClient.invalidateQueries({ queryKey: aigcWorkKeys.all })
    }
  })
}

export function useArchiveAigcWork() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ workId, expectedVersion }: { workId: number; expectedVersion: number }) =>
      aigcWorkApi.archive(workId, expectedVersion),
    onSuccess: (work) => {
      queryClient.invalidateQueries({ queryKey: aigcWorkKeys.all })
      queryClient.invalidateQueries({ queryKey: aigcProjectKeys.detail(work.projectId) })
      queryClient.invalidateQueries({ queryKey: aigcProjectKeys.summary(work.projectId) })
    }
  })
}
