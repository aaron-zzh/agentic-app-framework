/**
 * 媒体生成共享契约。
 *
 * 页面工作台与画布节点通过同一草稿、提交事件和模式类型协作，controller
 * 不依赖路由、Slot 或结果列表。
 *
 * @author AaronZZH & Kiro
 */

import type { ReactNode } from "react"
import type { AigcTaskEvent } from "@/lib/hooks/use-aigc-task-stream"

export const MEDIA_GENERATION_MODES = ["image", "video", "voice", "music", "model-3d"] as const

export type MediaGenerationMode = (typeof MEDIA_GENERATION_MODES)[number]

export const VIDEO_INPUT_MODES = ["T2V", "REFERENCE", "FIRST_LAST_FRAME"] as const

/** 视频 Composer 的交互模式，与后端供应商路由模式分离。 */
export type VideoInputMode = (typeof VIDEO_INPUT_MODES)[number]

/** 判断 URL 参数是否为受支持的媒体生成模式。 */
export function isMediaGenerationMode(value: string | undefined): value is MediaGenerationMode {
  return MEDIA_GENERATION_MODES.includes(value as MediaGenerationMode)
}

/** 构建统一媒体生成页面 URL。 */
export function getMediaGenerationPath(
  mode: MediaGenerationMode,
  params: Record<string, string | undefined> = {}
): string {
  const searchParams = new URLSearchParams({ mode })
  for (const [key, value] of Object.entries(params)) {
    if (value) searchParams.set(key, value)
  }
  return `/studio/create?${searchParams.toString()}`
}

export type MediaTaskType = Extract<
  AigcTaskEvent["type"],
  "IMAGE" | "VIDEO" | "VOICE" | "MUSIC" | "MODEL_3D"
>

export type MediaResultType = "IMAGE" | "VIDEO" | "AUDIO" | "MUSIC" | "MODEL_3D"

export interface MediaCreditEstimate {
  credits: number | null
  sufficient: boolean
  isLoading: boolean
}

export interface MediaImageAttachment {
  url: string
  previewSrc: string
  name: string
}

export interface PendingMediaImageAttachment {
  previewSrc: string
  name: string
}

export interface MediaGenerationDraft {
  revision: number
  prompt: string
  model?: string
  referenceImageUrl?: string
  lastFrameImageUrl?: string
  videoImageMode?: VideoInputMode
}

export interface MediaTaskSubmission {
  mode: MediaGenerationMode
  taskId: number
}

export interface MediaGenerationControllerOptions {
  projectId?: number | null
  initialDraft?: MediaGenerationDraft
  onTaskSubmitted?: (submission: MediaTaskSubmission) => void
}

export interface MediaGenerationComposerProps extends MediaGenerationControllerOptions {
  leadingTools?: ReactNode
  appearance?: "workspace" | "embedded"
  className?: string
}

export const TASK_TYPE_BY_MODE: Record<MediaGenerationMode, MediaTaskType> = {
  image: "IMAGE",
  video: "VIDEO",
  voice: "VOICE",
  music: "MUSIC",
  "model-3d": "MODEL_3D"
}

export const MODE_BY_TASK_TYPE: Record<MediaTaskType, MediaGenerationMode> = {
  IMAGE: "image",
  VIDEO: "video",
  VOICE: "voice",
  MUSIC: "music",
  MODEL_3D: "model-3d"
}

export const RESULT_TYPE_BY_MODE: Record<MediaGenerationMode, MediaResultType> = {
  image: "IMAGE",
  video: "VIDEO",
  voice: "AUDIO",
  music: "MUSIC",
  "model-3d": "MODEL_3D"
}

export function isMediaTaskType(type: AigcTaskEvent["type"]): type is MediaTaskType {
  return Object.values(TASK_TYPE_BY_MODE).includes(type as MediaTaskType)
}
