import { request } from "./client"

/** 视频任务状态 */
export type VideoTaskStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELED" | "UNKNOWN"

export interface VideoTaskResult {
  taskId: string
  status: VideoTaskStatus
  videoUrl: string | null
  origPrompt: string | null
  submitTime: string | null
  endTime: string | null
  duration: number | null
}

export interface TextToVideoParams {
  prompt: string
  model?: string
  resolution?: string
  ratio?: string
  duration?: number
  seed?: number
}

export interface ImageToVideoParams {
  prompt?: string
  firstFrameUrl: string
  model?: string
  resolution?: string
  duration?: number
  seed?: number
}

export interface VideoEditParams {
  prompt: string
  videoUrl: string
  referenceImageUrls?: string[]
  model?: string
  resolution?: string
  audioSetting?: string
  seed?: number
}

/** 文生视频 */
export function submitTextToVideo(params: TextToVideoParams) {
  return request<string>("/api/aigc/video/text-to-video", { method: "POST", body: JSON.stringify(params), headers: { "Content-Type": "application/json" } })
}

/** 图生视频 */
export function submitImageToVideo(params: ImageToVideoParams) {
  return request<string>("/api/aigc/video/image-to-video", { method: "POST", body: JSON.stringify(params), headers: { "Content-Type": "application/json" } })
}

/** 视频编辑 */
export function submitVideoEdit(params: VideoEditParams) {
  return request<string>("/api/aigc/video/edit", { method: "POST", body: JSON.stringify(params), headers: { "Content-Type": "application/json" } })
}

/** 查询视频任务状态 */
export function queryVideoTask(taskId: string) {
  return request<VideoTaskResult>(`/api/aigc/video/task/${taskId}`)
}
