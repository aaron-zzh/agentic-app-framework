/**
 * 项目任务标签页：总览为 AIGC 项目与根执行记录；点击执行记录下钻查看执行树。
 *
 * 下钻状态只在本次会话内保留，切换标签页或刷新后回到总览。
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { CanvasBreadcrumb } from "./CanvasBreadcrumb"
import { ProjectRunsCanvas } from "./ProjectRunsCanvas"
import { RunTreeCanvas } from "./RunTreeCanvas"
import type { CanvasFrame } from "./types"

interface DrillTarget {
  id: number
  title: string
  mock: boolean
}

interface ProjectTabCanvasProps {
  frame: CanvasFrame
}

export function ProjectTabCanvas({ frame }: ProjectTabCanvasProps) {
  const [drill, setDrill] = useState<DrillTarget | null>(null)

  if (drill === null) {
    return <ProjectRunsCanvas frame={frame} onDrill={setDrill} />
  }
  return (
    <RunTreeCanvas
      frame={frame}
      rootId={drill.id}
      mock={drill.mock}
      header={
        <CanvasBreadcrumb root="项目任务" current={drill.title} onBack={() => setDrill(null)} />
      }
    />
  )
}
