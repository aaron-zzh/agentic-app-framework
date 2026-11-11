/**
 * 运行实况演示：按示例脚本每隔一个节拍推进传入示例任务的步骤状态，营造实时感。
 * 助理下钻（多个任务）与工作流（单个示例工作流）共用。
 *
 * - 每个阶段持续 STAGE_TICKS 个节拍后进入下一阶段；到达审批阶段时停住，直到用户批准或驳回。
 * - 批准：审批步骤完成，继续推进；驳回：审批步骤变为失败，停止推进。
 * - 全部完成或驳回后停留 RESTART_TICKS 个节拍，再从头演示。
 * - 定时器在卸载时清理。批准、驳回只弹提示，不调用接口。
 *
 * @example
 * const { tasks, activeTaskId, selectTask, nodes, edges } = useRunLiveSimulation(MOCK_RUN_TASKS)
 * @author AaronZZH & Kiro
 */

"use client"

import type { Edge } from "@xyflow/react"
import { useCallback, useEffect, useMemo, useReducer, useState } from "react"
import { toast } from "sonner"
import {
  buildRunLiveGraph,
  initialProgressOf,
  isApprovalStage,
  maxStageOf,
  RUN_TICK_MS,
  taskStatusOf
} from "./run-live-layout"
import type { RunStepFlowNode, RunTaskDef, RunTaskProgress, RunTaskSummary } from "./types"

/** 每个阶段持续的节拍数 */
const STAGE_TICKS = 2
/** 完成或驳回后停留的节拍数 */
const RESTART_TICKS = 6

const DEMO_NOTICE = "示例数据，暂不提交"

type ProgressState = Record<string, RunTaskProgress>

type ProgressAction =
  | { type: "tick" }
  | { type: "approve"; taskId: string }
  | { type: "reject"; taskId: string }

function initialState(tasks: readonly RunTaskDef[]): ProgressState {
  const state: ProgressState = {}
  for (const task of tasks) state[task.id] = initialProgressOf(task)
  return state
}

/** 单个任务推进一个节拍 */
function advance(task: RunTaskDef, progress: RunTaskProgress): RunTaskProgress {
  const finished = progress.rejected || progress.stage > maxStageOf(task)
  if (finished) {
    const idleTicks = progress.idleTicks + 1
    return idleTicks >= RESTART_TICKS
      ? { stage: 0, stageTicks: 0, rejected: false, idleTicks: 0 }
      : { ...progress, idleTicks }
  }
  // 审批阶段停住，只累计等待时长
  if (isApprovalStage(task, progress.stage)) {
    return { ...progress, stageTicks: progress.stageTicks + 1 }
  }
  const stageTicks = progress.stageTicks + 1
  return stageTicks >= STAGE_TICKS
    ? { ...progress, stage: progress.stage + 1, stageTicks: 0 }
    : { ...progress, stageTicks }
}

function reduce(
  tasks: readonly RunTaskDef[],
  state: ProgressState,
  action: ProgressAction
): ProgressState {
  if (action.type === "tick") {
    const next: ProgressState = {}
    for (const task of tasks) {
      const progress = state[task.id] ?? initialProgressOf(task)
      next[task.id] = advance(task, progress)
    }
    return next
  }

  const task = tasks.find((item) => item.id === action.taskId)
  const progress = state[action.taskId]
  if (!task || !progress || progress.rejected || !isApprovalStage(task, progress.stage)) {
    return state
  }
  return {
    ...state,
    [action.taskId]:
      action.type === "approve"
        ? { ...progress, stage: progress.stage + 1, stageTicks: 0 }
        : { ...progress, rejected: true, idleTicks: 0 }
  }
}

interface RunLiveSimulation {
  tasks: RunTaskSummary[]
  activeTaskId: string
  selectTask: (taskId: string) => void
  nodes: RunStepFlowNode[]
  edges: Edge[]
}

/** tasks 需为模块级常量或稳定引用 */
export function useRunLiveSimulation(tasks: readonly RunTaskDef[]): RunLiveSimulation {
  const [progressByTask, dispatch] = useReducer(
    (state: ProgressState, action: ProgressAction) => reduce(tasks, state, action),
    tasks,
    initialState
  )
  const [activeTaskId, setActiveTaskId] = useState<string>(tasks[0]?.id ?? "")

  useEffect(() => {
    const timer = window.setInterval(() => dispatch({ type: "tick" }), RUN_TICK_MS)
    return () => window.clearInterval(timer)
  }, [])

  const selectTask = useCallback(
    (taskId: string) => {
      if (tasks.some((task) => task.id === taskId)) setActiveTaskId(taskId)
    },
    [tasks]
  )

  const summaries = useMemo<RunTaskSummary[]>(
    () =>
      tasks.map((task) => ({
        id: task.id,
        name: task.name,
        status: taskStatusOf(task, progressByTask[task.id] ?? initialProgressOf(task))
      })),
    [tasks, progressByTask]
  )

  const activeTask = tasks.find((task) => task.id === activeTaskId) ?? tasks[0]
  const activeProgress = activeTask ? progressByTask[activeTask.id] : undefined

  const graph = useMemo(() => {
    if (!activeTask || !activeProgress) return { nodes: [], edges: [] }
    const taskId = activeTask.id
    return buildRunLiveGraph(activeTask, activeProgress, {
      onApprove: () => {
        toast.info(DEMO_NOTICE)
        dispatch({ type: "approve", taskId })
      },
      onReject: () => {
        toast.info(DEMO_NOTICE)
        dispatch({ type: "reject", taskId })
      }
    })
  }, [activeTask, activeProgress])

  return { tasks: summaries, activeTaskId, selectTask, nodes: graph.nodes, edges: graph.edges }
}
