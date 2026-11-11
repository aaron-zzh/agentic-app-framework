/**
 * 项目任务总览画布：左列 AIGC 项目，右列各项目最近的根执行记录，运行中的连线流动。
 *
 * 点击项目节点进入项目详情；点击执行记录节点下钻查看执行树（onDrill）。
 * 没有执行记录或接口不可用（如无 AIGC License）时显示示例数据。
 * @author AaronZZH & Kiro
 */

"use client"

import type { NodeMouseHandler, NodeTypes } from "@xyflow/react"
import { useRouter } from "next/navigation"
import { useCallback, useMemo } from "react"
import { CanvasFlow } from "./CanvasFlow"
import { CanvasMockBadge } from "./CanvasMockBadge"
import { ProjectNode } from "./ProjectNode"
import { buildProjectRunsGraph, runIdOfNode } from "./project-run-layout"
import { RunStepNode } from "./RunStepNode"
import type { CanvasFrame, ProjectRunsFlowNode } from "./types"
import { useProjectRunsOverview } from "./use-project-runs"

const NODE_TYPES: NodeTypes = { project: ProjectNode, runStep: RunStepNode }

interface ProjectRunsCanvasProps {
  frame: CanvasFrame
  /** 下钻到某条根执行记录 */
  onDrill: (run: { id: number; title: string; mock: boolean }) => void
}

export function ProjectRunsCanvas({ frame, onDrill }: ProjectRunsCanvasProps) {
  const router = useRouter()
  const overview = useProjectRunsOverview()

  const graph = useMemo(
    () =>
      buildProjectRunsGraph(overview.projects, overview.runs, (projectId) =>
        overview.mock ? null : `/studio/projects/${projectId}`
      ),
    [overview.projects, overview.runs, overview.mock]
  )

  const handleNodeClick = useCallback<NodeMouseHandler<ProjectRunsFlowNode>>(
    (_event, node) => {
      if (node.type === "project") {
        if (node.data.href) router.push(node.data.href)
        return
      }
      const runId = runIdOfNode(node.id)
      if (runId !== null) onDrill({ id: runId, title: node.data.title, mock: overview.mock })
    },
    [onDrill, overview.mock, router]
  )

  return (
    <CanvasFlow<ProjectRunsFlowNode>
      nodes={graph.nodes}
      edges={graph.edges}
      nodeTypes={NODE_TYPES}
      frame={frame}
      fitKey={`project:${overview.mock ? "mock" : "real"}:${graph.nodes.length}:${overview.loading}`}
      onNodeClick={handleNodeClick}
      overlay={
        overview.mock ? <CanvasMockBadge hint="创建 AIGC 项目并执行后显示真实运行状态" /> : null
      }
    />
  )
}
