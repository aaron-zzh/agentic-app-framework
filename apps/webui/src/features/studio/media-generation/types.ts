/**
 * 媒体生成共享契约。
 *
 * 页面工作台与画布节点通过同一草稿、提交事件和模式类型协作，controller
 * 不依赖路由、Slot 或结果列表。
 *
 * @author AaronZZH & Kiro
 */

import type { ReactNode } from "react"
import type { VideoImageMode } from "@/lib/api/rest/ai"
import type { AigcTaskEvent } from "@/lib/hooks/use-aigc-task-stream"

export type MediaGenerationMode = "image" | "video" | "voice" | "music" | "model-3d"

export type MediaTaskType = Extract<
  AigcTaskEvent["type"],
  "IMAGE" | "VIDEO" | "VOICE" | "MUSIC" | "MODEL_3D"
>

export type MediaResultType = "IMAGE" | "VIDEO" | "AUDIO" | "MUSIC" | "MODEL_3D"

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
  videoImageMode?: VideoImageMode
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
