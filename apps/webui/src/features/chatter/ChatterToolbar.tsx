/**
 * ChatterToolbar——顶部工具栏
 * TargetSwitcher（切换 AI/Kiro/用户）+ SessionManager + 外部 toolbar slot
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Bot, Plus, Sparkles, User } from "lucide-react"
import type { ReactNode } from "react"
import { Button } from "@/components/ui/button"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import type { ChatterPreset, ChatterTarget } from "./types"

interface ChatterToolbarProps {
  preset: ChatterPreset
  target: ChatterTarget
  onTargetChange: (target: ChatterTarget) => void
  onNewSession?: () => void
  toolbar?: ReactNode
}

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
 * 包含 target 切换按钮组 + 新建会话按钮 + 外部注入 toolbar
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

      {showNewSession && (
        <Button variant="ghost" size="icon-sm" onClick={onNewSession} aria-label="新建会话">
          <Plus className="size-3.5" />
        </Button>
      )}

      {toolbar && <div className="ml-auto">{toolbar}</div>}
    </div>
  )
}
