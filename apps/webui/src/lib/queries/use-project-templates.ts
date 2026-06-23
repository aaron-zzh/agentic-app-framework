/**
 * 项目模板 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { backendApi } from "@/lib/api/rest/backend-client"
import { buildQuery } from "@/lib/api/rest/crud/client"
import type { PageResult } from "@/lib/api/types"
import { notify } from "@/lib/notification"

const API_PATH = "/aigc/project-templates"

export interface UserProjectTemplateVO {
  id: number
  code: string
  name: string
  description?: string
  coverUrl?: string
  category: string
  projectType: string
  templateConfig: Record<string, unknown>
  isOfficial: boolean
  usageCount: number
  createTime: string
  updateTime: string
}

interface TemplatesParams {
  category?: string
  isOfficial?: boolean
  keyword?: string
  page?: number
  size?: number
}

interface ForkTemplateDTO {
  name: string
  description?: string
}

/** 项目模板列表 */
export function useProjectTemplates(params: TemplatesParams = {}) {
  return useQuery({
    queryKey: ["project-templates", params] as const,
    queryFn: () =>
      backendApi.get<PageResult<UserProjectTemplateVO>>(
        `${API_PATH}${buildQuery(params as Record<string, string | number | boolean | string[] | undefined>)}`
      ),
    staleTime: 5 * 60 * 1000
  })
}

/** 单个模板详情 */
export function useProjectTemplate(id: number | null) {
  return useQuery({
    queryKey: ["project-templates", "detail", id] as const,
    queryFn: () => backendApi.get<UserProjectTemplateVO>(`${API_PATH}/${id}`),
    enabled: id !== null
  })
}

/** Fork 模板创建新项目，返回新项目 VO */
export function useForkProjectTemplate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ templateId, ...dto }: { templateId: number } & ForkTemplateDTO) =>
      backendApi.post<{ id: number; [key: string]: unknown }>(
        `${API_PATH}/${templateId}/fork`,
        dto
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["aigc-projects"] })
    },
    onError: () => {
      notify.error("创建项目失败，请重试")
    }
  })
}
