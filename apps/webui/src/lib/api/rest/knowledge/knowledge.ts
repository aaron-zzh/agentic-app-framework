/**
 * 知识库 API 客户端与 TanStack Query hooks
 * @author AaronZZH & Kiro
 */

import { type QueryClient, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useEffect, useRef } from "react"
import { useAuthStore } from "@/lib/store/auth-store"
import type {
  CreateKnowledgeBaseInput,
  GraphData,
  KnowledgeBase,
  KnowledgeBaseStats,
  KnowledgeDocument,
  KnowledgeSegment,
  SearchRequest,
  SearchResponse
} from "@/lib/types/knowledge"
import { buildApiUrl } from "../../config"
import { backendApi } from "../backend-client"
import { buildQuery, type ListParams, type PageResult } from "../entity/crud"

const API_PATH = "/knowledge-bases"
type KnowledgeResourceId = string | number
type KnowledgeListParams = Pick<ListParams, "pageNo" | "pageSize" | "sort">

export const knowledgeApi = {
  /** 知识库列表。 */
  list: (params: KnowledgeListParams = {}) =>
    backendApi.get<PageResult<KnowledgeBase>>(`${API_PATH}${buildQuery(params)}`),

  /** 知识库详情。 */
  get: (id: KnowledgeResourceId) => backendApi.get<KnowledgeBase>(`${API_PATH}/${id}`),

  /** 创建知识库。 */
  create: (data: CreateKnowledgeBaseInput) => backendApi.post<KnowledgeBase>(API_PATH, data),

  /** 更新知识库。 */
  update: (id: KnowledgeResourceId, data: Partial<CreateKnowledgeBaseInput>) =>
    backendApi.put<KnowledgeBase>(`${API_PATH}/${id}`, data),

  /** 删除知识库。 */
  delete: (id: KnowledgeResourceId) => backendApi.delete<void>(`${API_PATH}/${id}`),

  /** 知识库统计。 */
  stats: (id: KnowledgeResourceId) => backendApi.get<KnowledgeBaseStats>(`${API_PATH}/${id}/stats`),

  /** 文档列表。 */
  documents: (id: KnowledgeResourceId, params: KnowledgeListParams = {}) =>
    backendApi.get<PageResult<KnowledgeDocument>>(
      `${API_PATH}/${id}/documents${buildQuery(params)}`
    ),

  /** 文档详情。 */
  document: (id: KnowledgeResourceId, documentId: number) =>
    backendApi.get<KnowledgeDocument>(`${API_PATH}/${id}/documents/${documentId}`),

  /** 删除文档。 */
  deleteDocument: (id: KnowledgeResourceId, documentId: number) =>
    backendApi.delete<void>(`${API_PATH}/${id}/documents/${documentId}`),

  /** 重试失败文档。 */
  retryDocument: (id: KnowledgeResourceId, documentId: number) =>
    backendApi.post<KnowledgeDocument>(`${API_PATH}/${id}/documents/${documentId}/retry`),

  /** 图谱数据。 */
  graph: (id: KnowledgeResourceId) => backendApi.get<GraphData>(`${API_PATH}/${id}/graph`),

  /** 检索。 */
  search: (id: KnowledgeResourceId, params: SearchRequest) =>
    backendApi.post<SearchResponse>(`${API_PATH}/${id}/search`, params),

  /** 段落列表（按文档）。 */
  segments: (kbId: KnowledgeResourceId, documentId: number, page = 0, size = 20) =>
    backendApi.get<PageResult<KnowledgeSegment>>(
      `${API_PATH}/${kbId}/segments?documentId=${documentId}&page=${page}&size=${size}`
    ),

  /** 创建段落。 */
  createSegment: (
    kbId: KnowledgeResourceId,
    data: { documentId: number; content: string; position?: number }
  ) => backendApi.post<KnowledgeSegment>(`${API_PATH}/${kbId}/segments`, data),

  /** 更新段落。 */
  updateSegment: (kbId: KnowledgeResourceId, segmentId: number, data: { content: string }) =>
    backendApi.put<KnowledgeSegment>(`${API_PATH}/${kbId}/segments/${segmentId}`, data),

  /** 删除段落。 */
  deleteSegment: (kbId: KnowledgeResourceId, segmentId: number) =>
    backendApi.delete<void>(`${API_PATH}/${kbId}/segments/${segmentId}`),

  /** 切换段落启用状态。 */
  toggleSegment: (kbId: KnowledgeResourceId, segmentId: number, enabled: boolean) =>
    backendApi.patch<void>(`${API_PATH}/${kbId}/segments/${segmentId}/enabled?enabled=${enabled}`),

  /** 上传文档。 */
  uploadDocument: (id: KnowledgeResourceId, file: File, onProgress?: (pct: number) => void) => {
    return new Promise<KnowledgeDocument>((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.open("POST", buildApiUrl(`${API_PATH}/${id}/documents/batch`))

      const { accessToken } = useAuthStore.getState()
      if (accessToken) {
        xhr.setRequestHeader("Authorization", `Bearer ${accessToken}`)
      }

      xhr.upload.onprogress = (event) => {
        if (event.lengthComputable && onProgress) {
          onProgress(Math.round((event.loaded / event.total) * 100))
        }
      }

      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          const response = JSON.parse(xhr.responseText) as { data?: unknown }
          const data = Array.isArray(response.data) ? response.data[0] : response.data
          resolve(data as KnowledgeDocument)
          return
        }
        reject(new Error(`上传失败: ${xhr.status} ${xhr.statusText}`))
      }

      xhr.onerror = () => reject(new Error("网络错误"))

      const formData = new FormData()
      formData.append("files", file)
      xhr.send(formData)
    })
  }
}

export const knowledgeKeys = {
  all: ["knowledge-bases"] as const,
  list: (params: KnowledgeListParams) => ["knowledge-bases", "list", params] as const,
  detail: (id: KnowledgeResourceId) => ["knowledge-bases", id] as const,
  stats: (id: KnowledgeResourceId) => ["knowledge-bases", id, "stats"] as const,
  documents: (id: KnowledgeResourceId) => ["knowledge-bases", id, "documents", "list"] as const,
  documentList: (id: KnowledgeResourceId, params: KnowledgeListParams) =>
    ["knowledge-bases", id, "documents", "list", params] as const,
  document: (id: KnowledgeResourceId, documentId: number) =>
    ["knowledge-bases", id, "documents", "detail", documentId] as const,
  graph: (id: KnowledgeResourceId) => ["knowledge-bases", id, "graph"] as const,
  segments: (kbId: KnowledgeResourceId, documentId: number) =>
    ["knowledge-bases", kbId, "segments", documentId] as const
}

/** 失效文档异步处理影响的统计与图谱查询。 */
export async function invalidateKnowledgeDerivedQueries(
  queryClient: QueryClient,
  knowledgeBaseId: KnowledgeResourceId
): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: knowledgeKeys.stats(knowledgeBaseId) }),
    queryClient.invalidateQueries({ queryKey: knowledgeKeys.graph(knowledgeBaseId) })
  ])
}

/** 失效文档变化影响的列表、统计与图谱查询。 */
export async function invalidateKnowledgeDocumentQueries(
  queryClient: QueryClient,
  knowledgeBaseId: KnowledgeResourceId
): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: knowledgeKeys.documents(knowledgeBaseId) }),
    invalidateKnowledgeDerivedQueries(queryClient, knowledgeBaseId)
  ])
}

function hasActiveKnowledgeDocuments(data: PageResult<KnowledgeDocument> | undefined): boolean {
  return data?.list.some((document) => document.status === 0 || document.status === 1) ?? false
}

/** 文档存在待处理或处理中记录时每两秒轮询，否则停止。 */
export function getKnowledgeDocumentsRefetchInterval(
  data: PageResult<KnowledgeDocument> | undefined
): 2000 | false {
  return hasActiveKnowledgeDocuments(data) ? 2000 : false
}

/** 知识库列表。 */
export function useKnowledgeBases(params: KnowledgeListParams = {}) {
  return useQuery({
    queryKey: knowledgeKeys.list(params),
    queryFn: () => knowledgeApi.list(params)
  })
}

/** 知识库详情。 */
export function useKnowledgeBase(id: KnowledgeResourceId) {
  return useQuery({
    queryKey: knowledgeKeys.detail(id),
    queryFn: () => knowledgeApi.get(id),
    enabled: id !== ""
  })
}

/** 知识库统计。 */
export function useKnowledgeBaseStats(id: KnowledgeResourceId) {
  return useQuery({
    queryKey: knowledgeKeys.stats(id),
    queryFn: () => knowledgeApi.stats(id),
    enabled: id !== ""
  })
}

/** 知识库文档列表。 */
export function useKnowledgeDocuments(id: KnowledgeResourceId, params: KnowledgeListParams = {}) {
  const queryClient = useQueryClient()
  const activityRef = useRef<{
    knowledgeBaseId: KnowledgeResourceId
    hasActiveDocuments: boolean
  } | null>(null)
  const query = useQuery({
    queryKey: knowledgeKeys.documentList(id, params),
    queryFn: () => knowledgeApi.documents(id, params),
    enabled: id !== "",
    refetchInterval: (currentQuery) => getKnowledgeDocumentsRefetchInterval(currentQuery.state.data)
  })

  useEffect(() => {
    if (!query.data) return

    const hasActiveDocuments = hasActiveKnowledgeDocuments(query.data)
    const previous = activityRef.current
    activityRef.current = { knowledgeBaseId: id, hasActiveDocuments }

    if (previous?.knowledgeBaseId === id && previous.hasActiveDocuments && !hasActiveDocuments) {
      void invalidateKnowledgeDerivedQueries(queryClient, id)
    }
  }, [id, query.data, queryClient])

  return query
}

/** 知识库文档详情。 */
export function useKnowledgeDocument(id: KnowledgeResourceId, documentId: number, enabled = true) {
  return useQuery({
    queryKey: knowledgeKeys.document(id, documentId),
    queryFn: () => knowledgeApi.document(id, documentId),
    enabled: enabled && id !== ""
  })
}

/** 知识图谱数据。 */
export function useKnowledgeGraph(id: KnowledgeResourceId) {
  return useQuery({
    queryKey: knowledgeKeys.graph(id),
    queryFn: () => knowledgeApi.graph(id),
    enabled: id !== ""
  })
}

/** 创建知识库。 */
export function useCreateKnowledgeBase() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateKnowledgeBaseInput) => knowledgeApi.create(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: knowledgeKeys.all })
  })
}

/** 更新知识库。 */
export function useUpdateKnowledgeBase() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      id,
      data
    }: {
      id: KnowledgeResourceId
      data: Partial<CreateKnowledgeBaseInput>
    }) => knowledgeApi.update(id, data),
    onSuccess: async (_data, { id }) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: knowledgeKeys.detail(id) }),
        queryClient.invalidateQueries({ queryKey: knowledgeKeys.all })
      ])
    }
  })
}

/** 删除知识库。 */
export function useDeleteKnowledgeBase() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: KnowledgeResourceId) => knowledgeApi.delete(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: knowledgeKeys.all })
  })
}

/** 删除知识库文档。 */
export function useDeleteKnowledgeDocument(knowledgeBaseId: KnowledgeResourceId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (documentId: number) => knowledgeApi.deleteDocument(knowledgeBaseId, documentId),
    onSuccess: async (_data, documentId) => {
      queryClient.removeQueries({ queryKey: knowledgeKeys.document(knowledgeBaseId, documentId) })
      await invalidateKnowledgeDocumentQueries(queryClient, knowledgeBaseId)
    }
  })
}

/** 重试失败的知识库文档。 */
export function useRetryKnowledgeDocument(knowledgeBaseId: KnowledgeResourceId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (documentId: number) => knowledgeApi.retryDocument(knowledgeBaseId, documentId),
    onSuccess: async (document) => {
      queryClient.setQueryData(knowledgeKeys.document(knowledgeBaseId, document.id), document)
      await invalidateKnowledgeDocumentQueries(queryClient, knowledgeBaseId)
    }
  })
}

/** 段落列表。 */
export function useKnowledgeSegments(
  kbId: KnowledgeResourceId,
  documentId: number,
  enabled = true
) {
  return useQuery({
    queryKey: knowledgeKeys.segments(kbId, documentId),
    queryFn: () => knowledgeApi.segments(kbId, documentId),
    enabled: enabled && kbId !== ""
  })
}

/** 创建段落。 */
export function useCreateSegment(kbId: KnowledgeResourceId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: { documentId: number; content: string; position?: number }) =>
      knowledgeApi.createSegment(kbId, data),
    onSuccess: (_data, variables) => {
      return queryClient.invalidateQueries({
        queryKey: knowledgeKeys.segments(kbId, variables.documentId)
      })
    }
  })
}

/** 更新段落。 */
export function useUpdateSegment(kbId: KnowledgeResourceId, documentId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, content }: { id: number; content: string }) =>
      knowledgeApi.updateSegment(kbId, id, { content }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: knowledgeKeys.segments(kbId, documentId) })
  })
}

/** 删除段落。 */
export function useDeleteSegment(kbId: KnowledgeResourceId, documentId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (segmentId: number) => knowledgeApi.deleteSegment(kbId, segmentId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: knowledgeKeys.segments(kbId, documentId) })
  })
}

/** 切换段落启用状态。 */
export function useToggleSegment(kbId: KnowledgeResourceId, documentId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) =>
      knowledgeApi.toggleSegment(kbId, id, enabled),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: knowledgeKeys.segments(kbId, documentId) })
  })
}
