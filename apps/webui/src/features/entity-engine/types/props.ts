/**
 * 组件 Props 契约——表单字段和列表单元格的统一接口
 * @author AaronZZH & Kiro
 */

import type { FieldDef } from "./field"

/** 表单字段组件 Props */
export interface FieldProps<T = unknown> {
  name: string
  value: T
  onChange: (value: T) => void
  error?: string
  disabled?: boolean
  field: FieldDef
}

/** 列表单元格组件 Props */
export interface CellProps<T = unknown> {
  value: T
  record: Record<string, unknown>
  field: FieldDef
}
