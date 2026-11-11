/**
 * Studio 首页画布的 React Flow 外壳：深色点阵背景、可拖动缩放、节点只读，
 * 适应视图时按浮层留出内边距，并在画布右下角提供"适应画布"按钮。
 *
 * fitKey 变化时（如切换任务、数据加载完成）重新适应视图。
 * @author AaronZZH & Kiro
 */

"use client"

import {
  Background,
  BackgroundVariant,
  type Edge,
  type Node,
  type NodeMouseHandler,
  type NodeTypes,
  ReactFlow,
  useReactFlow
} from "@xyflow/react"
import { Scan } from "lucide-react"
import { type ReactNode, useEffect } from "react"
import "@xyflow/react/dist/style.css"
import { Button } from "@/components/ui/button"
import type { CanvasFrame } from "./types"

interface CanvasFlowProps<N extends Node> {
  nodes: N[]
  edges: Edge[]
  nodeTypes: NodeTypes
  frame: CanvasFrame
  /** 变化时重新适应视图 */
  fitKey: string
  onNodeClick?: NodeMouseHandler<N>
  onNodeMouseEnter?: NodeMouseHandler<N>
  onNodeMouseLeave?: NodeMouseHandler<N>
  /** 叠在可视区内的附加内容（示例数据标记、任务切换条等） */
  overlay?: ReactNode
}

/** 视图适应动画时长 */
const FIT_DURATION_MS = 400

/** fitKey 变化时重新适应视图；必须渲染在 ReactFlow 内部才能取到实例 */
function AutoFit({ fitKey, frame }: { fitKey: string; frame: CanvasFrame }) {
  const { fitView } = useReactFlow()
  // biome-ignore lint/correctness/useExhaustiveDependencies: fitKey 只作为重新适应的触发键
  useEffect(() => {
    // 等节点测量完尺寸后再适应
    const timer = window.setTimeout(() => {
      void fitView({ padding: frame.fitPadding, duration: FIT_DURATION_MS })
    }, 60)
    return () => window.clearTimeout(timer)
  }, [fitKey, fitView, frame.fitPadding])
  return null
}

/** "适应画布"按钮贴画布右下角的距离（px） */
const FIT_BUTTON_EDGE = 4

function FitButton({ frame }: { frame: CanvasFrame }) {
  const { fitView } = useReactFlow()
  return (
    <Button
      type="button"
      size="icon-sm"
      variant="outline"
      aria-label="适应画布"
      title="适应画布"
      className="absolute z-10 bg-background/50 backdrop-blur-md"
      style={{ right: FIT_BUTTON_EDGE, bottom: FIT_BUTTON_EDGE }}
      onClick={() => void fitView({ padding: frame.fitPadding, duration: FIT_DURATION_MS })}
    >
      <Scan />
    </Button>
  )
}

export function CanvasFlow<N extends Node>({
  nodes,
  edges,
  nodeTypes,
  frame,
  fitKey,
  onNodeClick,
  onNodeMouseEnter,
  onNodeMouseLeave,
  overlay
}: CanvasFlowProps<N>) {
  return (
    <ReactFlow<N>
      nodes={nodes}
      edges={edges}
      nodeTypes={nodeTypes}
      onNodeClick={onNodeClick}
      onNodeMouseEnter={onNodeMouseEnter}
      onNodeMouseLeave={onNodeMouseLeave}
      fitView
      fitViewOptions={{ padding: frame.fitPadding }}
      minZoom={0.2}
      maxZoom={1.8}
      nodesDraggable={false}
      nodesConnectable={false}
      elementsSelectable={false}
      zoomOnScroll={frame.wheelZoom}
      preventScrolling={frame.wheelZoom}
      zoomOnDoubleClick={false}
      proOptions={{ hideAttribution: true }}
      colorMode="dark"
      className="!bg-transparent"
    >
      <Background
        variant={BackgroundVariant.Dots}
        gap={22}
        size={1}
        color="rgba(148, 163, 184, 0.22)"
      />
      <AutoFit fitKey={fitKey} frame={frame} />
      {/* 可视区：浮层之外的区域，放置标记与切换条 */}
      <div className="pointer-events-none absolute z-10" style={frame.area}>
        {overlay}
      </div>
      <FitButton frame={frame} />
    </ReactFlow>
  )
}
