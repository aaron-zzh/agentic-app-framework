/**
 * 协作关系画布：角色卡片节点，显示名称、可见性、启用状态、默认角色标记、技能/工具/知识库数量与运行中的节点数。
 *
 * 点击由画布的 onNodeClick 统一处理跳转。
 * @author AaronZZH & Kiro
 */

"use client"

import { Handle, type NodeProps, Position } from "@xyflow/react"
import { BookOpen, Sparkles, Star, UserRound, Wrench } from "lucide-react"
import type { AiRoleVisibility } from "@/lib/api/rest/ai/role"
import { cn } from "@/lib/utils/cn"
import type { RoleFlowNode } from "./types"

const HIDDEN_HANDLE = "!size-1 !min-h-0 !min-w-0 !border-0 !bg-transparent"

const VISIBILITY_LABELS: Record<AiRoleVisibility, string> = {
  SYSTEM: "内置",
  PUBLIC: "公开",
  PRIVATE: "私有"
}

export function RoleNode({ data }: NodeProps<RoleFlowNode>) {
  return (
    <div
      title="点击查看运行中的任务"
      className={cn(
        "flex h-[92px] w-60 cursor-pointer flex-col justify-between rounded-xl border bg-slate-950/85 px-3 py-2.5 shadow-[0_8px_24px_-12px_rgba(0,0,0,0.8)] backdrop-blur-sm transition-colors hover:border-indigo-300/70",
        data.enabled ? "border-indigo-400/40" : "border-foreground/10 opacity-60"
      )}
    >
      <Handle type="target" position={Position.Left} className={HIDDEN_HANDLE} />
      <div className="flex items-center gap-2">
        <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-indigo-400/15 text-indigo-300">
          <UserRound className="size-4" />
        </span>
        <span className="min-w-0 flex-1 truncate font-medium text-sm text-white">{data.name}</span>
        {(data.runningNodes ?? 0) > 0 ? (
          <span className="flex shrink-0 items-center gap-1 rounded-full border border-emerald-300/40 px-1.5 py-px text-[10px] text-emerald-300">
            <span className="size-1.5 animate-pulse rounded-full bg-emerald-400" />
            {data.runningNodes} 运行中
          </span>
        ) : null}
        {data.defaultRole ? (
          <Star aria-label="默认角色" className="size-3.5 shrink-0 fill-amber-300 text-amber-300" />
        ) : null}
        <span
          className={cn(
            "size-2 shrink-0 rounded-full",
            data.enabled ? "bg-emerald-400 shadow-[0_0_6px_rgba(52,211,153,0.9)]" : "bg-slate-500"
          )}
        >
          <span className="sr-only">{data.enabled ? "已启用" : "已停用"}</span>
        </span>
      </div>
      <div className="flex items-center gap-3 text-[11px] text-slate-400">
        <span className="rounded border border-foreground/10 px-1.5 py-px text-slate-300">
          {VISIBILITY_LABELS[data.visibility]}
        </span>
        <span className="flex items-center gap-1">
          <Sparkles className="size-3 text-violet-300" />
          {data.skillCount}
        </span>
        <span className="flex items-center gap-1">
          <Wrench className="size-3 text-cyan-300" />
          {data.toolCount}
        </span>
        <span className="flex items-center gap-1">
          <BookOpen className="size-3 text-emerald-300" />
          {data.knowledgeCount}
        </span>
      </div>
      <Handle type="source" position={Position.Right} className={HIDDEN_HANDLE} />
    </div>
  )
}
