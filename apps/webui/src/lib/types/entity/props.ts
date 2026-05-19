/**
 * 组件 Props 契约——表单字段和列表单元格的统一接口
 * @author AaronZZH & Kiro
 */

import type { FieldDef, GroupField, RowField, TabsField } from "./field"

/** 数据字段（排除布局字段） */
export type DataFieldDef = Exclude<FieldDef, GroupField | TabsField | RowField>

/** 表单字段组件 Props */
export interface FieldProps<T = unknown> {
  name: string
  value: T
  onChange: (value: T) => void
  error?: string
  disabled?: boolean
  field: DataFieldDef
}

/** 列表单元格组件 Props */
export interface CellProps<T = unknown> {
  value: T
  record: Record<string, unknown>
  field: DataFieldDef
}
