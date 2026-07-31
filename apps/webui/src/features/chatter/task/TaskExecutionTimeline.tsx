"use client"

/**
 * TaskExecutionTimeline——展示委托任务的历史与实时执行事件。
 * @author AaronZZH & Kiro
 */

import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useEffect, useMemo, useRef } from "react"
import { Badge } from "@/components/ui/badge"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  DELEGATED_TASK_EVENT_TYPES,
  type DelegatedTaskEventType,
  type DelegatedTaskEventVO,
  delegatedTaskApi,
  delegatedTaskKeys,
  getDelegatedTaskEventStreamUrl,
  parseDelegatedTaskEvent
} from "@/lib/api/rest/ai"

interface TimelineItem {
  id: string
  time: string
  parentExecutionId: string | null
  icon: string
  label: string
  detail: string | null
  durationSeconds?: number
  tokens?: number
}

const EVENT_META: Record<DelegatedTaskEventType, { icon: string; label: string }> = {
  EXECUTION_STARTED: { icon: "🆕", label: "执行开始" },
  EXECUTION_COMPLETED: { icon: "🎉", label: "执行完成" },
  EXECUTION_FAILED: { icon: "❌", label: "执行失败" },
  EXECUTION_CANCELED: { icon: "⛔", label: "执行取消" },
  EXECUTION_PAUSED: { icon: "⏸️", label: "执行暂停" },
  EXECUTION_RESUMED: { icon: "▶️", label: "执行恢复" },
  COMMAND_REJECTED: { icon: "🚫", label: "命令拒绝" },
  RUN_STARTED: { icon: "▶️", label: "运行开始" },
  RUN_COMPLETED: { icon: "✅", label: "运行完成" },
  RUN_FAILED: { icon: "❌", label: "运行失败" },
  MESSAGE_STARTED: { icon: "💬", label: "消息开始" },
  MESSAGE_DELTA: { icon: "✍️", label: "消息增量" },
  MESSAGE_COMPLETED: { icon: "💬", label: "消息完成" },
  MODEL_CALL_STARTED: { icon: "🧠", label: "模型调用开始" },
  MODEL_CALL_COMPLETED: { icon: "🧠", label: "模型调用完成" },
  MODEL_CALL_FAILED: { icon: "⚠️", label: "模型调用失败" },
  TOOL_CALL_STARTED: { icon: "🔧", label: "工具调用开始" },
  TOOL_CALL_COMPLETED: { icon: "🔧", label: "工具调用完成" },
  TOOL_CALL_FAILED: { icon: "⚠️", label: "工具调用失败" },
  AUTHORIZATION_REQUESTED: { icon: "🔐", label: "请求授权" },
  AUTHORIZATION_GRANTED: { icon: "🔓", label: "授权通过" },
  AUTHORIZATION_DENIED: { icon: "🔒", label: "授权拒绝" },
  AUTHORIZATION_REVOKED: { icon: "🚫", label: "授权撤销" },
  APPROVAL_REQUESTED: { icon: "🙋", label: "请求确认" },
  APPROVAL_RESOLVED: { icon: "✅", label: "确认完成" },
  SUBTASK_CREATED: { icon: "🔀", label: "子任务创建" },
  SUBTASK_STARTED: { icon: "▶️", label: "子任务开始" },
  SUBTASK_COMPLETED: { icon: "✅", label: "子任务完成" },
  SUBTASK_FAILED: { icon: "❌", label: "子任务失败" },
  SUBTASK_CANCELED: { icon: "⛔", label: "子任务取消" },
  VALIDATION_STARTED: { icon: "🔎", label: "验证开始" },
  VALIDATION_COMPLETED: { icon: "✅", label: "验证完成" },
  VALIDATION_FAILED: { icon: "⚠️", label: "验证未通过" },
  RECOVERY_STARTED: { icon: "♻️", label: "恢复开始" },
  RECOVERY_COMPLETED: { icon: "✅", label: "恢复完成" },
  OWNERSHIP_TRANSFERRED: { icon: "👤", label: "执行权转交" },
  TASK_STATUS_CHANGED: { icon: "📌", label: "任务状态变更" },
  CONTROL_MODE_CHANGED: { icon: "🎛️", label: "控制模式变更" },
  INPUT_CANCELED: { icon: "⛔", label: "输入取消" },
  INPUT_MODIFIED: { icon: "✏️", label: "输入修改" },
  INPUT_SUPPLEMENTED: { icon: "➕", label: "输入补充" },
  INPUT_UNRELATED: { icon: "↪️", label: "无关输入" }
}

function payloadString(payload: Record<string, unknown>, key: string): string | null {
  const value = payload[key]
  return typeof value === "string" && value.length > 0 ? value : null
}

function payloadNumber(payload: Record<string, unknown>, key: string): number | undefined {
  const value = payload[key]
  return typeof value === "number" && Number.isFinite(value) ? value : undefined
}

function parseEvent(event: DelegatedTaskEventVO): TimelineItem {
  const meta = EVENT_META[event.type]
  const payload = event.payload
  const toolName = payloadString(payload, "toolName")
  const reason = payloadString(payload, "reason")
  const text = payloadString(payload, "text")
  const modelId = payloadString(payload, "modelId")
  const taskStatus = payloadString(payload, "taskStatus")
  const detail = toolName
    ? `工具: ${toolName}`
    : (reason ?? text ?? (modelId ? `模型: ${modelId}` : taskStatus))
  const inputTokens = payloadNumber(payload, "inputTokens") ?? 0
  const outputTokens = payloadNumber(payload, "outputTokens") ?? 0
  const tokens = inputTokens + outputTokens

  return {
    id: event.eventId,
    time: new Date(event.createdAt).toLocaleTimeString(),
    parentExecutionId: event.parentExecutionId,
    icon: meta.icon,
    label: meta.label,
    detail,
    durationSeconds: payloadNumber(payload, "durationSeconds"),
    tokens: tokens > 0 ? tokens : undefined
  }
}

interface TaskExecutionTimelineProps {
  taskId: string
  live?: boolean
}

export function TaskExecutionTimeline({ taskId, live = true }: TaskExecutionTimelineProps) {
  const queryClient = useQueryClient()
  const scrollRef = useRef<HTMLDivElement>(null)
  const queryKey = useMemo(() => delegatedTaskKeys.events(taskId), [taskId])
  const { data: events = [], isLoading } = useQuery({
    queryKey,
    queryFn: () => delegatedTaskApi.listEvents(taskId)
  })

  useEffect(() => {
    if (!live) return

    const source = new EventSource(getDelegatedTaskEventStreamUrl(taskId), {
      withCredentials: true
    })
    const handleEvent = (message: MessageEvent<string>) => {
      const event = parseDelegatedTaskEvent(message.data)
      if (!event) return
      queryClient.setQueryData<DelegatedTaskEventVO[]>(queryKey, (current = []) => {
        if (current.some((item) => item.eventId === event.eventId)) return current
        return [...current, event].toSorted((left, right) => left.eventOffset - right.eventOffset)
      })
    }

    for (const type of DELEGATED_TASK_EVENT_TYPES) {
      source.addEventListener(type, handleEvent)
    }

    return () => source.close()
  }, [live, queryClient, queryKey, taskId])

  const items = events.map(parseEvent)
  const eventCount = events.length

  useEffect(() => {
    if (eventCount === 0) return
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" })
  }, [eventCount])

  if (isLoading) {
    return <p className="py-4 text-center text-muted-foreground text-sm">正在加载执行记录…</p>
  }
  if (items.length === 0) {
    return <p className="py-4 text-center text-muted-foreground text-sm">暂无执行记录</p>
  }

  return (
    <ScrollArea className="h-80" ref={scrollRef}>
      <div className="flex flex-col gap-1 p-2">
        {items.map((item) => (
          <div key={item.id} className="flex items-start gap-2 rounded px-2 py-1 hover:bg-muted/50">
            <span className="shrink-0 text-sm">{item.icon}</span>
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="font-medium text-sm">{item.label}</span>
                {item.parentExecutionId ? (
                  <Badge variant="outline" className="max-w-32 truncate text-xs">
                    {item.parentExecutionId}
                  </Badge>
                ) : null}
                <span className="ml-auto text-muted-foreground text-xs">{item.time}</span>
              </div>
              {item.detail ? (
                <p className="truncate text-muted-foreground text-xs">{item.detail}</p>
              ) : null}
              {item.durationSeconds || item.tokens ? (
                <div className="mt-0.5 flex gap-3 text-muted-foreground text-xs">
                  {item.durationSeconds ? <span>⏱ {item.durationSeconds.toFixed(1)}s</span> : null}
                  {item.tokens ? <span>🪙 {item.tokens} tokens</span> : null}
                </div>
              ) : null}
            </div>
          </div>
        ))}
      </div>
    </ScrollArea>
  )
}
