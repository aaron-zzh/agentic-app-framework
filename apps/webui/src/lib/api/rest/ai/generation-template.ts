/**
 * 生成模板 API 与 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { notify } from "@/lib/notification"

import { backendApi } from "../backend-client"
import { buildQuery } from "../crud/client"

const PROMPT_TEMPLATE_PATH = "/aigc/prompt-templates"
const PROMPT_TEMPLATE_ME_KEY = ["prompt-templates", "me"] as const

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
  createTime?: string
  updateTime?: string
}

export interface GenerationTemplatePageResult {
  list: GenerationTemplateVO[]
  total: number
}

export interface PublicTemplateParams {
  type?: string
  category?: string
  scope?: string
  page?: number
  size?: number
}

export interface PromptTemplatesParams {
  category?: string
  keyword?: string
  pageNo?: number
  pageSize?: number
}

export interface CreatePromptTemplateDTO {
  name: string
  category: string
  prompt: string
  negativePrompt?: string
  model?: string
  isPublic?: boolean
}

export interface UpdatePromptTemplateDTO {
  name?: string
  category?: string
  prompt?: string
  negativePrompt?: string
  model?: string
  isPublic?: boolean
}

export const generationTemplateApi = {
  listPublic: (params: PublicTemplateParams): Promise<GenerationTemplatePageResult> =>
    backendApi.get("/aigc/templates/public", { params: { size: 20, ...params } }),
  markUsed: (id: number): Promise<GenerationTemplateVO> =>
    backendApi.post(`/aigc/templates/${id}/use`),
  listMine: (params: PromptTemplatesParams = {}): Promise<GenerationTemplatePageResult> =>
    backendApi.get(
      `${PROMPT_TEMPLATE_PATH}/me${buildQuery(params as Record<string, string | number | boolean | string[] | undefined>)}`
    ),
  create: (dto: CreatePromptTemplateDTO): Promise<GenerationTemplateVO> =>
    backendApi.post(PROMPT_TEMPLATE_PATH, dto),
  update: (id: number, dto: UpdatePromptTemplateDTO): Promise<GenerationTemplateVO> =>
    backendApi.put(`${PROMPT_TEMPLATE_PATH}/${id}`, dto),
  delete: (id: number): Promise<void> => backendApi.delete(`${PROMPT_TEMPLATE_PATH}/${id}`)
}

export function listPublicTemplates(
  params: PublicTemplateParams
): Promise<GenerationTemplatePageResult> {
  return generationTemplateApi.listPublic(params)
}

export function markTemplateUsed(id: number): Promise<GenerationTemplateVO> {
  return generationTemplateApi.markUsed(id)
}

/** 我的提示词模板列表 */
export function useMyPromptTemplates(params: PromptTemplatesParams = {}) {
  return useQuery({
    queryKey: [...PROMPT_TEMPLATE_ME_KEY, params] as const,
    queryFn: () => generationTemplateApi.listMine(params)
  })
}

/** 创建提示词模板 */
export function useCreatePromptTemplate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (dto: CreatePromptTemplateDTO) => generationTemplateApi.create(dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PROMPT_TEMPLATE_ME_KEY })
      notify.success("创建成功")
    },
    onError: () => {
      notify.error("创建失败")
    }
  })
}

/** 更新提示词模板 */
export function useUpdatePromptTemplate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...dto }: { id: number } & UpdatePromptTemplateDTO) =>
      generationTemplateApi.update(id, dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PROMPT_TEMPLATE_ME_KEY })
      notify.success("更新成功")
    },
    onError: () => {
      notify.error("更新失败")
    }
  })
}

/** 删除提示词模板 */
export function useDeletePromptTemplate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => generationTemplateApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PROMPT_TEMPLATE_ME_KEY })
    },
    onError: () => {
      notify.error("删除失败")
    }
  })
}

