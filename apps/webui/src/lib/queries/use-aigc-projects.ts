/**
 * AIGC 创作项目 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { backendApi } from "@/lib/api/rest/backend-client"
import { buildQuery, request } from "@/lib/api/rest/entity/crud"
import type { ListParams, PageResult } from "@/lib/api/types"

const API_PATH = "/aigc/projects"

export interface AigcProjectVO {
  id: number
  name: string
  coverUrl?: string
  description?: string
  /** VIDEO_DRAMA | IMAGE_POST | SHORT_VIDEO | MIXED */
  type: string
  /** DRAFT | IN_PROGRESS | COMPLETED | ARCHIVED */
  status: string
  userId: number
  prompt?: string
  createTime: string
  updateTime: string
}

export interface AigcProjectCreateDTO {
  name: string
  type: string
  description?: string
  coverUrl?: string
  prompt?: string
}

export interface AigcProjectUpdateDTO {
  name?: string
  type?: string
  description?: string
  coverUrl?: string
  status?: string
  prompt?: string
}

export interface AigcProjectDocVO {
  id: number
  projectId: number
  docId: number
  docTitle?: string
  docType?: string
  sourceFileId?: number
  role: string
  sortOrder: number
  createTime: string
}

export interface AigcProjectSummaryVO {
  id: number
  name: string
  storyboardCount: number
  timelineCount: number
  contentCount: number
  assetCount: number
}

/** 项目列表分页 */
export function useAigcProjects(params: ListParams & { type?: string; status?: string } = {}) {
  return useQuery({
    queryKey: ["aigc-projects", "list", params] as const,
    queryFn: () => backendApi.get<PageResult<AigcProjectVO>>(`${API_PATH}${buildQuery(params)}`)
  })
}

/** 项目详情 */
export function useAigcProject(id: number | null) {
  return useQuery({
    queryKey: ["aigc-projects", "detail", id] as const,
    queryFn: () => request<AigcProjectVO>(`${API_PATH}/${id}`),
    enabled: id !== null
  })
}

/** 项目概览统计 */
export function useAigcProjectSummary(id: number | null) {
  return useQuery({
    queryKey: ["aigc-projects", "summary", id] as const,
    queryFn: () => request<AigcProjectSummaryVO>(`${API_PATH}/${id}/summary`),
    enabled: id !== null
  })
}

/** 创建项目 */
export function useCreateAigcProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: AigcProjectCreateDTO) =>
      request<AigcProjectVO>(API_PATH, {
        method: "POST",
        body: JSON.stringify(data),
        headers: { "Content-Type": "application/json" }
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["aigc-projects"] })
    }
  })
}

/** 删除项目 */
export function useDeleteAigcProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request<void>(`${API_PATH}/${id}`, { method: "DELETE" }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["aigc-projects"] })
    }
  })
}

/** 更新项目 */
export function useUpdateAigcProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...data }: { id: number } & AigcProjectUpdateDTO) =>
      request<AigcProjectVO>(`${API_PATH}/${id}`, {
        method: "PUT",
        body: JSON.stringify(data),
        headers: { "Content-Type": "application/json" }
      }),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["aigc-projects", "detail", id] })
      queryClient.invalidateQueries({ queryKey: ["aigc-projects", "list"] })
    }
  })
}

/** 项目关联文档列表 */
export function useAigcProjectDocs(projectId: number | null) {
  return useQuery({
    queryKey: ["aigc-projects", "docs", projectId] as const,
    queryFn: () => request<AigcProjectDocVO[]>(`${API_PATH}/${projectId}/docs`),
    enabled: projectId !== null
  })
}

/** 关联文档到项目 */
export function useLinkProjectDoc() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ projectId, docId, role }: { projectId: number; docId: number; role?: string }) =>
      request<AigcProjectDocVO>(`${API_PATH}/${projectId}/docs`, {
        method: "POST",
        body: JSON.stringify({ docId, role }),
        headers: { "Content-Type": "application/json" }
      }),
    onSuccess: (_, { projectId }) => {
      queryClient.invalidateQueries({ queryKey: ["aigc-projects", "docs", projectId] })
    }
  })
}

/** 取消文档关联 */
export function useUnlinkProjectDoc() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ projectId, docId }: { projectId: number; docId: number }) =>
      request<void>(`${API_PATH}/${projectId}/docs/${docId}`, { method: "DELETE" }),
    onSuccess: (_, { projectId }) => {
      queryClient.invalidateQueries({ queryKey: ["aigc-projects", "docs", projectId] })
    }
  })
}

/** 上传 PDF 并自动关联到项目 */
export function useImportProjectPdf(projectId: number | null) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (form: FormData) => {
      // 1. 导入 PDF → doc_document（multipart，不加 Content-Type 让浏览器自动设置 boundary）
      const doc = await backendApi.post<{ id: number }>("/docs/import-pdf", form)
      // 2. 自动关联到当前项目（role=ref）
      if (projectId) {
        await request(`${API_PATH}/${projectId}/docs`, {
          method: "POST",
          body: JSON.stringify({ docId: doc.id, role: "ref" }),
          headers: { "Content-Type": "application/json" }
        })
      }
      return doc
    },
    onSuccess: () => {
      if (projectId) {
        queryClient.invalidateQueries({ queryKey: ["aigc-projects", "docs", projectId] })
      }
    }
  })
}
