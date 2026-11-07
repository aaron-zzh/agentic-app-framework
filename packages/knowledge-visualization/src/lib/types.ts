/**
 * 知识可视化引擎的公共数据合同，不包含业务 API 或 Three.js 对象。
 * @author AaronZZH & Kiro
 */

import type { CSSProperties } from "react"

export type GraphDimension = "2d" | "2.5d" | "3d"
export type GraphLayout = "force" | "radial" | "grid"
export type VisualizationColor = string | number
/** 画布所在背景的明暗，决定弱化色与光照强度 */
export type GraphColorScheme = "light" | "dark"

export interface GraphPosition {
  readonly x: number
  readonly y: number
  readonly z: number
}

export interface VisualizationNode<TData = unknown> {
  readonly id: string
  readonly label: string
  readonly position?: GraphPosition
  readonly layer?: number
  readonly color?: VisualizationColor
  readonly size?: number
  readonly data?: TData
}

export interface VisualizationEdge<TData = unknown> {
  readonly id: string
  readonly source: string
  readonly target: string
  /** 关系名称；聚焦节点时显示在关系中点 */
  readonly label?: string
  readonly color?: VisualizationColor
  readonly strength?: number
  readonly data?: TData
}

export interface VisualizationGraph<TNodeData = unknown, TEdgeData = unknown> {
  readonly nodes: readonly VisualizationNode<TNodeData>[]
  readonly edges: readonly VisualizationEdge<TEdgeData>[]
}

export interface LayoutOptions {
  readonly dimension: GraphDimension
  readonly layout: GraphLayout
  readonly spacing: number
  readonly layerGap: number
  readonly iterations?: number
}

export interface ThreeGraphEngineOptions<TNodeData = unknown> {
  readonly dimension?: GraphDimension
  readonly layout?: GraphLayout
  readonly colorScheme?: GraphColorScheme
  readonly background?: VisualizationColor
  readonly nodeSize?: number
  readonly spacing?: number
  readonly layerGap?: number
  readonly maxPixelRatio?: number
  readonly onNodePick?: (node: VisualizationNode<TNodeData> | null) => void
  readonly onNodeHover?: (node: VisualizationNode<TNodeData> | null) => void
  readonly onError?: (error: Error) => void
}

export interface GraphVisualizationProps<TNodeData = unknown, TEdgeData = unknown> {
  readonly graph: VisualizationGraph<TNodeData, TEdgeData>
  readonly dimension?: GraphDimension
  readonly layout?: GraphLayout
  readonly colorScheme?: GraphColorScheme
  readonly selectedNodeId?: string | null
  readonly className?: string
  readonly style?: CSSProperties
  readonly ariaLabel?: string
  readonly onNodePick?: (node: VisualizationNode<TNodeData> | null) => void
  readonly onNodeHover?: (node: VisualizationNode<TNodeData> | null) => void
  readonly onError?: (error: Error) => void
}
