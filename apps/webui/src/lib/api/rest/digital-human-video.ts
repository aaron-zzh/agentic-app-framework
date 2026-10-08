import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { backendApi } from "./backend-client"

/** 视频历史分页响应（视频历史接口仍返回 Spring Data Page 结构） */
export interface DigitalHumanVideoPage {
  content: DigitalHumanVideo[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface DigitalHumanVideo {
  id: string
  digitalHumanId: string
  digitalHumanName: string
  projectId: string
  projectName: string
  title: string
  videoUrl: string
  thumbnailUrl?: string
  duration: number
  aspectRatio: string
  provider: string
  sceneCount?: number
  creditsConsumed: number
  status: string
  createdAt: string
  completedAt?: string
}

interface VideoListParams {
  projectName?: string
  page?: number
  size?: number
}

const KEYS = {
  all: ["digital-human-videos"] as const,
  lists: () => [...KEYS.all, "list"] as const,
  list: (digitalHumanId: string, params: VideoListParams = {}) =>
    [...KEYS.lists(), digitalHumanId, params] as const,
  detail: (digitalHumanId: string, videoId: string) =>
    [...KEYS.all, "detail", digitalHumanId, videoId] as const
}

const digitalHumanVideoApi = {
  list: (digitalHumanId: string, params: VideoListParams = {}) =>
    backendApi.get<DigitalHumanVideoPage>(`/api/digital-humans/${digitalHumanId}/videos`, {
      params
    }),

  getById: (digitalHumanId: string, videoId: string) =>
    backendApi.get<DigitalHumanVideo>(`/api/digital-humans/${digitalHumanId}/videos/${videoId}`),

  delete: (digitalHumanId: string, videoId: string) =>
    backendApi.delete<void>(`/api/digital-humans/${digitalHumanId}/videos/${videoId}`, {
      showSuccess: "删除成功"
    })
}

// React Query hooks

export function useDigitalHumanVideos(digitalHumanId: string, params: VideoListParams = {}) {
  return useQuery({
    queryKey: KEYS.list(digitalHumanId, params),
    queryFn: () => digitalHumanVideoApi.list(digitalHumanId, params),
    enabled: !!digitalHumanId,
    staleTime: 30_000
  })
}

export function useDigitalHumanVideo(digitalHumanId: string, videoId: string) {
  return useQuery({
    queryKey: KEYS.detail(digitalHumanId, videoId),
    queryFn: () => digitalHumanVideoApi.getById(digitalHumanId, videoId),
    enabled: !!digitalHumanId && !!videoId,
    staleTime: 60_000
  })
}

export function useDeleteDigitalHumanVideo() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ digitalHumanId, videoId }: { digitalHumanId: string; videoId: string }) =>
      digitalHumanVideoApi.delete(digitalHumanId, videoId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: KEYS.lists() })
    }
  })
}
