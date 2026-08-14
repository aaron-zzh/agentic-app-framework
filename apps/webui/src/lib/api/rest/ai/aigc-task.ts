/**
 * AIGC 任务 API、DTO 与 TanStack Query mutations
 * @author AaronZZH & Kiro
 */

import { useMutation, useQueryClient } from "@tanstack/react-query"

import { request } from "../entity/crud"

export interface GenerateImageParams {
  prompt: string
  displayPrompt?: string
  model?: string
  width?: number
  height?: number
  imageFileIds?: number[]
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
  systemPrompt?: string
}

export type VideoImageMode = "T2V" | "FIRST_FRAME" | "REFERENCE"

export interface GenerateVideoParams {
  prompt: string
  model?: string
  projectId?: number | null
  resolution?: string
  duration?: number
  ratio?: string
  seed?: number
  imageMode?: VideoImageMode
  imageFileId?: number
  referenceImageFileIds?: number[]
  referenceVideoUrls?: string[]
  referenceAudioUrls?: string[]
  audioSetting?: string
  promptExtend?: boolean
  generateAudio?: boolean
  systemPrompt?: string
}

export const aigcTaskApi = {
  cancel: (taskId: number): Promise<void> => request(`/aigc/tasks/${taskId}`, { method: "DELETE" }),
  generateImage: (params: GenerateImageParams): Promise<number> =>
    request("/aigc/tasks/submit", {
      method: "POST",
      body: JSON.stringify({
        type: "IMAGE",
        prompt: params.prompt,
        displayPrompt: params.displayPrompt,
        model: params.model,
        projectId: params.projectId ?? null,
        systemPrompt: params.systemPrompt ?? null,
        params: {
          width: params.width ?? 1024,
          height: params.height ?? 1024,
          ...(params.imageFileIds?.length ? { imageFileIds: params.imageFileIds } : {}),
          ...(params.negativePrompt ? { negativePrompt: params.negativePrompt } : {}),
          ...(params.seed ? { seed: params.seed } : {}),
          ...(params.promptExtend !== undefined ? { promptExtend: params.promptExtend } : {}),
          ...(params.imageCount && params.imageCount > 1 ? { imageCount: params.imageCount } : {}),
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
  generateVideo: (params: GenerateVideoParams): Promise<number> =>
    request("/aigc/tasks/submit", {
      method: "POST",
      body: JSON.stringify({
        type: "VIDEO",
        prompt: params.prompt,
        model: params.model,
        projectId: params.projectId ?? null,
        systemPrompt: params.systemPrompt ?? null,
        params: {
          ...(params.resolution ? { resolution: params.resolution } : {}),
          ...(params.duration ? { duration: params.duration } : {}),
          ...(params.ratio ? { ratio: params.ratio } : {}),
          ...(params.seed ? { seed: params.seed } : {}),
          ...(params.imageMode ? { imageMode: params.imageMode } : {}),
          ...(params.imageFileId ? { imageFileId: params.imageFileId } : {}),
          ...(params.referenceImageFileIds?.length
            ? { referenceImageFileIds: params.referenceImageFileIds }
            : {}),
          ...(params.referenceVideoUrls?.length
            ? { referenceVideoUrls: params.referenceVideoUrls }
            : {}),
          ...(params.referenceAudioUrls?.length
            ? { referenceAudioUrls: params.referenceAudioUrls }
            : {}),
          ...(params.audioSetting ? { audioSetting: params.audioSetting } : {}),
          ...(params.promptExtend !== undefined ? { promptExtend: params.promptExtend } : {}),
          ...(params.generateAudio !== undefined ? { generateAudio: params.generateAudio } : {})
        }
      }),
      headers: { "Content-Type": "application/json" }
    }),
  generate3d: (params: { prompt: string; model?: string }): Promise<number> =>
    request("/aigc/tasks/submit", {
      method: "POST",
      body: JSON.stringify({ type: "MODEL_3D", prompt: params.prompt, model: params.model }),
      headers: { "Content-Type": "application/json" }
    })
}

export function useCancelAigcTask() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcTaskApi.cancel,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["aigc.task"] })
      queryClient.invalidateQueries({ queryKey: ["aigc.task", "history"] })
    }
  })
}

export function useGenerateImage() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcTaskApi.generateImage,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["aigc.task"] })
  })
}

export function useGenerateVideo() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcTaskApi.generateVideo,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["aigc.task"] })
  })
}

export function useGenerate3d() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcTaskApi.generate3d,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["aigc.task"] })
  })
}
