/**
 * 助理总览画布：助理 → 角色 → 技能 → 工具 / 知识库的静态拓扑。
 *
 * - 点击助理或角色节点下钻查看其运行中的任务（onDrill）；点击技能、知识库节点跳到对应管理页。
 * - 点击"系统技能""系统工具"或"+N"节点就地展开或收起。
 * - 角色按分类用虚线框分组：点框标题折叠为一张卡片，点卡片展开。
 * - 悬停角色或资源停留 500ms 后高亮其关联链路，其余节点与连线淡化；鼠标快速掠过不触发。
 * - 没有默认助理或没有绑定角色时显示示例拓扑，并在左上角标"示例数据"。
 * @author AaronZZH & Kiro
 */

"use client"

import type { NodeMouseHandler, NodeTypes } from "@xyflow/react"
import { useRouter } from "next/navigation"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { type TaskNodeStatus, useActiveTasks } from "@/lib/api/rest/ai/task"
import { AssistantNode } from "./AssistantNode"
import { CanvasFlow } from "./CanvasFlow"
import { CanvasMockBadge } from "./CanvasMockBadge"
import { highlightCollaborationGraph } from "./collaboration-layout"
import { ResourceNode } from "./ResourceNode"
import { RoleGroupNode } from "./RoleGroupNode"
import { RoleNode } from "./RoleNode"
import type { CanvasFrame, CollaborationFlowNode } from "./types"
import { useCollaborationGraph } from "./use-collaboration-graph"

const NODE_TYPES: NodeTypes = {
  assistant: AssistantNode,
  role: RoleNode,
  resource: ResourceNode,
  roleGroup: RoleGroupNode
}

/** 悬停多久才高亮关联链路 */
const HOVER_ENTER_DELAY_MS = 500
/** 离开节点后多久取消高亮；期间移到另一个节点则直接切换 */
const HOVER_LEAVE_DELAY_MS = 150
/** 已终结的节点状态，不计入角色运行节点数 */
const TERMINAL_NODE_STATUSES: ReadonlySet<TaskNodeStatus> = new Set([
  "COMPLETED",
  "FAILED",
  "CANCELED"
])

interface CollaborationCanvasProps {
  frame: CanvasFrame
  /** 下钻到某个助理或角色，参数为其名称 */
  onDrill: (name: string) => void
}

/** 在集合中切换某个 id，返回新集合 */
function toggle(current: ReadonlySet<string>, id: string): ReadonlySet<string> {
  const next = new Set(current)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  return next
}

export function CollaborationCanvas({ frame, onDrill }: CollaborationCanvasProps) {
  const router = useRouter()
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(() => new Set())
  const [collapsedCategories, setCollapsedCategories] = useState<ReadonlySet<string>>(
    () => new Set()
  )
  const [hoveredId, setHoveredId] = useState<string | null>(null)
  const layoutOptions = useMemo(
    () => ({ expanded, collapsedCategories }),
    [expanded, collapsedCategories]
  )
  const { graph, mock, loading } = useCollaborationGraph(layoutOptions)
  const { data: activeTasks } = useActiveTasks()

  // 助理节点显示当前用户未终结的任务数；角色节点显示该角色执行中的未终结节点数，均从 query 派生
  const baseGraph = useMemo(() => {
    const tasks = activeTasks ?? []
    const nodesByRole = new Map<string, number>()
    for (const task of tasks) {
      for (const node of task.plan?.nodes ?? []) {
        if (!node.roleKey || TERMINAL_NODE_STATUSES.has(node.status)) continue
        nodesByRole.set(node.roleKey, (nodesByRole.get(node.roleKey) ?? 0) + 1)
      }
    }
    return {
      nodes: graph.nodes.map((node): CollaborationFlowNode => {
        if (node.type === "assistant") {
          return { ...node, data: { ...node.data, runningTasks: tasks.length } }
        }
        if (node.type === "role") {
          const runningNodes = node.data.roleKey ? (nodesByRole.get(node.data.roleKey) ?? 0) : 0
          return { ...node, data: { ...node.data, runningNodes } }
        }
        return node
      }),
      edges: graph.edges
    }
  }, [graph, activeTasks])
  const { nodes, edges } = useMemo(
    () => highlightCollaborationGraph(baseGraph, hoveredId),
    [baseGraph, hoveredId]
  )

  const handleNodeClick = useCallback<NodeMouseHandler<CollaborationFlowNode>>(
    (event, node) => {
      if (node.type === "assistant" || node.type === "role") {
        onDrill(node.data.name)
        return
      }
      if (node.type === "roleGroup") {
        // 展开的分类框只响应标题点击，框内空白处不折叠
        const onToggle =
          node.data.collapsed ||
          (event.target instanceof Element && event.target.closest("[data-role-group-toggle]"))
        if (onToggle) setCollapsedCategories((current) => toggle(current, node.id))
        return
      }
      if (node.data.expanded !== undefined) {
        setExpanded((current) => toggle(current, node.id))
        return
      }
      if (node.data.href) router.push(node.data.href)
    },
    [onDrill, router]
  )
  // 悬停防抖：停留一段时间才高亮，离开后稍等再取消，鼠标快速掠过节点时不闪烁
  const hoverTimerRef = useRef<number | null>(null)
  const scheduleHover = useCallback((id: string | null, delay: number) => {
    if (hoverTimerRef.current !== null) window.clearTimeout(hoverTimerRef.current)
    hoverTimerRef.current = window.setTimeout(() => {
      hoverTimerRef.current = null
      setHoveredId(id)
    }, delay)
  }, [])
  useEffect(
    () => () => {
      if (hoverTimerRef.current !== null) window.clearTimeout(hoverTimerRef.current)
    },
    []
  )
  const handleNodeMouseEnter = useCallback<NodeMouseHandler<CollaborationFlowNode>>(
    (_event, node) => scheduleHover(node.id, HOVER_ENTER_DELAY_MS),
    [scheduleHover]
  )
  const handleNodeMouseLeave = useCallback<NodeMouseHandler<CollaborationFlowNode>>(
    () => scheduleHover(null, HOVER_LEAVE_DELAY_MS),
    [scheduleHover]
  )

  return (
    <CanvasFlow<CollaborationFlowNode>
      nodes={nodes}
      edges={edges}
      nodeTypes={NODE_TYPES}
      frame={frame}
      fitKey={`assistant:${mock ? "mock" : "real"}:${loading}`}
      onNodeClick={handleNodeClick}
      onNodeMouseEnter={handleNodeMouseEnter}
      onNodeMouseLeave={handleNodeMouseLeave}
      overlay={
        mock && !loading ? <CanvasMockBadge hint="为助理绑定角色后显示真实协作关系" /> : null
      }
    />
  )
}
