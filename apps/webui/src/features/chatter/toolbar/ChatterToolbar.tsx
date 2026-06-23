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

import { useVoiceControls, useVoiceState } from "@assistant-ui/react"
import {
  Bot,
  Maximize2,
  MessageSquareIcon,
  PanelRight,
  PanelRightClose,
  Phone,
  PhoneOff,
  PlusIcon,
  Sparkles,
  User,
  X
} from "lucide-react"
import { type ReactNode, useCallback, useEffect, useRef, useState } from "react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import type { ChatterPreset, ChatterTarget } from "@/features/chatter/types"
import { chatApi } from "@/lib/api/rest/ai/chat"
import { useAssistants } from "@/lib/queries/use-assistants"
import { useChatterStore } from "@/lib/store/chatter-store"

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

const FALLBACK_ROLES = [
  { roleId: "default-generalist", name: "通用助理", avatar: undefined },
  { roleId: "content-creator-role", name: "内容创作", avatar: undefined }
]

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
    <div
      {...dragProps}
      className={`flex items-center gap-2 border-b px-3 py-2 overflow-hidden${dragProps?.className ? ` ${dragProps.className}` : ""}`}
    >
      {/* 左：对话图标，点击展开会话列表 + 新建会话 */}
      <SessionPopover onNewSession={onNewSession} />

      {/* 中：角色名称（dialog）或 target 切换+角色选择（panel/page） */}
      {isFloating ? (
        <span className="flex-1 truncate font-medium text-sm">
          {currentRole?.name ?? "AI 助理"}
        </span>
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
            {targets.map((t) => (
              <ToggleGroupItem key={t} value={t} aria-label={TARGET_LABELS[t]}>
                {TARGET_ICONS[t]}
                <span className="ml-1 text-xs">{TARGET_LABELS[t]}</span>
              </ToggleGroupItem>
            ))}
          </ToggleGroup>

          {showRoleSelector && (
            <Select
              value={target.agentRole ?? "default-generalist"}
              onValueChange={(role) => onTargetChange({ ...target, agentRole: role ?? undefined })}
            >
              <SelectTrigger className="h-7 w-auto gap-1.5 border-none bg-muted/50 px-2 text-xs">
                <Avatar className="size-4">
                  <AvatarImage src={currentRole?.avatar} />
                  <AvatarFallback className="text-[8px]">
                    {currentRole?.name?.charAt(0)}
                  </AvatarFallback>
                </Avatar>
                <SelectValue>{currentRole?.name}</SelectValue>
              </SelectTrigger>
              <SelectContent>
                {roles.map((r, i) => (
                  <SelectItem key={r.roleId ?? i} value={r.roleId}>
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

/** 对话图标 + 会话列表（模糊搜索 + 滚动加载更多）+ 新建会话 popover */
function SessionPopover({ onNewSession }: { onNewSession?: () => void }) {
  const [open, setOpen] = useState(false)
  const [keyword, setKeyword] = useState("")
  const [sessions, setSessions] = useState<{ id: string; title: string }[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const PAGE_SIZE = 10
  const pageRef = useRef(1)
  const listRef = useRef<HTMLDivElement>(null)
  const loadingRef = useRef(false)

  const load = useCallback(
    async (reset = false) => {
      if (loadingRef.current) return
      const nextPage = reset ? 1 : pageRef.current
      loadingRef.current = true
      setLoading(true)
      try {
        const res = await chatApi.pageListSessions({
          page: nextPage,
          pageSize: PAGE_SIZE,
          search: keyword || undefined
        })
        const list = res.list ?? []
        const tot = res.total ?? 0
        setSessions((prev) => (reset ? list : [...prev, ...list]))
        setTotal(tot)
        pageRef.current = nextPage + 1
      } catch {
        // 静默失败
      } finally {
        loadingRef.current = false
        setLoading(false)
      }
    },
    [keyword]
  )

  // 打开时初始加载
  useEffect(() => {
    if (!open) return
    pageRef.current = 1
    setSessions([])
    load(true)
  }, [open, load])

  // 搜索防抖
  useEffect(() => {
    if (!open) return
    const timer = setTimeout(() => {
      pageRef.current = 1
      setSessions([])
      load(true)
    }, 300)
    return () => clearTimeout(timer)
  }, [open, load])

  // 滚动到底部加载更多
  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const el = e.currentTarget
      if (
        el.scrollHeight - el.scrollTop - el.clientHeight < 40 &&
        (sessions?.length ?? 0) < total &&
        !loadingRef.current
      ) {
        load(false)
      }
    },
    [sessions?.length, total, load]
  )

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
        {/* 新建会话 + 搜索框，合并为一个 header 区域 */}
        <div className="flex shrink-0 flex-col gap-2 p-3">
          <Button
            variant="outline"
            size="sm"
            className="w-full gap-2 rounded-full"
            onClick={() => {
              onNewSession?.()
              setOpen(false)
            }}
          >
            <PlusIcon className="size-3.5" />
            新建会话
          </Button>
          <div className="flex items-center gap-2 rounded-md border bg-muted/50 px-3 py-1.5">
            <input
              type="text"
              placeholder="搜索会话..."
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              className="flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
            />
          </div>
        </div>

        {/* 会话列表 */}
        <div ref={listRef} className="flex-1 overflow-y-auto" onScroll={handleScroll}>
          {sessions.length === 0 && !loading ? (
            <p className="px-3 py-6 text-center text-muted-foreground text-xs">
              {keyword ? "没有匹配的会话" : "暂无历史会话"}
            </p>
          ) : (
            sessions.map((s) => (
              <button
                key={s.id}
                type="button"
                className="flex w-full items-center gap-2 px-3 py-2 text-left hover:bg-muted"
                onClick={() => setOpen(false)}
              >
                <MessageSquareIcon className="size-3.5 shrink-0 text-muted-foreground" />
                <span className="flex-1 truncate text-sm">{s.title || "未命名会话"}</span>
              </button>
            ))
          )}
          {loading && <p className="py-2 text-center text-muted-foreground text-xs">加载中...</p>}
          {!loading && sessions.length > 0 && sessions.length >= total && (
            <p className="py-2 text-center text-muted-foreground text-xs">已加载全部</p>
          )}
        </div>
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
