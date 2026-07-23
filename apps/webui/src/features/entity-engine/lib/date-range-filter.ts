/**
 * 日期范围筛选条件——统一快捷筛选、搜索栏与高级搜索的日期条件语义。
 * @author AaronZZH & Kiro
 */

import type { FilterCondition } from "@/lib/types/entity"

export interface DateRangeValues {
  start: string
  end: string
}

/** 将已有日期筛选条件还原为范围输入的起止值。 */
export function getDateRangeValues(filter: FilterCondition | undefined): DateRangeValues {
  const value = filter?.values[0] ?? ""
  switch (filter?.operator) {
    case "between":
      return { start: value, end: filter.values[1] ?? "" }
    case "gt":
    case "gte":
      return { start: value, end: "" }
    case "lt":
    case "lte":
      return { start: "", end: value }
    case "eq":
      return { start: value, end: value }
    default:
      return { start: "", end: "" }
  }
}

/** 根据已填写的日期边界构造后端认可的筛选条件；两端为空时不产生条件。 */
export function buildDateRangeFilter(
  field: string,
  start: string,
  end: string
): FilterCondition | undefined {
  if (start && end) return { field, operator: "between", values: [start, end] }
  if (start) return { field, operator: "gte", values: [start] }
  if (end) return { field, operator: "lte", values: [end] }
  return undefined
}
