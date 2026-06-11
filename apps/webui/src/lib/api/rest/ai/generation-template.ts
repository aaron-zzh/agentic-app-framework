import { backendApi } from "../backend-client"

export interface GenerationTemplateVO {
  id: number
  type: string
  name: string
  category: string
  prompt: string
  negativePrompt?: string
  model?: string
  isPublic: boolean
  usageCount: number
  scope?: string
}

export interface PageResult<T> {
  list: T[]
  total: number
}

export function listPublicTemplates(params: {
  type?: string
  category?: string
  scope?: string
  page?: number
  size?: number
}): Promise<PageResult<GenerationTemplateVO>> {
  return backendApi.get("/aigc/templates/public", { params: { size: 20, ...params } })
}

export function markTemplateUsed(id: number): Promise<GenerationTemplateVO> {
  return backendApi.post(`/aigc/templates/${id}/use`)
}
