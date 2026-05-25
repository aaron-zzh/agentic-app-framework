import { useMutation, useQuery } from "@tanstack/react-query"
import {
  queryVideoTask,
  submitImageToVideo,
  submitTextToVideo,
  submitVideoEdit,
  type ImageToVideoParams,
  type TextToVideoParams,
  type VideoEditParams,
} from "@/lib/api/video-generation"

/** 文生视频 mutation */
export function useTextToVideo() {
  return useMutation({
    mutationFn: (params: TextToVideoParams) => submitTextToVideo(params),
  })
}

/** 图生视频 mutation */
export function useImageToVideo() {
  return useMutation({
    mutationFn: (params: ImageToVideoParams) => submitImageToVideo(params),
  })
}

/** 视频编辑 mutation */
export function useVideoEdit() {
  return useMutation({
    mutationFn: (params: VideoEditParams) => submitVideoEdit(params),
  })
}

/** 轮询视频任务状态（15秒间隔，直到完成或失败） */
export function useVideoTaskStatus(taskId: string | null) {
  return useQuery({
    queryKey: ["video-task", taskId],
    queryFn: () => queryVideoTask(taskId!),
    enabled: !!taskId,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      if (status === "SUCCEEDED" || status === "FAILED" || status === "CANCELED") {
        return false
      }
      return 15_000
    },
  })
}
