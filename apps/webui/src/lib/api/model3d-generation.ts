/**
 * 3D 模型生成 API 客户端
 * @author AaronZZH & Kiro
 */

import { request } from "./client"
import type { Model3dTaskResult } from "@/features/aigc/types"

const API_PATH = "/aigc/model3d"

export interface TextTo3dParams {
  prompt: string
  style?: string
  format?: string
}

export interface ImageTo3dParams {
  imageUrl: string
  format?: string
}

export const model3dApi = {
  /** 文本生成 3D 模型 */
  submitTextTo3d: (params: TextTo3dParams): Promise<string> =>
    request<string>(`${API_PATH}/text-to-3d`, {
      method: "POST",
      body: JSON.stringify(params),
    }),

  /** 图片生成 3D 模型 */
  submitImageTo3d: (params: ImageTo3dParams): Promise<string> =>
    request<string>(`${API_PATH}/image-to-3d`, {
      method: "POST",
      body: JSON.stringify(params),
    }),

  /** 查询 3D 生成任务状态 */
  queryTask: (taskId: string): Promise<Model3dTaskResult> =>
    request<Model3dTaskResult>(`${API_PATH}/task/${taskId}`),
}
