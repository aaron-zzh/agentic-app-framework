/**
 * ChatterToolbar——顶部工具栏
 *
 * 布局：
 * 左：对话图标（点击展开会话列表 + 新建会话）
 * 中：角色选择器 + target 切换（panel/page 模式）
 * 右：语音电话 + 操作按钮（panel/page 模式）
 *
 * dialog 模式下操作按钮（全屏/嵌入/关闭）由 GlobalChatterDialog 标题栏提供，不在此重复渲染
 *
 * @author AaronZZH & Kiro
 */

"use client"

import {
  ThreadListItemPrimitive,
  ThreadListPrimitive,
  useAuiState,
  useThreadListItemRuntime,
  useVoiceControls,
  useVoiceState
} from "@assistant-ui/react"
import {
  Archive,
  Bot,
  Maximize2,
  MessageSquareIcon,
  MoreHorizontal,
  PanelRight,
  PanelRightClose,
  Phone,
  PhoneOff,
  PlusIcon,
  Sparkles,
  Trash2,
  User,
  X
} from "lucide-react"
import { type ReactNode, useEffect, useState } from "react"
import { toast } from "sonner"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import type { ChatterPreset, ChatterTarget } from "@/features/chatter/types"
import { type AssistantItem, useAssistants } from "@/lib/api/rest/ai"
import { useChatterStore } from "@/lib/store/chatter-store"
import { cn } from "@/lib/utils"

interface ChatterToolbarProps {
  preset: ChatterPreset
  target: ChatterTarget
  onTargetChange: (target: ChatterTarget) => void
  onNewSession?: () => void
  toolbar?: ReactNode
  dragProps?: React.HTMLAttributes<HTMLDivElement>
  availableModes?: ("panel" | "page")[]
  /** 隐藏 AI/Kiro 切换和角色选择器 */
  hideRoleSwitch?: boolean
}

interface AssistantRoleOption {
  value: string
  assistantId: string
  assistantName: string
  roleKey: string
  roleName: string
}

function roleOptionValue(assistantId: string, roleKey: string): string {
  return JSON.stringify([assistantId, roleKey])
}

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

export function ChatterToolbar({
  preset,
  target,
  onTargetChange,
  onNewSession,
  toolbar,
  dragProps,
  availableModes = ["panel", "page"],
  hideRoleSwitch = false
}: ChatterToolbarProps) {
  const targets = getAvailableTargets(preset)
  const showRoleSelector = target.type === "ai"
  const layoutOverride = useChatterStore((s) => s.layoutOverride)
  const setLayoutOverride = useChatterStore((s) => s.setLayoutOverride)
  const setOpen = useChatterStore((s) => s.setOpen)
  const setMode = useChatterStore((s) => s.setMode)
  const isFloating = layoutOverride === null
  const isPanelMode = layoutOverride === "panel"
  const isPageMode = layoutOverride === "page"
  const canPanel = availableModes.includes("panel")
  const canPage = availableModes.includes("page")
  const hasVoice = preset === "ai" || preset === "kiro"

  const { data: assistants } = useAssistants()
  const roleOptions: AssistantRoleOption[] = (assistants ?? []).flatMap((assistant) =>
    (assistant.roles ?? []).map((role) => ({
      value: roleOptionValue(assistant.assistantId, role.roleKey),
      assistantId: assistant.assistantId,
      assistantName: assistant.name,
      roleKey: role.roleKey,
      roleName: role.name
    }))
  )
  const currentRole = roleOptions.find(
    (option) => option.assistantId === target.assistantId && option.roleKey === target.agentRole
  )

  // 旧页面配置只有 roleKey；仅当 API 中唯一匹配时一次性补齐 assistantId，随后始终原子发送。
  useEffect(() => {
    if (target.type !== "ai" || target.assistantId || !target.agentRole || !assistants) return
    const matches = roleOptions.filter((option) => option.roleKey === target.agentRole)
    if (matches.length !== 1) return
    const [option] = matches
    onTargetChange({
      ...target,
      assistantId: option.assistantId,
      agentRole: option.roleKey,
      agentSkill: undefined
    })
  }, [assistants, onTargetChange, roleOptions, target])

  const roleSelector = showRoleSelector ? (
    <AssistantRoleSelect
      assistants={assistants ?? []}
      options={roleOptions}
      current={currentRole}
      onChange={(option) =>
        onTargetChange({
          ...target,
          type: "ai",
          assistantId: option.assistantId,
          agentRole: option.roleKey,
          agentSkill: undefined
        })
      }
    />
  ) : null

  return (
    <div
      {...dragProps}
      className={`flex items-center gap-2 border-b px-3 py-2 overflow-hidden${dragProps?.className ? ` ${dragProps.className}` : ""}`}
    >
      {/* 左：对话图标，点击展开会话列表 + 新建会话 */}
      <SessionPopover onNewSession={onNewSession} />

      {/* 中：浮窗直接显示角色选择；panel/page 保留 target 切换。 */}
      {isFloating ? (
        (roleSelector ?? (
          <span className="min-w-0 flex-1 truncate font-medium text-sm">
            {TARGET_LABELS[target.type]}
          </span>
        ))
      ) : hideRoleSwitch ? (
        <span className="flex-1" />
      ) : (
        <>
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
            {targets.map((targetType) => (
              <ToggleGroupItem
                key={targetType}
                value={targetType}
                aria-label={TARGET_LABELS[targetType]}
              >
                {TARGET_ICONS[targetType]}
                <span className="ml-1 text-xs">{TARGET_LABELS[targetType]}</span>
              </ToggleGroupItem>
            ))}
          </ToggleGroup>

          {roleSelector}
        </>
      )}

      {toolbar && <div className="ml-auto">{toolbar}</div>}

      {/* 右：语音电话 + 操作按钮 */}
      <div className="ml-auto flex shrink-0 items-center gap-0.5">
        {/* 语音电话按钮 */}
        {hasVoice && <VoiceButton />}

        {/* dialog 模式：嵌入侧边 + 全屏 + 关闭 */}
        {isFloating && (
          <>
            {canPanel && (
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="嵌入侧边"
                onClick={() => {
                  setMode("panel")
                  setLayoutOverride("panel")
                  setOpen(true)
                }}
              >
                <PanelRight className="size-3.5" />
              </Button>
            )}
            {canPage && (
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="全屏对话"
                onClick={() => {
                  setMode("page")
                  setLayoutOverride("page")
                  setOpen(true)
                }}
              >
                <Maximize2 className="size-3.5" />
              </Button>
            )}
            <Button variant="ghost" size="icon-sm" aria-label="关闭" onClick={() => setOpen(false)}>
              <X className="size-3.5" />
            </Button>
          </>
        )}

        {/* panel/page 模式操作按钮 */}
        {!isFloating &&
          (isPageMode ? (
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label="返回"
              onClick={() => {
                setMode("dialog")
                setLayoutOverride(null)
                setOpen(true)
              }}
            >
              <X className="size-3.5" />
            </Button>
          ) : (
            <>
              {canPage && (
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label="全屏对话"
                  onClick={() => {
                    setMode("page")
                    setLayoutOverride("page")
                    setOpen(true)
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
                    setMode("dialog")
                    setLayoutOverride(null)
                    setOpen(false)
                  }}
                >
                  <PanelRightClose className="size-3.5" />
                </Button>
              )}
            </>
          ))}
      </div>
    </div>
  )
}

function AssistantRoleSelect({
  assistants,
  options,
  current,
  onChange
}: {
  assistants: AssistantItem[]
  options: AssistantRoleOption[]
  current?: AssistantRoleOption
  onChange: (option: AssistantRoleOption) => void
}) {
  return (
    <Select
      value={current?.value ?? null}
      onValueChange={(value) => {
        const option = options.find((item) => item.value === value)
        if (option) onChange(option)
      }}
    >
      <SelectTrigger
        size="sm"
        aria-label="选择助理角色"
        className="h-7 min-w-0 flex-1 justify-start gap-1.5 overflow-hidden border-none bg-muted/50 px-2 text-xs"
      >
        <Avatar className="size-4 shrink-0">
          <AvatarFallback className="text-[8px]">
            {(current?.assistantName ?? "AI").charAt(0)}
          </AvatarFallback>
        </Avatar>
        <SelectValue className="min-w-0 truncate">
          {current ? `${current.assistantName} · ${current.roleName}` : "AI 助理 · 自动角色"}
        </SelectValue>
      </SelectTrigger>
      <SelectContent align="start" className="min-w-56">
        {assistants.map((assistant) => (
          <SelectGroup key={assistant.assistantId}>
            <SelectLabel>{assistant.name}</SelectLabel>
            {(assistant.roles ?? []).map((role) => {
              const value = roleOptionValue(assistant.assistantId, role.roleKey)
              return (
                <SelectItem key={value} value={value}>
                  <Avatar className="size-5">
                    <AvatarFallback className="text-[9px]">{role.name.charAt(0)}</AvatarFallback>
                  </Avatar>
                  <span>{role.name}</span>
                </SelectItem>
              )
            })}
          </SelectGroup>
        ))}
      </SelectContent>
    </Select>
  )
}

/**
 * 会话列表单项——用 ThreadListItemPrimitive 消费 runtime 状态。
 *
 * rename/archive/unarchive/delete 通过 useThreadListItemRuntime() 调用而非官方具名
 * ThreadListItemPrimitive.Archive/Delete/Unarchive 组件——那些组件渲染为独立 <button>，
 * 直接嵌入 DropdownMenuItem（base-ui <div> 语义）会产生嵌套交互元素问题；
 * useThreadListItemRuntime() 是同一套官方运行时能力的公开 hook 形式。
 */
function SessionListItem({ onAfterSwitch }: { onAfterSwitch: () => void }) {
  const itemRuntime = useThreadListItemRuntime()
  const title = useAuiState((s) => s.threadListItem.title)
  const status = useAuiState((s) => s.threadListItem.status)

  return (
    <ThreadListItemPrimitive.Root className="group flex w-full items-center gap-1 rounded-md px-1 hover:bg-muted">
      <ThreadListItemPrimitive.Trigger
        className="flex flex-1 items-center gap-2 overflow-hidden px-2 py-2 text-left"
        onClick={onAfterSwitch}
      >
        <MessageSquareIcon className="size-3.5 shrink-0 text-muted-foreground" />
        <span className="flex-1 truncate text-sm">
          <ThreadListItemPrimitive.Title fallback="未命名会话" />
        </span>
      </ThreadListItemPrimitive.Trigger>
      <DropdownMenu>
        <DropdownMenuTrigger
          className="invisible flex size-6 shrink-0 items-center justify-center rounded hover:bg-background group-hover:visible"
          aria-label="会话操作"
        >
          <MoreHorizontal className="size-3.5" />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem
            onClick={async () => {
              const nextTitle = window.prompt("重命名会话", title ?? "")
              if (!nextTitle || nextTitle === title) return
              try {
                await itemRuntime.rename(nextTitle)
              } catch {
                toast.error("重命名失败，请重试")
              }
            }}
          >
            重命名
          </DropdownMenuItem>
          {status === "archived" ? (
            <DropdownMenuItem
              onClick={async () => {
                try {
                  await itemRuntime.unarchive()
                } catch {
                  toast.error("取消归档失败，请重试")
                }
              }}
            >
              <Archive className="size-3.5" />
              取消归档
            </DropdownMenuItem>
          ) : (
            <DropdownMenuItem
              onClick={async () => {
                try {
                  await itemRuntime.archive()
                } catch {
                  toast.error("归档失败，请重试")
                }
              }}
            >
              <Archive className="size-3.5" />
              归档
            </DropdownMenuItem>
          )}
          <DropdownMenuItem
            variant="destructive"
            onClick={async () => {
              if (!window.confirm("确定删除该会话？此操作不可恢复。")) return
              try {
                await itemRuntime.delete()
              } catch {
                toast.error("删除失败，请重试")
              }
            }}
          >
            <Trash2 className="size-3.5" />
            删除
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </ThreadListItemPrimitive.Root>
  )
}

/**
 * 对话图标 + 会话列表 + 新建会话 popover。
 *
 * AAF-114 官方模式改造：完全由 ThreadListPrimitive/ThreadListItemPrimitive 消费 runtime
 * 的 threads/archivedThreads 状态，不再自建 sessions/total/loading 等并行状态——
 * 列表数据源统一来自 ag-ui-runtime.tsx 的 threadList adapter（TanStack Query 驱动）。
 */
function SessionPopover({ onNewSession }: { onNewSession?: () => void }) {
  const [open, setOpen] = useState(false)
  const [showArchived, setShowArchived] = useState(false)

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        className="inline-flex size-7 items-center justify-center rounded-md hover:bg-muted"
        aria-label="会话列表"
      >
        <MessageSquareIcon className="size-3.5" />
      </PopoverTrigger>
      <PopoverContent
        side="bottom"
        align="start"
        className="w-72 p-0"
        style={{ maxHeight: "70vh", display: "flex", flexDirection: "column" }}
      >
        <ThreadListPrimitive.Root className="flex min-h-0 flex-1 flex-col">
          {/* 新建会话 + 归档切换，合并为一个 header 区域 */}
          <div className="flex shrink-0 items-center gap-2 p-3">
            <ThreadListPrimitive.New
              className="flex flex-1 items-center justify-center gap-2 rounded-full border px-3 py-1.5 text-sm hover:bg-muted"
              onClick={() => {
                setOpen(false)
                onNewSession?.()
              }}
            >
              <PlusIcon className="size-3.5" />
              新建会话
            </ThreadListPrimitive.New>
            <button
              type="button"
              className={cn(
                "shrink-0 rounded-full border px-2.5 py-1.5 text-xs",
                showArchived ? "bg-muted" : "hover:bg-muted"
              )}
              onClick={() => setShowArchived((v) => !v)}
            >
              {showArchived ? "常规" : "归档"}
            </button>
          </div>

          {/* 会话列表 */}
          <div className="flex-1 overflow-y-auto px-2 pb-2">
            <ThreadListPrimitive.Items archived={showArchived}>
              {() => <SessionListItem onAfterSwitch={() => setOpen(false)} />}
            </ThreadListPrimitive.Items>
          </div>
        </ThreadListPrimitive.Root>
      </PopoverContent>
    </Popover>
  )
}

function VoiceButton() {
  const voiceState = useVoiceState()
  const { connect, disconnect } = useVoiceControls()
  const isActive = voiceState != null
  return (
    <Button
      variant={isActive ? "destructive" : "ghost"}
      size="icon-sm"
      aria-label={isActive ? "结束语音对话" : "实时语音对话"}
      onClick={() => (isActive ? disconnect() : connect())}
    >
      {isActive ? <PhoneOff className="size-3.5" /> : <Phone className="size-3.5" />}
    </Button>
  )
}
