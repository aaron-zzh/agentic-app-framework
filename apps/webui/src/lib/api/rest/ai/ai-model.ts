/**
 * AI 模型 API、能力类型与 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"

import { backendApi } from "../backend-client"

/** 从宽高反算最简比例字符串，如 1024×1024 → "1:1"，1280×720 → "16:9" */
export function calcRatio(w: number, h: number): string {
  const gcd = (a: number, b: number): number => (b === 0 ? a : gcd(b, a % b))
  const g = gcd(w, h)
  return `${w / g}:${h / g}`
}

export interface ImageModeConfig {
  maxImages?: number
  maxInputImages?: number
  quality?: string[]
  format?: string[]
  sizePresets?: string[]
  background?: string[]
  contentModeration?: string[]
  seed?: boolean
  promptExtend?: boolean
  negativePrompt?: boolean
}

export interface ImageConfig {
  mode: "ratio" | "fixed"
  sizes?: Record<string, [number, number][]> | (string | [number, number])[]
  generate?: ImageModeConfig
  edit?: ImageModeConfig
}

export interface VideoConfig {
  resolutions?: string[] | null
  ratios?: string[] | null
  durations?: number[] | null
  maxDuration?: number | null
  seed?: boolean | null
  watermark?: boolean | null
  audioSetting?: string[] | null
  generateAudio?: boolean | null
  promptExtend?: boolean | null
  maxReferenceImages?: number | null
  maxReferenceVideos?: number | null
  maxReferenceAudios?: number | null
  modes?: string[] | null
}

/** 与后端 AiModelVO 对齐；仅暴露 apiKeyConfigured，不包含 apiKey。 */
export interface AiModelVO {
  id: number
  modelId: string
  displayName: string
  provider: string
  providerType: string
  modelName: string
  baseUrl: string
  apiKeyConfigured: boolean
  capabilities: string
  temperature: number | null
  maxTokens: number | null
  contextWindow: number | null
  inputPricePerK: number | null
  outputPricePerK: number | null
  modelPrice: number | null
  quotaType: 0 | 1
  enabled: boolean
  fallbackModelId: string | null
  sortOrder: number
  remark: string | null
  imageConfig?: ImageConfig | null
  videoConfig?: VideoConfig | null
  createTime: string
  updateTime: string
}

export interface PublicModelPricingVO {
  modelId: string
  displayName: string
  provider: string
  capabilities: string
  quotaType: 0 | 1 | 2 | 3
  inputCreditPerK?: number
  outputCreditPerK?: number
  creditPerUse?: number
  creditPerSec?: number
  creditPerUnit?: number
  markupRate: number
}

export const aiModelApi = {
  listEnabled: (capability: string): Promise<AiModelVO[]> =>
    backendApi.get(`/ai/models/enabled?capability=${encodeURIComponent(capability)}`),
  listPublicPricing: (): Promise<PublicModelPricingVO[]> =>
    backendApi.get("/ai/models/public-pricing")
}

export function listImageModels(): Promise<AiModelVO[]> {
  return aiModelApi.listEnabled("IMAGE_GEN")
}

export function listVideoModels(): Promise<AiModelVO[]> {
  return aiModelApi.listEnabled("VIDEO_GEN")
}

export function listTextModels(): Promise<AiModelVO[]> {
  return aiModelApi.listEnabled("CHAT")
}

export function listPublicPricing(): Promise<PublicModelPricingVO[]> {
  return aiModelApi.listPublicPricing()
}

async function fetchModelsByCapability(capability: string): Promise<AiModelVO[]> {
  switch (capability) {
    case "CHAT":
      return listTextModels()
    case "IMAGE_GEN":
      return listImageModels()
    case "VIDEO_GEN":
      return listVideoModels()
    default:
      return aiModelApi.listEnabled(capability)
  }
}

export function useAiModels(capability: string) {
  return useQuery({
    queryKey: ["ai", "models", capability],
    queryFn: () => fetchModelsByCapability(capability),
    enabled: !!capability,
    staleTime: 5 * 60 * 1000
  })
}

/** 用户侧模型定价列表（积分/次，含加价倍率，10 分钟缓存） */
export function useModelPricing() {
  return useQuery<PublicModelPricingVO[]>({
    queryKey: ["ai", "models", "public-pricing"],
    queryFn: listPublicPricing,
    staleTime: 10 * 60 * 1000
  })
}
