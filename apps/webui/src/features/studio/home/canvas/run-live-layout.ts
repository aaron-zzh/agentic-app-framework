/**
 * 运行实况：由示例任务与推进进度推出步骤状态、任务状态，以及 React Flow 节点与连线（纯函数）。
 *
 * - 阶段（stage）小于当前阶段的步骤已完成；等于当前阶段的步骤运行中（审批步骤为待审批，驳回后为失败）；
 *   其余等待中。
 * - 连线按终点步骤状态着色：运行中、待审批的连线流动；已完成的连线显示处理条数；未开始的置灰。
 *
 * @example
 * const { nodes, edges } = buildRunLiveGraph(task, progress, { onApprove, onReject })
 * @author AaronZZH & Kiro
 */

import type { Edge } from "@xyflow/react"
import { RUN_STATUS_STROKE } from "./canvas-palette"
import type {
  RunStepDef,
  RunStepFlowNode,
  RunStepStatus,
  RunTaskDef,
  RunTaskProgress,
  RunTaskStatus
} from "./types"

/** 推进节拍间隔（毫秒），use-run-live-simulation 与耗时文字共用 */
export const RUN_TICK_MS = 1500

const STAGE_WIDTH = 270
const ROW_HEIGHT = 118

/** 任务的最大阶段号 */
export function maxStageOf(task: RunTaskDef): number {
  return task.steps.reduce((max, step) => Math.max(max, step.stage), 0)
}

/** 当前阶段是否是审批阶段 */
export function isApprovalStage(task: RunTaskDef, stage: number): boolean {
  return task.steps.some((step) => step.stage === stage && step.approval === true)
}

/** 打开首页时各任务的初始进度 */
export function initialProgressOf(task: RunTaskDef): RunTaskProgress {
  return { stage: task.initialStage, stageTicks: 0, rejected: false, idleTicks: 0 }
}

export function stepStatusOf(step: RunStepDef, progress: RunTaskProgress): RunStepStatus {
  if (step.stage < progress.stage) return "completed"
  if (step.stage > progress.stage) return "waiting"
  if (step.approval) return progress.rejected ? "failed" : "awaiting"
  return "running"
}

export function taskStatusOf(task: RunTaskDef, progress: RunTaskProgress): RunTaskStatus {
  if (progress.rejected) return "failed"
  if (progress.stage > maxStageOf(task)) return "completed"
  return isApprovalStage(task, progress.stage) ? "awaiting" : "running"
}

/** 秒数格式化：59s 以内显示秒，否则"1m 05s" */
function formatSeconds(seconds: number): string {
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  return `${minutes}m ${String(seconds % 60).padStart(2, "0")}s`
}

function timeLabelOf(step: RunStepDef, status: RunStepStatus, progress: RunTaskProgress): string {
  switch (status) {
    case "completed":
      return step.approval ? "已批准" : `耗时 ${formatSeconds(step.durationSec)}`
    case "running":
      return `已运行 ${formatSeconds(Math.round((progress.stageTicks * RUN_TICK_MS) / 1000))}`
    case "awaiting":
      return "等待你的决定"
    case "failed":
      return "已驳回"
    case "waiting":
      return "未开始"
  }
}

interface RunLiveHandlers {
  onApprove: () => void
  onReject: () => void
}

export function buildRunLiveGraph(
  task: RunTaskDef,
  progress: RunTaskProgress,
  handlers: RunLiveHandlers
): { nodes: RunStepFlowNode[]; edges: Edge[] } {
  // 每个阶段内的行数，用于让该列垂直居中
  const rowsInStage = new Map<number, number>()
  for (const step of task.steps) rowsInStage.set(step.stage, (rowsInStage.get(step.stage) ?? 0) + 1)
  const rowCursor = new Map<number, number>()
  const statusById = new Map<string, RunStepStatus>()

  const nodes: RunStepFlowNode[] = task.steps.map((step) => {
    const row = rowCursor.get(step.stage) ?? 0
    rowCursor.set(step.stage, row + 1)
    const rows = rowsInStage.get(step.stage) ?? 1
    const status = stepStatusOf(step, progress)
    statusById.set(step.id, status)
    return {
      id: `${task.id}:${step.id}`,
      type: "runStep",
      position: { x: step.stage * STAGE_WIDTH, y: (row - (rows - 1) / 2) * ROW_HEIGHT },
      data: {
        title: step.title,
        actor: step.actor,
        icon: step.icon,
        status,
        timeLabel: timeLabelOf(step, status, progress),
        onApprove: status === "awaiting" ? handlers.onApprove : null,
        onReject: status === "awaiting" ? handlers.onReject : null
      }
    }
  })

  const edges: Edge[] = task.edges.map((edge) => {
    const target = statusById.get(edge.to) ?? "waiting"
    const completed = target === "completed"
    const flowing = target === "running" || target === "awaiting"
    const stroke = RUN_STATUS_STROKE[target]
    return {
      id: `${task.id}:${edge.from}->${edge.to}`,
      source: `${task.id}:${edge.from}`,
      target: `${task.id}:${edge.to}`,
      animated: flowing,
      label:
        completed && edge.items !== undefined ? `${edge.items.toLocaleString()} 条` : undefined,
      labelStyle: { fill: "#e2e8f0", fontSize: 10 },
      labelBgStyle: { fill: "rgba(15, 23, 42, 0.85)", stroke: "rgba(148, 163, 184, 0.25)" },
      labelBgPadding: [6, 3] as [number, number],
      labelBgBorderRadius: 6,
      style: {
        stroke,
        strokeWidth: flowing ? 2 : 1.5,
        strokeDasharray: target === "failed" ? "5 4" : undefined,
        opacity: target === "waiting" ? 0.35 : 0.9
      }
    }
  })

  return { nodes, edges }
}
