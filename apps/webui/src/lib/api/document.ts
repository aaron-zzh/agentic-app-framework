/**
 * 文档管理 API 客户端
 * @author AaronZZH & Kiro
 */
import { request } from "./client"
import type { DocTreeNode, Document, DocSearchResult, DocRelationGraph } from "@/lib/types/document"

const BASE = "/docs"

export const documentApi = {
  /** 获取文档树 */
  tree: () => request<DocTreeNode[]>(`${BASE}/tree`),

  /** 获取文档详情 */
  get: (id: number) => request<Document>(`${BASE}/${id}`),

  /** 更新文档内容 */
  update: (id: number, content: string) =>
    request<Document>(`${BASE}/${id}`, {
      method: "PUT",
      body: JSON.stringify({ content }),
    }),

  /** 触发全量导入 */
  import: () => request<number>(`${BASE}/import`, { method: "POST" }),

  /** 获取关系图 */
  relations: (id: number) => request<DocRelationGraph>(`${BASE}/${id}/relations`),

  /** 全文检索 */
  search: (q: string) => request<DocSearchResult[]>(`${BASE}/search?q=${encodeURIComponent(q)}`),
}
