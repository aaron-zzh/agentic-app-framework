/**
 * 项目任务画布布局（纯函数）：
 * - 总览：左列项目，右列各项目最近的根执行记录，连线按执行状态着色，运行中的流动。
 * - 下钻：一条根执行记录的执行树，按父子层级从左到右排布。
 *
 * AIGC 执行状态映射到运行步骤状态：等待绑定/排队 → 等待中，运行中 → 运行中，
 * 成功/部分成功 → 已完成，失败/取消 → 失败。
 *
 * @example
 * const { nodes, edges } = buildProjectRunsGraph(projects, runs)
 * const tree = buildRunTreeGraph(rootRun, descendants)
 * @author AaronZZH & Kiro
 */

import type { Edge } from "@xyflow/react"
import type { AigcExecutionStatus, AigcExecutionTargetType } from "@/lib/api/rest/ai/aigc/execution"
import { RUN_STATUS_STROKE } from "./canvas-palette"
import type {
  ProjectFlowNode,
  ProjectRunItem,
  ProjectRunProject,
  ProjectRunsFlowNode,
  RunStepFlowNode,
  RunStepIcon,
  RunStepStatus
} from "./types"

/** 总览最多展示的项目数与每个项目的执行记录数 */
const MAX_PROJECTS = 6
const MAX_RUNS_PER_PROJECT = 4

const PROJECT_X = 0
const RUN_X = 360
const PROJECT_HEIGHT = 64
const RUN_ROW_HEIGHT = 112
const PROJECT_GAP = 36
const TREE_COLUMN_WIDTH = 270
const TREE_ROW_HEIGHT = 112

const STATUS_MAP: Record<AigcExecutionStatus, RunStepStatus> = {
  PENDING_BIND: "waiting",
  PENDING: "waiting",
  RUNNING: "running",
  PARTIALLY_SUCCEEDED: "completed",
  SUCCEEDED: "completed",
  FAILED: "failed",
  CANCELED: "failed"
}

const TARGET_ICONS: Record<AigcExecutionTargetType, RunStepIcon> = {
  AGENT: "coordinator",
  TOOL: "tool",
  WORKFLOW: "plan",
  UNRESOLVED: "review"
}

const TARGET_LABELS: Record<AigcExecutionTargetType, string> = {
  AGENT: "智能体",
  TOOL: "工具",
  WORKFLOW: "工作流",
  UNRESOLVED: "待绑定"
}

/** 秒数格式化：59s 以内显示秒，否则"1m 05s" */
function formatSeconds(seconds: number): string {
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  return `${minutes}m ${String(seconds % 60).padStart(2, "0")}s`
}

function secondsBetween(start: string, end: Date): number {
  return Math.max(0, Math.round((end.getTime() - new Date(start).getTime()) / 1000))
}

function timeLabelOf(run: ProjectRunItem, status: RunStepStatus, now: Date): string {
  switch (status) {
    case "running":
      return run.startTime
        ? `已运行 ${formatSeconds(secondsBetween(run.startTime, now))}`
        : "运行中"
    case "completed":
      return run.startTime && run.endTime
        ? `耗时 ${formatSeconds(secondsBetween(run.startTime, new Date(run.endTime)))}`
        : "已完成"
    case "failed":
      return run.status === "CANCELED" ? "已取消" : "执行失败"
    case "awaiting":
      return "等待确认"
    case "waiting":
      return run.status === "PENDING_BIND" ? "等待绑定" : "排队中"
  }
}

function runNode(
  run: ProjectRunItem,
  position: { x: number; y: number },
  now: Date
): RunStepFlowNode {
  const status = STATUS_MAP[run.status]
  return {
    id: `run:${run.id}`,
    type: "runStep",
    position,
    data: {
      title: run.title,
      actor: [TARGET_LABELS[run.targetType], run.roleProfileCode].filter(Boolean).join(" · "),
      icon: TARGET_ICONS[run.targetType],
      status,
      timeLabel: timeLabelOf(run, status, now),
      onApprove: null,
      onReject: null
    }
  }
}

function statusEdge(id: string, source: string, target: string, status: RunStepStatus): Edge {
  const flowing = status === "running"
  return {
    id,
    source,
    target,
    animated: flowing,
    style: {
      stroke: RUN_STATUS_STROKE[status],
      strokeWidth: flowing ? 2 : 1.5,
      strokeDasharray: status === "failed" ? "5 4" : undefined,
      opacity: status === "waiting" ? 0.4 : 0.85
    }
  }
}

/** 节点 id 中的执行记录 id；不是执行记录节点时返回 null */
export function runIdOfNode(nodeId: string): number | null {
  if (!nodeId.startsWith("run:")) return null
  const id = Number(nodeId.slice(4))
  return Number.isFinite(id) ? id : null
}

/** 总览：项目 → 根执行记录 */
export function buildProjectRunsGraph(
  projects: readonly ProjectRunProject[],
  runs: readonly ProjectRunItem[],
  hrefOf: (projectId: number) => string | null,
  now: Date = new Date()
): { nodes: ProjectRunsFlowNode[]; edges: Edge[] } {
  const runsByProject = new Map<number, ProjectRunItem[]>()
  for (const run of runs) {
    const list = runsByProject.get(run.projectId) ?? []
    list.push(run)
    runsByProject.set(run.projectId, list)
  }
  const names = new Map(projects.map((project) => [project.id, project.name]))
  // 有运行中记录的项目排前面
  const projectIds = Array.from(runsByProject.keys())
    .sort((left, right) => {
      const running = (id: number) =>
        (runsByProject.get(id) ?? []).some((run) => run.status === "RUNNING") ? 1 : 0
      return running(right) - running(left)
    })
    .slice(0, MAX_PROJECTS)

  const blockHeights = projectIds.map((id) => {
    const count = Math.min(MAX_RUNS_PER_PROJECT, runsByProject.get(id)?.length ?? 0)
    return Math.max(PROJECT_HEIGHT, count * RUN_ROW_HEIGHT)
  })
  const total =
    blockHeights.reduce((sum, height) => sum + height, 0) +
    Math.max(0, projectIds.length - 1) * PROJECT_GAP
  let blockY = -total / 2

  const nodes: ProjectRunsFlowNode[] = []
  const edges: Edge[] = []
  projectIds.forEach((projectId, index) => {
    const projectRuns = (runsByProject.get(projectId) ?? []).slice(0, MAX_RUNS_PER_PROJECT)
    const height = blockHeights[index] ?? PROJECT_HEIGHT
    const projectNode: ProjectFlowNode = {
      id: `project:${projectId}`,
      type: "project",
      position: { x: PROJECT_X, y: blockY + (height - PROJECT_HEIGHT) / 2 },
      data: {
        name: names.get(projectId) ?? `项目 #${projectId}`,
        runCount: runsByProject.get(projectId)?.length ?? 0,
        runningCount: projectRuns.filter((run) => run.status === "RUNNING").length,
        href: hrefOf(projectId)
      }
    }
    nodes.push(projectNode)
    const runsHeight = projectRuns.length * RUN_ROW_HEIGHT
    projectRuns.forEach((run, row) => {
      const node = runNode(
        run,
        { x: RUN_X, y: blockY + (height - runsHeight) / 2 + row * RUN_ROW_HEIGHT },
        now
      )
      nodes.push(node)
      edges.push(
        statusEdge(`${projectNode.id}->${node.id}`, projectNode.id, node.id, node.data.status)
      )
    })
    blockY += height + PROJECT_GAP
  })

  return { nodes, edges }
}

/** 下钻：一条根执行记录的执行树，按层级从左到右 */
export function buildRunTreeGraph(
  root: ProjectRunItem,
  descendants: readonly ProjectRunItem[],
  now: Date = new Date()
): { nodes: RunStepFlowNode[]; edges: Edge[] } {
  const children = new Map<number, ProjectRunItem[]>()
  for (const run of descendants) {
    const parent = run.parentId ?? root.id
    const list = children.get(parent) ?? []
    list.push(run)
    children.set(parent, list)
  }

  // 广度优先分层
  const levels: ProjectRunItem[][] = []
  const visited = new Set<number>()
  let frontier = [root]
  while (frontier.length > 0) {
    const level = frontier.filter((run) => !visited.has(run.id))
    for (const run of level) visited.add(run.id)
    if (level.length === 0) break
    levels.push(level)
    frontier = level.flatMap((run) => children.get(run.id) ?? [])
  }

  const nodes: RunStepFlowNode[] = []
  const edges: Edge[] = []
  levels.forEach((level, depth) => {
    level.forEach((run, row) => {
      const node = runNode(
        run,
        { x: depth * TREE_COLUMN_WIDTH, y: (row - (level.length - 1) / 2) * TREE_ROW_HEIGHT },
        now
      )
      nodes.push(node)
      if (depth > 0) {
        const parentId = `run:${run.parentId ?? root.id}`
        edges.push(statusEdge(`${parentId}->${node.id}`, parentId, node.id, node.data.status))
      }
    })
  })

  return { nodes, edges }
}
