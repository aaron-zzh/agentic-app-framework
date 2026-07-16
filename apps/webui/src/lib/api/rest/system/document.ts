/**
 * 文档管理 API 客户端
 * @author AaronZZH & Kiro
 */

import type { DocRelationGraph, DocSearchResult, DocTreeNode, Document } from "@/lib/types/document"
import { backendApi } from "../backend-client"

const BASE = "/docs"

export interface DocListItem {
  id: number
  title: string
  docType: string
  publish: string
  updateTime: string
}

/** 新建文档请求参数 */
export interface DocCreateParams {
  title: string
  filePath?: string
  docType: string
  content?: string
  publish?: string
}

/** 更新文档请求参数 */
export interface DocUpdateParams {
  title?: string
  content?: string
  docType?: string
  publish?: string
}

export const documentApi = {
  /** 获取当前用户文档列表（不含正文） */
  list: () => backendApi.get<DocListItem[]>(`${BASE}/list`),

  /** 获取文档树 */
  tree: () => backendApi.get<DocTreeNode[]>(`${BASE}/tree`),

  /** 获取文档详情 */
  get: (id: number) => backendApi.get<Document>(`${BASE}/${id}`),

  /** 新建文档 */
  create: (params: DocCreateParams) => backendApi.post<Document>(BASE, params),

  /** 更新文档 */
  update: (id: number, params: DocUpdateParams) =>
    backendApi.put<Document>(`${BASE}/${id}`, params),

  /** 发布文档 */
  publish: (id: number) => backendApi.post<Document>(`${BASE}/${id}/publish`),

  /** 取消发布 */
  unpublish: (id: number) => backendApi.post<Document>(`${BASE}/${id}/unpublish`),

  /** 删除文档 */
  delete: (id: number) => backendApi.delete<void>(`${BASE}/${id}`),

  /** 获取已发布文档列表（公开端） */
  published: () => backendApi.get<Document[]>(`${BASE}/published`),

  /** 触发全量导入 */
  import: () => backendApi.post<number>(`${BASE}/import`),

  /** 获取关系图 */
  relations: (id: number) => backendApi.get<DocRelationGraph>(`${BASE}/${id}/relations`),

  /** 全文检索 */
  search: (q: string) =>
    backendApi.get<DocSearchResult[]>(`${BASE}/search?q=${encodeURIComponent(q)}`)
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

export const docKeys = {
  list: ["docs", "list"] as const,
  tree: ["docs", "tree"] as const,
  detail: (id: number) => ["docs", id] as const,
  relations: (id: number) => ["docs", id, "relations"] as const,
  search: (q: string) => ["docs", "search", q] as const,
  published: ["docs", "published"] as const
}

export function useDocList() {
  return useQuery({ queryKey: docKeys.list, queryFn: documentApi.list })
}

export function useDocTree() {
  return useQuery({ queryKey: docKeys.tree, queryFn: documentApi.tree })
}

export function useDocument(id: number | null) {
  return useQuery({
    queryKey: docKeys.detail(id as number),
    queryFn: () => documentApi.get(id as number),
    enabled: id != null && id > 0
  })
}

export function useDocRelations(id: number | null) {
  return useQuery({
    queryKey: docKeys.relations(id as number),
    queryFn: () => documentApi.relations(id as number),
    enabled: id != null
  })
}

export function useDocSearch(q: string) {
  return useQuery({
    queryKey: docKeys.search(q),
    queryFn: () => documentApi.search(q),
    enabled: q.length > 0
  })
}

export function usePublishedDocs() {
  return useQuery({ queryKey: docKeys.published, queryFn: documentApi.published })
}

export function useUpdateDocument() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...params }: { id: number } & DocUpdateParams) =>
      documentApi.update(id, params),
    onSuccess: (_, { id }) => {
      qc.invalidateQueries({ queryKey: docKeys.detail(id) })
      qc.invalidateQueries({ queryKey: docKeys.list })
      qc.invalidateQueries({ queryKey: docKeys.tree })
      qc.invalidateQueries({ queryKey: docKeys.published })
    }
  })
}

export function usePublishDocument() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => documentApi.publish(id),
    onSuccess: (_, id) => {
      qc.invalidateQueries({ queryKey: docKeys.detail(id) })
      qc.invalidateQueries({ queryKey: docKeys.tree })
      qc.invalidateQueries({ queryKey: docKeys.published })
    }
  })
}

export function useUnpublishDocument() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => documentApi.unpublish(id),
    onSuccess: (_, id) => {
      qc.invalidateQueries({ queryKey: docKeys.detail(id) })
      qc.invalidateQueries({ queryKey: docKeys.tree })
      qc.invalidateQueries({ queryKey: docKeys.published })
    }
  })
}

export function useDeleteDocument() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => documentApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: docKeys.list })
      qc.invalidateQueries({ queryKey: docKeys.tree })
      qc.invalidateQueries({ queryKey: docKeys.published })
    }
  })
}

export function useImportDocs() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: documentApi.import,
    onSuccess: () => qc.invalidateQueries({ queryKey: docKeys.tree })
  })
}

export function useCreateDocument() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (params: DocCreateParams) => documentApi.create(params),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: docKeys.list })
      qc.invalidateQueries({ queryKey: docKeys.tree })
      qc.invalidateQueries({ queryKey: docKeys.published })
    }
  })
}
