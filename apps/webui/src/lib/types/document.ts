/**
 * 文档管理模块类型定义
 * @author AaronZZH & Kiro
 */

/** 文档树节点 */
export interface DocTreeNode {
  id: number | null
  name: string
  title: string | null
  path: string
  isDirectory: boolean
  docType: string | null
  currentVersionId: number | null
  publish: string | null
  children: DocTreeNode[]
}

/** 文档详情 */
export interface Document {
  id: number
  currentVersionId: number
  currentVersionNo: number
  publishedVersionId: number | null
  title: string
  filePath: string | null
  content: string
  docType: string
  status: string
  /** draft | published */
  publish: string
  sourceFileId: number | null
  createTime: string
  updateTime: string
}

export type DocumentVersionSource = "MANUAL" | "AIGC" | "IMPORT" | "RESTORE"

export interface DocumentVersion {
  id: number
  documentId: number
  versionNo: number
  parentVersionId: number | null
  title: string
  content: string
  format: "MARKDOWN" | "HTML" | "PLAIN_TEXT" | "JSON"
  checksum: string
  sourceType: DocumentVersionSource
  sourceRef: string | null
  changeSummary: string | null
  createdBy: number | null
  createdByType: string | null
  createdTime: string
}

export interface DocumentDiffLine {
  type: "CONTEXT" | "ADD" | "DELETE"
  oldLine: number | null
  newLine: number | null
  content: string
}

export interface DocumentDiffHunk {
  oldStart: number
  oldCount: number
  newStart: number
  newCount: number
  lines: DocumentDiffLine[]
}

export interface DocumentDiff {
  documentId: number
  baseVersionId: number
  targetVersionId: number
  hunks: DocumentDiffHunk[]
}

/** 全文检索结果 */
export interface DocSearchResult {
  id: number
  title: string
  filePath: string
  snippet: string
}

/** 关系图数据 */
export interface DocRelationGraph {
  nodes: Array<{ id: number; title: string; filePath: string; isCenter: boolean }>
  edges: Array<{ source: number; target: number; type: string }>
}
