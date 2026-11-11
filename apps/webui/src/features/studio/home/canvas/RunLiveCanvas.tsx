/**
 * 运行流程画布：示例任务或示例工作流的执行流程，状态按脚本自动推进，待审批节点可批准或驳回（演示）。
 *
 * 助理下钻与工作流标签页共用。多个任务时顶部显示任务切换条；header 放面包屑或标题。
 * 将来接 AAF-115 任务接口或工作流运行实例接口，替换 useRunLiveSimulation 即可。
 * @author AaronZZH & Kiro
 */

"use client"

import type { NodeTypes } from "@xyflow/react"
import type { ReactNode } from "react"
import { CanvasFlow } from "./CanvasFlow"
import { CanvasMockBadge } from "./CanvasMockBadge"
import { RunStepNode } from "./RunStepNode"
import { RunTaskSwitcher } from "./RunTaskSwitcher"
import type { CanvasFrame, RunStepFlowNode, RunTaskDef } from "./types"
import { useRunLiveSimulation } from "./use-run-live-simulation"

const NODE_TYPES: NodeTypes = { runStep: RunStepNode }

interface RunLiveCanvasProps {
  frame: CanvasFrame
  /** 示例任务，需为模块级常量 */
  tasks: readonly RunTaskDef[]
  /** 左上角内容（面包屑或标题），放在"示例数据"标记右侧 */
  header?: ReactNode
}

export function RunLiveCanvas({ frame, tasks, header }: RunLiveCanvasProps) {
  const simulation = useRunLiveSimulation(tasks)

  return (
    <CanvasFlow<RunStepFlowNode>
      nodes={simulation.nodes}
      edges={simulation.edges}
      nodeTypes={NODE_TYPES}
      frame={frame}
      // 只在切换任务时重新适应视图，状态推进不打断用户的拖动缩放
      fitKey={`run:${simulation.activeTaskId}`}
      overlay={
        <>
          <div className="pointer-events-auto absolute top-0 left-0 flex items-center gap-3">
            {header}
            <CanvasMockBadge inline />
          </div>
          {simulation.tasks.length > 1 ? (
            <RunTaskSwitcher
              tasks={simulation.tasks}
              activeTaskId={simulation.activeTaskId}
              onSelect={simulation.selectTask}
            />
          ) : null}
        </>
      }
    />
  )
}
