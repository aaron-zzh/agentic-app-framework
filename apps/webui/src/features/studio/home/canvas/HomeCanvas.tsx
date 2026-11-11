/**
 * Studio 首页中央画布：按当前标签页显示助理、项目任务或工作流，切换时淡入。
 *
 * 每个标签页都是"总览 → 点击节点下钻 → 面包屑返回"。背景是深色底加中心径向光晕和四周暗角；
 * 点阵网格由 React Flow 的 Background 绘制。每个画布各自创建 React Flow 实例。
 * @author AaronZZH & Kiro
 */

"use client"

import { useStudioHomeLayoutStore } from "@/lib/store/studio-home-layout-store"
import { cn } from "@/lib/utils/cn"
import { AssistantTabCanvas } from "./AssistantTabCanvas"
import { ProjectTabCanvas } from "./ProjectTabCanvas"
import type { CanvasFrame } from "./types"
import { WorkflowTabCanvas } from "./WorkflowTabCanvas"

interface HomeCanvasProps {
  frame: CanvasFrame
  className?: string
}

export function HomeCanvas({ frame, className }: HomeCanvasProps) {
  const canvasTab = useStudioHomeLayoutStore((state) => state.canvasTab)

  return (
    <div
      className={cn(
        "relative overflow-hidden bg-[radial-gradient(ellipse_at_50%_45%,rgba(79,70,229,0.22),rgba(2,6,23,0.96)_65%)]",
        className
      )}
    >
      {/* 四周暗角，画在画布之下，不压暗节点与标记 */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 shadow-[inset_0_0_160px_40px_rgba(2,6,23,0.85)]"
      />
      {/* key 随标签页变化，切换时重新挂载（下钻状态随之清空）并淡入 */}
      <div key={canvasTab} className="fade-in-0 absolute inset-0 animate-in duration-500">
        {canvasTab === "assistant" ? <AssistantTabCanvas frame={frame} /> : null}
        {canvasTab === "project" ? <ProjectTabCanvas frame={frame} /> : null}
        {canvasTab === "workflow" ? <WorkflowTabCanvas frame={frame} /> : null}
      </div>
    </div>
  )
}
