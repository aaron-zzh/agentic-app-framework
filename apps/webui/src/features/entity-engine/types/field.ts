import type { ComponentType } from "react"

import type { CellProps, FieldProps } from "./props"

/** 所有字段类型共享的基础属性 */
interface BaseFieldDef {
  name: string
  label?: string
  required?: boolean
  hidden?: boolean
  readOnly?: boolean
  defaultValue?: unknown
  placeholder?: string
  description?: string
  /** 字段级组件覆盖 */
  components?: {
    Field?: ComponentType<FieldProps>
    Cell?: ComponentType<CellProps>
  }
}

/** 字段定义联合类型 */
export type FieldDef =
  | TextField
  | TextareaField
  | NumberField
  | EmailField
  | DateField
  | CheckboxField
  | SelectField
  | RelationshipField
  | RichTextField
  | JsonField
  | CodeField
  | UploadField
  | GroupField
  | TabsField
  | RowField

// —— 数据字段 ——

export interface TextField extends BaseFieldDef {
  type: "text"
  maxLength?: number
  minLength?: number
}

export interface TextareaField extends BaseFieldDef {
  type: "textarea"
  maxLength?: number
  rows?: number
}

export interface NumberField extends BaseFieldDef {
  type: "number"
  min?: number
  max?: number
  step?: number
}

export interface EmailField extends BaseFieldDef {
  type: "email"
}

export interface DateField extends BaseFieldDef {
  type: "date"
  includeTime?: boolean
}

export interface CheckboxField extends BaseFieldDef {
  type: "checkbox"
}

export interface SelectField extends BaseFieldDef {
  type: "select"
  options: SelectOption[]
  multiple?: boolean
}

export interface SelectOption {
  label: string
  value: string
  color?: string
  icon?: string
}

export interface RelationshipField extends BaseFieldDef {
  type: "relationship"
  /** 目标实体 slug */
  relationTo: string
  /** 一对多 */
  hasMany?: boolean
  /** 目标实体用于展示的字段 */
  displayField?: string
  /** 在父表单内嵌套编辑（子表模式） */
  inline?: boolean
}

export interface RichTextField extends BaseFieldDef {
  type: "richText"
  /** 启用 Yjs 实时协同 */
  collaboration?: boolean
}

export interface JsonField extends BaseFieldDef {
  type: "json"
}

export interface CodeField extends BaseFieldDef {
  type: "code"
  language?: string
}

export interface UploadField extends BaseFieldDef {
  type: "upload"
  accept?: string
  maxSize?: number
  multiple?: boolean
}

// —— 布局字段 ——

export interface GroupField {
  type: "group"
  label: string
  fields: FieldDef[]
  collapsible?: boolean
  defaultCollapsed?: boolean
}

export interface TabsField {
  type: "tabs"
  tabs: { label: string; fields: FieldDef[] }[]
}

export interface RowField {
  type: "row"
  fields: (FieldDef & { width?: string })[]
}
