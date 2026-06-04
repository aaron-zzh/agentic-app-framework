/**
 * useCalendarEvents——日历视图事件查询与变更 Hook
 * @author AaronZZH & Kiro
 */

import { useMutation, useQueryClient } from "@tanstack/react-query"
import { fromEntityDef } from "@/lib/api/rest/crud"
import { updateRecord } from "@/lib/api/rest/entity/crud"
import type { EntityDef } from "@/lib/types/entity"

export interface CalendarEvent {
  id: string
  title: string
  start: string
  end?: string
  allDay?: boolean
  color?: string
  rrule?: string
  /** 原始记录数据 */
  record: Record<string, unknown>
}

/** 将实体记录映射为日历事件 */
export function mapRecordsToEvents(
  records: Record<string, unknown>[],
  entity: EntityDef
): CalendarEvent[] {
  const config = entity.calendarView
  if (!config) return []

  const { startField, endField, titleField, colorField, allDayField, rruleField } = config

  // 获取颜色字段的选项映射
  const colorFieldDef = entity.fields.find((f) => "name" in f && f.name === colorField)
  const colorMap = new Map<string, string>()
  if (colorFieldDef && "options" in colorFieldDef && colorFieldDef.options) {
    for (const opt of colorFieldDef.options) {
      if (opt.color) colorMap.set(opt.value, opt.color)
    }
  }

  return records
    .filter((r) => r[startField])
    .map((r) => ({
      id: String(r.id),
      title: String(r[titleField] ?? ""),
      start: String(r[startField]),
      end: endField ? (r[endField] ? String(r[endField]) : undefined) : undefined,
      allDay: allDayField ? Boolean(r[allDayField]) : !endField,
      color: colorField ? colorMap.get(String(r[colorField])) : undefined,
      rrule: rruleField ? (r[rruleField] ? String(r[rruleField]) : undefined) : undefined,
      record: r
    }))
}

/** 日历事件时间变更（拖拽/调整大小） */
export function useCalendarEventUpdate(entity: EntityDef) {
  const queryClient = useQueryClient()
  const config = entity.calendarView
  const resource = fromEntityDef(entity)

  return useMutation({
    mutationFn: async (params: { id: string; start: string; end?: string; allDay?: boolean }) => {
      if (!config) throw new Error("未配置日历视图")
      const data: Record<string, unknown> = { [config.startField]: params.start }
      if (config.endField && params.end) {
        data[config.endField] = params.end
      }
      if (config.allDayField !== undefined) {
        data[config.allDayField] = params.allDay
      }
      return updateRecord(resource, params.id, data)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [entity.slug, "list"] })
    }
  })
}
