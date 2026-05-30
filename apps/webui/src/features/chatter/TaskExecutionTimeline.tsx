"use client"

/**
 * TaskExecutionTimeline——实时展示助理任务执行状态
 * 包含：实例、步骤、工具调用、积分消耗、耗时
 */

import { useEffect, useRef, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { ScrollArea } from "@/components/ui/scroll-area"

/** 事件类型 */
interface TaskEventData {
  id: number
  taskId: number
  executionId: number | null
  subtaskKey: string | null
  type: string
  payloadJson: string | null
  createTime: string
}

/** 解析后的事件展示 */
interface TimelineItem {
  id: number
  time: string
  type: string
  subtask: string | null
  icon: string
  label: string
  detail: string | null
  duration?: number
  tokens?: number
}

const EVENT_META: Record<string, { icon: string; label: string }> = {
  execution_created: { icon: "🆕", label: "创建执行" },
  task_started: { icon: "▶️", label: "开始执行" },
  subtask_forked: { icon: "🔀", label: "Fork 子任务" },
  step_started: { icon: "⏩", label: "步骤开始" },
  step_completed: { icon: "✅", label: "步骤完成" },
  tool_called: { icon: "🔧", label: "工具调用" },
  tool_completed: { icon: "🔧", label: "工具完成" },
  checkpoint_saved: { icon: "💾", label: "检查点保存" },
  subtask_completed: { icon: "✅", label: "子任务完成" },
  join_completed: { icon: "🔗", label: "聚合完成" },
  task_completed: { icon: "🎉", label: "任务完成" },
  task_failed: { icon: "❌", label: "任务失败" },
  error: { icon: "⚠️", label: "错误" }
}

function parseEvent(event: TaskEventData): TimelineItem {
  const meta = EVENT_META[event.type] ?? { icon: "📌", label: event.type }
  let detail: string | null = null
  let duration: number | undefined
  let tokens: number | undefined

  if (event.payloadJson) {
    try {
      const payload = JSON.parse(event.payloadJson)
      if (payload.title) detail = payload.title
      if (payload.description) detail = payload.description
      if (payload.message) detail = payload.message
      if (payload.role) detail = `角色: ${payload.role}`
      if (payload.tool) detail = `工具: ${payload.tool}`
      if (payload.duration_ms) duration = payload.duration_ms
      if (payload.tokens) tokens = payload.tokens
      if (payload.done !== undefined) detail = `完成: ${payload.done}/${payload.done + (payload.failed ?? 0)}`
    } catch {
      // ignore
    }
  }

  return {
    id: event.id,
    time: new Date(event.createTime).toLocaleTimeString(),
    type: event.type,
    subtask: event.subtaskKey,
    icon: meta.icon,
    label: meta.label,
    detail,
    duration,
    tokens
  }
}

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? ""

interface TaskExecutionTimelineProps {
  taskId: number
  /** 是否实时订阅 SSE */
  live?: boolean
}

export function TaskExecutionTimeline({ taskId, live = true }: TaskExecutionTimelineProps) {
  const [items, setItems] = useState<TimelineItem[]>([])
  const scrollRef = useRef<HTMLDivElement>(null)

  // 加载历史事件
  useEffect(() => {
    fetch(`${API_BASE}/api/chat/tasks/${taskId}/events`)
      .then((r) => r.json())
      .then((res: { data: TaskEventData[] }) => {
        if (res.data) {
          setItems(res.data.map(parseEvent))
        }
      })
      .catch(() => {})
  }, [taskId])

  // SSE 实时订阅（指数退避重连）
  useEffect(() => {
    if (!live) return

    let retries = 0
    const MAX_SSE_RETRIES = 5
    let timer: ReturnType<typeof setTimeout> | null = null
    let source: EventSource | null = null

    function createSource() {
      source = new EventSource(`${API_BASE}/api/chat/tasks/${taskId}/events/stream`)

      const handleEvent = (e: MessageEvent) => {
        const event: TaskEventData = JSON.parse(e.data)
        setItems((prev) => [...prev, parseEvent(event)])
      }

      for (const type of Object.keys(EVENT_META)) {
        source.addEventListener(type, handleEvent)
      }

      source.onopen = () => {
        retries = 0
      }

      source.onerror = () => {
        source?.close()
        source = null
        if (retries >= MAX_SSE_RETRIES) return
        const delay = Math.min(1000 * 2 ** retries, 30000)
        retries += 1
        timer = setTimeout(createSource, delay)
      }
    }

    createSource()

    return () => {
      source?.close()
      if (timer) clearTimeout(timer)
    }
  }, [taskId, live])

  // 自动滚动到底部
  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" })
  }, [items.length])

  if (items.length === 0) {
    return <p className="text-muted-foreground py-4 text-center text-sm">暂无执行记录</p>
  }

  return (
    <ScrollArea className="h-80" ref={scrollRef}>
      <div className="space-y-1 p-2">
        {items.map((item) => (
          <div key={item.id} className="flex items-start gap-2 rounded px-2 py-1 hover:bg-muted/50">
            <span className="shrink-0 text-sm">{item.icon}</span>
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium">{item.label}</span>
                {item.subtask && (
                  <Badge variant="outline" className="text-xs">
                    {item.subtask}
                  </Badge>
                )}
                <span className="text-muted-foreground ml-auto text-xs">{item.time}</span>
              </div>
              {item.detail && (
                <p className="text-muted-foreground truncate text-xs">{item.detail}</p>
              )}
              {(item.duration || item.tokens) && (
                <div className="mt-0.5 flex gap-3 text-xs text-muted-foreground">
                  {item.duration && <span>⏱ {(item.duration / 1000).toFixed(1)}s</span>}
                  {item.tokens && <span>🪙 {item.tokens} tokens</span>}
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    </ScrollArea>
  )
}
