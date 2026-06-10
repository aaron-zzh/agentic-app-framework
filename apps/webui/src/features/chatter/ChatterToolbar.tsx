/**
 * ChatterToolbar——顶部工具栏
 * TargetSwitcher（切换 AI/Kiro/用户）+ RoleSelector + SessionManager + 外部 toolbar slot
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Bot, Maximize2, PanelRightClose, Plus, Sparkles, User, X } from "lucide-react"
import { useRouter } from "next/navigation"
import type { ReactNode } from "react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { useAssistants } from "@/lib/queries/use-assistants"
import { useChatterStore } from "@/lib/store/chatter-store"
import type { ChatterPreset, ChatterTarget } from "./types"

interface ChatterToolbarProps {
  preset: ChatterPreset
  target: ChatterTarget
  onTargetChange: (target: ChatterTarget) => void
  onNewSession?: () => void
  toolbar?: ReactNode
}

/** 静态 fallback（后端未返回时使用） */
const FALLBACK_ROLES = [
  { roleId: "default-generalist", name: "通用助理", avatar: undefined },
  { roleId: "content-creator-role", name: "内容创作", avatar: undefined }
]

/** preset 决定显示哪些 target 选项 */
function getAvailableTargets(preset: ChatterPreset): ChatterTarget["type"][] {
  switch (preset) {
    case "ai":
      return ["ai", "kiro"]
    case "kiro":
      return ["kiro", "ai"]
    case "livechat":
      return ["ai", "kiro", "user"]
    default:
      return []
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

  const layoutOverride = useChatterStore((s) => s.layoutOverride)
  const setLayoutOverride = useChatterStore((s) => s.setLayoutOverride)
  const setOpen = useChatterStore((s) => s.setOpen)
  const isPanelMode = layoutOverride === "panel"
  const router = useRouter()

  const { data: assistants } = useAssistants()
  // 将助理树展开为扁平角色列表（单角色助理用助理名，多角色助理用"助理名·角色名"）
  const roles = assistants
    ? assistants.flatMap((a) =>
        (a.roles ?? []).map((r) => ({
          roleId: r.roleId,
          name: (a.roles?.length ?? 0) > 1 ? `${a.name} · ${r.name}` : a.name,
          avatar: a.avatar
        }))
      )
    : FALLBACK_ROLES
  const currentRole =
    roles.find((r) => r.roleId === (target.agentRole ?? "default-generalist")) ?? roles[0]

  return (
    <div className="flex items-center gap-2 border-b px-3 py-2">
      <ToggleGroup
        value={[target.type]}
        onValueChange={(value: string[]) => {
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
          <SelectTrigger className="h-7 w-auto gap-1.5 border-none bg-muted/50 px-2 text-xs">
            <Avatar className="size-4">
              <AvatarImage src={currentRole?.avatar} />
              <AvatarFallback className="text-[8px]">{currentRole?.name?.charAt(0)}</AvatarFallback>
            </Avatar>
            <SelectValue>{currentRole?.name}</SelectValue>
          </SelectTrigger>
          <SelectContent>
            {roles.map((r) => (
              <SelectItem key={r.roleId} value={r.roleId}>
                <span className="flex items-center gap-2">
                  <Avatar className="size-5">
                    <AvatarImage src={r.avatar} />
                    <AvatarFallback className="text-[9px]">{r.name.charAt(0)}</AvatarFallback>
                  </Avatar>
                  <span>{r.name}</span>
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

      {/* panel/page 模式才显示操作区（dialog 模式的按钮在标题栏） */}
      {(isPanelMode || layoutOverride === "page") && (
        <div className={`flex items-center gap-0.5 ${toolbar ? "" : "ml-auto"}`}>
          {layoutOverride === "page" ? (
            <Button variant="ghost" size="icon-sm" aria-label="返回" onClick={() => router.back()}>
              <X className="size-3.5" />
            </Button>
          ) : (
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label="全屏对话"
              onClick={() => {
                setOpen(false)
                router.push("/ai/chat")
              }}
            >
              <Maximize2 className="size-3.5" />
            </Button>
          )}

          {isPanelMode && (
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label="切换为浮动"
              onClick={() => {
                setLayoutOverride(null)
                setOpen(false)
              }}
            >
              <PanelRightClose className="size-3.5" />
            </Button>
          )}
        </div>
      )}
    </div>
  )
}
