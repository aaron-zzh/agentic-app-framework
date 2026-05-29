/**
 * 文档管理 API 客户端（业务文档，对接 /api/docs）
 * @author AaronZZH & Kiro
 */

import type { DocSearchResult, DocTreeNode, Document } from "@/lib/types/document"
import { request } from "./client"

const BASE = "/docs"

export const docApi = {
  tree: () => request<DocTreeNode[]>(`${BASE}/tree`),
  get: (id: number) => request<Document>(`${BASE}/${id}`),
  create: (dto: { title: string; filePath?: string; docType?: string; content?: string }) =>
    request<Document>(`${BASE}`, { method: "POST", body: JSON.stringify(dto) }),
  update: (id: number, content: string) =>
    request<Document>(`${BASE}/${id}`, { method: "PUT", body: JSON.stringify({ content }) }),
  search: (q: string) => request<DocSearchResult[]>(`${BASE}/search?q=${encodeURIComponent(q)}`)
}
