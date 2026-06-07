/**
 * ActivityStream（Chatter）——活动流组件
 * @author AaronZZH & Kiro
 *
 * @example
 * ```tsx
 * <ActivityStream entityType="document" entityId="123" />
 * ```
 */

"use client"

import { Calendar, CheckCircle2, Mail, Phone, Plus, Send } from "lucide-react"
import { useRef, useState } from "react"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import type { ActivityItem, ScheduledActivity } from "@/lib/api/rest/entity/activity"
import { notify } from "@/lib/notification"
import {
  useActivities,
  useAddComment,
  useCompleteSchedule,
  useCreateSchedule,
  useDeleteComment,
  useSchedules
} from "@/lib/queries/use-activities"
import { cn } from "@/lib/utils/cn"
import { formatTimeAgo } from "@/lib/utils/time"
import { useMentionSearch } from "../hooks/use-mention-search"

interface Props {
  entityType: string
  entityId: string
}

export function ActivityStream({ entityType, entityId }: Props) {
  return (
    <div className="border-t pt-4">
      <h3 className="mb-3 font-medium text-sm">活动</h3>
      <Tabs defaultValue="all">
        <TabsList className="h-8">
          <TabsTrigger value="all" className="text-xs">
            全部
          </TabsTrigger>
          <TabsTrigger value="comments" className="text-xs">
            评论
          </TabsTrigger>
          <TabsTrigger value="schedule" className="text-xs">
            待办
          </TabsTrigger>
        </TabsList>

        <TabsContent value="all">
          <CommentInput entityType={entityType} entityId={entityId} />
          <ActivityTimeline entityType={entityType} entityId={entityId} />
        </TabsContent>
        <TabsContent value="comments">
          <CommentInput entityType={entityType} entityId={entityId} />
          <ActivityTimeline entityType={entityType} entityId={entityId} filterType="comment" />
        </TabsContent>
        <TabsContent value="schedule">
          <ScheduleInput entityType={entityType} entityId={entityId} />
          <ScheduleList entityType={entityType} entityId={entityId} />
        </TabsContent>
      </Tabs>
    </div>
  )
}

function CommentInput({ entityType, entityId }: Props) {
  const [content, setContent] = useState("")
  const [mentionIds, setMentionIds] = useState<number[]>([])
  const [mentionQuery, setMentionQuery] = useState<string | null>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const { mutate: addComment, isPending } = useAddComment(entityType, entityId)
  const mentionUsers = useMentionSearch(mentionQuery)

  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const val = e.target.value
    setContent(val)
    // 检测光标前的 @query
    const cursor = e.target.selectionStart ?? val.length
    const before = val.slice(0, cursor)
    const match = before.match(/@(\w*)$/)
    setMentionQuery(match ? match[1] : null)
  }

  const insertMention = (user: { id: number; nickname: string; username: string }) => {
    const textarea = textareaRef.current
    if (!textarea) return
    const cursor = textarea.selectionStart ?? content.length
    const before = content.slice(0, cursor)
    const atIdx = before.lastIndexOf("@")
    const displayName = user.nickname || user.username
    const newContent = `${content.slice(0, atIdx)}@${displayName} ${content.slice(cursor)}`
    setContent(newContent)
    setMentionIds((prev) => (prev.includes(user.id) ? prev : [...prev, user.id]))
    setMentionQuery(null)
    // 移动光标到插入文字后
    const newCursor = atIdx + displayName.length + 2
    setTimeout(() => textarea.setSelectionRange(newCursor, newCursor), 0)
  }

  const handleSubmit = () => {
    const trimmed = content.trim()
    if (!trimmed) return
    addComment(
      { content: trimmed, mentions: mentionIds.map(String) },
      {
        onSuccess: () => {
          setContent("")
          setMentionIds([])
          notify.success("评论已发布")
        },
        onError: () => notify.error("发布失败，请重试")
      }
    )
  }

  return (
    <div className="relative mb-4 flex gap-2">
      <div className="relative flex-1">
        <Textarea
          ref={textareaRef}
          placeholder="写评论，支持 @提及用户..."
          value={content}
          onChange={handleChange}
          className="min-h-[72px] resize-none text-sm"
          onKeyDown={(e) => {
            if (e.key === "Escape") setMentionQuery(null)
            if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) handleSubmit()
          }}
        />
        {/* @提及下拉 */}
        {mentionQuery !== null && mentionUsers.length > 0 && (
          <div className="absolute top-full left-0 z-50 mt-1 w-56 rounded-md border bg-popover p-1 shadow-md">
            {mentionUsers.map((u) => (
              <div
                key={u.id}
                role="option"
                aria-selected={false}
                tabIndex={0}
                className="flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 text-sm hover:bg-accent"
                onMouseDown={(e) => {
                  e.preventDefault() // 阻止 textarea 失焦
                  insertMention(u)
                }}
                onKeyDown={(e) => e.key === "Enter" && insertMention(u)}
              >
                <Avatar size="sm">
                  <AvatarFallback>{(u.nickname || u.username).slice(0, 1)}</AvatarFallback>
                </Avatar>
                <span>{u.nickname || u.username}</span>
              </div>
            ))}
          </div>
        )}
      </div>
      <Button
        size="sm"
        className="self-end"
        disabled={!content.trim() || isPending}
        onClick={handleSubmit}
      >
        <Send className="size-4" />
      </Button>
    </div>
  )
}

function ActivityTimeline({
  entityType,
  entityId,
  filterType
}: Props & { filterType?: ActivityItem["type"] }) {
  const { data: activities, isLoading } = useActivities(entityType, entityId)
  const { mutate: deleteComment } = useDeleteComment(entityType, entityId)

  if (isLoading) return <ActivitySkeleton />

  const items = filterType ? activities?.filter((a) => a.type === filterType) : activities

  if (!items?.length) {
    return (
      <Empty className="py-8">
        <EmptyHeader>
          <EmptyTitle>暂无活动记录</EmptyTitle>
        </EmptyHeader>
      </Empty>
    )
  }

  return (
    <ol className="space-y-3">
      {items.map((item) => (
        <ActivityItemRow
          key={item.id}
          item={item}
          onDeleteComment={item.type === "comment" ? () => deleteComment(item.id) : undefined}
        />
      ))}
    </ol>
  )
}

function ActivityItemRow({
  item,
  onDeleteComment
}: {
  item: ActivityItem
  onDeleteComment?: () => void
}) {
  return (
    <li className="flex gap-3">
      <Avatar size="sm" className="shrink-0">
        <AvatarFallback>{item.actorName.slice(0, 1)}</AvatarFallback>
      </Avatar>
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <span className="font-medium text-sm">{item.actorName}</span>
            <span className="ml-1 text-muted-foreground text-xs">{getActivityLabel(item)}</span>
          </div>
          <div className="flex shrink-0 items-center gap-1">
            <span className="text-muted-foreground text-xs">{formatTimeAgo(item.createdAt)}</span>
            {onDeleteComment && (
              <button
                type="button"
                className="rounded p-0.5 text-muted-foreground hover:text-destructive"
                onClick={onDeleteComment}
                aria-label="删除评论"
              >
                ×
              </button>
            )}
          </div>
        </div>

        {item.type === "comment" && item.content && (
          <div className="mt-1 rounded-md bg-muted/50 px-3 py-2 text-sm">{item.content}</div>
        )}

        {item.type === "update" && item.changes && item.changes.length > 0 && (
          <div className="mt-1 space-y-0.5">
            {item.changes.map((c) => (
              <p key={c.field} className="text-muted-foreground text-xs">
                <span className="font-medium">{c.label}</span>：
                <span className="line-through opacity-60">{String(c.oldValue ?? "（空）")}</span>
                {" → "}
                <span>{String(c.newValue ?? "（空）")}</span>
              </p>
            ))}
          </div>
        )}
      </div>
    </li>
  )
}

function getActivityLabel(item: ActivityItem): string {
  switch (item.type) {
    case "create":
      return "创建了记录"
    case "update":
      return "修改了记录"
    case "status_change":
      return `更新了状态${item.content ? `：${item.content}` : ""}`
    case "comment":
      return "发表了评论"
    case "schedule":
      return "安排了活动"
    default:
      return "进行了操作"
  }
}

const SCHEDULE_LABELS: Record<ScheduledActivity["category"], string> = {
  todo: "待办",
  call: "电话",
  email: "邮件",
  meeting: "会议"
}

const SCHEDULE_ICONS: Record<ScheduledActivity["category"], React.ReactNode> = {
  todo: <CheckCircle2 className="size-3" />,
  call: <Phone className="size-3" />,
  email: <Mail className="size-3" />,
  meeting: <Calendar className="size-3" />
}

function ScheduleInput({ entityType, entityId }: Props) {
  const [open, setOpen] = useState(false)
  const [category, setCategory] = useState<ScheduledActivity["category"]>("todo")
  const [title, setTitle] = useState("")
  const [dueDate, setDueDate] = useState("")
  const { mutate: create, isPending } = useCreateSchedule(entityType, entityId)

  const handleCreate = () => {
    if (!title.trim() || !dueDate) return
    create(
      { category, title: title.trim(), dueDate },
      {
        onSuccess: () => {
          setTitle("")
          setDueDate("")
          setOpen(false)
          notify.success("活动已安排")
        }
      }
    )
  }

  if (!open) {
    return (
      <Button variant="outline" size="sm" className="mb-4 w-full" onClick={() => setOpen(true)}>
        <Plus className="size-4" />
        安排活动
      </Button>
    )
  }

  return (
    <div className="mb-4 space-y-3 rounded-lg border p-3">
      {/* 类型选择：ToggleGroup */}
      <ToggleGroup
        value={[category]}
        onValueChange={(v) => {
          if (v.length > 0) setCategory(v[v.length - 1] as ScheduledActivity["category"])
        }}
        variant="outline"
        size="sm"
        spacing={0}
      >
        {(["todo", "call", "email", "meeting"] as const).map((t) => (
          <ToggleGroupItem key={t} value={t} aria-label={SCHEDULE_LABELS[t]}>
            {SCHEDULE_ICONS[t]}
            <span className="ml-1">{SCHEDULE_LABELS[t]}</span>
          </ToggleGroupItem>
        ))}
      </ToggleGroup>

      <Input placeholder="活动标题" value={title} onChange={(e) => setTitle(e.target.value)} />
      <Input type="datetime-local" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
      <div className="flex justify-end gap-2">
        <Button variant="ghost" size="sm" onClick={() => setOpen(false)}>
          取消
        </Button>
        <Button size="sm" disabled={!title.trim() || !dueDate || isPending} onClick={handleCreate}>
          确认
        </Button>
      </div>
    </div>
  )
}

function ScheduleList({ entityType, entityId }: Props) {
  const { data: schedules, isLoading } = useSchedules(entityType, entityId)
  const { mutate: complete } = useCompleteSchedule(entityType, entityId)

  if (isLoading) return <ActivitySkeleton />

  if (!schedules?.length) {
    return (
      <Empty className="py-8">
        <EmptyHeader>
          <EmptyTitle>暂无待办活动</EmptyTitle>
          <EmptyDescription>点击上方按钮安排活动</EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }

  return (
    <ol className="space-y-2">
      {schedules.map((s) => (
        <li
          key={s.id}
          className={cn("flex items-start gap-3 rounded-lg border p-3", s.done && "opacity-50")}
        >
          <button
            type="button"
            className={cn(
              "mt-0.5 size-4 shrink-0 rounded-full border-2",
              s.done ? "border-primary bg-primary" : "border-muted-foreground hover:border-primary"
            )}
            onClick={() => !s.done && complete(s.id)}
            aria-label={s.done ? "已完成" : "标记完成"}
          />
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <span className={cn("text-sm", s.done && "line-through")}>{s.title}</span>
              <Badge variant="outline" className="text-[10px]">
                {SCHEDULE_LABELS[s.category]}
              </Badge>
            </div>
            <p className="mt-0.5 text-muted-foreground text-xs">
              {s.dueDate && `截止：${new Date(s.dueDate).toLocaleString("zh-CN")}`}
              {s.assigneeName && ` · ${s.assigneeName}`}
            </p>
          </div>
        </li>
      ))}
    </ol>
  )
}

function ActivitySkeleton() {
  return (
    <div className="space-y-3">
      {[1, 2, 3].map((i) => (
        <div key={i} className="flex gap-3">
          <Skeleton className="size-6 rounded-full" />
          <div className="flex-1 space-y-1">
            <Skeleton className="h-4 w-32" />
            <Skeleton className="h-3 w-full" />
          </div>
        </div>
      ))}
    </div>
  )
}
