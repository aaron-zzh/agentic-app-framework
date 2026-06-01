/**
 * 开发文档 API 客户端（对接 /api/autodev/docs）
 * @author AaronZZH & Kiro
 */

import type { DocRelationGraph, DocSearchResult, DocTreeNode, Document } from "@/lib/types/document"
import { request } from "../entity/crud"

const BASE = "/autodev/docs"

export const autodevDocApi = {
  tree: () => request<DocTreeNode[]>(`${BASE}/tree`),
  get: (id: number) => request<Document>(`${BASE}/${id}`),
  create: (dto: { title: string; filePath: string; docType?: string; content?: string }) =>
    request<Document>(`${BASE}`, { method: "POST", body: JSON.stringify(dto) }),
  update: (id: number, content: string) =>
    request<Document>(`${BASE}/${id}`, { method: "PUT", body: content }),
  import: () => request<number>(`${BASE}/import`, { method: "POST" }),
  relations: (id: number) => request<DocRelationGraph>(`${BASE}/${id}/relations`),
  search: (q: string) => request<DocSearchResult[]>(`${BASE}/search?q=${encodeURIComponent(q)}`)
}
