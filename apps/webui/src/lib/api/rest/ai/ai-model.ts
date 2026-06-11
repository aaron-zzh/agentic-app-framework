import { request } from "@/lib/api/rest/entity/crud"

/** 从宽高反算最简比例字符串，如 1024×1024 → "1:1"，1280×720 → "16:9" */
export function calcRatio(w: number, h: number): string {
  const gcd = (a: number, b: number): number => (b === 0 ? a : gcd(b, a % b))
  const g = gcd(w, h)
  return `${w / g}:${h / g}`
}

/**
 * 单种模式（文生图 或 图像编辑）的参数配置。
 * - 字段存在 = 支持该参数，显示对应控件
 * - 字段不存在/undefined = 不支持，不显示
 * - 数组值 = 可选项列表（直接渲染成下拉选项）
 * - 数字值 = 上限
 * - true = 支持（开关类参数）
 */
export interface ImageModeConfig {
  maxImages?: number
  maxInputImages?: number
  quality?: string[]
  format?: string[]
  /** 尺寸档位预设，如 ["1K","2K","4K"]（适用于用档位而非像素选择尺寸的模型） */
  sizePresets?: string[]
  /** 背景模式可选项，如 ["auto","transparent","opaque"]（图像编辑接口支持） */
  background?: string[]
  /** 内容审核级别可选项，如 ["auto","low"] */
  contentModeration?: string[]
  seed?: boolean
  promptExtend?: boolean
  negativePrompt?: boolean
}

export interface ImageConfig {
  mode: "ratio" | "fixed"
  /**
   * ratio 模式：key 为比例字符串（如 "1:1"），value 为 [[w,h],...] 可选尺寸列表
   * fixed 模式：[[w,h],...] 直接选像素
   */
  sizes?: Record<string, [number, number][]> | [number, number][]
  /** 文生图参数配置（存在则支持文生图） */
  generate?: ImageModeConfig
  /** 图像编辑参数配置（存在则支持图像编辑） */
  edit?: ImageModeConfig
}

export interface AiModelVO {
  id: number
  modelId: string
  displayName: string
  provider: string
  modelName: string
  capabilities: string
  enabled: boolean
  imageConfig?: ImageConfig
}

/** 获取已启用的图像生成模型列表 */
export async function listImageModels(): Promise<AiModelVO[]> {
  return request<AiModelVO[]>("/ai/models/enabled?capability=IMAGE_GEN")
}

/** 获取已启用的视频生成模型列表 */
export async function listVideoModels(): Promise<AiModelVO[]> {
  return request<AiModelVO[]>("/ai/models/enabled?capability=VIDEO_GEN")
}
