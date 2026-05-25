import { useMutation } from "@tanstack/react-query"
import { request } from "@/lib/api/client"

interface GenerateImageParams {
  prompt: string
  model?: string
  width?: number
  height?: number
}

interface GenerateImageResult {
  url: string
  modelId: string
}

/** 图像生成 mutation */
export function useGenerateImage() {
  return useMutation({
    mutationFn: (params: GenerateImageParams) =>
      request<GenerateImageResult>("/api/aigc/generate-image", { method: "POST", body: JSON.stringify(params), headers: { "Content-Type": "application/json" } }),
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
      request("/api/aigc/assets/save-from-generation?userId=1", { method: "POST", body: JSON.stringify(params), headers: { "Content-Type": "application/json" } }),
  })
}
