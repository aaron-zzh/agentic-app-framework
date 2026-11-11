/**
 * 协作关系画布：角色分类节点。
 *
 * - 展开：包住该分类全部角色的虚线框，只有左上角标题可点击（带 data-role-group-toggle），
 *   框体本身不拦截鼠标，框内角色照常点击、悬停。
 * - 折叠：一张虚线小卡片，显示分类名与角色数；该分类角色的连线都汇到它上面，点击展开。
 * 点击由画布的 onNodeClick 统一处理。
 * @author AaronZZH & Kiro
 */

"use client"

import { Handle, type NodeProps, Position } from "@xyflow/react"
import { ChevronDown, ChevronUp, UsersRound } from "lucide-react"
import type { RoleGroupFlowNode } from "./types"

const HIDDEN_HANDLE = "!size-1 !min-h-0 !min-w-0 !border-0 !bg-transparent"

export function RoleGroupNode({ data }: NodeProps<RoleGroupFlowNode>) {
  if (data.collapsed) {
    return (
      <div
        title={`${data.name}：点击展开 ${data.roleCount} 个角色`}
        className="flex h-14 w-60 cursor-pointer items-center gap-2 rounded-xl border border-indigo-400/50 border-dashed bg-slate-950 px-3 transition-colors hover:border-indigo-300/80"
      >
        <Handle type="target" position={Position.Left} className={HIDDEN_HANDLE} />
        <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-indigo-400/15 text-indigo-300">
          <UsersRound className="size-4" />
        </span>
        <span className="min-w-0 flex-1 truncate font-medium text-sm text-white">{data.name}</span>
        <span className="shrink-0 text-[11px] text-slate-400">{data.roleCount} 个角色</span>
        <ChevronDown className="size-3.5 shrink-0 text-indigo-300" />
        <Handle type="source" position={Position.Right} className={HIDDEN_HANDLE} />
      </div>
    )
  }
  return (
    <div className="pointer-events-none size-full rounded-2xl border border-indigo-400/30 border-dashed bg-indigo-400/[0.03]">
      <button
        type="button"
        data-role-group-toggle
        title={`折叠「${data.name}」`}
        className="pointer-events-auto flex h-8 cursor-pointer items-center gap-1.5 px-3 text-[11px] text-slate-300 transition-colors hover:text-white"
      >
        <UsersRound className="size-3 text-indigo-300" />
        <span className="font-medium">{data.name}</span>
        <span className="text-slate-500">{data.roleCount}</span>
        <ChevronUp className="size-3" />
      </button>
    </div>
  )
}
