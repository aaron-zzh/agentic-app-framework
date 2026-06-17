/**
 * useGenerationParams——模型选择 + 参数状态一体化 hook
 *
 * 传入 capability，内部管理模型列表、当前选中模型、以及根据模型配置推导的参数状态。
 * 切换模型时自动 reset 参数为新模型的默认值。
 *
 * @example
 * const { modelId, setModelId, currentModel, params, onChangeParams } = useGenerationParams("IMAGE_GEN")
 * <ModelSelector capability="IMAGE_GEN" value={modelId} onChange={setModelId} />
 * <ModelParamsBar model={currentModel} params={params} onChangeParams={onChangeParams} />
 * generateImage.mutate({ model: modelId, ...params })
 */
import { useEffect, useState } from "react"
import type { AiModelVO, ImageConfig, ImageModeConfig } from "@/lib/api/rest/ai/ai-model"

export interface GenerationParams {
  aspectRatio?: string
  fixedSize?: string
  sizePreset?: string
  resolution?: string
  imageCount?: number
  quality?: string
  format?: string
  background?: string
  contentModeration?: string
  seed?: number
  promptExtend?: boolean
  videoDuration?: string
}

function defaultsFromModel(model: AiModelVO | undefined): GenerationParams {
  if (!model) return {}

  const isVideo = model.capabilities?.includes("VIDEO_GEN")
  if (isVideo) return { aspectRatio: "9:16", videoDuration: "5s" }

  const cfg: ImageConfig | undefined = model.imageConfig
  if (!cfg) return { aspectRatio: "1:1", resolution: "2K" }

  const modeConfig: ImageModeConfig | undefined = cfg.generate
  const p: GenerationParams = {}

  if (modeConfig?.sizePresets?.length) {
    p.sizePreset = modeConfig.sizePresets[0]
  }
  if (cfg.mode === "ratio") {
    const ratios = Object.keys((cfg.sizes ?? {}) as Record<string, unknown>)
    p.aspectRatio = ratios[0] ?? "1:1"
    const first = (cfg.sizes as Record<string, [number, number][]>)?.[p.aspectRatio]?.[0]
    if (first) p.fixedSize = `${first[0]}x${first[1]}`
  } else {
    const fixed = cfg.sizes as [number, number][] | undefined
    if (fixed?.length) {
      const mid = fixed[Math.floor(fixed.length / 2)]
      p.fixedSize = `${mid[0]}x${mid[1]}`
    }
  }

  if (modeConfig?.quality?.length) p.quality = modeConfig.quality[0]
  if (modeConfig?.format?.length) p.format = modeConfig.format[0]
  if (modeConfig?.background?.length) p.background = modeConfig.background[0]
  if (modeConfig?.contentModeration?.length) p.contentModeration = modeConfig.contentModeration[0]
  if (modeConfig?.promptExtend) p.promptExtend = false
  if (modeConfig?.seed) p.seed = 0
  if (modeConfig?.maxImages && modeConfig.maxImages > 1) p.imageCount = 1

  return p
}

/** 降级换算：分辨率档位 + 比例 → 像素 */
function resolveSize(resolution: string, aspectRatio: string): { width: number; height: number } {
  const base =
    resolution === "1K" ? 1024 : resolution === "2K" ? 2048 : resolution === "4K" ? 4096 : 1024
  const map: Record<string, [number, number]> = {
    "1:1": [1, 1],
    "16:9": [16, 9],
    "9:16": [9, 16],
    "4:3": [4, 3],
    "3:4": [3, 4]
  }
  const [rw, rh] = map[aspectRatio] ?? [1, 1]
  if (rw >= rh) return { width: base, height: Math.round((base * rh) / rw / 64) * 64 }
  return { width: Math.round((base * rw) / rh / 64) * 64, height: base }
}

export function useGenerationParams(model: AiModelVO | undefined) {
  const [params, setParams] = useState<GenerationParams>(() => defaultsFromModel(model))

  // 切换模型时 reset 参数
  useEffect(() => {
    setParams(defaultsFromModel(model))
  }, [model])

  function onChangeParams(patch: Partial<GenerationParams>) {
    setParams((prev) => ({ ...prev, ...patch }))
  }

  /** 解析后的最终宽高 + sizePreset，直接传给后端 */
  const resolvedSize: { width: number; height: number; sizePreset?: string } = (() => {
    if (params.sizePreset) return { width: 1024, height: 1024, sizePreset: params.sizePreset }
    if (params.fixedSize) {
      const [w, h] = params.fixedSize.split("x").map(Number)
      return { width: w || 1024, height: h || 1024 }
    }
    return resolveSize(params.resolution ?? "2K", params.aspectRatio ?? "1:1")
  })()

  return {
    params,
    onChangeParams,
    resolvedSize
  }
}
