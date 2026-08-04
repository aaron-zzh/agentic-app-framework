"use client"

/**
 * AAF 知识可视化引擎公共入口。
 *
 * @example
 * ```tsx
 * <GraphVisualization graph={graph} dimension="2.5d" onNodePick={setSelectedNode} />
 * ```
 *
 * @author AaronZZH & Kiro
 */

export { GraphVisualization } from "./lib/graph-visualization"
export { resolveGraphLayout, validateGraph } from "./lib/layout"
export { ThreeGraphEngine } from "./lib/three-graph-engine"
export type {
  GraphDimension,
  GraphLayout,
  GraphPosition,
  GraphVisualizationProps,
  LayoutOptions,
  ThreeGraphEngineOptions,
  VisualizationColor,
  VisualizationEdge,
  VisualizationGraph,
  VisualizationNode
} from "./lib/types"
