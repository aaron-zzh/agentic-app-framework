/**
 * 助理标签页：总览为助理协作拓扑；点击助理或角色下钻查看其运行中的任务流程（示例数据，待 AAF-115）。
 *
 * 下钻状态只在本次会话内保留，切换标签页或刷新后回到总览。
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { CanvasBreadcrumb } from "./CanvasBreadcrumb"
import { CollaborationCanvas } from "./CollaborationCanvas"
import { MOCK_RUN_TASKS } from "./canvas-mock-data"
import { RunLiveCanvas } from "./RunLiveCanvas"
import type { CanvasFrame } from "./types"

interface AssistantTabCanvasProps {
  frame: CanvasFrame
}

export function AssistantTabCanvas({ frame }: AssistantTabCanvasProps) {
  const [drillName, setDrillName] = useState<string | null>(null)

  if (drillName === null) {
    return <CollaborationCanvas frame={frame} onDrill={setDrillName} />
  }
  return (
    <RunLiveCanvas
      frame={frame}
      tasks={MOCK_RUN_TASKS}
      header={
        <CanvasBreadcrumb root="助理" current={drillName} onBack={() => setDrillName(null)} />
      }
    />
  )
}
