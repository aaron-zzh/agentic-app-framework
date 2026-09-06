/**
 * AIGC 轻时间线 API；Track/Clip 只作为 Composition 子记录读取。
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { ApiError } from "@/lib/api/errors"
import type { PageResult } from "../../../types"
import { backendApi } from "../../backend-client"
import { invalidateAigcProject } from "./project"

export interface AigcTimelineClip {
  id: number
  trackId: number
  mediaVersionId: number
  sourceObjectId?: number
  sourceObjectVersionId?: number
  positionMs: number
  inMs: number
  outMs: number
  properties?: Record<string, unknown>
  transition?: Record<string, unknown>
  volume?: number
}

export interface AigcTimelineTrack {
  id: number
  compositionId: number
  trackType: string
  name: string
  sortOrder: number
  muted: boolean
  locked: boolean
  clips: AigcTimelineClip[]
}

export interface AigcTimelineComposition {
  id: number
  version: number
  projectId: number
  deliverableObjectId?: number
  title: string
  durationMs: number
  fps: number
  width: number
  height: number
  status: string
  adoptedRevisionNo?: number
  tracks: AigcTimelineTrack[]
  createTime: string
  updateTime: string
}

export interface AigcTimelineParams {
  pageNo?: number
  pageSize?: number
  projectId?: number
  deliverableObjectId?: number
  status?: string
}

export interface AigcTimelineCreateInput {
  projectId: number
  expectedProjectVersion: number
  deliverableObjectId?: number
  title: string
  durationMs?: number
  frameRate?: number
  width?: number
  height?: number
}

export interface AigcTimelineClipInput {
  mediaVersionId?: number
  sourceObjectId?: number
  sourceObjectVersionId?: number
  positionMs?: number
  inMs?: number
  outMs?: number
  propertiesJson?: string
  transitionJson?: string
  volume?: number
}

export interface AigcTimelineTrackInput {
  trackType: string
  name?: string
  orderNo: number
  muted?: boolean
  locked?: boolean
  clips: AigcTimelineClipInput[]
}

export interface AigcTimelineReplaceInput {
  timelineId: number
  projectId: number
  expectedProjectVersion: number
  expectedVersion: number
  tracks: AigcTimelineTrackInput[]
}

export interface AigcTimelineDeleteInput {
  timelineId: number
  projectId: number
  expectedProjectVersion: number
  expectedVersion: number
}

export const aigcTimelineApi = {
  timelines: (params: AigcTimelineParams = {}) =>
    backendApi.get<PageResult<AigcTimelineComposition>>("/aigc/timelines", {
      params: { pageNo: 1, pageSize: 50, ...params }
    }),
  composition: (id: number) =>
    backendApi.get<AigcTimelineComposition>(`/aigc/timelines/${id}/composition`),
  create: (data: AigcTimelineCreateInput) =>
    backendApi.post<{
      id: number
      projectId: number
      deliverableObjectId?: number
      status: string
      version: number
    }>("/aigc/timelines/_create", data),
  replace: ({ timelineId, ...data }: AigcTimelineReplaceInput) =>
    backendApi.put<AigcTimelineComposition>(`/aigc/timelines/${timelineId}/composition`, data),
  delete: ({ timelineId, ...data }: AigcTimelineDeleteInput) =>
    backendApi.post<void>(`/aigc/timelines/${timelineId}/_delete`, data)
}

export const aigcTimelineKeys = {
  all: ["aigc.timeline"] as const,
  list: (params: AigcTimelineParams) => ["aigc.timeline", "list", params] as const,
  composition: (id: number) => ["aigc.timeline", "composition", id] as const
}

export function useAigcTimelines(params: AigcTimelineParams = {}, enabled = true) {
  return useQuery({
    queryKey: aigcTimelineKeys.list(params),
    queryFn: () => aigcTimelineApi.timelines(params),
    enabled
  })
}

export function useAigcTimelineComposition(id: number | null) {
  return useQuery({
    queryKey: aigcTimelineKeys.composition(id ?? 0),
    queryFn: () => aigcTimelineApi.composition(id as number),
    enabled: id !== null
  })
}

export function invalidateAigcTimelineMutation(
  queryClient: ReturnType<typeof useQueryClient>,
  projectId: number,
  error?: unknown
) {
  queryClient.invalidateQueries({ queryKey: aigcTimelineKeys.all })
  if (error === undefined || (error instanceof ApiError && error.code === 409)) {
    invalidateAigcProject(queryClient, projectId)
  }
}

export function useCreateAigcTimeline() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcTimelineApi.create,
    onSuccess: (_, variables) => invalidateAigcTimelineMutation(queryClient, variables.projectId),
    onError: (error, variables) =>
      invalidateAigcTimelineMutation(queryClient, variables.projectId, error)
  })
}

export function useReplaceAigcTimeline() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcTimelineApi.replace,
    onSuccess: (_, variables) => invalidateAigcTimelineMutation(queryClient, variables.projectId),
    onError: (error, variables) =>
      invalidateAigcTimelineMutation(queryClient, variables.projectId, error)
  })
}

export function useDeleteAigcTimeline() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcTimelineApi.delete,
    onSuccess: (_, variables) => invalidateAigcTimelineMutation(queryClient, variables.projectId),
    onError: (error, variables) =>
      invalidateAigcTimelineMutation(queryClient, variables.projectId, error)
  })
}
