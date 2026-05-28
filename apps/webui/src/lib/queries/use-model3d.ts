/**
 * 3D 模型生成 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery } from "@tanstack/react-query"
import { model3dApi, type TextTo3dParams, type ImageTo3dParams } from "@/lib/api/model3d-generation"

const KEYS = {
  task: (taskId: string) => ["model3d", "task", taskId] as const,
}

/** 文本生成 3D 模型 */
export function useTextTo3d() {
  return useMutation({
    mutationFn: (params: TextTo3dParams) => model3dApi.submitTextTo3d(params),
  })
}

/** 图片生成 3D 模型 */
export function useImageTo3d() {
  return useMutation({
    mutationFn: (params: ImageTo3dParams) => model3dApi.submitImageTo3d(params),
  })
}

/** 轮询 3D 生成任务状态（直到完成或失败） */
export function useModel3dTaskStatus(taskId: string | null) {
  return useQuery({
    queryKey: KEYS.task(taskId!),
    queryFn: () => model3dApi.queryTask(taskId!),
    enabled: taskId !== null,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      if (status === "COMPLETED" || status === "FAILED") return false
      return 2000
    },
  })
}
