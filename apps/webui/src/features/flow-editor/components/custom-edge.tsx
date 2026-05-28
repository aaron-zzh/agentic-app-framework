/**
 * 自定义边——条件标签 + 动画
 * @author AaronZZH & Kiro
 */

"use client"

import { memo } from "react"
import { BaseEdge, EdgeLabelRenderer, getBezierPath, type EdgeProps } from "@xyflow/react"

function CustomEdgeComponent({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  data,
  selected
}: EdgeProps) {
  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition
  })

  const condition = (data as Record<string, unknown>)?.condition as string | undefined

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        className={selected ? "!stroke-primary !stroke-2" : ""}
        style={{ strokeWidth: 1.5, stroke: "var(--color-muted-foreground)" }}
      />
      {condition && (
        <EdgeLabelRenderer>
          <div
            className="bg-background border-border absolute rounded border px-1.5 py-0.5 text-xs"
            style={{ transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)` }}
          >
            {condition}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  )
}

export const CustomEdge = memo(CustomEdgeComponent)
