/**
 * 知识库 API 客户端与 TanStack Query hooks
 * @author AaronZZH & Kiro
 */

import { type QueryClient, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useEventSubscription } from "@/lib/realtime/use-event-subscription"
import type {
  CreateKnowledgeBaseInput,
  GraphData,
  KnowledgeBase,
  KnowledgeBaseStats,
  KnowledgeChunk,
  KnowledgeDocument,
  SearchRequest,
  SearchResponse
} from "@/lib/types/knowledge"
import type { GraphProjectionStatus } from "../admin/knowledge"
import { backendApi } from "../backend-client"
import { buildQuery, type ListParams, type PageResult } from "../crud"

const API_PATH = "/knowledge-bases"
type KnowledgeResourceId = string | number
type KnowledgeListParams = Pick<ListParams, "pageNo" | "pageSize" | "sort">

/** 从文档库导入知识库的结果。 */
export interface KnowledgeDocumentLibraryImportResult {
  imported: KnowledgeDocument[]
  skipped: { documentId: number; reason: string }[]
}
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

  /** 知识库所有者重建图投影：从 PostgreSQL 事实重新投影到 Neo4j。 */
  rebuildGraph: (id: KnowledgeResourceId, requestKey: string) =>
    backendApi.post<GraphProjectionStatus>(`${API_PATH}/${id}/graph/rebuild`, undefined, {
      headers: { "Idempotency-Key": requestKey }
    }),

  /** 多库检索，默认同时检索当前用户可见的公开知识库。 */
  search: (params: SearchRequest) =>
    backendApi.post<SearchResponse>(`${API_PATH}/search`, {
      ...params,
      includePublic: params.includePublic ?? true
    }),

  /** 文档当前发布代际的分块（只读）。 */
  chunks: (kbId: KnowledgeResourceId, documentId: number, page = 0, size = 50) =>
    backendApi.get<PageResult<KnowledgeChunk>>(
      `${API_PATH}/${kbId}/documents/${documentId}/chunks?page=${page}&size=${size}`
    ),

  /** 启用或停用分块：停用后不参与检索，仅由其证据支撑的事实从图谱移除。 */
  setChunkEnabled: (
    kbId: KnowledgeResourceId,
    documentId: number,
    chunkId: string,
    enabled: boolean
  ) =>
    backendApi.patch<void>(
      `${API_PATH}/${kbId}/documents/${documentId}/chunks/${chunkId}/enabled?enabled=${enabled}`
    ),
  /** 从文档库按当前版本导入文档；同版本跳过，同文档旧版本替换。 */
  importFromDocs: (id: KnowledgeResourceId, documentIds: number[]) =>
    backendApi.post<KnowledgeDocumentLibraryImportResult>(`${API_PATH}/${id}/documents/from-docs`, {
      documentIds
    }),

  /** 上传文档。 */
  uploadDocument: (id: KnowledgeResourceId, file: File, onProgress?: (pct: number) => void) => {
    const formData = new FormData()
    formData.append("files", file)
    return backendApi
      .post<KnowledgeDocument[]>(`${API_PATH}/${id}/documents/batch`, formData, {
        headers: { "Content-Type": undefined },
        onUploadProgress: (event) => {
          if (event.total && onProgress) {
            onProgress(Math.round((event.loaded / event.total) * 100))
          }
        }
      })
      .then(([document]) => {
        if (!document) throw new Error("上传成功但未返回文档")
        return document
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
  chunks: (kbId: KnowledgeResourceId, documentId: number) =>
    ["knowledge-bases", kbId, "chunks", documentId] as const
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

/** 知识库文档处理事件前缀：processing / completed / failed，由后端推送给上传人。 */
const KNOWLEDGE_DOCUMENT_EVENT_PREFIX = "knowledge.document."

/** 知识图投影事件前缀：projected，Neo4j 投影写入后推送给知识库所有者（晚于文档 completed）。 */
const KNOWLEDGE_GRAPH_EVENT_PREFIX = "knowledge.graph."

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

/**
 * 知识库文档列表；文档处理事件到达时失效列表，完成或失败时再失效文档详情、统计与图谱。
 *
 * 图谱由后端异步投影到 Neo4j，文档 completed 时尚未写入；投影完成事件到达后再失效图谱。
 * 订阅放在页面常驻的本 hook 中，图谱 tab 未挂载时也能把缓存标记为过期，切换过去即重新拉取。
 */
export function useKnowledgeDocuments(id: KnowledgeResourceId, params: KnowledgeListParams = {}) {
  const queryClient = useQueryClient()

  useEventSubscription(KNOWLEDGE_GRAPH_EVENT_PREFIX, (event) => {
    if (id === "" || String(event.refs.knowledgeBaseId) !== String(id)) return
    void invalidateKnowledgeDerivedQueries(queryClient, id)
  })

  useEventSubscription(KNOWLEDGE_DOCUMENT_EVENT_PREFIX, (event) => {
    if (id === "" || String(event.refs.knowledgeBaseId) !== String(id)) return
    if (event.type === `${KNOWLEDGE_DOCUMENT_EVENT_PREFIX}processing`) {
      void queryClient.invalidateQueries({ queryKey: knowledgeKeys.documents(id) })
      return
    }
    const documentId = event.refs.documentId
    if (documentId !== undefined) {
      void queryClient.invalidateQueries({ queryKey: knowledgeKeys.document(id, documentId) })
    }
    void invalidateKnowledgeDocumentQueries(queryClient, id)
  })

  return useQuery({
    queryKey: knowledgeKeys.documentList(id, params),
    queryFn: () => knowledgeApi.documents(id, params),
    enabled: id !== ""
  })
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

/** 重建当前知识库的图投影，完成后刷新图谱数据。 */
export function useRebuildKnowledgeGraph(id: KnowledgeResourceId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () =>
      knowledgeApi.rebuildGraph(id, `knowledge-graph-${id}-${globalThis.crypto.randomUUID()}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: knowledgeKeys.graph(id) })
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

/** 文档分块列表。 */
export function useKnowledgeChunks(kbId: KnowledgeResourceId, documentId: number, enabled = true) {
  return useQuery({
    queryKey: knowledgeKeys.chunks(kbId, documentId),
    queryFn: () => knowledgeApi.chunks(kbId, documentId),
    enabled: enabled && kbId !== ""
  })
}

/** 启用/停用分块，成功后刷新分块列表与图谱。 */
export function useSetChunkEnabled(kbId: KnowledgeResourceId, documentId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ chunkId, enabled }: { chunkId: string; enabled: boolean }) =>
      knowledgeApi.setChunkEnabled(kbId, documentId, chunkId, enabled),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: knowledgeKeys.chunks(kbId, documentId) }),
        queryClient.invalidateQueries({ queryKey: knowledgeKeys.graph(kbId) })
      ])
    }
  })
}
/** 从文档库导入知识库，成功后刷新该库文档与统计。 */
export function useImportKnowledgeFromDocs(id: KnowledgeResourceId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (documentIds: number[]) => knowledgeApi.importFromDocs(id, documentIds),
    onSuccess: () => invalidateKnowledgeDocumentQueries(queryClient, id)
  })
}
