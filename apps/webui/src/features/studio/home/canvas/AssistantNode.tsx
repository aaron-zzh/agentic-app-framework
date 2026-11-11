/**
 * 协作关系画布：助理主节点，圆形发光，显示助理名称与绑定角色数。
 * @author AaronZZH & Kiro
 */

"use client"

import { Handle, type NodeProps, Position } from "@xyflow/react"
import { Bot } from "lucide-react"
import type { AssistantFlowNode } from "./types"

/** 连接点只用于连线定位，不显示也不可交互 */
const HIDDEN_HANDLE = "!size-1 !min-h-0 !min-w-0 !border-0 !bg-transparent"

export function AssistantNode({ data }: NodeProps<AssistantFlowNode>) {
  const running = data.runningTasks ?? 0
  return (
    <div
      title="点击查看运行中的任务"
      className="relative flex size-32 cursor-pointer flex-col items-center justify-center gap-1 rounded-full border border-indigo-300/50 bg-[radial-gradient(circle_at_50%_35%,rgba(129,140,248,0.45),rgba(15,23,42,0.92)_70%)] text-center shadow-[0_0_48px_-6px_rgba(129,140,248,0.75)] transition-shadow hover:shadow-[0_0_64px_-4px_rgba(129,140,248,0.95)]"
    >
      {/* 外圈光环 */}
      <span
        aria-hidden="true"
        className="pointer-events-none absolute -inset-3 rounded-full border border-indigo-300/20"
      />
      {running > 0 ? (
        <span className="absolute -top-1 -right-1 flex items-center gap-1 rounded-full border border-emerald-300/40 bg-slate-950/90 px-1.5 py-0.5 text-[10px] text-emerald-300">
          <span className="size-1.5 animate-pulse rounded-full bg-emerald-400 shadow-[0_0_6px_rgba(52,211,153,0.9)]" />
          {running} 运行中
        </span>
      ) : null}
      <Bot className="size-7 text-indigo-200" />
      <span className="max-w-24 truncate font-semibold text-sm text-white">{data.name}</span>
      <span className="text-[10px] text-indigo-200/80">{data.roleCount} 个角色</span>
      <Handle type="source" position={Position.Right} className={HIDDEN_HANDLE} />
    </div>
  )
}
