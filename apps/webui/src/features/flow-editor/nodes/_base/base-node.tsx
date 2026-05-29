/**
 * 节点基础组件——端口、图标、状态指示
 * @author AaronZZH & Kiro
 */

"use client"

import { Handle, type NodeProps, Position } from "@xyflow/react"
import { memo } from "react"
import { cn } from "@/lib/utils/cn"
import type { ExecutionState, PortDef } from "../../types"

export interface BaseNodeData {
  label?: string
  icon?: string
  ports?: PortDef[]
  /** 运行时状态（由外部注入） */
  executionStatus?: "idle" | "running" | "completed" | "failed"
  [key: string]: unknown
}

interface BaseNodeProps extends NodeProps {
  data: BaseNodeData
  /** 节点主色 */
  color?: string
  /** 是否为紧凑模式（开始/结束节点） */
  compact?: boolean
}

/** 状态颜色映射 */
const statusColors: Record<string, string> = {
  running: "ring-2 ring-blue-400 animate-pulse",
  completed: "ring-2 ring-green-400",
  failed: "ring-2 ring-red-400"
}

function BaseNodeComponent({ data, selected, compact, color }: BaseNodeProps) {
  const ports = (data.ports as PortDef[] | undefined) ?? []
  const inputPorts = ports.filter((p) => p.direction === "input")
  const outputPorts = ports.filter((p) => p.direction === "output")
  const status = data.executionStatus as string | undefined

  return (
    <div
      className={cn(
        "rounded-lg border bg-card shadow-sm transition-shadow",
        compact ? "px-3 py-2" : "min-w-[160px] px-4 py-3",
        selected && "ring-2 ring-primary",
        status && statusColors[status],
        color
      )}
    >
      {/* 输入端口 */}
      {inputPorts.map((port, i) => (
        <Handle
          key={port.id}
          id={port.id}
          type="target"
          position={Position.Top}
          className="!bg-muted-foreground !h-2 !w-2"
          style={{ left: `${((i + 1) / (inputPorts.length + 1)) * 100}%` }}
        />
      ))}

      {/* 节点内容 */}
      <div className="flex items-center gap-2">
        {data.icon && <span className="text-base">{data.icon}</span>}
        <span className="font-medium text-sm">{data.label ?? "未命名"}</span>
      </div>

      {/* 输出端口 */}
      {outputPorts.map((port, i) => (
        <Handle
          key={port.id}
          id={port.id}
          type="source"
          position={Position.Bottom}
          className="!bg-muted-foreground !h-2 !w-2"
          style={{ left: `${((i + 1) / (outputPorts.length + 1)) * 100}%` }}
        />
      ))}
    </div>
  )
}

export const BaseNode = memo(BaseNodeComponent)

/** 注入执行状态到节点数据 */
export function injectExecutionStatus(
  nodes: Array<{ id: string; data: Record<string, unknown> }>,
  executionState?: ExecutionState
) {
  if (!executionState) return nodes
  return nodes.map((node) => {
    let status: string = "idle"
    if (executionState.currentNodeId === node.id) status = "running"
    else if (executionState.completedNodes.includes(node.id)) status = "completed"
    else if (executionState.failedNodes.includes(node.id)) status = "failed"
    return { ...node, data: { ...node.data, executionStatus: status } }
  })
}
