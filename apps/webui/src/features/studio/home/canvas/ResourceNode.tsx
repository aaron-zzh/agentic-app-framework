/**
 * 协作关系画布：技能 / 工具 / 知识库小卡片节点；overflow 为真时是"+N"汇总节点。
 *
 * - expanded 不为空时是可展开节点（系统技能、系统工具、"+N"），右侧显示展开/收起箭头。
 * - 左右各有一个隐藏连接点：左侧接角色，右侧供技能连向所需工具。
 * - 底色不透明：角色直连工具、知识库的线会从技能列后方穿过，避免压在卡片文字上。
 * @author AaronZZH & Kiro
 */

"use client"

import { Handle, type NodeProps, Position } from "@xyflow/react"
import {
  BookOpen,
  ChevronDown,
  type LucideIcon,
  MoreHorizontal,
  Sparkles,
  UserRound,
  Wrench
} from "lucide-react"
import { cn } from "@/lib/utils/cn"
import { RESOURCE_KIND_LABELS, RESOURCE_TONE_CLASSES } from "./canvas-palette"
import type { ResourceFlowNode, ResourceNodeData } from "./types"

const HIDDEN_HANDLE = "!size-1 !min-h-0 !min-w-0 !border-0 !bg-transparent"

const KIND_ICONS: Record<ResourceNodeData["kind"], LucideIcon> = {
  skill: Sparkles,
  tool: Wrench,
  knowledge: BookOpen,
  role: UserRound
}

export function ResourceNode({ data }: NodeProps<ResourceFlowNode>) {
  const Icon = data.overflow ? MoreHorizontal : KIND_ICONS[data.kind]
  const expandable = data.expanded !== undefined
  const title = expandable
    ? `${data.label}：点击${data.expanded ? "收起" : "展开"}`
    : `${RESOURCE_KIND_LABELS[data.kind]}：${data.label}`
  return (
    <div
      title={title}
      className={cn(
        "flex h-10 w-52 items-center gap-2 rounded-lg border bg-slate-950 px-2.5 transition-colors",
        RESOURCE_TONE_CLASSES[data.kind],
        data.href || expandable ? "cursor-pointer hover:bg-slate-900" : "cursor-default",
        data.overflow && "border-dashed"
      )}
    >
      <Handle type="target" position={Position.Left} className={HIDDEN_HANDLE} />
      <Icon className="size-3.5 shrink-0" />
      <span className="min-w-0 flex-1 truncate text-slate-200 text-xs">{data.label}</span>
      <span className="shrink-0 text-[10px] text-slate-500">
        {data.badge ?? RESOURCE_KIND_LABELS[data.kind]}
      </span>
      {expandable ? (
        <ChevronDown
          className={cn("size-3.5 shrink-0 transition-transform", data.expanded && "rotate-180")}
        />
      ) : null}
      <Handle type="source" position={Position.Right} className={HIDDEN_HANDLE} />
    </div>
  )
}
