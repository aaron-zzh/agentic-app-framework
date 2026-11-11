/**
 * 项目任务下钻画布：一条根执行记录的执行树，按父子层级从左到右，运行中的连线流动。
 * @author AaronZZH & Kiro
 */

"use client"

import type { NodeTypes } from "@xyflow/react"
import { type ReactNode, useMemo } from "react"
import { CanvasFlow } from "./CanvasFlow"
import { CanvasMockBadge } from "./CanvasMockBadge"
import { buildRunTreeGraph } from "./project-run-layout"
import { RunStepNode } from "./RunStepNode"
import type { CanvasFrame, RunStepFlowNode } from "./types"
import { useProjectRunTree } from "./use-project-runs"

const NODE_TYPES: NodeTypes = { runStep: RunStepNode }

interface RunTreeCanvasProps {
  frame: CanvasFrame
  rootId: number
  mock: boolean
  /** 左上角面包屑 */
  header: ReactNode
}

export function RunTreeCanvas({ frame, rootId, mock, header }: RunTreeCanvasProps) {
  const tree = useProjectRunTree(rootId, mock)
  const graph = useMemo(
    () => (tree.root ? buildRunTreeGraph(tree.root, tree.descendants) : { nodes: [], edges: [] }),
    [tree.root, tree.descendants]
  )

  return (
    <CanvasFlow<RunStepFlowNode>
      nodes={graph.nodes}
      edges={graph.edges}
      nodeTypes={NODE_TYPES}
      frame={frame}
      fitKey={`tree:${rootId}:${graph.nodes.length}`}
      overlay={
        <div className="pointer-events-auto absolute top-0 left-0 flex items-center gap-3">
          {header}
          {mock ? <CanvasMockBadge inline /> : null}
          {tree.loading ? <span className="text-muted-foreground text-xs">加载执行树…</span> : null}
          {tree.error ? <span className="text-rose-300 text-xs">执行树加载失败</span> : null}
        </div>
      }
    />
  )
}
