/**
 * AIGC 模块类型定义
 * @author AaronZZH & Kiro
 */

/** 统一媒体类型，对齐后端 AigcMediaType。 */
export type AigcMediaType = "IMAGE" | "VIDEO" | "AUDIO" | "MUSIC" | "MODEL_3D"

/** 媒体来源类型由后端枚举返回，前端只透传筛选值。 */
export type AigcMediaSourceType = string

/** 媒体当前版本。 */
export interface AigcMediaVersion {
  id: number
  versionNo: number
  fileId: number
  url: string
  thumbnailFileId: number | null
  thumbnailUrl: string | null
  mimeType: string | null
  size: number | null
  width: number | null
  height: number | null
  duration: number | null
  frameRate: number | null
  generationInfo: string | null
  checksum: string | null
  createTime: string
}

/** 生成或上传后持久化的媒体对象。 */
export interface AigcMedia {
  id: number
  name: string
  mediaType: AigcMediaType
  sourceType: AigcMediaSourceType
  sourceExecutionRunId: number | null
  sourceTaskId: number | null
  originalProjectId: number | null
  assetId: number | null
  currentVersion: AigcMediaVersion
  createTime: string
  updateTime: string
}

/** 用户从媒体库标记保存的资产对象。 */
export interface AigcAsset {
  id: number
  mediaId: number
  categoryId: number | null
  scope: string
  copyrightInfo: string | null
  status: string
  usageCount: number
  media: AigcMedia
  createTime: string
}

/**
 * AIGC 任务类型，对应后端 AigcTaskTypeEnum / 字典 aigc_task_type。
 * 与 AigcTaskController#submit 分支一致。
 */
export type AigcTaskType = "IMAGE" | "VIDEO" | "MODEL_3D" | "MUSIC" | "VOICE" | "IMAGE_PROCESS"

/** AigcTaskType 全部取值（用于遍历/校验，避免散落硬编码） */
export const AIGC_TASK_TYPES: readonly AigcTaskType[] = [
  "IMAGE",
  "VIDEO",
  "MODEL_3D",
  "MUSIC",
  "VOICE",
  "IMAGE_PROCESS"
]

/** AIGC 任务状态，对应后端 AigcTaskStatusEnum / 字典 aigc_task_status。 */
export type AigcTaskStatus = "PENDING" | "RUNNING" | "SUCCESS" | "FAIL"

/** AigcTaskStatus 全部取值（用于遍历/校验，避免散落硬编码） */
export const AIGC_TASK_STATUSES: readonly AigcTaskStatus[] = [
  "PENDING",
  "RUNNING",
  "SUCCESS",
  "FAIL"
]

/** 3D 模型生成任务状态 */
export type Model3dTaskStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED"

/** 3D 模型生成任务结果 */
export interface Model3dTaskResult {
  taskId: string
  status: Model3dTaskStatus
  progress: number
  resultUrl: string | null
  thumbnailUrl: string | null
  errorMessage: string | null
}

/** 故事板元素 */
export interface StoryElement {
  id: string
  name: string
  description: string
  thumbnail: string
  tags: string[]
}

/** 生成参数 */
export interface GenerationParams {
  prompt: string
  model: string
  resolution: string
  aspectRatio: string
  referenceAssets: AigcMedia[]
}
