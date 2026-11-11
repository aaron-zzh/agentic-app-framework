/**
 * 项目任务画布：项目节点，显示项目名称、执行记录数与运行中数量；点击进入项目详情。
 * @author AaronZZH & Kiro
 */

"use client"

import { Handle, type NodeProps, Position } from "@xyflow/react"
import { FolderKanban } from "lucide-react"
import { cn } from "@/lib/utils/cn"
import type { ProjectFlowNode } from "./types"

const HIDDEN_HANDLE = "!size-1 !min-h-0 !min-w-0 !border-0 !bg-transparent"

export function ProjectNode({ data }: NodeProps<ProjectFlowNode>) {
  return (
    <div
      title={data.href ? "进入项目" : undefined}
      className={cn(
        "flex h-16 w-64 items-center gap-3 rounded-xl border border-amber-400/40 bg-slate-950/85 px-3 shadow-[0_0_28px_-10px_rgba(251,191,36,0.7)] backdrop-blur-sm transition-colors",
        data.href && "cursor-pointer hover:border-amber-300/70"
      )}
    >
      <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-amber-400/15 text-amber-300">
        <FolderKanban className="size-4" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate font-medium text-sm text-white">{data.name}</p>
        <p className="text-[11px] text-slate-400">
          {data.runCount} 次执行
          {data.runningCount > 0 ? (
            <span className="ml-2 text-indigo-300">{data.runningCount} 运行中</span>
          ) : null}
        </p>
      </div>
      <Handle type="source" position={Position.Right} className={HIDDEN_HANDLE} />
    </div>
  )
}
