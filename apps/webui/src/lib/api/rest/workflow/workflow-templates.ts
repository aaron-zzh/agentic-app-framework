/**
 * 工作流模板 API、DTO 与 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { backendApi } from "../backend-client"

export interface WorkflowStep {
  kind: "COPY" | "IMAGE" | "VIDEO" | "OCR"
  label: string
  skill?: string
  model?: string
  aspect?: string
  ratio?: string
  duration?: number
  count?: number
  inputKey?: string
  promptFrom?: string
}

export interface WorkflowTemplateConfig {
  steps: WorkflowStep[]
}

export interface WorkflowTemplate {
  id: number
  code: string
  name: string
  description?: string
  coverUrl?: string
  category: string
  templateConfig: WorkflowTemplateConfig
  isOfficial: boolean
  usageCount: number
  sortOrder: number
  createTime: string
  updateTime: string
}

export interface WorkflowTemplatePageResult {
  list: WorkflowTemplate[]
  total: number
}

export interface WorkflowTemplateParams {
  category?: string
  pageSize?: number
}

const KEYS = {
  all: ["workflow-templates"] as const,
  page: (params: WorkflowTemplateParams) => ["workflow-templates", "page", params] as const,
  detail: (id: number) => ["workflow-templates", "detail", id] as const
}

export const workflowTemplateApi = {
  list: (params: WorkflowTemplateParams = {}): Promise<WorkflowTemplatePageResult> => {
    const query = new URLSearchParams()
    query.set("isOfficial", "true")
    query.set("pageSize", String(params.pageSize ?? 20))
    if (params.category) query.set("category", params.category)
    return backendApi.get(`/aigc/workflow-templates/page?${query.toString()}`)
  },
  getById: (id: number): Promise<WorkflowTemplate> =>
    backendApi.get(`/aigc/workflow-templates/${id}`),
  incrementRunCount: (id: number): Promise<void> =>
    backendApi.post(`/aigc/workflow-templates/${id}/run-count`, {})
}

export function useWorkflowTemplates(params: WorkflowTemplateParams = {}) {
  return useQuery({
    queryKey: KEYS.page(params),
    queryFn: () => workflowTemplateApi.list(params)
  })
}

export function useWorkflowTemplate(id: number | undefined) {
  return useQuery({
    queryKey: KEYS.detail(id ?? 0),
    queryFn: () => workflowTemplateApi.getById(id as number),
    enabled: !!id
  })
}

export function useIncrementRunCount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: workflowTemplateApi.incrementRunCount,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: KEYS.all })
  })
}
