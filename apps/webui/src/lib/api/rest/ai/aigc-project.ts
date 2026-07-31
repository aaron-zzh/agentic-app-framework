/**
 * AIGC 创作项目 API、DTO 与 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { backendApi } from "../backend-client"
import { buildQuery, type ListParams, type PageResult, request } from "../entity/crud"

const API_PATH = "/aigc/projects"

export interface AigcProjectVO {
  id: number
  name: string
  coverUrl?: string
  description?: string
  type: string
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

export interface AigcProjectListParams extends ListParams {
  type?: string
  status?: string
}

export interface LinkProjectDocParams {
  projectId: number
  docId: number
  role?: string
}

export const aigcProjectApi = {
  list: (params: AigcProjectListParams = {}): Promise<PageResult<AigcProjectVO>> =>
    backendApi.get(`${API_PATH}${buildQuery(params)}`),
  getById: (id: number): Promise<AigcProjectVO> => request(`${API_PATH}/${id}`),
  getSummary: (id: number): Promise<AigcProjectSummaryVO> => request(`${API_PATH}/${id}/summary`),
  create: (data: AigcProjectCreateDTO): Promise<AigcProjectVO> =>
    request(API_PATH, {
      method: "POST",
      body: JSON.stringify(data),
      headers: { "Content-Type": "application/json" }
    }),
  update: (id: number, data: AigcProjectUpdateDTO): Promise<AigcProjectVO> =>
    request(`${API_PATH}/${id}`, {
      method: "PUT",
      body: JSON.stringify(data),
      headers: { "Content-Type": "application/json" }
    }),
  delete: (id: number): Promise<void> => request(`${API_PATH}/${id}`, { method: "DELETE" }),
  listDocs: (projectId: number): Promise<AigcProjectDocVO[]> =>
    request(`${API_PATH}/${projectId}/docs`),
  linkDoc: ({ projectId, docId, role }: LinkProjectDocParams): Promise<AigcProjectDocVO> =>
    request(`${API_PATH}/${projectId}/docs`, {
      method: "POST",
      body: JSON.stringify({ docId, role }),
      headers: { "Content-Type": "application/json" }
    }),
  unlinkDoc: (projectId: number, docId: number): Promise<void> =>
    request(`${API_PATH}/${projectId}/docs/${docId}`, { method: "DELETE" }),
  importPdf: async (projectId: number | null, form: FormData): Promise<{ id: number }> => {
    const doc = await backendApi.post<{ id: number }>("/docs/import-pdf", form)
    if (projectId) {
      await request(`${API_PATH}/${projectId}/docs`, {
        method: "POST",
        body: JSON.stringify({ docId: doc.id, role: "ref" }),
        headers: { "Content-Type": "application/json" }
      })
    }
    return doc
  }
}

export function useAigcProjects(params: AigcProjectListParams = {}) {
  return useQuery({
    queryKey: ["aigc-projects", "list", params] as const,
    queryFn: () => aigcProjectApi.list(params)
  })
}

export function useAigcProject(id: number | null) {
  return useQuery({
    queryKey: ["aigc-projects", "detail", id] as const,
    queryFn: () => aigcProjectApi.getById(id as number),
    enabled: id !== null
  })
}

export function useAigcProjectSummary(id: number | null) {
  return useQuery({
    queryKey: ["aigc-projects", "summary", id] as const,
    queryFn: () => aigcProjectApi.getSummary(id as number),
    enabled: id !== null
  })
}

export function useCreateAigcProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.create,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["aigc-projects"] })
  })
}

export function useDeleteAigcProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.delete,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["aigc-projects"] })
  })
}

export function useUpdateAigcProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...data }: { id: number } & AigcProjectUpdateDTO) =>
      aigcProjectApi.update(id, data),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["aigc-projects", "detail", id] })
      queryClient.invalidateQueries({ queryKey: ["aigc-projects", "list"] })
    }
  })
}

export function useAigcProjectDocs(projectId: number | null) {
  return useQuery({
    queryKey: ["aigc-projects", "docs", projectId] as const,
    queryFn: () => aigcProjectApi.listDocs(projectId as number),
    enabled: projectId !== null
  })
}

export function useLinkProjectDoc() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: aigcProjectApi.linkDoc,
    onSuccess: (_, { projectId }) => {
      queryClient.invalidateQueries({ queryKey: ["aigc-projects", "docs", projectId] })
    }
  })
}

export function useUnlinkProjectDoc() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ projectId, docId }: { projectId: number; docId: number }) =>
      aigcProjectApi.unlinkDoc(projectId, docId),
    onSuccess: (_, { projectId }) => {
      queryClient.invalidateQueries({ queryKey: ["aigc-projects", "docs", projectId] })
    }
  })
}

export function useImportProjectPdf(projectId: number | null) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (form: FormData) => aigcProjectApi.importPdf(projectId, form),
    onSuccess: () => {
      if (projectId) {
        queryClient.invalidateQueries({ queryKey: ["aigc-projects", "docs", projectId] })
      }
    }
  })
}
