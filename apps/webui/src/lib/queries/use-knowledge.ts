/**
 * 知识库相关 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { ListParams } from "@/lib/api/rest/entity/crud"
import { knowledgeApi } from "@/lib/api/rest/knowledge/knowledge"
import type { CreateKnowledgeBaseInput } from "@/lib/types/knowledge"

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
