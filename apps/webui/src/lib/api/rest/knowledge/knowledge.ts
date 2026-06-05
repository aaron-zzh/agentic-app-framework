/**
 * 知识库 API 客户端
 * @author AaronZZH & Kiro
 */

import type {
  CreateKnowledgeBaseInput,
  GraphData,
  KnowledgeBase,
  KnowledgeBaseStats,
  KnowledgeDocument,
  SearchResponse
} from "@/lib/types/knowledge"
import { buildApiUrl } from "../../config"
import { backendApi } from "../backend-client"
import { buildQuery, type ListParams, type PageResult, request } from "../entity/crud"

const API_PATH = "/knowledge-bases"

export const knowledgeApi = {
  /** 知识库列表 */
  list: (params: ListParams = {}) =>
    backendApi.get<PageResult<KnowledgeBase>>(`${API_PATH}${buildQuery(params)}`),

  /** 知识库详情 */
  get: (id: string) => request<KnowledgeBase>(`${API_PATH}/${id}`),

  /** 创建知识库 */
  create: (data: CreateKnowledgeBaseInput) =>
    request<KnowledgeBase>(API_PATH, { method: "POST", body: JSON.stringify(data) }),

  /** 更新知识库 */
  update: (id: string, data: Partial<CreateKnowledgeBaseInput>) =>
    request<KnowledgeBase>(`${API_PATH}/${id}`, { method: "PUT", body: JSON.stringify(data) }),

  /** 删除知识库 */
  delete: (id: string) => request<void>(`${API_PATH}/${id}`, { method: "DELETE" }),

  /** 知识库统计 */
  stats: (id: string) => request<KnowledgeBaseStats>(`${API_PATH}/${id}/stats`),

  /** 文档列表 */
  documents: (id: string, params: ListParams = {}) =>
    backendApi.get<PageResult<KnowledgeDocument>>(
      `${API_PATH}/${id}/documents${buildQuery(params)}`
    ),

  /** 图谱数据 */
  graph: (id: string) => request<GraphData>(`${API_PATH}/${id}/graph`),

  /** 检索 */
  search: (
    id: string,
    params: { query: string; topK?: number; threshold?: number; mode?: string }
  ) =>
    request<SearchResponse>(`${API_PATH}/${id}/search`, {
      method: "POST",
      body: JSON.stringify(params)
    }),

  /** 上传文档 */
  uploadDocument: (id: string, file: File, onProgress?: (pct: number) => void) => {
    return new Promise<KnowledgeDocument>((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.open("POST", buildApiUrl(`${API_PATH}/${id}/documents`))

      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable && onProgress) {
          onProgress(Math.round((e.loaded / e.total) * 100))
        }
      }

      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          const json = JSON.parse(xhr.responseText)
          resolve(json.data as KnowledgeDocument)
        } else {
          reject(new Error(`上传失败: ${xhr.statusText}`))
        }
      }

      xhr.onerror = () => reject(new Error("网络错误"))

      const formData = new FormData()
      formData.append("file", file)
      xhr.send(formData)
    })
  }
}
