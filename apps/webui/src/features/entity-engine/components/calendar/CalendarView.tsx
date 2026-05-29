/**
 * 日历视图——基于 FullCalendar 实现月/周/日/议程多视图
 * @author AaronZZH & Kiro
 *
 * 用法：
 * ```tsx
 * <CalendarView entity={meetingEntity} data={records} />
 * ```
 */

"use client"

import "./calendar.css"

import type { DateSelectArg, EventClickArg, EventDropArg } from "@fullcalendar/core"
import dayGridPlugin from "@fullcalendar/daygrid"
import type { EventResizeDoneArg } from "@fullcalendar/interaction"
import interactionPlugin from "@fullcalendar/interaction"
import listPlugin from "@fullcalendar/list"
import FullCalendar from "@fullcalendar/react"
import timeGridPlugin from "@fullcalendar/timegrid"
import { useCallback, useMemo, useRef, useState } from "react"

import { useResponsive } from "@/lib/hooks/use-responsive"
import { mapRecordsToEvents, useCalendarEventUpdate } from "@/lib/queries/use-calendar-events"

import type { EntityDef } from "../../types"
import { EventDialog } from "./EventDialog"

interface CalendarViewProps {
  entity: EntityDef
  data?: Record<string, unknown>[]
  loading?: boolean
}

/** 日历视图 */
export function CalendarView({ entity, data = [], loading }: CalendarViewProps) {
  const calendarRef = useRef<FullCalendar>(null)
  const { isMobile } = useResponsive()
  const config = entity.calendarView
  const updateMutation = useCalendarEventUpdate(entity)

  // 弹窗状态
  const [dialogOpen, setDialogOpen] = useState(false)
  const [selectedEvent, setSelectedEvent] = useState<Record<string, unknown> | null>(null)
  const [selectedRange, setSelectedRange] = useState<{ start: string; end: string } | null>(null)

  // 将实体数据映射为日历事件
  const events = useMemo(() => mapRecordsToEvents(data, entity), [data, entity])

  // FullCalendar 事件格式
  const fcEvents = useMemo(
    () =>
      events.map((e) => ({
        id: e.id,
        title: e.title,
        start: e.start,
        end: e.end,
        allDay: e.allDay,
        backgroundColor: e.color,
        borderColor: e.color,
        extendedProps: { record: e.record }
      })),
    [events]
  )

  // 点击空白时段——创建事件
  const handleDateSelect = useCallback((info: DateSelectArg) => {
    setSelectedEvent(null)
    setSelectedRange({ start: info.startStr, end: info.endStr })
    setDialogOpen(true)
  }, [])

  // 点击事件——编辑
  const handleEventClick = useCallback((info: EventClickArg) => {
    const record = info.event.extendedProps.record as Record<string, unknown>
    setSelectedEvent(record)
    setSelectedRange(null)
    setDialogOpen(true)
  }, [])

  // 拖拽移动事件
  const handleEventDrop = useCallback(
    (info: EventDropArg) => {
      updateMutation.mutate({
        id: info.event.id,
        start: info.event.startStr,
        end: info.event.endStr || undefined,
        allDay: info.event.allDay
      })
    },
    [updateMutation]
  )

  // 拖拽调整结束时间
  const handleEventResize = useCallback(
    (info: EventResizeDoneArg) => {
      updateMutation.mutate({
        id: info.event.id,
        start: info.event.startStr,
        end: info.event.endStr || undefined,
        allDay: info.event.allDay
      })
    },
    [updateMutation]
  )

  if (!config) {
    return <p className="p-4 text-muted-foreground text-sm">未配置日历视图</p>
  }

  if (loading) {
    return <CalendarSkeleton />
  }

  // 移动端降级为列表视图
  const defaultView = isMobile ? "listWeek" : (config.defaultView ?? "dayGridMonth")

  return (
    <div className="p-4">
      <FullCalendar
        ref={calendarRef}
        plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin, listPlugin]}
        initialView={defaultView}
        headerToolbar={{
          left: "prev,next today",
          center: "title",
          right: isMobile ? "listWeek" : "dayGridMonth,timeGridWeek,timeGridDay,listWeek"
        }}
        events={fcEvents}
        editable
        selectable
        selectMirror
        dayMaxEvents
        nowIndicator
        locale="zh-cn"
        buttonText={{
          today: "今天",
          month: "月",
          week: "周",
          day: "日",
          list: "议程"
        }}
        select={handleDateSelect}
        eventClick={handleEventClick}
        eventDrop={handleEventDrop}
        eventResize={handleEventResize}
        height="auto"
      />

      <EventDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        entity={entity}
        record={selectedEvent}
        defaultRange={selectedRange}
      />
    </div>
  )
}

/** 日历骨架屏 */
function CalendarSkeleton() {
  return (
    <div className="space-y-4 p-4">
      <div className="flex items-center justify-between">
        <div className="h-8 w-32 animate-pulse rounded bg-muted" />
        <div className="h-8 w-48 animate-pulse rounded bg-muted" />
      </div>
      <div className="grid grid-cols-7 gap-1">
        {Array.from({ length: 35 }).map((_, i) => (
          <div key={i} className="h-20 animate-pulse rounded bg-muted" />
        ))}
      </div>
    </div>
  )
}
