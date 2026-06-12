/**
 * 图像生成 mutation——使用统一 AIGC 任务接口
 * @author AaronZZH & Kiro
 */

import { useMutation, useQueryClient } from "@tanstack/react-query"
import { request } from "@/lib/api/rest/entity/crud"

export interface GenerateImageParams {
  prompt: string
  /** 用于展示/命名的用户原始输入（不含项目提示词前缀） */
  displayPrompt?: string
  model?: string
  width?: number
  height?: number
  imageUrls?: string[]
  negativePrompt?: string
  seed?: number
  promptExtend?: boolean
  imageCount?: number
  quality?: string
  format?: string
  sizePreset?: string
  aspectRatio?: string
  background?: string
  contentModeration?: string
  projectId?: number | null
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
          displayPrompt: params.displayPrompt,
          model: params.model,
          projectId: params.projectId ?? null,
          params: {
            width: params.width ?? 1024,
            height: params.height ?? 1024,
            ...(params.imageUrls?.length ? { imageUrls: params.imageUrls } : {}),
            ...(params.negativePrompt ? { negativePrompt: params.negativePrompt } : {}),
            ...(params.seed ? { seed: params.seed } : {}),
            ...(params.promptExtend !== undefined ? { promptExtend: params.promptExtend } : {}),
            ...(params.imageCount && params.imageCount > 1
              ? { imageCount: params.imageCount }
              : {}),
            ...(params.quality ? { quality: params.quality } : {}),
            ...(params.format ? { format: params.format } : {}),
            ...(params.sizePreset ? { sizePreset: params.sizePreset } : {}),
            ...(params.aspectRatio ? { aspectRatio: params.aspectRatio } : {}),
            ...(params.background ? { background: params.background } : {}),
            ...(params.contentModeration ? { contentModeration: params.contentModeration } : {})
          }
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
