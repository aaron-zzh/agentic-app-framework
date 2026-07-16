/**
 * 知识库 API 客户端
 * @author AaronZZH & Kiro
 */

import { useAuthStore } from "@/lib/store/auth-store"
import type {
  CreateKnowledgeBaseInput,
  GraphData,
  KnowledgeBase,
  KnowledgeBaseStats,
  KnowledgeDocument,
  KnowledgeSegment,
  SearchResponse
} from "@/lib/types/knowledge"
import { buildApiUrl } from "../../config"
import { backendApi } from "../backend-client"
import { buildQuery, type ListParams, type PageResult } from "../entity/crud"

const API_PATH = "/knowledge-bases"

export const knowledgeApi = {
  /** 知识库列表 */
  list: (params: ListParams = {}) =>
    backendApi.get<PageResult<KnowledgeBase>>(`${API_PATH}${buildQuery(params)}`),

  /** 知识库详情 */
  get: (id: string) => backendApi.get<KnowledgeBase>(`${API_PATH}/${id}`),

  /** 创建知识库 */
  create: (data: CreateKnowledgeBaseInput) => backendApi.post<KnowledgeBase>(API_PATH, data),

  /** 更新知识库 */
  update: (id: string, data: Partial<CreateKnowledgeBaseInput>) =>
    backendApi.put<KnowledgeBase>(`${API_PATH}/${id}`, data),

  /** 删除知识库 */
  delete: (id: string) => backendApi.delete<void>(`${API_PATH}/${id}`),

  /** 知识库统计 */
  stats: (id: string) => backendApi.get<KnowledgeBaseStats>(`${API_PATH}/${id}/stats`),

  /** 文档列表 */
  documents: (id: string, params: ListParams = {}) =>
    backendApi.get<PageResult<KnowledgeDocument>>(
      `${API_PATH}/${id}/documents${buildQuery(params)}`
    ),

  /** 图谱数据 */
  graph: (id: string) => backendApi.get<GraphData>(`${API_PATH}/${id}/graph`),

  /** 检索 */
  search: (
    id: string,
    params: { query: string; topK?: number; threshold?: number; mode?: string }
  ) => backendApi.post<SearchResponse>(`${API_PATH}/${id}/search`, params),

  // ── Segment (分块) CRUD ────────────────────────────────────────────
  /** 段落列表（按文档） */
  segments: (kbId: string, documentId: string, page = 0, size = 20) =>
    backendApi.get<PageResult<KnowledgeSegment>>(
      `${API_PATH}/${kbId}/segments?documentId=${documentId}&page=${page}&size=${size}`
    ),

  /** 创建段落 */
  createSegment: (kbId: string, data: { documentId: string; content: string; position?: number }) =>
    backendApi.post<KnowledgeSegment>(`${API_PATH}/${kbId}/segments`, data),

  /** 更新段落 */
  updateSegment: (kbId: string, segmentId: string, data: { content: string }) =>
    backendApi.put<KnowledgeSegment>(`${API_PATH}/${kbId}/segments/${segmentId}`, data),

  /** 删除段落 */
  deleteSegment: (kbId: string, segmentId: string) =>
    backendApi.delete<void>(`${API_PATH}/${kbId}/segments/${segmentId}`),

  /** 切换段落启用状态 */
  toggleSegment: (kbId: string, segmentId: string, enabled: boolean) =>
    backendApi.patch<void>(`${API_PATH}/${kbId}/segments/${segmentId}/enabled?enabled=${enabled}`),

  /** 上传文档 */
  uploadDocument: (id: string, file: File, onProgress?: (pct: number) => void) => {
    return new Promise<KnowledgeDocument>((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.open("POST", buildApiUrl(`${API_PATH}/${id}/documents/batch`))

      // 从 auth-store 读取 token 并注入 Authorization header
      const { accessToken } = useAuthStore.getState()
      if (accessToken) {
        xhr.setRequestHeader("Authorization", `Bearer ${accessToken}`)
      }

      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable && onProgress) {
          onProgress(Math.round((e.loaded / e.total) * 100))
        }
      }

      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          const json = JSON.parse(xhr.responseText)
          // batch 接口返回 List，取第一个
          const data = Array.isArray(json.data) ? json.data[0] : json.data
          resolve(data as KnowledgeDocument)
        } else {
          reject(new Error(`上传失败: ${xhr.status} ${xhr.statusText}`))
        }
      }

      xhr.onerror = () => reject(new Error("网络错误"))

      const formData = new FormData()
      formData.append("files", file)
      xhr.send(formData)
    })
  }
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

const KEYS = {
  all: ["knowledge-bases"] as const,
  list: (params: ListParams) => ["knowledge-bases", "list", params] as const,
  detail: (id: string) => ["knowledge-bases", id] as const,
  stats: (id: string) => ["knowledge-bases", id, "stats"] as const,
  documents: (id: string, params: ListParams) =>
    ["knowledge-bases", id, "documents", params] as const,
  graph: (id: string) => ["knowledge-bases", id, "graph"] as const,
  segments: (kbId: string, documentId: string) =>
    ["knowledge-bases", kbId, "segments", documentId] as const
}

/** 知识库列表 */
export function useKnowledgeBases(params: ListParams = {}) {
  return useQuery({
    queryKey: KEYS.list(params),
    queryFn: () => knowledgeApi.list(params)
  })
}

/** 知识库详情 */
export function useKnowledgeBase(id: string) {
  return useQuery({
    queryKey: KEYS.detail(id),
    queryFn: () => knowledgeApi.get(id),
    enabled: !!id
  })
}

/** 知识库统计 */
export function useKnowledgeBaseStats(id: string) {
  return useQuery({
    queryKey: KEYS.stats(id),
    queryFn: () => knowledgeApi.stats(id),
    enabled: !!id
  })
}

/** 知识库文档列表 */
export function useKnowledgeDocuments(id: string, params: ListParams = {}) {
  return useQuery({
    queryKey: KEYS.documents(id, params),
    queryFn: () => knowledgeApi.documents(id, params),
    enabled: !!id
  })
}

/** 知识图谱数据 */
export function useKnowledgeGraph(id: string) {
  return useQuery({
    queryKey: KEYS.graph(id),
    queryFn: () => knowledgeApi.graph(id),
    enabled: !!id
  })
}

/** 创建知识库 */
export function useCreateKnowledgeBase() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateKnowledgeBaseInput) => knowledgeApi.create(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}

/** 更新知识库 */
export function useUpdateKnowledgeBase() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<CreateKnowledgeBaseInput> }) =>
      knowledgeApi.update(id, data),
    onSuccess: (_data, { id }) => {
      qc.invalidateQueries({ queryKey: KEYS.detail(id) })
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}

/** 删除知识库 */
export function useDeleteKnowledgeBase() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => knowledgeApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all })
    }
  })
}

/** 段落列表 */
export function useKnowledgeSegments(kbId: string, documentId: string, enabled = true) {
  return useQuery({
    queryKey: KEYS.segments(kbId, documentId),
    queryFn: () => knowledgeApi.segments(kbId, documentId),
    enabled: enabled && !!kbId && !!documentId
  })
}

/** 创建段落 */
export function useCreateSegment(kbId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { documentId: string; content: string; position?: number }) =>
      knowledgeApi.createSegment(kbId, data),
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: KEYS.segments(kbId, vars.documentId) })
    }
  })
}

/** 更新段落 */
export function useUpdateSegment(kbId: string, documentId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, content }: { id: string; content: string }) =>
      knowledgeApi.updateSegment(kbId, id, { content }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.segments(kbId, documentId) })
    }
  })
}

/** 删除段落 */
export function useDeleteSegment(kbId: string, documentId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (segmentId: string) => knowledgeApi.deleteSegment(kbId, segmentId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.segments(kbId, documentId) })
    }
  })
}

/** 切换段落启用 */
export function useToggleSegment(kbId: string, documentId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      knowledgeApi.toggleSegment(kbId, id, enabled),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.segments(kbId, documentId) })
    }
  })
}
