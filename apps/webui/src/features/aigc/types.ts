/**
 * AIGC 模块类型定义
 * @author AaronZZH & Kiro
 */

/** 故事板元素 */
export interface StoryElement {
  id: string
  name: string
  description: string
  thumbnail: string
  tags: string[]
}

/** 素材文件 */
export interface MediaAsset {
  id: string
  name: string
  url: string
  thumbnail: string
  width: number
  height: number
  model?: string
  resolution?: string
}

/** 生成参数 */
export interface GenerationParams {
  prompt: string
  model: string
  resolution: string
  aspectRatio: string
  referenceAssets: MediaAsset[]
}
