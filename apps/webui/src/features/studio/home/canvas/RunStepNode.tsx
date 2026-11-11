/**
 * 运行实况画布：任务步骤节点，显示图标、名称、所用角色或工具、状态与耗时；
 * 待审批时节点内直接提供"批准""驳回"按钮（演示阶段只弹提示）。
 * @author AaronZZH & Kiro
 */

"use client"

import { Handle, type NodeProps, Position } from "@xyflow/react"
import {
  BookOpen,
  Bot,
  Brain,
  CheckCircle2,
  Clapperboard,
  FileText,
  ImageIcon,
  ListChecks,
  type LucideIcon,
  PackageCheck,
  Search,
  ShieldCheck,
  Waypoints,
  Wrench,
  Zap
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils/cn"
import {
  RUN_STATUS_LABELS,
  RUN_STATUS_NODE_CLASSES,
  RUN_STATUS_TEXT_CLASSES
} from "./canvas-palette"
import type { RunStepFlowNode, RunStepIcon } from "./types"

const HIDDEN_HANDLE = "!size-1 !min-h-0 !min-w-0 !border-0 !bg-transparent"

const STEP_ICONS: Record<RunStepIcon, LucideIcon> = {
  trigger: Zap,
  coordinator: Bot,
  plan: Waypoints,
  memory: Brain,
  retrieve: BookOpen,
  search: Search,
  tool: Wrench,
  write: FileText,
  image: ImageIcon,
  video: Clapperboard,
  review: ListChecks,
  approval: ShieldCheck,
  deliver: PackageCheck
}

export function RunStepNode({ data }: NodeProps<RunStepFlowNode>) {
  const Icon = STEP_ICONS[data.icon]
  const awaiting = data.status === "awaiting"
  return (
    <div
      className={cn(
        "flex w-56 flex-col gap-2 rounded-xl border bg-slate-950/85 px-3 py-2.5 backdrop-blur-sm transition-[border-color,box-shadow,opacity] duration-500",
        RUN_STATUS_NODE_CLASSES[data.status]
      )}
    >
      <Handle type="target" position={Position.Left} className={HIDDEN_HANDLE} />
      <div className="flex items-center gap-2">
        <span
          className={cn(
            "flex size-8 shrink-0 items-center justify-center rounded-lg bg-foreground/[0.06]",
            RUN_STATUS_TEXT_CLASSES[data.status]
          )}
        >
          {data.status === "completed" ? (
            <CheckCircle2 className="size-4" />
          ) : (
            <Icon className={cn("size-4", data.status === "running" && "animate-pulse")} />
          )}
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium text-sm text-white">{data.title}</p>
          <p className="truncate text-[11px] text-slate-400">{data.actor}</p>
        </div>
      </div>
      <div className="flex items-center justify-between gap-2 text-[11px]">
        <span className={RUN_STATUS_TEXT_CLASSES[data.status]}>
          {RUN_STATUS_LABELS[data.status]}
        </span>
        <span className="truncate text-slate-400 tabular-nums">{data.timeLabel}</span>
      </div>
      {awaiting && data.onApprove && data.onReject ? (
        // nodrag/nopan：按钮点击不触发画布拖动
        <div className="nodrag nopan flex gap-2">
          <Button
            type="button"
            size="xs"
            className="flex-1 bg-amber-400 text-slate-950 hover:bg-amber-300"
            onClick={data.onApprove}
          >
            批准
          </Button>
          <Button
            type="button"
            size="xs"
            variant="outline"
            className="flex-1 border-rose-400/50 text-rose-300 hover:bg-rose-400/10"
            onClick={data.onReject}
          >
            驳回
          </Button>
        </div>
      ) : null}
      <Handle type="source" position={Position.Right} className={HIDDEN_HANDLE} />
    </div>
  )
}
