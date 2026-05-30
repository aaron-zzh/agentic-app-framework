/**
 * ChatterToolbar——顶部工具栏
 * TargetSwitcher（切换 AI/Kiro/用户）+ RoleSelector + SessionManager + 外部 toolbar slot
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Bot, Plus, Sparkles, User } from "lucide-react"
import type { ReactNode } from "react"
import { Button } from "@/components/ui/button"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import type { ChatterPreset, ChatterTarget } from "./types"

interface ChatterToolbarProps {
  preset: ChatterPreset
  target: ChatterTarget
  onTargetChange: (target: ChatterTarget) => void
  onNewSession?: () => void
  toolbar?: ReactNode
}

/** 内置角色列表 */
const AGENT_ROLES = [
  { id: "default-generalist", label: "通用助理", avatar: "🤖" },
  { id: "content-creator-role", label: "内容创作", avatar: "✍️" }
] as const

/** preset 决定显示哪些 target 选项 */
function getAvailableTargets(preset: ChatterPreset): ChatterTarget["type"][] {
  switch (preset) {
    case "ai":
      return ["ai", "kiro"]
    case "kiro":
      return ["kiro", "ai"]
    case "livechat":
      return ["ai", "kiro", "user"]
  }
}

const TARGET_ICONS: Record<ChatterTarget["type"], ReactNode> = {
  ai: <Sparkles className="size-3.5" />,
  kiro: <Bot className="size-3.5" />,
  user: <User className="size-3.5" />
}

const TARGET_LABELS: Record<ChatterTarget["type"], string> = {
  ai: "AI",
  kiro: "Kiro",
  user: "用户"
}

/**
 * 对话工具栏
 * 包含 target 切换按钮组 + 角色选择 + 新建会话按钮 + 外部注入 toolbar
 */
export function ChatterToolbar({
  preset,
  target,
  onTargetChange,
  onNewSession,
  toolbar
}: ChatterToolbarProps) {
  const targets = getAvailableTargets(preset)
  const showNewSession = preset === "ai" || preset === "livechat"
  const showRoleSelector = target.type === "ai"

  return (
    <div className="flex items-center gap-2 border-b px-3 py-2">
      <ToggleGroup
        value={[target.type]}
        onValueChange={(value: string[]) => {
          // 防止全部取消选中（至少保留一个）
          const newType = value.find((v) => v !== target.type) ?? target.type
          if (newType !== target.type) {
            onTargetChange({ ...target, type: newType as ChatterTarget["type"] })
          }
        }}
        size="sm"
        spacing={0}
      >
        {targets.map((t) => (
          <ToggleGroupItem key={t} value={t} aria-label={TARGET_LABELS[t]}>
            {TARGET_ICONS[t]}
            <span className="ml-1 text-xs">{TARGET_LABELS[t]}</span>
          </ToggleGroupItem>
        ))}
      </ToggleGroup>

      {/* 角色选择下拉（仅 AI target 时显示） */}
      {showRoleSelector && (
        <Select
          value={target.agentRole ?? "default-generalist"}
          onValueChange={(role) => onTargetChange({ ...target, agentRole: role ?? undefined })}
        >
          <SelectTrigger className="h-7 w-auto gap-1 border-none bg-muted/50 px-2 text-xs">
            <SelectValue>
              {AGENT_ROLES.find((r) => r.id === (target.agentRole ?? "default-generalist"))?.avatar}{" "}
              {AGENT_ROLES.find((r) => r.id === (target.agentRole ?? "default-generalist"))?.label}
            </SelectValue>
          </SelectTrigger>
          <SelectContent>
            {AGENT_ROLES.map((r) => (
              <SelectItem key={r.id} value={r.id}>
                <span className="flex items-center gap-1.5">
                  <span>{r.avatar}</span>
                  <span>{r.label}</span>
                </span>
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      )}

      {showNewSession && (
        <Button variant="ghost" size="icon-sm" onClick={onNewSession} aria-label="新建会话">
          <Plus className="size-3.5" />
        </Button>
      )}

      {toolbar && <div className="ml-auto">{toolbar}</div>}
    </div>
  )
}
