/**
 * 3D 模型生成 API 客户端（百炼 Tripo + Meshy）
 * @author AaronZZH & Kiro
 */

import type { Model3dTaskResult } from "@/features/aigc/types"
import { request } from "../entity/crud"

const API_PATH = "/aigc/model3d"

export interface TextTo3dParams {
  prompt: string
  /** 贴图质量：standard / detailed */
  textureQuality?: string
  /** 是否生成 PBR 材质 */
  pbr?: boolean
}

export interface ImageTo3dParams {
  imageUrl: string
  textureQuality?: string
  pbr?: boolean
}

export interface ImageInputItem {
  type: string
  fileToken: string
}

export interface MultiImageTo3dParams {
  /** 四视角图片（前/左/后/右），不需要的视角传 null */
  images: (ImageInputItem | null)[]
  textureQuality?: string
  pbr?: boolean
}

export const model3dApi = {
  /** 文本生成 3D 模型 */
  submitTextTo3d: (params: TextTo3dParams): Promise<string> =>
    request<string>(`${API_PATH}/text-to-3d`, {
      method: "POST",
      body: JSON.stringify(params)
    }),

  /** 单图生成 3D 模型 */
  submitImageTo3d: (params: ImageTo3dParams): Promise<string> =>
    request<string>(`${API_PATH}/image-to-3d`, {
      method: "POST",
      body: JSON.stringify(params)
    }),

  /** 多图生成 3D 模型（四视角：前/左/后/右） */
  submitMultiImageTo3d: (params: MultiImageTo3dParams): Promise<string> =>
    request<string>(`${API_PATH}/multi-image-to-3d`, {
      method: "POST",
      body: JSON.stringify(params)
    }),

  /** 查询 3D 生成任务状态 */
  queryTask: (taskId: string): Promise<Model3dTaskResult> =>
    request<Model3dTaskResult>(`${API_PATH}/task/${taskId}`)
}
