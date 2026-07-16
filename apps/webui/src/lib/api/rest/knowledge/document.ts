/**
 * 文档管理 API 客户端（业务文档，对接 /api/docs）
 * @author AaronZZH & Kiro
 */

import type { DocSearchResult, DocTreeNode, Document } from "@/lib/types/document"
import { backendApi } from "../backend-client"

const BASE = "/docs"

export const docApi = {
  tree: () => backendApi.get<DocTreeNode[]>(`${BASE}/tree`),
  get: (id: number) => backendApi.get<Document>(`${BASE}/${id}`),
  create: (dto: { title: string; filePath?: string; docType?: string; content?: string }) =>
    backendApi.post<Document>(`${BASE}`, dto),
  update: (id: number, content: string) => backendApi.put<Document>(`${BASE}/${id}`, { content }),
  search: (q: string) =>
    backendApi.get<DocSearchResult[]>(`${BASE}/search?q=${encodeURIComponent(q)}`)
}
