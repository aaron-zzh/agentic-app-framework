/**
 * AgentRunStatus——Agent 运行过程指示条
 * 读取 agent-run-store（客户端瞬时状态），展示运行阶段 / 工具调用 / 过程事件
 * @author AaronZZH & Kiro
 */
"use client"

import { Loader2 } from "lucide-react"
import { useAgentRunStore } from "../runtime/agent-run-store"

const PHASE_LABEL: Record<string, string> = {
  running: "运行中",
  finished: "已完成",
  error: "运行失败"
}

/**
 * @param processOnly 只显示过程性记录（如步骤上报），不显示"运行中 / 调用工具"。
 *   消息气泡已用思考占位和工具组展示阶段与工具名时传 true，避免重复。
 */
export function AgentRunStatus({ processOnly = false }: { processOnly?: boolean }) {
  const phase = useAgentRunStore((s) => s.phase)
  const activeTool = useAgentRunStore((s) => s.activeTool)
  const entries = useAgentRunStore((s) => s.entries)

  if (phase === "idle") return null

  // 角色切换、转为任务已在消息流中以行内标记显示（ControlToolChip），这里只显示过程性记录
  const last = entries
    .filter(
      (entry) =>
        entry.type !== "ROLE_SWITCHED" &&
        entry.type !== "TASK_PROMOTED" &&
        // processOnly 时工具名已在气泡的工具组中列出，不再重复
        (!processOnly || entry.type !== "TOOL_CALL_STARTED")
    )
    .at(-1)
  if (processOnly) {
    if (phase !== "running") return null
    // 气泡已列出具体工具，这里只提示正在调用；无工具调用时只显示步骤上报等过程记录
    const text = activeTool
      ? "调用工具中…"
      : last?.title
        ? `${last.title}${last.message ? `：${last.message}` : ""}`
        : null
    if (!text) return null
    return (
      <div className="mb-3 flex items-center gap-2 px-1 text-muted-foreground text-xs">
        <Loader2 className="size-3 animate-spin" />
        <span className="min-w-0 truncate">{text}</span>
      </div>
    )
  }
  const label = activeTool ? `调用工具：${activeTool}` : (PHASE_LABEL[phase] ?? phase)

  return (
    <div className="mb-3 flex items-center gap-2 px-1 text-muted-foreground text-xs">
      {phase === "running" && <Loader2 className="size-3 animate-spin" />}
      <span>{label}</span>
      {last?.title && (
        <span className="min-w-0 truncate">
          · {last.title}
          {last.message ? `：${last.message}` : ""}
        </span>
      )}
    </div>
  )
}
