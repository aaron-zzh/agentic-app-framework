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

export function AgentRunStatus() {
  const phase = useAgentRunStore((s) => s.phase)
  const activeTool = useAgentRunStore((s) => s.activeTool)
  const entries = useAgentRunStore((s) => s.entries)

  if (phase === "idle") return null

  const last = entries.at(-1)
  const label = activeTool ? `调用工具：${activeTool}` : (PHASE_LABEL[phase] ?? phase)

  return (
    <div className="flex items-center gap-2 border-t px-3 py-1.5 text-xs text-muted-foreground">
      {phase === "running" && <Loader2 className="size-3 animate-spin" />}
      <span>{label}</span>
      {last?.title && <span className="truncate">· {last.title}</span>}
    </div>
  )
}
