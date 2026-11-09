/**
 * 知识库相关类型定义
 * @author AaronZZH & Kiro
 */

/** 知识库启用状态。 */
export type KnowledgeBaseStatus = 0 | 1

/** 知识文档处理状态：待处理、处理中、已完成、失败。 */
export type KnowledgeDocumentStatus = 0 | 1 | 2 | 3

/** 知识检索模式。 */
export type KnowledgeSearchMode = "vector" | "keyword" | "graph" | "hybrid"

/** 知识检索通道。 */
export type KnowledgeSearchChannel = "VECTOR" | "KEYWORD" | "GRAPH"

/** 知识库可见范围。 */
export type KnowledgeBaseVisibility = "PRIVATE" | "ORG" | "SYSTEM_PUBLIC"

/** 分块策略：auto 使用默认块大小与重叠，semantic 使用知识库自定义块大小与重叠。 */
export type KnowledgeChunkStrategy = "auto" | "semantic"

/** 知识库。 */
export interface KnowledgeBase {
  id: number
  stableId: string
  name: string
  /** 知识库说明：用户填写，系统不会修改。 */
  description: string | null
  /** 涵盖主题：系统根据已入库文档自动生成，只读。 */
  aiSummary: string | null
  /** 涵盖主题最近更新时间（LocalDateTime 序列化字符串），只读。 */
  aiSummaryUpdatedAt: string | null
  visibility: KnowledgeBaseVisibility
  scopeCode: string | null
  embeddingModel: string | null
  chunkStrategy: KnowledgeChunkStrategy
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
  stableId: string
  knowledgeBaseId: number
  sourceDocumentId: number | null
  sourceDocumentVersionId: number | null
  uploadedBy: number | null
  sourceType: string
  sourceKey: string | null
  sourceUri: string | null
  activeRunId: string | null
  title: string
  /** 文档摘要：系统生成，只读。 */
  summary: string | null
  fileType: string | null
  fileSize: number | null
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

/** 知识文档当前发布代际的分块；内容只读，可启用/停用。 */
export interface KnowledgeChunk {
  id: string
  chunkIndex: number
  content: string
  tokenCount: number
  sectionPath: string | null
  pageNo: number | null
  enabled: boolean
}

/** 创建知识库入参。 */
export interface CreateKnowledgeBaseInput {
  name: string
  description?: string
  visibility?: KnowledgeBaseVisibility
  scopeCode?: string
  chunkStrategy?: KnowledgeChunkStrategy
  chunkSize?: number
  chunkOverlap?: number
}

/** 图谱节点。 */
export interface GraphNode {
  id: string
  name: string
  type: string
  description: string | null
}

/** 图谱边。 */
export interface GraphEdge {
  id: string
  factKey: string
  sourceId: string
  targetId: string
  predicate: string
  confidence: number
  evidenceIds: string[]
}

/** 图谱数据。 */
export interface GraphData {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

/** 可过滤的知识来源类型。 */
export type KnowledgeSourceType = "FILE" | "URL" | "BUSINESS_OBJECT" | "SYSTEM"

/** 三路检索共享的来源过滤。 */
export interface KnowledgeSourceFilters {
  sourceTypes?: KnowledgeSourceType[]
  sourceKeys?: string[]
  documentIds?: string[]
}

/** 授权多库检索请求。 */
export interface SearchRequest {
  query: string
  knowledgeBaseIds?: string[]
  includePublic?: boolean
  knowledgeBaseWeights?: Record<string, number>
  sourceFilters?: KnowledgeSourceFilters
  topK: number
  threshold: number
  mode: KnowledgeSearchMode
}

/** 检索结果的可追溯来源。 */
export interface SearchResultSource {
  knowledgeBaseId: string
  knowledgeBaseName: string
  visibility: KnowledgeBaseVisibility
  documentId: string
  sourceType: KnowledgeSourceType
  sourceKey: string | null
  sourceUri: string | null
  runId: string
  focusChunkId: string
  factIds: string[]
  evidenceIds: string[]
}

/** 检索结果条目。 */
export interface SearchResultItem {
  candidateKey: string
  content: string
  score: number
  matchedChannels: KnowledgeSearchChannel[]
  source: SearchResultSource
}

/** 授权多库检索响应。 */
export interface SearchResponse {
  results: SearchResultItem[]
  searchedKnowledgeBaseIds: string[]
  degradedChannels: KnowledgeSearchChannel[]
}
