/**
 * 工作流模板查询 Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { backendApi } from "@/lib/api/rest/backend-client"

export interface WorkflowStep {
  kind: "COPY" | "IMAGE" | "VIDEO" | "OCR"
  label: string
  /** COPY 用 */
  skill?: string
  /** IMAGE/VIDEO 用 */
  model?: string
  aspect?: string
  ratio?: string
  duration?: number
  count?: number
  /** 输入键（用户输入字段名） */
  inputKey?: string
  /** 上一步输出引用，例如 'step0' */
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

interface PageResp<T> {
  list: T[]
  total: number
}

const KEYS = {
  all: ["workflow-templates"] as const,
  page: (params: Record<string, unknown>) => ["workflow-templates", "page", params] as const,
  detail: (id: number) => ["workflow-templates", "detail", id] as const
}

/** 列表查询 */
export function useWorkflowTemplates(params: { category?: string; pageSize?: number } = {}) {
  return useQuery({
    queryKey: KEYS.page(params),
    queryFn: () => {
      const qs = new URLSearchParams()
      qs.set("isOfficial", "true")
      qs.set("pageSize", String(params.pageSize ?? 20))
      if (params.category) qs.set("category", params.category)
      return backendApi.get<PageResp<WorkflowTemplate>>(
        `/aigc/workflow-templates/page?${qs.toString()}`
      )
    }
  })
}

/** 单个模板详情 */
export function useWorkflowTemplate(id: number | undefined) {
  return useQuery({
    queryKey: KEYS.detail(id ?? 0),
    queryFn: () => backendApi.get<WorkflowTemplate>(`/aigc/workflow-templates/${id}`),
    enabled: !!id
  })
}

/** 调用前增加 run 计数 */
export function useIncrementRunCount() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) =>
      backendApi.post<void>(`/aigc/workflow-templates/${id}/run-count`, {}),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}
