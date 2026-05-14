/**
 * 列表单元格组件集
 * @author AaronZZH & Kiro
 */

import type { CellProps, SelectField } from "../../types"

/** 文本单元格 */
export function TextCell({ value }: CellProps<string>) {
  return <span className="truncate">{value ?? "—"}</span>
}

/** 日期单元格 */
export function DateCell({ value }: CellProps<string>) {
  if (!value) return <span className="text-muted-foreground">—</span>
  const date = new Date(value)
  return <span>{date.toLocaleDateString("zh-CN")}</span>
}

/** 徽章单元格（select 字段） */
export function BadgeCell({ value, field }: CellProps<string>) {
  const selectField = field as SelectField
  const option = selectField.options?.find((o) => o.value === value)
  if (!option) return <span className="text-muted-foreground">—</span>
  return (
    <span className="inline-flex items-center rounded-full bg-secondary px-2 py-0.5 font-medium text-secondary-foreground text-xs">
      {option.label}
    </span>
  )
}

/** 复选框单元格 */
export function CheckCell({ value }: CellProps<boolean>) {
  return <span>{value ? "✓" : "—"}</span>
}
