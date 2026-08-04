/**
 * 知识库后台运维 API 客户端。
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type {
  KnowledgeBaseStats,
  KnowledgeBaseStatus,
  KnowledgeBaseVisibility,
  KnowledgeDocument
} from "@/lib/types/knowledge"
import { backendApi } from "../backend-client"
import { buildQuery, type ListParams, type PageResult } from "../entity/crud"

const MAINTENANCE_PATH = "/knowledge-bases/maintenance"
const KNOWLEDGE_PATH = "/knowledge-bases"

export interface KnowledgeBaseMaintenance {
  id: number
  stableId: string
  orgId: number | null
  workspaceId: number | null
  ownerId: number | null
  name: string
  description: string | null
  visibility: KnowledgeBaseVisibility
  scopeCode: string | null
  embeddingModel: string | null
  chunkStrategy: "fixed" | "recursive" | "semantic"
  chunkSize: number
  chunkOverlap: number
  documentCount: number
  status: KnowledgeBaseStatus
  createTime: string
  updateTime: string
}

export interface KnowledgeMaintenanceListParams extends ListParams {
  name?: string
  orgId?: number
  workspaceId?: number
  ownerId?: number
  visibility?: KnowledgeBaseVisibility
  status?: KnowledgeBaseStatus
}

export interface GraphProjectionStatus {
  knowledgeBaseId: string
  projectionKind: string
  baseWatermark: number
  desiredWatermark: number
  appliedWatermark: number
  status: string
  rebuildRequestKey: string | null
  errorMessage: string | null
  updatedAt: string
  ready: boolean
}

export const adminKnowledgeApi = {
  list: (params: KnowledgeMaintenanceListParams = {}) =>
    backendApi.get<PageResult<KnowledgeBaseMaintenance>>(
      `${MAINTENANCE_PATH}${buildQuery(params)}`
    ),

  get: (id: number) => backendApi.get<KnowledgeBaseMaintenance>(`${MAINTENANCE_PATH}/${id}`),

  stats: (id: number) => backendApi.get<KnowledgeBaseStats>(`${MAINTENANCE_PATH}/${id}/stats`),

  documents: (id: number) =>
    backendApi.get<PageResult<KnowledgeDocument>>(
      `${MAINTENANCE_PATH}/${id}/documents?page=0&size=200`
    ),

  projection: (id: number) =>
    backendApi.get<GraphProjectionStatus>(`${KNOWLEDGE_PATH}/${id}/graph-projection/status`),

  retryDocument: (id: number, documentId: number) =>
    backendApi.post<KnowledgeDocument>(`${MAINTENANCE_PATH}/${id}/documents/${documentId}/retry`),

  rebuildProjection: (id: number, requestKey: string) =>
    backendApi.post<GraphProjectionStatus>(
      `${KNOWLEDGE_PATH}/${id}/graph-projection/rebuild`,
      undefined,
      { headers: { "Idempotency-Key": requestKey } }
    )
}

const adminKnowledgeKeys = {
  all: ["admin", "knowledge"] as const,
  list: (params: KnowledgeMaintenanceListParams) => ["admin", "knowledge", "list", params] as const,
  detail: (id: number) => ["admin", "knowledge", id] as const,
  stats: (id: number) => ["admin", "knowledge", id, "stats"] as const,
  documents: (id: number) => ["admin", "knowledge", id, "documents"] as const,
  projection: (id: number) => ["admin", "knowledge", id, "projection"] as const
}

/** 查询租户范围内的知识库运维列表。 */
export function useAdminKnowledgeList(params: KnowledgeMaintenanceListParams = {}) {
  return useQuery({
    queryKey: adminKnowledgeKeys.list(params),
    queryFn: () => adminKnowledgeApi.list(params)
  })
}

/** 查询知识库运维详情。 */
export function useAdminKnowledge(id: number) {
  return useQuery({
    queryKey: adminKnowledgeKeys.detail(id),
    queryFn: () => adminKnowledgeApi.get(id),
    enabled: Number.isFinite(id)
  })
}

/** 查询知识库运维统计。 */
export function useAdminKnowledgeStats(id: number) {
  return useQuery({
    queryKey: adminKnowledgeKeys.stats(id),
    queryFn: () => adminKnowledgeApi.stats(id),
    enabled: Number.isFinite(id)
  })
}

/** 查询知识库运维文档列表。 */
export function useAdminKnowledgeDocuments(id: number) {
  return useQuery({
    queryKey: adminKnowledgeKeys.documents(id),
    queryFn: () => adminKnowledgeApi.documents(id),
    enabled: Number.isFinite(id)
  })
}

/** 查询 Neo4j 投影 checkpoint。 */
export function useAdminKnowledgeProjection(id: number) {
  return useQuery({
    queryKey: adminKnowledgeKeys.projection(id),
    queryFn: () => adminKnowledgeApi.projection(id),
    enabled: Number.isFinite(id),
    retry: false
  })
}

/** 重试失败文档并刷新知识库运维数据。 */
export function useAdminRetryKnowledgeDocument(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (documentId: number) => adminKnowledgeApi.retryDocument(id, documentId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: adminKnowledgeKeys.documents(id) }),
        queryClient.invalidateQueries({ queryKey: adminKnowledgeKeys.stats(id) }),
        queryClient.invalidateQueries({ queryKey: adminKnowledgeKeys.all })
      ])
    }
  })
}

/** 触发幂等图投影重建并刷新 checkpoint。 */
export function useAdminRebuildKnowledgeProjection(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (requestKey: string) => adminKnowledgeApi.rebuildProjection(id, requestKey),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: adminKnowledgeKeys.projection(id) })
    }
  })
}
