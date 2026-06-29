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
   * fixed 模式：("auto" | [w,h])[]，"auto" 表示自动尺寸
   */
  sizes?: Record<string, [number, number][]> | (string | [number, number])[]
  /** 文生图参数配置（存在则支持文生图） */
  generate?: ImageModeConfig
  /** 图像编辑参数配置（存在则支持图像编辑） */
  edit?: ImageModeConfig
}

/**
 * 视频生成能力配置——对应后端 VideoConfig，字段存在即支持该参数。
 * 与后端 ai_model.video_config JSONB 字段保持一致。
 */
export interface VideoConfig {
  /** 支持的分辨率列表，如 ["720p","1080p"]；null 表示不限 */
  resolutions?: string[] | null
  /** 支持的宽高比列表，如 ["16:9","9:16","1:1"] */
  ratios?: string[] | null
  /** 支持的时长档位（秒），如 [3,5,10,15] */
  durations?: number[] | null
  maxDuration?: number | null
  seed?: boolean | null
  /** true=强制水印无法关闭，false=可开关 */
  watermark?: boolean | null
  /** 音频控制选项，如 ["auto","origin"] */
  audioSetting?: string[] | null
  /** 是否支持生成配套音频（Seedance 专属） */
  generateAudio?: boolean | null
  promptExtend?: boolean | null
  maxReferenceImages?: number | null
  maxReferenceVideos?: number | null
  maxReferenceAudios?: number | null
  /** 支持的生成模式，如 ["t2v","i2v","r2v","video-edit"] */
  modes?: string[] | null
}

export interface AiModelVO {
  id: number
  modelId: string
  displayName: string
  provider: string
  modelName: string
  capabilities: string
  enabled: boolean
  contextWindow?: number
  imageConfig?: ImageConfig
  videoConfig?: VideoConfig
}

/** 获取已启用的图像生成模型列表 */
export async function listImageModels(): Promise<AiModelVO[]> {
  return request<AiModelVO[]>("/ai/models/enabled?capability=IMAGE_GEN")
}

/** 获取已启用的视频生成模型列表 */
export async function listVideoModels(): Promise<AiModelVO[]> {
  return request<AiModelVO[]>("/ai/models/enabled?capability=VIDEO_GEN")
}

/** 获取已启用的文本生成模型列表 */
export async function listTextModels(): Promise<AiModelVO[]> {
  return request<AiModelVO[]>("/ai/models/enabled?capability=CHAT")
}

/** 用户侧模型公开定价 VO（积分，已含加价倍率） */
export interface PublicModelPricingVO {
  modelId: string
  displayName: string
  provider: string
  capabilities: string
  /** 计费类型：0=按量 1=按次 2=按秒 3=按单元 */
  quotaType: 0 | 1 | 2 | 3
  /** 积分/千token 输入（quotaType=0） */
  inputCreditPerK?: number
  /** 积分/千token 输出（quotaType=0） */
  outputCreditPerK?: number
  /** 积分/次（quotaType=1） */
  creditPerUse?: number
  /** 积分/秒（quotaType=2） */
  creditPerSec?: number
  /** 积分/单元（quotaType=3） */
  creditPerUnit?: number
  markupRate: number
}

/** 获取用户侧模型定价列表（积分/次，含倍率） */
export async function listPublicPricing(): Promise<PublicModelPricingVO[]> {
  return request<PublicModelPricingVO[]>("/ai/models/public-pricing")
}
