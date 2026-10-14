/**
 * TaskPlanGraph——任务计划 DAG 的只读可视化（React Flow）。
 *
 * 节点按依赖深度分层、状态着色；点击节点通过 onSelect 通知上层展示详情。
 *
 * @example
 * <TaskPlanGraph nodes={plan.nodes} selectedId={selected} onSelect={setSelected} />
 * @author AaronZZH & Kiro
 */

"use client"

import { Background, Controls, type Edge, type Node, ReactFlow } from "@xyflow/react"
import { useCallback, useMemo } from "react"
import "@xyflow/react/dist/style.css"
import type { TaskNodeDetails, TaskNodeStatus } from "@/lib/api/rest/ai"

const NODE_STATUS_LABEL: Record<TaskNodeStatus, string> = {
  PENDING: "待执行",
  READY: "就绪",
  CLAIMED: "已领取",
  RUNNING: "执行中",
  AWAITING_AUTHORIZATION: "等待授权",
  AWAITING_CLARIFICATION: "等待澄清",
  PAUSED: "已暂停",
  VERIFYING: "验证中",
  COMPLETED: "已完成",
  RETRYABLE: "待重试",
  FAILED: "失败",
  CANCELED: "已取消",
  BLOCKED: "阻塞"
}

const NODE_STATUS_BORDER: Partial<Record<TaskNodeStatus, string>> = {
  COMPLETED: "var(--primary)",
  RUNNING: "var(--primary)",
  VERIFYING: "var(--primary)",
  FAILED: "var(--destructive)",
  AWAITING_AUTHORIZATION: "var(--chart-4)",
  AWAITING_CLARIFICATION: "var(--chart-4)"
}

const COLUMN_WIDTH = 230
const ROW_HEIGHT = 90

/** 按依赖的最长路径计算分层深度；依赖不在计划内时按无依赖处理，遇环兜底为 0。 */
function layerDepths(nodes: TaskNodeDetails[]): Map<string, number> {
  const byId = new Map(nodes.map((node) => [node.nodeId, node]))
  const depths = new Map<string, number>()
  const visiting = new Set<string>()

  function depthOf(nodeId: string): number {
    const known = depths.get(nodeId)
    if (known !== undefined) return known
    const node = byId.get(nodeId)
    if (node === undefined || visiting.has(nodeId)) return 0
    visiting.add(nodeId)
    const depth = node.dependsOn
      .filter((dependency) => byId.has(dependency))
      .reduce((max, dependency) => Math.max(max, depthOf(dependency) + 1), 0)
    visiting.delete(nodeId)
    depths.set(nodeId, depth)
    return depth
  }

  for (const node of nodes) depthOf(node.nodeId)
  return depths
}

interface TaskPlanGraphProps {
  nodes: TaskNodeDetails[]
  selectedId: string | null
  onSelect: (nodeId: string) => void
}

export function TaskPlanGraph({ nodes, selectedId, onSelect }: TaskPlanGraphProps) {
  const { flowNodes, flowEdges } = useMemo(() => {
    const depths = layerDepths(nodes)
    const rowInLayer = new Map<number, number>()
    const flowNodes: Node[] = nodes.map((node) => {
      const depth = depths.get(node.nodeId) ?? 0
      const row = rowInLayer.get(depth) ?? 0
      rowInLayer.set(depth, row + 1)
      return {
        id: node.nodeId,
        position: { x: depth * COLUMN_WIDTH, y: row * ROW_HEIGHT },
        data: { label: `${node.nodeId}\n${NODE_STATUS_LABEL[node.status]}` },
        selected: node.nodeId === selectedId,
        style: {
          whiteSpace: "pre-line",
          fontSize: 12,
          borderRadius: 8,
          border: `1px solid ${NODE_STATUS_BORDER[node.status] ?? "var(--border)"}`,
          background: "var(--card)",
          color: "var(--card-foreground)"
        }
      }
    })
    const status = new Map(nodes.map((node) => [node.nodeId, node.status]))
    const flowEdges: Edge[] = nodes.flatMap((node) =>
      node.dependsOn
        .filter((dependency) => status.has(dependency))
        .map((dependency) => ({
          id: `${dependency}->${node.nodeId}`,
          source: dependency,
          target: node.nodeId,
          animated: node.status === "RUNNING"
        }))
    )
    return { flowNodes, flowEdges }
  }, [nodes, selectedId])

  const handleNodeClick = useCallback(
    (_event: React.MouseEvent, node: Node) => onSelect(node.id),
    [onSelect]
  )

  return (
    <section className="h-72 w-full rounded-md border" aria-label="任务计划依赖图">
      <ReactFlow
        nodes={flowNodes}
        edges={flowEdges}
        onNodeClick={handleNodeClick}
        nodesDraggable={false}
        nodesConnectable={false}
        fitView
      >
        <Background />
        <Controls showInteractive={false} />
      </ReactFlow>
    </section>
  )
}
