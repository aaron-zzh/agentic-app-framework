/**
 * 图像生成 mutation——使用统一 AIGC 任务接口
 * @author AaronZZH & Kiro
 */

import { useMutation, useQueryClient } from "@tanstack/react-query"
import { request } from "@/lib/api/rest/entity/crud"

export interface GenerateImageParams {
  prompt: string
  model?: string
  width?: number
  height?: number
}

/** 提交图像生成任务，返回统一任务 ID */
export function useGenerateImage() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (params: GenerateImageParams) =>
      request<number>("/aigc/tasks/submit", {
        method: "POST",
        body: JSON.stringify({
          type: "IMAGE",
          prompt: params.prompt,
          model: params.model,
          params: { width: params.width ?? 1024, height: params.height ?? 1024 }
        }),
        headers: { "Content-Type": "application/json" }
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["aigc", "tasks"] })
    }
  })
}

/** 提交视频生成任务 */
export function useGenerateVideo() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (params: { prompt: string; model?: string }) =>
      request<number>("/aigc/tasks/submit", {
        method: "POST",
        body: JSON.stringify({ type: "VIDEO", prompt: params.prompt, model: params.model }),
        headers: { "Content-Type": "application/json" }
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["aigc", "tasks"] })
    }
  })
}

/** 提交 3D 生成任务 */
export function useGenerate3d() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (params: { prompt: string; model?: string }) =>
      request<number>("/aigc/tasks/submit", {
        method: "POST",
        body: JSON.stringify({ type: "MODEL_3D", prompt: params.prompt, model: params.model }),
        headers: { "Content-Type": "application/json" }
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["aigc", "tasks"] })
    }
  })
}

interface SaveFromGenerationParams {
  url: string
  name?: string
  type?: "IMAGE" | "VIDEO" | "AUDIO" | "MODEL_3D"
  thumbnailUrl?: string
  generationParams?: string
  width?: number
  height?: number
}

/** 一键保存到素材库 */
export function useSaveToAssetLibrary() {
  return useMutation({
    mutationFn: (params: SaveFromGenerationParams) =>
      request("/aigc/assets/save-from-generation", {
        method: "POST",
        body: JSON.stringify(params),
        headers: { "Content-Type": "application/json" }
      })
  })
}
