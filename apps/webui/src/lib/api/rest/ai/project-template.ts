/**
 * 项目模板 API、DTO 与 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PageResult } from "@/lib/api/types"
import { notify } from "@/lib/notification"
import { backendApi } from "../backend-client"
import { buildQuery } from "../crud/client"

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

export interface ProjectTemplatesParams {
  category?: string
  isOfficial?: boolean
  keyword?: string
  page?: number
  size?: number
}

export interface ForkProjectTemplateDTO {
  name: string
  description?: string
}

export const projectTemplateApi = {
  list: (params: ProjectTemplatesParams = {}): Promise<PageResult<UserProjectTemplateVO>> =>
    backendApi.get(
      `${API_PATH}${buildQuery(params as Record<string, string | number | boolean | string[] | undefined>)}`
    ),
  getById: (id: number): Promise<UserProjectTemplateVO> => backendApi.get(`${API_PATH}/${id}`),
  fork: (
    templateId: number,
    dto: ForkProjectTemplateDTO
  ): Promise<{ id: number; [key: string]: unknown }> =>
    backendApi.post(`${API_PATH}/${templateId}/fork`, dto)
}

export function useProjectTemplates(params: ProjectTemplatesParams = {}) {
  return useQuery({
    queryKey: ["project-templates", params] as const,
    queryFn: () => projectTemplateApi.list(params),
    staleTime: 5 * 60 * 1000
  })
}

export function useProjectTemplate(id: number | null) {
  return useQuery({
    queryKey: ["project-templates", "detail", id] as const,
    queryFn: () => projectTemplateApi.getById(id as number),
    enabled: id !== null
  })
}

export function useForkProjectTemplate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ templateId, ...dto }: { templateId: number } & ForkProjectTemplateDTO) =>
      projectTemplateApi.fork(templateId, dto),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["aigc-projects"] }),
    onError: () => notify.error("创建项目失败，请重试")
  })
}
