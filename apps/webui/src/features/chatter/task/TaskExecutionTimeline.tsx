"use client"

/**
 * TaskExecutionTimeline——展示委托任务的安全公开事件与实时进度。
 * @author AaronZZH & Kiro
 */

import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useEffect, useMemo, useRef, useState } from "react"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  AAF_AI_TASK_EVENT_TYPES,
  type AafAiTaskEvent,
  type AafAiTaskEventType,
  appendAafAiTaskEvent,
  delegatedTaskApi,
  delegatedTaskKeys,
  getDelegatedTaskEventStreamUrl,
  parseAafAiTaskEvent
} from "@/lib/api/rest/ai"

interface TimelineItem {
  id: string
  time: string
  icon: string
  label: string
  detail: string | null
}
const EVENT_META: Partial<Record<AafAiTaskEventType, { icon: string; label: string }>> = {
  "aaf.task.started": { icon: "🆕", label: "任务开始" },
  "aaf.task.completed": { icon: "🎉", label: "任务完成" },
  "aaf.task.failed": { icon: "❌", label: "任务失败" },
  "aaf.task.canceled": { icon: "⛔", label: "任务取消" },
  "aaf.run.failed": { icon: "⚠️", label: "本轮失败，等待恢复" },
  "aaf.tool.started": { icon: "🔧", label: "工具调用开始" },
  "aaf.tool.completed": { icon: "✅", label: "工具调用完成" },
  "aaf.tool.failed": { icon: "⚠️", label: "工具调用失败" },
  "aaf.authorization.requested": { icon: "🔐", label: "请求授权" },
  "aaf.authorization.granted": { icon: "🔓", label: "授权通过" },
  "aaf.authorization.denied": { icon: "🔒", label: "授权拒绝" },
  "aaf.clarification.requested": { icon: "❓", label: "请求澄清" }
}
function detail(data: AafAiTaskEvent["data"]): string | null {
  const tool = data.toolName
  if (typeof tool === "string") return `工具: ${tool}`
  const action = data.action
  if (typeof action === "string") return `操作: ${action}`
  const code = data.errorCode
  if (typeof code === "string") return `状态码: ${code}`
  const length = data.contentLength
  return typeof length === "number" ? `已接收 ${length} 字符` : null
}
function parseEvent(event: AafAiTaskEvent): TimelineItem {
  const meta = EVENT_META[event.type] ?? {
    icon: "•",
    label: event.type.replace("aaf.", "").replaceAll(".", " · ")
  }
  return {
    id: event.eventId,
    time: new Date(event.createdAt).toLocaleTimeString(),
    icon: meta.icon,
    label: meta.label,
    detail: detail(event.data)
  }
}
interface TaskExecutionTimelineProps {
  taskId: string
  live?: boolean
}
export function TaskExecutionTimeline(props: TaskExecutionTimelineProps) {
  return <TaskExecutionTimelineContent key={props.taskId} {...props} />
}
function TaskExecutionTimelineContent({ taskId, live = true }: TaskExecutionTimelineProps) {
  const queryClient = useQueryClient()
  const scrollRef = useRef<HTMLDivElement>(null)
  const [streamCursor, setStreamCursor] = useState<number | null>(null)
  const queryKey = useMemo(() => delegatedTaskKeys.events(taskId), [taskId])
  const { data: snapshot, isLoading } = useQuery({
    queryKey,
    queryFn: () => delegatedTaskApi.listEvents(taskId)
  })
  useEffect(() => {
    if (snapshot && streamCursor === null) setStreamCursor(snapshot.nextEventOffset)
  }, [snapshot, streamCursor])
  useEffect(() => {
    if (!live || streamCursor === null) return
    const source = new EventSource(getDelegatedTaskEventStreamUrl(taskId, streamCursor), {
      withCredentials: true
    })
    const handleEvent = (message: MessageEvent<string>) => {
      const event = parseAafAiTaskEvent(message.data)
      if (!event) return
      queryClient.setQueryData(queryKey, (current: typeof snapshot | undefined) =>
        current ? appendAafAiTaskEvent(current, event) : current
      )
    }
    for (const type of AAF_AI_TASK_EVENT_TYPES) source.addEventListener(type, handleEvent)
    return () => source.close()
  }, [live, queryClient, queryKey, streamCursor, taskId])
  const events = snapshot?.events ?? []
  const items = events.map(parseEvent)
  useEffect(() => {
    if (items.length > 0)
      scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" })
  }, [items.length])
  if (isLoading)
    return <p className="py-4 text-center text-muted-foreground text-sm">正在加载执行记录…</p>
  if (items.length === 0)
    return <p className="py-4 text-center text-muted-foreground text-sm">暂无执行记录</p>
  return (
    <ScrollArea className="h-80" ref={scrollRef}>
      <div className="flex flex-col gap-1 p-2">
        {items.map((item) => (
          <div key={item.id} className="flex items-start gap-2 rounded px-2 py-1 hover:bg-muted/50">
            <span className="shrink-0 text-sm">{item.icon}</span>
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="font-medium text-sm">{item.label}</span>
                <span className="ml-auto text-muted-foreground text-xs">{item.time}</span>
              </div>
              {item.detail ? (
                <p className="truncate text-muted-foreground text-xs">{item.detail}</p>
              ) : null}
            </div>
          </div>
        ))}
      </div>
    </ScrollArea>
  )
}
