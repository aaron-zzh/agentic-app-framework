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
  publish: string | null
  children: DocTreeNode[]
}

/** 文档详情 */
export interface Document {
  id: number
  title: string
  filePath: string
  content: string
  docType: string
  status: string
  /** draft | published */
  publish: string
  createTime: string
  updateTime: string
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
