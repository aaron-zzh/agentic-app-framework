import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "@/lib/api-client"
import type { PaginatedResponse } from "@/types/api"

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
  list: async (digitalHumanId: string, params: VideoListParams = {}) => {
    const response = await apiClient.get<PaginatedResponse<DigitalHumanVideo>>(
      `/api/digital-humans/${digitalHumanId}/videos`,
      { params }
    )
    return response.data
  },

  getById: async (digitalHumanId: string, videoId: string) => {
    const response = await apiClient.get<DigitalHumanVideo>(
      `/api/digital-humans/${digitalHumanId}/videos/${videoId}`
    )
    return response.data
  },

  delete: async (digitalHumanId: string, videoId: string) => {
    await apiClient.delete(`/api/digital-humans/${digitalHumanId}/videos/${videoId}`)
  }
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
