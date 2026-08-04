/**
 * AIGC 轻时间线 API；Track/Clip 只作为 Composition 子记录读取。
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PageResult } from "../../../types"
import { backendApi } from "../../backend-client"

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
  deliverableObjectId?: number
  title: string
  durationMs?: number
  frameRate?: number
  width?: number
  height?: number
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
    }>("/aigc/timelines/_create", data)
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

export function useCreateAigcTimeline() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcTimelineApi.create,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: aigcTimelineKeys.all })
  })
}
