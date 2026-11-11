/**
 * SelectedRoleBadge——当前对话使用的角色标识
 * 读取 agent-run-store（客户端瞬时状态），展示本次执行冻结的角色与选定方式
 * @author AaronZZH & Kiro
 */
"use client"

import { UserRound } from "lucide-react"
import { useAgentRunStore } from "../runtime/agent-run-store"

/** ROUTE_AUTO 下按 selectedBy 显示角色来源；未知取值统一显示"助理判断" */
const SELECTED_BY_LABELS: Record<string, string> = {
  CONVERSATION_ROLE: "沿用会话角色",
  DEFAULT_ROLE: "默认角色",
  ROLE_SWITCHED: "助理切换"
}

export function SelectedRoleBadge() {
  const selectedRole = useAgentRunStore((s) => s.selectedRole)

  if (!selectedRole) return null

  const source =
    selectedRole.routeConstraint === "ROUTE_FIXED"
      ? "已指定"
      : ((selectedRole.selectedBy ? SELECTED_BY_LABELS[selectedRole.selectedBy] : undefined) ??
        "助理判断")

  return (
    <div className="flex items-center gap-1.5 border-b px-3 py-1.5 text-muted-foreground text-xs">
      <UserRound className="size-3" />
      <span className="font-medium text-foreground">{selectedRole.roleName}</span>
      <span>· {source}</span>
    </div>
  )
}
