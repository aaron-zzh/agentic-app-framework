"use client"

/**
 * Three.js 知识图的 React 挂载适配器。
 * @author AaronZZH & Kiro
 */

import { useEffect, useRef } from "react"
import { ThreeGraphEngine } from "./three-graph-engine"
import type { GraphVisualizationProps } from "./types"

/** 将命令式 Three.js 引擎安全挂载到 React 生命周期。 */
export function GraphVisualization<TNodeData = unknown, TEdgeData = unknown>({
  graph,
  dimension = "2.5d",
  layout = "force",
  selectedNodeId,
  className,
  style,
  ariaLabel = "知识图谱可视化",
  onNodePick,
  onNodeHover,
  onError
}: GraphVisualizationProps<TNodeData, TEdgeData>) {
  const containerRef = useRef<HTMLDivElement>(null)
  const engineRef = useRef<ThreeGraphEngine<TNodeData, TEdgeData> | null>(null)
  const graphRef = useRef(graph)
  const selectedNodeIdRef = useRef(selectedNodeId)
  const appliedGraphRef = useRef<typeof graph | null>(null)
  const onNodePickRef = useRef(onNodePick)
  const onNodeHoverRef = useRef(onNodeHover)
  const onErrorRef = useRef(onError)

  graphRef.current = graph
  selectedNodeIdRef.current = selectedNodeId
  onNodePickRef.current = onNodePick
  onNodeHoverRef.current = onNodeHover
  onErrorRef.current = onError

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    let engine: ThreeGraphEngine<TNodeData, TEdgeData> | null = null
    try {
      engine = new ThreeGraphEngine(container, {
        dimension,
        layout,
        onNodePick: (node) => onNodePickRef.current?.(node),
        onNodeHover: (node) => onNodeHoverRef.current?.(node),
        onError: (error) => onErrorRef.current?.(error)
      })
      engine.setGraph(graphRef.current)
      engine.setSelectedNodeId(selectedNodeIdRef.current)
      appliedGraphRef.current = graphRef.current
      engineRef.current = engine
    } catch (cause) {
      engine?.dispose()
      onErrorRef.current?.(toError(cause))
      return
    }

    const mountedEngine = engine
    return () => {
      if (engineRef.current === mountedEngine) engineRef.current = null
      appliedGraphRef.current = null
      mountedEngine.dispose()
    }
  }, [dimension, layout])

  useEffect(() => {
    const engine = engineRef.current
    if (!engine || appliedGraphRef.current === graph) return
    try {
      engine.setGraph(graph)
      appliedGraphRef.current = graph
    } catch (cause) {
      onErrorRef.current?.(toError(cause))
    }
  }, [graph])

  useEffect(() => {
    engineRef.current?.setSelectedNodeId(selectedNodeId)
  }, [selectedNodeId])

  return (
    <div
      ref={containerRef}
      role="img"
      aria-label={ariaLabel}
      className={className}
      data-dimension={dimension}
      data-layout={layout}
      style={{
        height: "100%",
        minHeight: 320,
        overflow: "hidden",
        position: "relative",
        width: "100%",
        ...style
      }}
    />
  )
}

function toError(cause: unknown): Error {
  return cause instanceof Error ? cause : new Error(String(cause))
}
