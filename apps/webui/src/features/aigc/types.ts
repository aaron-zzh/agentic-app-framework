/**
 * AIGC 模块类型定义
 * @author AaronZZH & Kiro
 */

/** 素材类型枚举 */
export type MediaAssetType = "IMAGE" | "VIDEO" | "AUDIO" | "MODEL_3D"

/** 素材资源 VO（对齐后端 MediaAssetVO） */
export interface MediaAssetVO {
  id: number
  name: string
  type: MediaAssetType
  url: string
  thumbnailUrl: string | null
  size: number | null
  width: number | null
  height: number | null
  duration: number | null
  generationParams: string | null
  tags: string | null
  categoryId: number | null
  groupId: number | null
  groupName: string | null
  aiGenerated: boolean
  modelName: string | null
  providerCode: string | null
  userId: number
  version: number
  createTime: string
  updateTime: string
}

/** 素材分类 VO */
export interface MediaCategoryVO {
  id: number
  name: string
  parentId: number | null
  sortOrder: number
  children: MediaCategoryVO[]
}

/** 素材标签 VO */
export interface MediaTagVO {
  id: number
  name: string
  color: string | null
}

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
  referenceAssets: MediaAssetVO[]
}
