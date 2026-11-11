/**
 * 工作流标签页：当前展示一个示例 AI 工作流（ai_flow_definition 形态）及其运行状态，均为示例数据。
 *
 * 将来改为列出用户的 AI 工作流（/api/ai/workflows），点击某个工作流显示其节点图与运行实例。
 * @author AaronZZH & Kiro
 */

"use client"

import { Workflow } from "lucide-react"
import { MOCK_WORKFLOW_RUNS } from "./canvas-mock-data"
import { RunLiveCanvas } from "./RunLiveCanvas"
import type { CanvasFrame } from "./types"

interface WorkflowTabCanvasProps {
  frame: CanvasFrame
}

export function WorkflowTabCanvas({ frame }: WorkflowTabCanvasProps) {
  const name = MOCK_WORKFLOW_RUNS[0]?.name ?? "示例工作流"
  return (
    <RunLiveCanvas
      frame={frame}
      tasks={MOCK_WORKFLOW_RUNS}
      header={
        <span className="flex items-center gap-1.5 rounded-full border border-foreground/10 bg-background/50 px-3 py-1 font-medium text-xs backdrop-blur-md">
          <Workflow className="size-3.5 text-cyan-300" />
          {name}
        </span>
      }
    />
  )
}
