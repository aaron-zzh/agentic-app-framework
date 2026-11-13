/**
 * ControlToolChip——协调者控制工具（切换角色、转为任务）在消息流中的行内标记
 * 不放进"N 个工具调用"折叠组，直接显示结果；理由放在一行 tooltip 里
 * ToolGatePromotionChip——授权工具触发的自动升级提示，与控制工具标记共用样式
 * @author AaronZZH & Kiro
 */
"use client"

import { ArrowRightLeft, ListChecks } from "lucide-react"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import { useRoleNames } from "@/features/livechat/components/use-role-names"
import { useAgentRunStore } from "@/features/livechat/runtime/agent-run-store"
import { SYSTEM_TOOL_LABELS } from "@/lib/constants/system-tools"
import { cn } from "@/lib/utils"

/** 以行内标记展示的控制工具；其余工具仍走折叠组 */
export const CONTROL_TOOL_NAMES = ["activate_role", "promote_to_task"] as const

const SHAPE_LABELS: Record<string, string> = {
  STEPS: "按步骤推进",
  DELEGATE: "多角色协作"
}

/** 控制类行内标记的统一样式，转为任务、切换角色、自动升级提示共用 */
function controlChipClassName(failed: boolean, running: boolean): string {
  return cn(
    "my-1 inline-flex max-w-full items-center gap-1.5 rounded-full border px-2 py-0.5 text-xs",
    failed
      ? "border-border/60 bg-muted/30 text-muted-foreground"
      : "border-primary/30 bg-primary/10 text-primary",
    running && "animate-pulse"
  )
}

/**
 * 网关自动升级提示：轻量态调用需授权工具时系统自动转为任务（`aaf.task.promoted`，trigger=TOOL_GATE）。
 * 数据来自本轮运行记录，仅在当前轮最后一条助手消息中显示；模型主动升级（trigger=MODEL）仍由 promote_to_task 标记展示。
 */
export function ToolGatePromotionChip() {
  const entry = useAgentRunStore((s) =>
    s.entries.findLast((item) => item.type === "TASK_PROMOTED" && item.trigger === "TOOL_GATE")
  )
  if (!entry) return null
  const toolLabel = entry.toolName
    ? (SYSTEM_TOOL_LABELS[entry.toolName] ?? entry.toolName)
    : undefined
  return (
    <span className={controlChipClassName(false, false)}>
      <ListChecks className="size-3 shrink-0" />
      <span className="truncate">
        {`该操作需要你审批，已转为任务${toolLabel ? ` · ${toolLabel}` : ""}`}
      </span>
    </span>
  )
}

function textArg(args: unknown, key: string): string | undefined {
  if (typeof args !== "object" || args === null) return undefined
  const value = (args as Record<string, unknown>)[key]
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : undefined
}

export function ControlToolChip({
  toolName,
  args,
  status,
  isError
}: {
  toolName: string
  args: unknown
  status?: { type: string }
  isError?: boolean
}) {
  const roleName = useRoleNames()
  const running = status?.type === "running"
  const failed = isError === true || status?.type === "incomplete"
  const reason = textArg(args, "reason")
  const roleKey = textArg(args, "roleKey")
  // 本轮切换事件携带服务端解析的角色名；角色不在可用助理列表（缓存未刷新等）时用它兜底显示中文名
  const switchedRoleName = useAgentRunStore(
    (s) =>
      s.entries.findLast(
        (entry) =>
          entry.type === "ROLE_SWITCHED" && (roleKey === undefined || entry.roleKey === roleKey)
      )?.roleName
  )
  const targetName = roleName(roleKey) ?? switchedRoleName ?? roleKey
  const isRoleSwitch = toolName === "activate_role"
  const Icon = isRoleSwitch ? ArrowRightLeft : ListChecks

  const shape = textArg(args, "shape")
  const label = isRoleSwitch
    ? running
      ? "正在切换角色…"
      : failed
        ? "未切换角色，保持当前角色"
        : `已切换到 ${targetName ?? "新角色"}`
    : running
      ? "正在转为任务…"
      : failed
        ? "转为任务未成功"
        : `已转为任务${shape && SHAPE_LABELS[shape] ? ` · ${SHAPE_LABELS[shape]}` : ""}${
            targetName ? ` · ${targetName} 负责` : ""
          }`

  const chipClassName = controlChipClassName(failed, running)

  if (!reason) {
    return (
      <span className={chipClassName}>
        <Icon className="size-3 shrink-0" />
        <span className="truncate">{label}</span>
      </span>
    )
  }

  return (
    <TooltipProvider delay={300}>
      <Tooltip>
        <TooltipTrigger render={<span className={chipClassName} />}>
          <Icon className="size-3 shrink-0" />
          <span className="truncate">{label}</span>
        </TooltipTrigger>
        <TooltipContent side="top" className="max-w-80">
          <p className="truncate text-xs">{reason}</p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}
