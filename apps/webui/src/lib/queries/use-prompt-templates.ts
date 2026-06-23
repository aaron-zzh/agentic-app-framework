/**
 * 个人提示词模板 TanStack Query Hooks（复用 GenerationTemplate）
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { backendApi } from "@/lib/api/rest/backend-client"
import { buildQuery } from "@/lib/api/rest/crud/client"
import type { PageResult } from "@/lib/api/types"
import { notify } from "@/lib/notification"

const API_PATH = "/aigc/prompt-templates"

export interface PromptTemplateVO {
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

interface PromptTemplatesParams {
  category?: string
  keyword?: string
  page?: number
  size?: number
}

interface CreatePromptDTO {
  name: string
  category: string
  prompt: string
  negativePrompt?: string
  model?: string
  isPublic?: boolean
}

interface UpdatePromptDTO {
  name?: string
  category?: string
  prompt?: string
  negativePrompt?: string
  model?: string
  isPublic?: boolean
}

const ME_KEY = ["prompt-templates", "me"] as const

/** 我的提示词模板列表 */
export function useMyPromptTemplates(params: PromptTemplatesParams = {}) {
  return useQuery({
    queryKey: [...ME_KEY, params] as const,
    queryFn: () =>
      backendApi.get<PageResult<PromptTemplateVO>>(
        `${API_PATH}/me${buildQuery(params as Record<string, string | number | boolean | string[] | undefined>)}`
      )
  })
}

/** 创建提示词模板 */
export function useCreatePromptTemplate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (dto: CreatePromptDTO) => backendApi.post<PromptTemplateVO>(API_PATH, dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ME_KEY })
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
    mutationFn: ({ id, ...dto }: { id: number } & UpdatePromptDTO) =>
      backendApi.put<PromptTemplateVO>(`${API_PATH}/${id}`, dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ME_KEY })
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
    mutationFn: (id: number) => backendApi.delete<void>(`${API_PATH}/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ME_KEY })
    },
    onError: () => {
      notify.error("删除失败")
    }
  })
}
