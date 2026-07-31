/**
 * 知识库相关类型定义
 * @author AaronZZH & Kiro
 */

/** 知识库启用状态。 */
export type KnowledgeBaseStatus = 0 | 1

/** 知识文档处理状态：待处理、处理中、已完成、失败。 */
export type KnowledgeDocumentStatus = 0 | 1 | 2 | 3

/** 知识检索模式。 */
export type KnowledgeSearchMode = "vector" | "keyword" | "hybrid"

/** 知识库。 */
export interface KnowledgeBase {
  id: number
  name: string
  description: string | null
  embeddingModel: string | null
  chunkStrategy: "fixed" | "recursive" | "semantic"
  chunkSize: number
  chunkOverlap: number
  status: KnowledgeBaseStatus
  documentCount: number
  createTime: string
  updateTime: string
}

/** 知识库文档。 */
export interface KnowledgeDocument {
  id: number
  knowledgeBaseId: number
  title: string
  filePath: string
  fileType: string
  fileSize: number
  contentHash: string | null
  status: KnowledgeDocumentStatus
  errorMessage: string | null
  chunkCount: number
  createTime: string
  updateTime: string
}

/** 知识库统计。 */
export interface KnowledgeBaseStats {
  documentCount: number
  chunkCount: number
  embeddingCount: number
  totalSize: number
}

/** 知识库段落（分块）。 */
export interface KnowledgeSegment {
  id: number
  documentId: number
  knowledgeBaseId: number
  content: string
  position: number
  wordCount: number
  enabled: boolean
  createTime: string
  updateTime: string
}

/** 创建知识库入参。 */
export interface CreateKnowledgeBaseInput {
  name: string
  description?: string
  embeddingModel?: string
  chunkStrategy?: "fixed" | "recursive" | "semantic"
  chunkSize?: number
  chunkOverlap?: number
}

/** 图谱节点。 */
export interface GraphNode {
  id: string
  label: string
  type: string
  description: string | null
  sourceDocumentId: number | null
}

/** 图谱边。 */
export interface GraphEdge {
  id: string
  source: string
  target: string
  label: string
  confidence: number | null
  sourceDocumentId: number | null
}

/** 图谱数据。 */
export interface GraphData {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

/** 检索请求。 */
export interface SearchRequest {
  query: string
  topK: number
  threshold: number
  mode: KnowledgeSearchMode
}

/** 检索结果条目。 */
export interface SearchResultItem {
  content: string
  score: number
  source: string
  metadata: Record<string, unknown>
}

/** 检索响应。 */
export interface SearchResponse {
  results: SearchResultItem[]
}
