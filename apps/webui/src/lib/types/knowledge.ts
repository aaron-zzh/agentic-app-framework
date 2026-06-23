/**
 * 知识库相关类型定义
 * @author AaronZZH & Kiro
 */

/** 知识库 */
export interface KnowledgeBase {
  id: string
  name: string
  description?: string
  embeddingModel: string
  chunkStrategy: "fixed" | "recursive" | "semantic"
  chunkSize: number
  chunkOverlap: number
  documentCount: number
  createdAt: string
  updatedAt: string
}

/** 知识库文档 */
export interface KnowledgeDocument {
  id: string
  knowledgeBaseId: string
  name: string
  type: string
  size: number
  status: "pending" | "processing" | "completed" | "failed"
  chunkCount: number
  createdAt: string
  updatedAt: string
}

/** 知识库统计 */
export interface KnowledgeBaseStats {
  documentCount: number
  chunkCount: number
  vectorCount: number
  totalSize: number
}

/** 知识库段落（分块） */
export interface KnowledgeSegment {
  id: string
  documentId: string
  knowledgeBaseId: string
  content: string
  position: number
  wordCount: number
  enabled: boolean
  createTime: string
  updateTime: string
}

/** 创建知识库入参 */
export interface CreateKnowledgeBaseInput {
  name: string
  description?: string
  embeddingModel?: string
  chunkStrategy?: "fixed" | "recursive" | "semantic"
  chunkSize?: number
  chunkOverlap?: number
}

/** 图谱节点 */
export interface GraphNode {
  id: string
  label: string
  type: string
}

/** 图谱边 */
export interface GraphEdge {
  id: string
  source: string
  target: string
  label?: string
}

/** 图谱数据 */
export interface GraphData {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

/** 检索结果条目 */
export interface SearchResultItem {
  id: string
  content: string
  score: number
  source: string
  metadata?: Record<string, unknown>
}

/** 检索响应 */
export interface SearchResponse {
  results: SearchResultItem[]
  answer?: string
}
